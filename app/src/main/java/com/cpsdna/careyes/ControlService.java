package com.cpsdna.careyes;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClientOption;
import com.cpsdna.careyes.entity.CommandMedia;
import com.cpsdna.careyes.manager.BLEManager;
import com.cpsdna.careyes.manager.LocalTaskManager;
import com.cpsdna.careyes.manager.NetCallback;
import com.cpsdna.careyes.manager.NetManager;
import com.cpsdna.careyes.manager.OBDCallback;
import com.cpsdna.careyes.manager.OBDManager;
import com.cpsdna.careyes.manager.UpdateNetManager;
import com.cpsdna.careyes.utility.UtilityTools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import xcoding.commons.lbs.amap.AMapLocationWrapper;
import xcoding.commons.lbs.amap.LocationListener;
import xcoding.commons.lbs.amap.LocationManager;
import xcoding.commons.telephony.SmsUtils;
import xcoding.commons.telephony.receiver.SmsReceiver;
import xcoding.commons.telephony.receiver.SmsSendCallback;
import xcoding.commons.ui.GenericActivity;
import xcoding.commons.util.LogManager;
import xcoding.commons.util.StringUtilities;

public class ControlService extends Service {

    private LocationManager locationMgr;
    private LocationListener listener;
    private byte[] updateIpData;
    private byte[] countData;

    private Timer timer = null;

    private BLEManager mBLEManager  =null;
    private int obdwatch = 2;
    private int netwatch = 20;
    private boolean obdwatchstate = false;
    private boolean netwatchstate = false;
    private boolean gpswatchstate = false;
    private final String BROADCAST_ACTION = "com.action.state";
    private long lasttime;
    private final long TIMEOUT  = 30000;    // 间隔30s时间
    private static Handler QUEUE_HANDLER = null;
    private Calendar nowTime;
    private double totalDistances = 0.0;
    private  float bearing = 0;

    private AMapLocation lastLocaltion;
    static {
        new Thread() {
            @Override
            public void run() {
                super.run();
                Looper.prepare();
                QUEUE_HANDLER = new Handler();
                Looper.loop();
            }
        }.start();
    }

