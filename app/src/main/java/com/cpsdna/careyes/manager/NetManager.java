package com.cpsdna.careyes.manager;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.amap.api.location.AMapLocation;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.SocketFactory;

import xcoding.commons.lbs.LocationConvert;
import xcoding.commons.lbs.LocationConvert.GeoPoint;
import xcoding.commons.util.LogManager;

/**
 * <p>
 * 除了start()和stop()外，其他方法基本为耗时方法，需在子线程调用
 * 
 * @author zhangwendell
 * 
 */
public final class NetManager
{

    // private static String REMOTE_IP = "58.215.50.50"; // 221.226.93.118
    private static String           REMOTE_IP        = "221.226.93.118";//"58.215.166.25";
    private static int              REMOTE_PORT      = 31498;//4008;           // 31498;
    private static boolean          isStarted        = false;
    private static boolean          isCanceled       = true;

    private static DataOutputStream output           = null;

    private static final byte[]     LOCKER_START     = new byte[0];    // 同步锁
    private static final byte[]     LOCKER_WRITE     = new byte[0];    // 同步锁
    private int                     mCurrentUrgentType = -1;
    private static byte             count            = 0;              // 包序列
    public static boolean                  initSocket       = false;
    private static Handler          CALLBACK_HANDLER = null;
    private long                    mLastGpsTime     = 0;// 最新上报Gps的时间，不重复传同一个点
    private ArrayList<Location>     mGpsData = new ArrayList<Location>();// Gps数据列表
    // 0 急加速 1 急减速 2 急刹车 3 碰撞 4 急转弯
    private static final int URGENT_ACCELERATION = 0;
    private static final int URGENT_DECELERATION = 1;
    private static final int URGENT_BRAKE = 2;
    private static final int URGENT_CRASH = 3;
    private static final int URGENT_WHEEL = 4;
    // 四急变化差值判断
    private static final double URGENT_ACCELERATION_VALUE = 0.2 * 9.8;// 0.2g
    private static final double URGENT_DECELERATION_VALUE = 0.3 * 9.8;// 0.3g
    private static final double URGENT_BRAKE_VALUE = 0.5 * 9.8;// 0.5g
    private static final double URGENT_CRASH_VALUE = 1 * 9.8;// 1g
    // 急转弯参数
    private static final int URGENT_WHEEL_SPEED = 11;// 速度不低于40km/h
    private static final int URGENT_WHEEL_FIRST_BEARING = 13;// 第一个转角不低于13度
    private static final int URGENT_WHEEL_ALL_BEARING = 60;// 总转角不低于60度
    public static final String REFRESHTYPE_COMMAND_REQUEST = "COMMAND_REQUEST";
    private boolean mRunningGps;// Gps读取
    private static final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
    private float[] mAcceleratesParams = null;// 四急参数
    private double mCurrentUrgentAcceleration = 0;// 当前加速度差值，如果进入四急判断状态，则该值为四急状态前一秒数值
    private LocationManager mLocationManager;// 位置管理
    private long mUrgentStartTime = 0;// 四急发生时候的时间，发生时间超过1秒就算
    private Location mUrgentLocationStart = null;// 四急数据第一个点
    private float[] mAcceleratesParamsCurrent = null;// 当前的四急参数
    private static boolean isReport = false;
    static
    {
        new Thread()
        {
            @Override
            public void run()
            {
                super.run();
                Looper.prepare();
                CALLBACK_HANDLER = new Handler();
                Looper.loop();
            }
        }.start();
    }

    private static NetCallback      CALLBACK         = null;

    private NetManager()
    {
    }

    public static void start(NetCallback callback)
    {
        if (callback == null)
            throw new NullPointerException();
        CALLBACK = callback;
        boolean shouldStart = shouldStart();
        if (shouldStart)
        {
            startImpl();
        }
    }
//设置socket状态
    private static void setScoket()
    {
        initSocket = true;
    }
//获取socket状态
    public static boolean getScoketStat()
    {
        return initSocket;
    }
    private static void startImpl()
    {
        new Thread()
        {
            public void run()
            {
                Timer timer = new Timer();
                Socket client = null;
                boolean shouldCancel = false;
                try
                {
                    LogManager.logI(NetManager.class, "startImpl...");
                    if (shouldCancel = shouldCancel())
                    {
                        LogManager.logI(NetManager.class, "shouldCancel = shouldCancel()...");
                        return;
                    }
                    LogManager.logI(NetManager.class, "连接到服务器...Thread");
                    client = SocketFactory.getDefault().createSocket();
                    SocketAddress remoteaddr = new InetSocketAddress(REMOTE_IP, REMOTE_PORT);

                    client.connect(remoteaddr, 20000); // 连接20秒超时
                    client.setSoTimeout(15000); // 读写15秒
                    LogManager.logI(NetManager.class, "已连接，开始监听...");
                    setScoket();
                    final DataOutputStream dos = new DataOutputStream(client.getOutputStream());
                    output = dos;
                    if (shouldCancel = shouldCancel())
                    {
                        return;
                    }

                    client.setSoTimeout(25000); // 修改为25秒超时以响应心跳，心跳时间为15秒
                    timer.schedule(new TimerTask()
                    {
                        @Override
                        public void run()
                        {
                            LogManager.logI(NetManager.class, "发送心跳...");
                            try
                            {
                                writeSync(dos, 0x05, null);
                            } catch (IOException e)
                            {
                                LogManager.logE(NetManager.class, "心跳发送失败", e);
                            }
                        }
                    }, 15000, 15000);
                    DataInputStream dis = new DataInputStream(client.getInputStream());
                    while (true)
                    {
                        if (shouldCancel = shouldCancel())
                        {
                            return;
                        }
                        int first;
                        while (true)
                        {
                            first = dis.read();
                            if (first == -1)
                                throw new IOException("data is at the end.");
                            if (first == 0xFA)
                            {
                                first = dis.read();
                                if (first == -1)
                                    throw new IOException("data is at the end.");
                                if (first == 0xFA)
                                { // 检测到开始
                                    break;
                                }
                            }
                        }
                        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(byteOut);
                        out.writeShort(0xFAFA);
                        final byte[] dataFront = new byte[8];
                        dis.readFully(dataFront);
                        out.write(dataFront);
                        short size = dis.readShort();
                        out.writeShort(size);
                        final byte[] data = new byte[size];
                        dis.readFully(data);
                        out.write(data);
                        int checkSum = dis.read();
                        if (checkSum == -1)
                            throw new IOException("data is at the end.");
                        int end = dis.read(); // 0xFB
                        if (end == -1)
                            throw new IOException("data is at the end.");
                        // check data
                        byte[] curData = byteOut.toByteArray();
                        byte cs = 0x00;
                        for (int i = 2; i < curData.length; i++)
                        {
                            cs = (byte) (cs ^ curData[i]);
                        }
                        if ((0xFF & cs) != checkSum)
                        {
                            LogManager.logE(NetManager.class, "数据校验失败，将忽略当前数据...");
                            continue;
                        }
                        out.write(checkSum);
                        out.write(end);
                        postCallback(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                CALLBACK.onReceive(dataFront[1], data, byteOut.toByteArray());
                                if (!isReport) {
                                    // 等待socket线程状态为开，则上报
                                    while (!getScoketStat()) {
                                        LogManager.logE(NetManager.class, "getScoketStat :  " + NetManager.getScoketStat());
                                        try {
                                            Thread.sleep(500); // 等待5秒后重试
                                        } catch (InterruptedException e) {
                                            LogManager.logE(NetManager.class, "", e);
                                        }
                                    }
                                    //上报软件和软件版本信息
                                    reportMachineMsg();
                                    isReport = true;
                                }
                            }
                        });
                    }

                } catch (Exception e)
                {
                    LogManager.logE(NetManager.class, "当前连接发生异常，将在5秒后重试...", e);
                } finally
                {
                    timer.cancel();
                    try
                    {
                        if (client != null)
                            client.close();
                    } catch (IOException e)
                    {
                        LogManager.logE(NetManager.class, "", e);
                    }
                    if (!shouldCancel)
                    {
                        try
                        {
                            Thread.sleep(5000); // 等待5秒后重试
                        } catch (InterruptedException e)
                        {
                            LogManager.logE(NetManager.class, "", e);
                        }
                        startImpl();
                    }
                }
            }
        }.start();
    }

    private static boolean shouldStart()
    {
        synchronized (LOCKER_START)
        {
            if (!isStarted)
            {
                isStarted = true;
                if (isCanceled)
                {
                    isCanceled = false;
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean shouldCancel()
    {
        synchronized (LOCKER_START)
        {
            if (!isStarted)
            {
                isCanceled = true;
                output = null;
                return true;
            }
            return false;
        }
    }

    public static void stop()
    {
        isStarted = false;
    }

    private static void reportMachineMsg()
    {
        try {
            NetManager.getAPKVersion();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void postCallback(Runnable runnable)
    {
        while (CALLBACK_HANDLER == null)
        {
            try
            {
                Thread.sleep(100);
            } catch (InterruptedException e)
            {
            }
        }
        CALLBACK_HANDLER.post(runnable);
    }

    private static void writeSync(DataOutputStream dos, int commandId, byte[] data) throws IOException
    {
        synchronized (LOCKER_WRITE)
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream cache = new DataOutputStream(out);
            if (count == Byte.MAX_VALUE)
                count = 0;
            count = (byte) (count + 1);
            cache.write(count);
            cache.write(commandId);
            ByteArrayOutputStream deviceId = new ByteArrayOutputStream();
            new DataOutputStream(deviceId).writeLong(OBDManager.getDeviceId()); // device id
            byte[] deviceIdArr = deviceId.toByteArray();
            cache.write(deviceIdArr, 2, deviceIdArr.length - 2);
            if (data == null)
            {
                cache.writeShort(0x0000);
            } else
            {
                cache.writeShort(data.length);
                cache.write(data);
            }
            byte[] curData = out.toByteArray();
            byte cs = 0x00;
            for (int i = 0; i < curData.length; i++)
            {
                cs = (byte) (cs ^ curData[i]);
            }
            cache.write(cs);
            dos.write(new byte[] { (byte) 0xFA, (byte) 0xFA });
            dos.write(out.toByteArray());
            dos.write(0xFB);
        }
    }

    public static void writeInfo(int commandId, byte[] data) throws IOException
    {
        if (output == null)
            throw new IOException("当前没有连接到服务器或者没有调用start(callback)方法");
        writeSync(output, commandId, data);
    }

    public static void writeDirect(byte[] data) throws IOException
    {
        if (output == null)
            throw new IOException("当前没有连接到服务器或者没有调用start(callback)方法");
        synchronized (LOCKER_WRITE)
        {
            output.write(data);
        }
    }

    public static void writeGPSInfo(AMapLocation arg0) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dOut = new DataOutputStream(out);
        SimpleDateFormat format = new SimpleDateFormat("yy-M-d-H-m-s");
        format.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        String date = format.format(new Date());
        String[] dateArr = date.split("-");
        dOut.write(new byte[] { Byte.parseByte(dateArr[3]), Byte.parseByte(dateArr[4]), Byte.parseByte(dateArr[5]), Byte.parseByte(dateArr[0]), Byte.parseByte(dateArr[1]), Byte.parseByte(dateArr[2]) });
        GeoPoint point = LocationConvert.gcj02Decrypt(arg0.getLatitude(), arg0.getLongitude());
        dOut.writeInt((int) (point.lat * 1000000));
        dOut.writeInt((int) (point.lng * 1000000));
        int altitude = (int) arg0.getAltitude();
        dOut.write(altitude >> 16);
        dOut.writeShort(altitude);
        dOut.writeShort((int) arg0.getSpeed() * 60 * 60 / 100);
        dOut.writeShort((int) arg0.getBearing());
        dOut.write(arg0.getSatellites());
        dOut.write(0); // Output?
        dOut.write(0); // Input Status?
        dOut.writeShort(0);
        writeInfo(0x00, out.toByteArray());
    }

    public static void writeCollisionInfo(AMapLocation arg0, byte[] eventIdData) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dOut = new DataOutputStream(out);
        SimpleDateFormat format = new SimpleDateFormat("yy-M-d-H-m-s");
        format.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        String date = format.format(new Date());
        String[] dateArr = date.split("-");
        dOut.write(new byte[] { Byte.parseByte(dateArr[3]), Byte.parseByte(dateArr[4]), Byte.parseByte(dateArr[5]), Byte.parseByte(dateArr[0]), Byte.parseByte(dateArr[1]), Byte.parseByte(dateArr[2]) });
        if (arg0 != null)
        {
            GeoPoint point = LocationConvert.gcj02Decrypt(arg0.getLatitude(), arg0.getLongitude());
            dOut.writeInt((int) (point.lat * 1000000));
            dOut.writeInt((int) (point.lng * 1000000));
            int altitude = (int) arg0.getAltitude();
            dOut.write(altitude >> 16);
            dOut.writeShort(altitude);
            dOut.writeShort((int) arg0.getSpeed() * 60 * 60 / 100);
            dOut.writeShort((int) arg0.getBearing());
            dOut.write(arg0.getSatellites());
        } else
        {
            dOut.writeInt(0);
            dOut.writeInt(0);
            int altitude = 0;
            dOut.write(altitude >> 16);
            dOut.writeShort(altitude);
            dOut.writeShort(0);// getSpeed
            dOut.writeShort(0);// Bearing
            dOut.write(0);// 卫星个数
        }
        dOut.write(0); // Output?
        dOut.write(0); // Input Status?
        dOut.writeShort(0);

        // 多媒体事件
        dOut.write((byte) 0x01);// 扩展事件项个数

        dOut.write((byte) 0x00);// 扩展事件类型 0x01:多媒体事件
        dOut.write((byte) 0x00);
        dOut.write((byte) 0x00);
        dOut.write((byte) 0x01);

        dOut.write((byte) 0x00);// 扩展事件长度
        dOut.write((byte) 0x35);

        dOut.write(eventIdData);
        // LogManager.logE(NetManager.class, "curEventIdData lenth==" + curEventIdData.length + "===" + new String(curEventIdData));

        dOut.write((byte) 0x00);// 多媒体类型
        dOut.write((byte) 0x00);// 多媒体格式编码
        dOut.write((byte) 0x03);// 多媒体事件项编码

        writeInfo(0x9E, out.toByteArray());
    }

//获取sim卡信息
    public static void getSIMMsg(TelephonyManager telManager) throws IOException
    {
        int i;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dOut = new DataOutputStream(out);

        if (telManager != null) {
            String imsi = telManager.getSubscriberId();
            String iccid = telManager.getSimSerialNumber();
            String simNum = telManager.getDeviceId();
            byte[] buf = imsi.getBytes();
            for (i=0; i<15; i++) {
                if (i >= imsi.length()) {
                    dOut.write((byte) 0x00);
                } else{
                    dOut.write((byte) buf[i]);
                }
            }
            buf = iccid.getBytes();
            for (i=0; i<20; i++) {
                if (i >= iccid.length()) {
                    dOut.write((byte) 0x00);
                } else{
                    dOut.write((byte) buf[i]);
                }
            }
            buf = simNum.getBytes();
            for (i=0; i<20; i++) {
                if (i >= simNum.length()) {
                    dOut.write((byte) 0x00);
                } else{
                    //dOut.write(simNum.getBytes());
                    dOut.write((byte) buf[i]);
                }
            }
            writeInfo(0x07, out.toByteArray());
        }
    }

    //获取软件版本
    public static void getAPKVersion() throws IOException
    {
        int i;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dOut = new DataOutputStream(out);

        String swVersion = "GIDAG000V100";
        String swDate = "2010-10-12";
        String hwVersion = "GIDA0V10";
        String GSMType = "SIM900A";
        String BootLoaderVersion = "XXXXV100";

        byte[] buf = swVersion.getBytes();
        for (i=0; i<12; i++) {
            if (i >= swVersion.length()) {
                dOut.write((byte)0X00);
            } else{
                dOut.write(buf[i]);
            }
        }
        buf = swDate.getBytes();
        for (i=0; i<10; i++) {
            if (i >= swDate.length()) {
                dOut.write((byte)0X00);
            } else{
                dOut.write(buf[i]);
            }
        }
        buf = hwVersion.getBytes();
        for (i=0; i<8; i++) {
            if (i >= hwVersion.length()) {
                dOut.write((byte)0X00);
            } else{
                dOut.write(buf[i]);
            }
        }
        buf = GSMType.getBytes();
        for (i=0; i<8; i++) {
            if (i >= GSMType.length()) {
                dOut.write((byte)0X00);
            } else{
                dOut.write(buf[i]);
            }
        }
        buf = BootLoaderVersion.getBytes();
        for (i=0; i<8; i++) {
            if (i >= BootLoaderVersion.length()) {
                dOut.write((byte)0X00);
            } else{
                dOut.write(buf[i]);
            }
        }
        writeInfo(0x03, out.toByteArray());
    }

    /**
     * 加入到Gps数据队列中
     *
     */
     public static double distanceInMeters(double latStart, double latEnd,
                                          double lonStart, double lonEnd, double alStart, double alEnd) {

        final int R = 6371; // Radius of the earth

        Double latDistance = Math.toRadians(latEnd - latStart);
        Double lonDistance = Math.toRadians(lonEnd - lonStart);
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(latStart))
                * Math.cos(Math.toRadians(latEnd)) * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = alStart - alEnd;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }

    public class SensorActivity extends Activity implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!mRunningGps)
                return;
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];
            String now = mSimpleDateFormat.format(new Date());

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                double currentUA = Math.sqrt(axisX * axisX + axisY * axisY + axisZ
                        * axisZ);
                Log.i("UBIDemo", "Acc=" + currentUA + " x=" + axisX + ", y=" + axisY
                        + ", z=" + axisZ);
                appendLog(now + "|" + axisX + "|"
                        + axisY + "|" + axisZ, "sdcard/sensor.txt");
                mAcceleratesParams = new float[]{axisX, axisY, axisZ};
                lblAcc:
                if (mCurrentUrgentAcceleration != 0) {
                    boolean isUrgent = false;
                    int tempUrgentType = -1;
                    // 可能一段时间是加速的，但不能确保每个点都是加速的。
                    double absDValue = Math.abs(currentUA - mCurrentUrgentAcceleration);
                    Location tempLocation = mLocationManager// 记录当前四急位置
                            .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (tempLocation == null)
                        tempLocation = mLocationManager
                                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (tempLocation == null) {// 如果捕捉不到位置则废弃
                        break lblAcc;
                    }
                    if (mGpsData.size() == 0)
                        break lblAcc;

                    Location lastLocation = mGpsData.get(mGpsData.size() - 1);
                    // tempLocation.setSpeed(lastLocation.getSpeed() - 0.5f);//模拟测试使用
                    if (tempLocation.getTime() == lastLocation.getTime())// 同一个点则返回
                        return;
                    if (tempLocation.getTime() - lastLocation.getTime() > 2000)// 如果当前点时间距离上一个点时间超过2秒则忽略。
                        break lblAcc;
                    float speed = lastLocation.getSpeed() - tempLocation.getSpeed();
                    if (speed > 0) {// 减速，要注意时间间隔
                        if (absDValue > URGENT_CRASH_VALUE) {// 碰撞消息
                            isUrgent = true;
                            tempUrgentType = URGENT_CRASH;
                        } else if (absDValue > URGENT_BRAKE_VALUE) {// 急刹车
                            isUrgent = true;
                            tempUrgentType = URGENT_BRAKE;
                        } else if (absDValue > URGENT_DECELERATION_VALUE) {// 急减速
                            isUrgent = true;
                            tempUrgentType = URGENT_DECELERATION;
                        }
                    } else if (speed < 0) {
                        if (absDValue > URGENT_ACCELERATION_VALUE) {// 急加速
                            isUrgent = true;
                            tempUrgentType = URGENT_ACCELERATION;
                        }
                    }

                    Log.i("UBIDemo", "Urgent=" + isUrgent + " mCurrentUrgentType="
                            + mCurrentUrgentType + " tempUrgentType=" + tempUrgentType);

                    if (mUrgentStartTime > 0) {
                        final long interval = System.currentTimeMillis()
                                - mUrgentStartTime;
                        Log.i("UBIDemo", "Urgent interval=" + interval);
                        if (interval > 1000) {// 大于1000毫秒
                            if (mCurrentUrgentType != tempUrgentType || interval > 8000) {// 如果加速度不是持续的或者持续超过8秒，则上报数据
                                final Location location = mUrgentLocationStart;
                                final float[] params = mAcceleratesParamsCurrent;
                                final int urgentType = mCurrentUrgentType;
                                // 启动单独的线程去处理
                                new Thread() {
                                    private int _mUrgentType = -1;
                                    private long _mInterval = 0;// 真实的时间间隔
                                    private float[] _mAcParams = null;
                                    private Location _mLocation = null;// 第一个点
                                    private boolean _mRunning = false;// 是否正在运行

                                    @Override
                                    public void run() {
                                        _mUrgentType = urgentType;
                                        _mInterval = interval;
                                        _mLocation = location;
                                        _mAcParams = params;
                                        _mRunning = true;

                                        while (mRunningGps && _mRunning) {
                                            int index = mGpsData.indexOf(_mLocation);
                                            Log.i("UBIDemo", "Start upload 4G index="
                                                    + index + " _mUrgentType="
                                                    + _mUrgentType);
                                            if (index > 0) {
                                                if (checkUrgentLocations(index)) {
                                                    float speedV = 0;// 后续速度跟第一个点的差值
                                                    boolean isValid = false;// 速度变化是否合法
                                                    ArrayList<Location> allLocations = getUrgentLocations(index);
                                                    int count = (allLocations.size() - 1) / 2;

                                                    lblFor:
                                                    for (int i = count + 1; i < 2 * count + 1; i++) {
                                                        Location l = allLocations
                                                                .get(i);
                                                        speedV = _mLocation.getSpeed()
                                                                - l.getSpeed();
                                                        Log.i("UBIDemo",
                                                                "Start upload 4G index="
                                                                        + i
                                                                        + " speedV="
                                                                        + speedV);

                                                        switch (_mUrgentType) {
                                                            case URGENT_ACCELERATION:
                                                                if (speedV <= -URGENT_ACCELERATION_VALUE) {
                                                                    isValid = true;
                                                                    break lblFor;
                                                                }
                                                                break;
                                                            case URGENT_DECELERATION:
                                                                if (speedV >= URGENT_DECELERATION_VALUE) {
                                                                    isValid = true;
                                                                    break lblFor;
                                                                }
                                                                break;
                                                            case URGENT_BRAKE:
                                                                if (speedV >= URGENT_BRAKE_VALUE) {
                                                                    isValid = true;
                                                                    break lblFor;
                                                                }
                                                                break;
                                                            case URGENT_CRASH:
                                                                if (speedV >= URGENT_CRASH_VALUE) {
                                                                    isValid = true;
                                                                    break lblFor;
                                                                }
                                                                break;
                                                        }
                                                    }

                                                    if (isValid) {
//                                                        mDataQueue
//                                                                .add(getUrgentData(
//                                                                        _mUrgentType,
//                                                                        allLocations,
//                                                                        _mAcParams,
//                                                                        (int) (_mInterval / 1000),
//                                                                        null));
                                                    } else {// 速度变化不合法

                                                    }

                                                    _mRunning = false;
                                                }

                                            } else {
                                                _mRunning = false;
                                            }

                                            try {
                                                Thread.sleep(1000);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }
                                        }

                                    }

                                }.start();
                            } else {
                                return;
                            }

                        } else {
                            if (mCurrentUrgentType == tempUrgentType)// 如果类型变化了则重置
                                return;

                        }
                    }
                    // isUrgent = true;
                    if (isUrgent) {
                        if (mUrgentStartTime == 0
                                || mCurrentUrgentType != tempUrgentType) {// 判断为四急开始
                            mUrgentLocationStart = tempLocation;// 捕捉到四急当前点
                            //addGps(mUrgentLocationStart);
                            mUrgentStartTime = System.currentTimeMillis();
                            mCurrentUrgentType = tempUrgentType;
                            mAcceleratesParamsCurrent = mAcceleratesParams;// 捕捉当前点对应的加速度
                            return;
                        }
                    }
                }

                mCurrentUrgentAcceleration = currentUA;
                mUrgentStartTime = 0;
                mCurrentUrgentType = -1;
                mUrgentLocationStart = null;
                mAcceleratesParamsCurrent = null;
            } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                //axisX:Azimuth, 背对和上边缘所指方向,  0/360=North, 90=East, 180=South, 270=West, north direction and the y-axis, around the z-axis (0 to 359)
                //axisY:Pinch, 屏幕上下翻转朝向，往上翻>0度，往下翻<0度，rotation around x-axis (-180 to 180), with positive values when the z-axis moves toward the y-axis.
                //axisZ:Roll, 屏幕左右翻, 左翻>0度, 右翻<0度, 不管屏幕上下，统一一个方向，rotation around the y-axis (-90 to 90) increasing as the device moves clockwise.
                //水平放置是XXX,0,0
                Log.i("UBIDemo2", "Directon=" + axisX + ", Pitch=" + axisY + ", Roll=" + axisZ);
                appendLog(now + "|" + axisX + "|"
                        + axisY + "|" + axisZ, "sdcard/sensorOrientation.txt");
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        public  void appendLog(String text, String filePath) {
            File logFile = new File(filePath);
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            try {
                // BufferedWriter for performance, true to set append to file flag
                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile,
                        true));
                buf.append(text);
                buf.newLine();
                buf.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    /**
     * 检查是否有急转弯并上报数据
     *
     //* @param location
     */
    private void checkUrgentWheel() {
        if (mGpsData.size() < 5)// 至少需要比较两个点
            return;
        float speedSum = 0;
        float speedMin = 0;
        int size = mGpsData.size();
        for (int i = 0; i < 4; i++) {
            float speed = mGpsData.get(size - i - 1).getSpeed();
            speedSum += speed;
            if (speedMin == 0 || speedMin > speed) {
                speedMin = speed;
            }
        }
        if (speedSum / 4 < URGENT_WHEEL_SPEED) {// 时速不低于40km/h（11m/s）并4秒内角度变化超过60度
            return;
        }
        Location locationCurrent = mGpsData.get(size - 4);// 当前点
        Location locationFirst = mGpsData.get(size - 5);// 最早一个点
        Location locationLast = mGpsData.get(size - 1);// 最新一个点
        long interval = locationCurrent.getTime() - locationFirst.getTime();
        if (interval > 2 * 1000) // 两点间隔大于2秒自动忽略
            return;
        float bearingFirstValue = Math.abs(locationCurrent.getBearing()
                - locationFirst.getBearing());
        if (bearingFirstValue < URGENT_WHEEL_FIRST_BEARING)// 第一个转弯幅度要大于13度
            return;
        float bearingDValue = Math.abs(locationCurrent.getBearing()
                - locationLast.getBearing());
        if (bearingDValue < URGENT_WHEEL_ALL_BEARING)// 总转弯幅度要大于60度
            return;

        Log.i("UBIDemo2", "checkUrgentWheel speedSum=" + speedSum
                + " bearingFirstValue=" + bearingFirstValue + " bearingDValue="
                + bearingDValue);

        final int duration = (int) (interval / 1000);
        final float[] wheelParams = new float[] { bearingDValue,
                speedMin * 3600 / 1000 };
        final Location location = locationCurrent;
        final float[] acParams = mAcceleratesParams;

        // 启动单独的进程去查询
        new Thread() {
            private boolean _mRunning = true;
            private Location _mLocation = null;
            private float[] _mAcParams = null;
            private float[] _mWheelParams = null;
            private int _mDuration = -1;

            @Override
            public void run() {
                _mRunning = true;
                _mDuration = duration;
                _mLocation = location;
                _mAcParams = acParams;
                _mWheelParams = wheelParams;

                while (mRunningGps && _mRunning) {
                    int index = mGpsData.indexOf(_mLocation);

                    if (index > 0) {
                        if (checkUrgentLocations(index)) {
//                            mDataQueue.add(getUrgentData(URGENT_WHEEL,
//                                    getUrgentLocations(index), _mAcParams,
//                                    _mDuration, _mWheelParams));
                            _mRunning = false;
                        }

                    } else {
                        _mRunning = false;
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

        }.start();

    }

    private synchronized boolean checkUrgentLocations(int index) {
        ArrayList<Location> tempLocations = new ArrayList<Location>();
        // 发生前的GPS点
        if (index < 10) {
            tempLocations.addAll(mGpsData.subList(0, index));
        } else {
            tempLocations.addAll(mGpsData.subList(index - 10, index));
        }
        int count = tempLocations.size();
        // 发生点
        tempLocations.add(mGpsData.get(index));
        // 后面的点
        if (mGpsData.size() - index > 1)
            if (mGpsData.size() - index <= count) {
                tempLocations.addAll(mGpsData.subList(index + 1,
                        mGpsData.size()));
            } else {
                tempLocations.addAll(mGpsData.subList(index + 1, index + count
                        + 1));
            }

        Log.i("UBIDemo", "checkUrgentLocations A=" + count + ", B="
                + (tempLocations.size() - 1 - count));

        if (tempLocations.size() - 1 - count == count)
            return true;
        return false;
    }

//    private byte[] getUrgentData(int type, ArrayList<Location> locations,
//                                 float[] accelerates, int duration, float[] wheelParams) {
//
//        // [上 行]
//        // 包头 包序列 功能ID 设备ID 数据长度 数据包 校验 包尾
//        // FAFA 1字节 0xAC 6字节 xx xx 数据包 XOR FB
//        // [下 行]
//        // 包头 包序列 功能ID 设备ID 数据长度 数据包 校验 包尾
//        // FAFA 1字节 0x80 6字节 00 01 0xAC XOR FB
//
//        //counter++;
//        byte[] tempBuffer = null;
//        int dataLength = 0;// 报文中数据包大小，不包含报文中固定长度数据
//        if (type < URGENT_WHEEL) {// 非急转弯
//            dataLength = 12 + 8 + 14 * locations.size() + 6 + 1 + 2 - 14;// 12head+8GPSI+14N(14*(10+10+10))GPSs+6M(6*10)+3=503
//        } else {
//            dataLength = 12 + 8 + 14 * locations.size() + 6 + 5 + 2 - 14;// 12head+8GPSI+14N(14*(10+10+10))GPSs+6M(6*10)+7=507
//        }
//
//        tempBuffer = new byte[dataLength + 14];
//
//        tempBuffer[0] = (byte) 0xFA;
//        tempBuffer[1] = (byte) 0xFA;
//
//        tempBuffer[2] = (byte) counter;
//
//        if (type == URGENT_ACCELERATION) {
//            tempBuffer[3] = (byte) 0xA9;
//        } else if (type == URGENT_DECELERATION) {
//            tempBuffer[3] = (byte) 0xA8;
//        } else if (type == URGENT_BRAKE) {
//            tempBuffer[3] = (byte) 0xAC;
//        } else if (type == URGENT_CRASH) {
//            tempBuffer[3] = (byte) 0xAB;
//        } else if (type == URGENT_WHEEL) {
//            tempBuffer[3] = (byte) 0xAA;
//        }
//
//        tempBuffer[4] = (byte) ((tempDeviceId >> 40) & 0xFF);
//        tempBuffer[5] = (byte) ((tempDeviceId >> 32) & 0xFF);
//        tempBuffer[6] = (byte) ((tempDeviceId >> 24) & 0xFF);
//        tempBuffer[7] = (byte) ((tempDeviceId >> 16) & 0xFF);
//        tempBuffer[8] = (byte) ((tempDeviceId >> 8) & 0xFF);
//        tempBuffer[9] = (byte) (tempDeviceId & 0xFF);
//
//        tempBuffer[10] = (byte) ((dataLength >> 8) & 0xFF);
//        tempBuffer[11] = (byte) (dataLength & 0xFF);
//
//        Calendar d = Calendar.getInstance();
//        d.setTimeInMillis(locations.get(0).getTime() - 8 * 3600L * 1000L);
//        // RTC Hour
//        tempBuffer[12] = (byte) d.get(Calendar.HOUR_OF_DAY);
//        // RTC Minute
//        tempBuffer[13] = (byte) d.get(Calendar.MINUTE);
//        // RTC Seconds
//        tempBuffer[14] = (byte) d.get(Calendar.SECOND);
//        // RTC Year
//        tempBuffer[15] = (byte) (d.get(Calendar.YEAR) - 2000);
//        // RTC Month
//        tempBuffer[16] = (byte) (d.get(Calendar.MONTH) + 1);
//        // RTC Day
//        tempBuffer[17] = (byte) d.get(Calendar.DATE);
//
//        // GPS interval
//        tempBuffer[18] = (byte) (0x11);
//        // GPS number 前后不超过10个点，中间1个点
//        tempBuffer[19] = (byte) (((((locations.size() - 1) / 2) << 4) & 0xFF) | 0x01);
//
//        // GPS data
//        int index = 20;
//        int idx = 0;
//        // 前10个点，过程10个点，后10个点
//        for (Location loc : locations) {
//            index = 20 + 14 * idx;
//
//            int longitude = (int) (1000000 * loc.getLongitude());
//            int latitude = (int) (1000000 * loc.getLatitude());
//
//            // Latitude
//            tempBuffer[index] = (byte) ((latitude >> 24) & 0XFF);
//            tempBuffer[index + 1] = (byte) ((latitude >> 16) & 0XFF);
//            tempBuffer[index + 2] = (byte) ((latitude >> 8) & 0XFF);
//            tempBuffer[index + 3] = (byte) ((latitude) & 0XFF);
//            index = index + 4;
//            // Longitude
//            tempBuffer[index] = (byte) ((longitude >> 24) & 0XFF);
//            tempBuffer[index + 1] = (byte) ((longitude >> 16) & 0XFF);
//            tempBuffer[index + 2] = (byte) ((longitude >> 8) & 0XFF);
//            tempBuffer[index + 3] = (byte) (longitude & 0XFF);
//            index = index + 4;
//            // speed
//            int speed = (int) (loc.getSpeed() * 3600 / 1000) * 10;// (int)
//            // loc.getSpeed()
//            // == 0 ?
//            // 80: (int)
//            // (loc.getSpeed()
//            // * 3600 /
//            // 1000) *
//            // 10;
//            tempBuffer[index] = (byte) ((speed >> 8) & 0xFF);
//            tempBuffer[index + 1] = (byte) (speed & 0xFF);
//            index = index + 2;
//            // direction
//            int direction = (int) loc.getBearing() * 10;// (int)
//            // loc.getBearing() == 0
//            // ? 180 : (int)
//            // loc.getBearing() *
//            // 10;
//            tempBuffer[index] = (byte) ((direction >> 8) & 0xFF);
//            tempBuffer[index + 1] = (byte) (direction & 0xFF);
//            index = index + 2;
//            // rpm
//            int rpm = 810;
//            tempBuffer[index] = (byte) ((rpm >> 8) & 0xFF);
//            tempBuffer[index + 1] = (byte) (rpm & 0xFF);
//            idx++;
//        }
//
//        // accelerate data
//        // for (int i = 0; i < 10; i++) {
//        // index = index + 6 * i;
//        index += 2;
//        int x = Math.abs((int) (accelerates[0] / 9.8f * 100));
//        int y = Math.abs((int) (accelerates[1] / 9.8f * 100));
//        int z = Math.abs((int) (accelerates[2] / 9.8f * 100));
//        tempBuffer[index] = (byte) ((x >> 8) & 0xFF);// x high
//        if (accelerates[0] < 0)
//            tempBuffer[index] = (byte) (tempBuffer[index] | 0x80);// x high
//        tempBuffer[index + 1] = (byte) (x & 0xFF);// x low
//
//        tempBuffer[index + 2] = (byte) ((y >> 8) & 0xFF);// y high
//        if (accelerates[1] < 0)
//            tempBuffer[index + 2] = (byte) (tempBuffer[index + 2] | 0x80);// y
//        // high
//        tempBuffer[index + 3] = (byte) (y & 0xFF);// y low
//
//        tempBuffer[index + 4] = (byte) ((z >> 8) & 0xFF);// z high
//        if (accelerates[1] < 0)
//            tempBuffer[index + 4] = (byte) (tempBuffer[index + 4] | 0x80);// z
//        // high
//
//        tempBuffer[index + 5] = (byte) (z & 0xFF);// z low
//
//        index += 6;
//        // }
//
//        // duration
//        // index = 20 + 14 * 30 + 6 * 10;
//        tempBuffer[index] = (byte) (duration & 0xFF);// 时间
//        index = index + 1;
//        if (type == URGENT_WHEEL) {
//            // direction
//            int directR = (int) (wheelParams[0] * 10);
//            tempBuffer[index] = (byte) ((directR >> 8) & 0xFF);
//            tempBuffer[index + 1] = (byte) (directR & 0xFF);
//            index = index + 2;
//            int lowS = (int) (wheelParams[1] * 10);
//            tempBuffer[index] = (byte) ((lowS >> 8) & 0xFF);
//            tempBuffer[index + 1] = (byte) (lowS & 0xFF);
//            index = index + 2;
//        }
//
//        byte cs = 0x00;
//        for (int i = 0; i < index; i++) {
//            cs = (byte) (cs ^ tempBuffer[i]);
//        }
//
//        // CheckSum
//        tempBuffer[index] = cs;
//        // Tail
//        tempBuffer[index + 1] = (byte) 0xFB;
//
//        Log.i("UBIDemo",
//                "UrgentType:" + type + " Loc count=" + locations.size()
//                        + " dataLength=" + (dataLength + 14) + " index="
//                        + (index + 1) + " x=" + x + " y=" + y + " z=" + z);
//
//        return tempBuffer;
//    }

    /**
     * 搜集四急前后的GPS点
     *
     * @param index
     * @return
     */
    private synchronized ArrayList<Location> getUrgentLocations(int index) {
        ArrayList<Location> tempLocations = new ArrayList<Location>();
        // 发生前的GPS点
        if (index < 10) {
            tempLocations.addAll(mGpsData.subList(0, index));
        } else {
            tempLocations.addAll(mGpsData.subList(index - 10, index));
        }
        int count = tempLocations.size();
        // 发生点
        tempLocations.add(mGpsData.get(index));
        // 后面的点
        if (mGpsData.size() - index > 1)
            if (mGpsData.size() - index <= count) {
                tempLocations.addAll(mGpsData.subList(index + 1,
                        mGpsData.size()));
            } else {
                tempLocations.addAll(mGpsData.subList(index + 1, index + count
                        + 1));
            }

        return tempLocations;
    }
}