    private static void postToQueue(Runnable runnable) {
        while (QUEUE_HANDLER == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        QUEUE_HANDLER.post(runnable);
    }

    private SmsReceiver receiver;

    private HttpServer server;

    TimerTask task = new TimerTask() {
        public void run() {

                obdwatch--;
                netwatch--;
                if (obdwatch < 0) {
                    obdwatchstate = false;
                } else {
                    obdwatchstate = true;
                }
                if (netwatch < 0) {
                    netwatchstate = false;
                } else {
                    netwatchstate = true;
                }
            Intent intent = new Intent();
            intent.setAction(BROADCAST_ACTION);
            intent.putExtra("netwatchstate",netwatchstate );
            intent.putExtra("obdwatchstate",obdwatchstate );
            intent.putExtra("gpswatchstate",gpswatchstate );
            sendBroadcast(intent);
            }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        openNetwork();
        openLocationManager();
        openOBD();
        lasttime = System.currentTimeMillis();

        server = new HttpServer(this);
        try {
            server.start();
        } catch (IOException e) {
            LogManager.logE(ControlService.class, "start http server failed.", e);
        }

        timer = new Timer(true);
        timer.schedule(task, 0, 2000); //延时1000ms后执行，1000ms执行一次

        mBLEManager = new BLEManager(this);
        mBLEManager.BLEStart();
        registerSmsReceiver();
        LogManager.logE(NetManager.class, "registerSmsReceiver ");
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String taskId = intent.getStringExtra("TASK");
        if (taskId != null) {
            String dataStr = intent.getStringExtra("DATA");
            LocalTaskManager.updateTask(taskId, dataStr);
        }
        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        LocationManager locationMgr = LocationManager.getInstance(this);
        locationMgr.unregisterListener(listener);
        locationMgr.stopLocation();
        NetManager.stop();
        try
        {
            OBDManager.close();
        } catch (IOException e)
        {
            LogManager.logE(ControlService.class, "关闭OBD串口失败", e);
        }
        receiver.unregisterMe();
        server.stop();
        mBLEManager.BLEStop();
        timer.cancel();
    }

    // 注册短信监听
    public void registerSmsReceiver()
    {
        receiver = new SmsReceiver(this)
        {
            @Override
            public void onReceive(SmsMessage[] msg)
            {
                super.onReceive(msg);
                String msgContent = msg[0].getDisplayMessageBody();
                String msgAddress = msg[0].getDisplayOriginatingAddress();
                if (msgContent.startsWith("AT+"))
                {
                    Log.i("zhangw", "收到短信的发送对象:  " + msgAddress);
                    Log.i("zhangw", "收到短信的内容: " + msgContent);
                    sendMessage2OBD(msgAddress, msgContent);
                }
            }
        };
        receiver.registerMe(0);
    }

    // 发送短信的功能
    public void sendMessage2User(String toUser, String content)
    {
        SmsUtils.sendTextMessage(this, toUser, content, new SmsSendCallback(this)
        {
            @Override
            public void onSendSuccess()
            {
                super.onSendSuccess();
                Log.i("zhangw", "短信发送成功");
            }

            @Override
            public void onSendFailure()
            {
                super.onSendFailure();
                Log.e("zhangw", "短信发送失败");
            }

            @Override
            public void onTimeout()
            {
                super.onTimeout();
                Log.e("zhangw", "短信发送超时");
            }
        }, 15000);

    }

    public void sendMessage2OBD(String phoneNumber, String content)
    {
        phoneNumber = phoneNumber + "F";
        if (phoneNumber.length() < 20)
        {
            for (int i = phoneNumber.length(); i < 20; i++)
            {
                phoneNumber = phoneNumber + "0";
            }
        }
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(message);
        try
        {
            out.write(UtilityTools.str2Bcd(phoneNumber));
            byte contentByte[] = content.getBytes();// String转换为byte[]
            out.write(contentByte);
            Log.e("zhangw", "短信透传至OBD的数据" + UtilityTools.bytesToHexString(message.toByteArray()));
            OBDManager.write(0x8001, message.toByteArray());
        } catch (IOException e)
        {
            LogManager.logE(ControlService.class, "短信透传至OBD时发生错误", e);
        }
    }

    @SuppressLint("ShowToast")
    public void openLocationManager()
    {
        Toast.makeText(getApplicationContext(), "定位启动", 2).show();
        // 定位处理
        locationMgr = LocationManager.getInstance(this);
        locationMgr.updateOption(new LocationManager.UpdateHandler()
        {
            @Override
            public void onHandle(AMapLocationClientOption arg0)
            {
                arg0.setInterval(1000);
            }
        });
        locationMgr.startLocation();


        locationMgr.registerListener(listener = new LocationListener() {
            @Override
            public void onError(final AMapLocation arg0) {
                LogManager.logE(ControlService.class, "[路眼]定位失败(" + arg0.getErrorInfo() + ")");
                gpswatchstate = false;
            }

            @Override
            public void onChanged(final AMapLocation arg0) {
                postToQueue(new Runnable() {
                    @Override
                    public void run() {
                        gpswatchstate = true;
//                        1;根据GPS方向角变化进行上报，与上次上报的GPS角度比较，变化20度上报一次。
//                        2：根据GPS速度计算行驶距离，每行驶200M上报一次。
//                        3：根据至少俩次上报间隔时间在30秒一次来上报位置信息。
//                        #4：熄火或者点火时上报一条位置数据
//                        5：整分钟上报一条GPS数据。
//                        满足上述触发条件的任何一条都会进行位置数据上报，并刷新上报的时间和角度以及累计的距离。

                        boolean needReport = true;
                        needReport = checkReportGPS(arg0, System.currentTimeMillis());
                        if (needReport) {
                            try {
                                LogManager.logE(ControlService.class, "writeGPSInfo" + arg0.getCity());
                                NetManager.writeGPSInfo(arg0); // 上报定位信息
                            } catch (IOException e) {
                                LogManager.logE(ControlService.class, "[路眼]向网关上报定位数据失败", e);
                            }
                        }
                        try {
                            OBDManager.writeGPSInfo(arg0); // 下发定位信息
                        } catch (IOException e) {
                            LogManager.logE(ControlService.class, "[路眼]向OBD下发定位数据失败", e);
                        }
                        lastLocaltion = arg0;
                    }
                });

            }
        });
    }

    public void openNetwork()
    {
        LogManager.logI(ControlService.class, "openNetwork now...");
        // 网关处理
        NetManager.start(new NetCallback()
        {
            @Override
            protected void onReceive(byte id, byte[] data, byte[] allData)
            {
                super.onReceive(id, data, allData);
                netwatch = 20;
                int idInt = 0xFF & id;
                LogManager.logI(ControlService.class, "[网关:0x" + Integer.toHexString(idInt) + "]接收到指令...");

                if (idInt == 0x73)
                {
                    boolean couldFeedback = false;
                    int curId = 0;
                    byte response;
                    DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
                    try
                    {
                        input.read(); // Params Number
                        curId = input.readInt(); // 操作指令流水号，唯一标识
                        couldFeedback = true;
                        int type = input.readInt(); // 操作指令类型
                        if (type == 0x00000001)
                        { // 多媒体拍摄
                            input.readShort(); // 操作指令长度
                            CommandMedia media = new CommandMedia();
                            media.id = curId;
                            media.type = input.readByte();
                            media.command = input.readShort();
                            media.totalTime = input.readShort();
                            media.saveSign = input.readByte();
                            media.resolution = input.readByte();
                            media.quality = input.readByte();
                            media.light = input.readByte();
                            media.contrast = input.readByte();
                            media.saturation = input.readByte();
                            media.chroma = input.readByte();
                            byte[] taskIdArr = new byte[input.readByte()];
                            input.readFully(taskIdArr);
                            media.taskId = new String(taskIdArr, "UTF-8");
                            AMapLocationWrapper curLocation = LocationManager.getInstance(ControlService.this).getLastPersistentLocation();
                            if (curLocation != null)
                            {
                                media.isLocation = true;
                                media.posLat = curLocation.getLatitude();
                                media.posLng = curLocation.getLongitude();
                                media.posAddr = StringUtilities.toStringWhenNull(curLocation.getAddress(), "");
                            }
                            Bundle bundle = new Bundle();
                            bundle.putSerializable("INFO", media);
                            GenericActivity.sendRefresh(ControlService.this, WelcomeActivity.REFRESHTYPE_COMMAND_REQUEST, bundle);
                            response = 0x00;
                        } else
                        {
                            response = 0x02;
                        }
                    } catch (IOException e)
                    {
                        LogManager.logE(ControlService.class, "[网关:0x" + Integer.toHexString(idInt) + "]处理指令时发生错误", e);
                        response = 0x01;
                    }

                    // 反馈
                    if (couldFeedback)
                    {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        DataOutputStream dOut = new DataOutputStream(out);
                        try
                        {
                            dOut.write(1);
                            dOut.writeInt(curId);
                            dOut.write(response);
                            NetManager.writeInfo(0x74, out.toByteArray());
                            LogManager.logI(ControlService.class, "[路眼]向网关反馈操作下发结果成功");
                        } catch (IOException e)
                        {
                            LogManager.logE(ControlService.class, "[路眼]向网关反馈操作下发结果时发生错误", e);
                        }
                    }
                } else
                {
                    // // 检测是否是更新请求（暂留）
                    // byte[] checkData = new byte[4];
                    // for (int i = 0; i < 4; i++) {
                    // checkData[i] = allData[i];
                    // }
                    // if (UtilityTools.bytesToHexString(checkData)
                    // .startsWith("FAFA")
                    // && UtilityTools.bytesToHexString(checkData)
                    // .endsWith("C1")) {
                    // Log.e("xxxxxx", "接收到网关下发的升级指令");
                    // reOpenOBD();
                    // }
                    ByteArrayOutputStream address = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(address);
                    try
                    {
                        out.write(new byte[] { 0, 0, 0, 0 });
                        out.writeShort(0);
                        out.write(allData);
                        OBDManager.write(0x8000, address.toByteArray());
                    } catch (IOException e)
                    {
                        LogManager.logE(ControlService.class, "[网关:0x" + Integer.toHexString(idInt) + "]透传至OBD时发生错误", e);
                    }
                }
            }
        });

        new Thread() {
            public void run() {
                while (!NetManager.getScoketStat()) {
                    try {
                        Thread.sleep(500); // 等待5秒后重试
                    } catch (InterruptedException e) {
                        LogManager.logE(NetManager.class, "", e);
                    }
                }
                LogManager.logI(ControlService.class, "reportSIMMsg");
                reportSIMMsg();

            }
        }.start();
    }

    // 升级OBD网关处理
    private void updateOBD(String ip, int port)
    {
        UpdateNetManager.start(ip, port, new NetCallback()
        {
            @Override
            protected void onReceive(byte id, byte[] data, byte[] allData)
            {
                super.onReceive(id, data, allData);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bos);
                try
                {
                    if (updateIpData.length > 0)
                    {
                        out.write(updateIpData);
                    } else
                    {
                        out.write(new byte[] { 0, 0, 0, 0 });
                        out.writeShort(0);
                    }
                    out.write(allData);
                    Log.w("--------", "OBD的升级数据" + UtilityTools.bytesToHexString(bos.toByteArray()));
                    OBDManager.write(0x8000, countData, bos.toByteArray());
                } catch (IOException e)
                {
                    LogManager.logE(ControlService.class, "OBD升级时发生错误", e);
                }
            }
        });
    }

    // OBD处理
    public void openOBD()
    {
        try
        {
            OBDManager.open(getApplicationContext(), new OBDCallback()
            {
                private int    eventLen = 50;
                private byte[] eventIdData;

                @Override
                protected void onReceive(short id, byte[] data, byte[] allData)
                {
                    super.onReceive(id, data, allData);
                    obdwatch = 1;
                    int idInt = 0xFFFF & id;
                    LogManager.logI(ControlService.class, "[OBD:0x" + Integer.toHexString(idInt) + "]接收到指令...");
                    if (idInt == 0x0000)
                    { // 透传
                        int len = data.length;
                        if (len < 6)
                        {
                            LogManager.logE(ControlService.class, "[OBD:0x" + Integer.toHexString(idInt) + "]数据格式有误，忽略...");
                            return;
                        }
                        byte[] filterData = new byte[len - 6];
                        for (int i = 0; i < filterData.length; i++)
                        {
                            filterData[i] = data[i + 6];
                        }
                        // 检测是否是更新请求
                        byte[] checkData = new byte[4];
                        for (int i = 0; i < 4; i++)
                        {
                            checkData[i] = filterData[i];
                        }
                        if (UtilityTools.bytesToHexString(checkData).startsWith("FAFA") && UtilityTools.bytesToHexString(checkData).endsWith("C2"))
                        {
                            Log.e("xxxxxx", "接收到OBD升级指令");
                            byte[] ipData = new byte[6];
                            for (int i = 0; i < 6; i++)
                            {
                                ipData[i] = data[i];
                            }
                            updateIpData = ipData;
                            countData = new byte[2];
                            for (int i = 4; i < 6; i++)
                            {
                                countData[i - 4] = allData[i];
                            }
                            String ipString = UtilityTools.bytesToHexString(ipData);

                            updateOBD(UtilityTools.getRemoteIp(ipString), UtilityTools.getRemotePort(ipString));
                            try
                            {
                                Log.w("xxxxxx", "发送升级请求到网关" + UtilityTools.bytesToHexString(filterData));
                                UpdateNetManager.writeDirect(filterData);
                            } catch (IOException e)
                            {
                                LogManager.logE(ControlService.class, "[OBD:0x" + Integer.toHexString(idInt) + "]发送升级请求到网关时发生错误", e);
                            }
                        } else
                        {
                            try
                            {
                                NetManager.writeDirect(filterData);
                            } catch (IOException e)
                            {
                                LogManager.logE(ControlService.class, "[OBD:0x" + Integer.toHexString(idInt) + "]透传至网关时发生错误", e);
                            }
                        }
                    } else if (idInt == 0x0002)
                    {// obd心跳包
                        //parse obd heart package
                        int len = data.length;
                        LogManager.logE(ControlService.class, "[OBD] heart beat parse.");
                        int index = 0;
                        // serial number
                        int serialNumber = ((data[index]&0xFF)<<8) | (data[index+1]&0xFF);
                        index = index + 2;
                        //gid status, 5a--response, a5/aa just give up
                        int gidStatus  = data[index]&0xFF;
                        index = index + 1;
                        if(gidStatus == 0x5A) {
                            //response to obd parter
                            //obd parter status
                            int obdStatus = data[index]&0xFF;
                            index = index + 1;
                            //parameter number
                            int parameterNumber = data[index]&0xFF;
                            index = index + 1;
                            for(int i=0; i<parameterNumber; i++) {
                                if(len <= index) {
                                    break;
                                }
                                //id
                                int paramerId = ((data[index]&0xFF)<<8) | (data[index+1]&0xFF);
                                index = index + 2;
                                //id len
                                int parameterLen = data[index]&0xFF;
                                index = index + 1;
                                //data
                                LogManager.logE(ControlService.class, "[OBD] heart beat parameterid:" + paramerId);
                                if(paramerId == 0x0001) {
                                    //rpm
                                    int rpm = ((data[index]&0xFF)<<8) | (data[index]&0xFF);
                                    index = index + parameterLen;
                                } else if (paramerId == 0x0002) {
                                    //speed
                                    int obdSpeed = ((data[index]&0xFF)<<8) | (data[index]&0xFF);
                                    index = index + parameterLen;
                                } else if (paramerId == 0x0003) {
                                    //voltage
                                    int value = ((data[index]&0xFF)<<8) | (data[index]&0xFF);
                                    float voltage = (float)(value*0.1);
                                    index = index + parameterLen;
                                } else if( paramerId == 0x0004) {
                                    //temperature
                                    int temperature = (data[index]&0xFF) - 40;
                                    index = index + parameterLen;
                                } else if( paramerId == 0x0005) {
                                    //trouble code
                                    int troubleNumber = parameterNumber/5;
                                    String troubleStr = null;
                                    for(int j=0; j<troubleNumber; j++) {
                                        byte[]  dtc = new byte[5];
                                        System.arraycopy(data, index, dtc, 0, 5);
                                        String value = new String(dtc);
                                        troubleStr = troubleStr + value + ";";
                                        index = index + 5;
                                    }
                                    index = index + parameterLen;
                                }

                            }
                        }


                        try
                        {
                            AMapLocation arg0 = locationMgr.getLastKnownLocation();
                            if (arg0 != null)
                            {
                                OBDManager.writeGPSInfo(arg0); // 下发定位信息
                            }
                        } catch (IOException e)
                        {
                            LogManager.logE(ControlService.class, "[路眼]向OBD下发定位数据失败", e);
                        }
                    } else if (idInt == 0x0001)
                    {// OBD短信上发请求
                        int len = data.length;
                        if (len < 6)
                        {
                            LogManager.logE(ControlService.class, "[OBD:0x" + Integer.toHexString(idInt) + "]数据格式有误，忽略...");
                            return;
                        }
                        // 获取短信号码和短信内容;
                        String phoneNumber = "";
                        for (int i = 0; i < 10; i++)
                        {
                            StringBuffer temp = new StringBuffer();
                            temp.append((byte) ((data[i] & 0xf0) >>> 4));
                            StringBuffer temp2 = new StringBuffer();
                            temp2.append((byte) ((data[i] & 0x0f)));

                            // 高4位低4位分别判断一次 遇到F结尾就停止
                            if (temp.toString().equals("15"))
                            {
                                break;
                            }
                            phoneNumber = phoneNumber + temp.toString();
                            if (temp2.toString().equals("15"))
                            {
                                break;
                            }
                            phoneNumber = phoneNumber + temp2.toString();
                        }
                        byte[] contentData = new byte[data.length - 10];
                        for (int i = 10; i < data.length; i++)
                        {
                            contentData[i - 10] = data[i];
                        }
                        String content = new String(contentData);
                        Log.e("zhangw", "OBD收到短信后的回复:" + UtilityTools.bytesToHexString(data));
                        Log.e("zhangw", "发送短信号码:" + phoneNumber);
                        Log.e("zhangw", "发送短信内容:" + content);
                        sendMessage2User(phoneNumber, content);
                    } else if (idInt == 0x0003)
                    {
                        // 碰撞事件
                        int len = data.length;
                        if (len < 13)
                        {
                            LogManager.logE(ControlService.class, "[OBD:0x" + Integer.toHexString(idInt) + "]数据格式有误，忽略...");
                            return;
                        }
                        int index = 0;
                        // 碰撞事件类型
                        int eventType = data[index] & 0xFF;
                        LogManager.logE(ControlService.class, "[OBD]碰撞事件类型eventType=" + eventType);
                        // 时间
                        index = index + 6;
                        // deviceId
                        index = index + 1;
                        String deviceId = Long.toString(((long) (data[index]) & 0xff) | (((long) (data[index + 1]) & 0xff) << 8) | (((long) (data[index + 2]) & 0xff) << 16)
                                | (((long) (data[index + 3]) & 0xff) << 24) | (((long) (data[index + 4]) & 0xff) << 32) | (((long) (data[index + 5]) & 0xff) << 40));
                        LogManager.logE(ControlService.class, "[OBD]碰撞事件deviceId=" + deviceId);
                        Bundle bundle = new Bundle();
                        AMapLocation arg0 = locationMgr.getLastKnownLocation();
                        try
                        {

                            switch (eventType)
                            {
                                case 0:// 预判碰撞事件
                                    String uuid = UUID.randomUUID().toString();
                                    byte[] uuidData = uuid.getBytes("ASCII");
                                    eventIdData = new byte[eventLen];
                                    if (uuidData.length < eventLen)
                                    {
                                        int count = uuidData.length;
                                        for (int i = 0; i < count; i++)
                                        {
                                            eventIdData[i] = uuidData[i];
                                        }
                                        for (int i = count; i < eventLen; i++)
                                        {
                                            eventIdData[i] = 48;// 字符0
                                        }

                                    } else
                                    {
                                        for (int i = 0; i < eventLen; i++)
                                            eventIdData[i] = uuidData[i];
                                    }
                                    bundle.putBoolean("CONFIRM", false);
                                    bundle.putString("EVENTID", new String(eventIdData, "ASCII"));
                                    if (arg0 == null)
                                    {
                                        bundle.putBoolean("IS_LOCATION", false);
                                    } else
                                    {
                                        bundle.putBoolean("IS_LOCATION", true);
                                        bundle.putDouble("POSLAT", arg0.getLatitude());
                                        bundle.putDouble("POSLNG", arg0.getLongitude());
                                        bundle.putString("POSADDR", arg0.getAddress());
                                    }
                                    GenericActivity.sendRefresh(ControlService.this, WelcomeActivity.REFRESHTYPE_COMMAND_COLLISION, bundle);
                                    LogManager.logE(ControlService.class, "[OBD]碰撞预判事件" + eventType);
                                    // =================测试用=========
                                    Bundle confirmBundle = new Bundle();
                                    confirmBundle.putBoolean("CONFIRM", true);
                                    confirmBundle.putString("EVENTID", new String(eventIdData, "ASCII"));
                                    try
                                    {
                                        NetManager.writeCollisionInfo(arg0, eventIdData);
                                        LogManager.logE(ControlService.class, "[OBD]碰撞事件" + "上报成功");
                                    } catch (IOException e)
                                    {
                                        // TODO Auto-generated catch block
                                        LogManager.logE(ControlService.class, "[OBD]碰撞事件" + "上报失败" + e);
                                    }
                                    GenericActivity.sendRefresh(ControlService.this, WelcomeActivity.REFRESHTYPE_COMMAND_COLLISION, confirmBundle);

                                    break;
                                case 1:// 确认碰撞事件
                                    if (eventIdData == null)
                                    {
                                        LogManager.logE(ControlService.class, "[OBD]碰撞事件" + "预判与确认事件不一致");
                                        return;
                                    } else
                                    {

                                        try
                                        {
                                            NetManager.writeCollisionInfo(arg0, eventIdData);
                                            LogManager.logE(ControlService.class, "[OBD]碰撞事件" + "上报成功");
                                        } catch (IOException e)
                                        {
                                            // TODO Auto-generated catch block
                                            LogManager.logE(ControlService.class, "[OBD]碰撞事件" + "上报失败" + e);
                                        }
                                        bundle.putBoolean("CONFIRM", true);
                                        bundle.putString("EVENTID", new String(eventIdData, "ASCII"));
                                        GenericActivity.sendRefresh(ControlService.this, WelcomeActivity.REFRESHTYPE_COMMAND_COLLISION, bundle);
                                        eventIdData = null;
                                    }
                                    break;
                                default:
                                    break;
                            }

                        } catch (Exception e)
                        {
                            // TODO: handle exception
                            LogManager.logE(ControlService.class, "Collision send data failed" + e);
                        }
                    } else if(idInt == 0x0004){
                        //response for obd parter request
                        int len = data.length;
                       
                        int index = 0;
                        // frame serial
                        int serialNumber = ((data[index]&0xFF)<<8)|(data[index+1]&0xFF);
                        LogManager.logE(ControlService.class, "[OBD]requesttype serial number=" + serialNumber);
                        index = index + 2;
                        // parameter number
                        int parameterNumber = (data[index]&0xFF);
                        index = index + 1;
                        for(int i=0; i<parameterNumber; i++) {
                            try {
                                //if there is no enough data , just give up
                                if(len <=index ) {
                                    //there is no other data
                                    break;
                                }
                                //function id
                                int functionId = ((data[index]&0xFF)<<8) | (data[index+1]&0xFF);
                                index = index + 2;
                                //data length
                                int idLen = data[index]&0xFF;
                                index = index + 1;
                                //data content
                                LogManager.logE(ControlService.class, "[OBD] functionid :" + functionId);
                                if(functionId == 0x0003) {
                                    //get obd parter id
                                    if(idLen == 0x06) {
                                        String deviceId = Long.toString(((long) (data[index]) & 0xff) | (((long) (data[index + 1]) & 0xff) << 8) | (((long) (data[index + 2]) & 0xff) << 16)
                                                | (((long) (data[index + 3]) & 0xff) << 24) | (((long) (data[index + 4]) & 0xff) << 32) | (((long) (data[index + 5]) & 0xff) << 40));
                                        LogManager.logE(ControlService.class, "[OBD] query deviceId=" + deviceId);
                                    } else {
                                        LogManager.logE(ControlService.class, "[OBD] query deviceid id length exception");
                                    }
                                } else if(functionId == 0x0004) {
                                    //obd parter request sim information
                                    //get iccid, imsi
                                    OBDManager.writeSIMInfo(serialNumber);
                                } else if(functionId == 0x0005)  {
                                    //obd parter request base information
                                    OBDManager.writeBASEInfo(serialNumber);
                                } else {
                                    LogManager.logE(ControlService.class, "[OBD] functionid not support:" + functionId);
                                }
                                index = index + idLen;

                            }catch(Exception e ) {
                                LogManager.logE(ControlService.class, "parse OBD 04 type exception", e);
                            }
                        }
                        index = index + 6;
                        // deviceId
                        index = index + 1;
                     }
                }

            });
        } catch (IOException e)
        {
            LogManager.logE(ControlService.class, "打开到OBD的串口失败", e);
        }
    }

    private void reportSIMMsg()
    {
        TelephonyManager telMgr;
        try {
            telMgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            LogManager.logI(ControlService.class, "Get TelephonyManager.....");
            if (telMgr != null) {
                NetManager.getSIMMsg(telMgr);
            } else {
                LogManager.logE(ControlService.class, "Get telephonyManager err.....");
            }
        } catch (IOException e) {
            LogManager.logE(ControlService.class, "Get TelephonyManager exception.....", e);
            e.printStackTrace();
        }
    }
    // 重启OBD串口
    public void reOpenOBD()
    {
        new Handler().postDelayed(new Runnable()
        {
            public void run()
            {
                try
                {
                    OBDManager.reOpenOBDCOM();
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }, 5000);
    }

    // 判断gps数据是否需要上报
    public boolean checkReportGPS(final AMapLocation arg0, long sysTime)
    {
        boolean needSend = false;
        float speed;
        long duration;
        long distance;

        speed = arg0.getSpeed();
        duration = (sysTime - lasttime);
        distance = (long)(speed * duration / 1000);
        nowTime = Calendar.getInstance();
        LogManager.logE(ControlService.class, "checkReportGPS ....." + duration + "::: " + arg0.getTime() + ":::::" + lasttime);

        if (Math.abs(duration) >= TIMEOUT || distance >= 200 || nowTime.get(Calendar.SECOND) == 0) {
            LogManager.logE(ControlService.class, "checkReportGPS true.....");
            needSend = true;
        }

        if ((arg0.getBearing() - bearing + 360) % 360 > 5) {
            needSend = true;
            LogManager.logE(ControlService.class, "checkReportGPS true.....");
        }
        if (needSend) {
            totalDistances += distance;
            lasttime = System.currentTimeMillis();
            bearing = arg0.getBearing();
        }

        return needSend;
    }
}
