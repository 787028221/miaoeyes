package com.cpsdna.careyes.manager;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import xcoding.commons.lbs.LocationConvert;
import xcoding.commons.lbs.LocationConvert.GeoPoint;
import xcoding.commons.telephony.TelephonyMgr;
import xcoding.commons.ui.ToastManager;
import xcoding.commons.util.LogManager;
import android.R.integer;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.cpsdna.careyes.ControlService;
import com.cpsdna.careyes.WelcomeActivity;
import com.cpsdna.careyes.utility.TelephonyTools;
import com.cpsdna.careyes.utility.UtilityTools;
import com.amap.api.location.AMapLocation;
import com.obd.serial.UartCtrl;

/**
 * <p>线程不同步
 * @author zhangwendell
 *
 */
public final class OBDManager
{
    
    private static UartCtrl uartCtrl = new UartCtrl();
    
    private static short count = 0;
    
    private static String isOpen = null;
    
    private static Context curContext;
    
    private static Handler CALLBACK_HANDLER = null;
    
    private static String curToken;
    
    private static DataInputStream dInput;
    
    static {
        new Thread() {
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
    
    
    private OBDManager() {
    	
    }
    
    public static void open(Context context,final OBDCallback callback) throws IOException {
        if(isOpen != null) return;
        curContext = context;
        final InputStream input = uartCtrl.openSerialPort();
        curToken = UUID.randomUUID().toString();
        isOpen = curToken;
        new Thread(){
            public void run() {
                dInput = new DataInputStream(input);
                while(isOpen == curToken) {
                    try {
                        int first;
                        boolean isHead = false;
                        while(true) {
                            int b = dInput.read();
                            if(b == -1) throw new IOException("data is at the end.");
                            if(isHead) {
                                if(b != 0x7e) {
                                    first = b;
                                    break;
                                }
                            }else {
                                if(b == 0x7e) {
                                    isHead = true;
                                    continue;
                                }
                            }
                        }
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        while(first != 0x7e) {
                            if(first == 0x7d) {
                                first = dInput.read();
                                if(first == -1) throw new IOException("data is at the end.");
                                if(first == 0x02) {
                                    out.write(0x7e);
                                    first = dInput.read();
                                    if(first == -1) throw new IOException("data is at the end.");
                                }else if(first == 0x01) {
                                    out.write(0x7d);
                                    first = dInput.read();
                                    if(first == -1) throw new IOException("data is at the end.");
                                }else {
                                    out.write(0x7d);
                                }
                            }else {
                                out.write(first);
                                first = dInput.read();
                                if(first == -1) throw new IOException("data is at the end.");
                            }
                        }
                        final byte[] data = out.toByteArray();
                        int contentLength = data.length - 1 - 8;
                        if(contentLength < 0) {
                            LogManager.logW(OBDManager.class, "data length is wrong,ignore...");
                            continue;
                        }
                        byte cs = 0x00;
                        for (int i = 0; i < data.length - 1; i++) {
                                  cs = (byte) (cs ^ data[i]);
                        }
                        if(cs != data[data.length-1]) {
                            LogManager.logW(OBDManager.class, "checksum is wrong,ignore...");
                            continue;
                        }
                        //获取ID
                        final short id = (short) ((data[0] & 0xff) << 8 | data[1] & 0xff);
                        // 获取内容
                        final byte[] content = new byte[contentLength];
                        for(int i = 0;i < content.length;i++) {
                            content[i] = data[8 + i];
                        }
                        postCallback(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                callback.onReceive(id, content,data);
                            }
                        });
                    }catch(IOException e) {
                        LogManager.logE(OBDManager.class, "io error in receiving.", e);
                        try {
                            InputStream in = reOpen(curToken);
                            dInput = new DataInputStream(in);
                        }catch(IOException e1) {
                            LogManager.logE(OBDManager.class, "reopen failed.", e1);
                        }
                    }
                }
            }
        }.start();
        //test 20160409 , query OBD parter id
        queryOBDID();
    }
    
    private static InputStream reOpen(String openToken) throws IOException {
        synchronized (OBDManager.class) {
            if(isOpen == openToken) {
                uartCtrl.closeSerialPort();
                return uartCtrl.openSerialPort();
            }
        }
        throw new IOException("is closed by user");
    }
    
    public static void close() throws IOException {
        synchronized (OBDManager.class) {
            uartCtrl.closeSerialPort();
            isOpen = null;
        }
    }
    
    public static void reOpenOBDCOM() throws IOException{
		synchronized (OBDManager.class) {
			uartCtrl.closeSerialPort();
			InputStream in = uartCtrl.openSerialPort();
			dInput = new DataInputStream(in);
		}
    }
    
    private static void postCallback(Runnable runnable) {
        while(CALLBACK_HANDLER == null) {
            try {
                Thread.sleep(100);
            }catch(InterruptedException e) {
            }
        }
        CALLBACK_HANDLER.post(runnable);
    }
    
    public static long getDeviceId() throws IOException {
        //return 41021600508l;
        //return 41011100010l;
        //return 41021600522l;
        //return 61087654301l;
        //return 61087654302l;
        //return 61087654303l;
        //return 61087654304l;
        //return 61087654305l;
        //return 61087654306l;
        //return 61087654307l;
        return 61087654308l;
        //return 61087654309l;
        //return 61087654310l;
        //return 61087654311l;
        //return 61087654312l;
        //return 61087654313l;
        //return 61087654314l;
        //return 61087654315l;
        //return 61087654316l;
        //return 61087654317l;
        //return 61087654318l;
        //return 61087654320l;
    }
    
    public static String getRecorderId(Context context) {
        return TelephonyMgr.getDeviceId(context);
    }
    
    private static void write(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x7e);
        for(byte one : data) {
            if(one == 0x7e) {
                output.write(0x7d);
                output.write(0x02);
            }else if(one == 0x7d) {
                output.write(one);
                output.write(0x01);
            }else {
                output.write(one);
            }
        }
        output.write(0x7e);
        uartCtrl.sendDataPackage(output.toByteArray());
    }
    
    public static void write(int msgId,byte[] data) throws IOException {
        short msgSize = (short)data.length;
//        msgSize = (short)(msgSize << 6);
//        msgSize = (short)(msgSize >>> 6);
        if(count == Short.MAX_VALUE) count = 0;
        count = (short)(count + 1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(out);
        output.writeShort(msgId);
        output.writeShort(msgSize);
        output.writeShort(count);
        output.writeShort(0x0000);
        output.write(data);
        byte[] curData = out.toByteArray();
        byte cs = 0x00;
        for (int i = 0; i < curData.length; i++) {
                  cs = (byte) (cs ^ curData[i]);
        }
        output.writeByte(cs);
        write(out.toByteArray());
    }
    
    public static void write(int msgId,byte[] countData,byte[] data) throws IOException {
        short msgSize = (short)data.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(out);
        output.writeShort(msgId);
        output.writeShort(msgSize);
//        output.writeShort(count);
        output.write(countData);//序号
        output.writeShort(0x0000);
        output.write(data);
        byte[] curData = out.toByteArray();
        byte cs = 0x00;
        for (int i = 0; i < curData.length; i++) {
                  cs = (byte) (cs ^ curData[i]);
        }
        output.writeByte(cs);
        write(out.toByteArray());
    }
    
    public static void writeGPSInfo(AMapLocation arg0) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(out);
        output.writeShort(0);
        output.write(2);
        output.writeShort(0x8000); //定位信息
        output.write(25);
        output.write(1);
        SimpleDateFormat format = new SimpleDateFormat("yy-M-d-H-m-s");
        format.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        String date = format.format(new Date());
        String[] dateArr = date.split("-");
        output.write(new byte[]{Byte.parseByte(dateArr[3]), Byte.parseByte(dateArr[4]), Byte.parseByte(dateArr[5]), Byte.parseByte(dateArr[0]), Byte.parseByte(dateArr[1]), Byte.parseByte(dateArr[2])});
        GeoPoint point = LocationConvert.gcj02Decrypt(arg0.getLatitude(),arg0.getLongitude());
        output.writeInt((int) (point.lng * 1000000));//经度
        output.writeInt((int)(point.lat*1000000));//纬度
        output.writeShort((int)arg0.getAltitude());//海拔
        output.writeShort((int)arg0.getSpeed()*60*60/1000*10);//速度
        output.writeShort((int)arg0.getBearing()*10); //角度
        output.write(arg0.getSatellites()); //卫星个数
        output.write(new byte[]{60,50,40});//卫星信号强度！！暂时未获取到
        
        output.writeShort(0x8001); //手机网络信息
        output.write(1);//数据长度
        output.write(TelephonyTools.getNetworkType(curContext));//当前网络状态
        output.writeShort(TelephonyTools.getCountryIso(curContext));//MCC
        output.write(WelcomeActivity.getCurrentSignalStrength());//网络信号强度
        if (TelephonyTools.getPhoneType(curContext) == TelephonyManager.PHONE_TYPE_GSM) {
        	output.write(0x60);
        	output.writeShort(TelephonyTools.getLAC(curContext));
        	output.writeInt(TelephonyTools.getCID(curContext));
		}else if (TelephonyTools.getPhoneType(curContext) == TelephonyManager.PHONE_TYPE_CDMA) {
			output.write(0x61);
        	output.writeShort(TelephonyTools.getSID(curContext));
        	output.writeShort(TelephonyTools.getNID(curContext));
        	output.writeShort(TelephonyTools.getBID(curContext));
		}else {
			output.write(0);
		}
        write(0x8002,out.toByteArray());
    }
    public static void writeSIMInfo(int serialNumber) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(out);
        //content  not include the header
        //serial number for obd parter request  2 bytes
        output.writeShort(serialNumber);
        //parameter number
        output.write(1);
        //function id
        output.writeShort(0x0004); //sim information
        //content length
        output.write(55);
        //content
        //content iccid 20 bytes
        String iccid = TelephonyTools.getICCID(curContext);
        if(iccid==null || iccid.length()<20) {
            output.write(new byte[20]);
        } else {
            output.write(iccid.getBytes());
        }
        //content imsi 15 bytes
        String imsi = TelephonyTools.getIMSI(curContext);
        if(imsi==null || imsi.length()<10) {
            output.write(new byte[15]);
        } else {
            output.write(imsi.getBytes());
        }
        //content sim 20 bytes, normally  sim number is empty
        output.write(new byte[20]);

        write(0x8004,out.toByteArray());
    }
    public static void writeBASEInfo(int serialNumber) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(out);
        //content  not include the header
        //serial number for obd parter request  2 bytes
        output.writeShort(serialNumber);
        //parameter number
        output.write(1);
        //function id
        output.writeShort(0x0005); //sim information
        //content length
        output.write(11);
        //content
        output.write(TelephonyTools.getNetworkType(curContext));//当前网络状态
        output.writeShort(TelephonyTools.getCountryIso(curContext));//MCC
        output.write(WelcomeActivity.getCurrentSignalStrength());//网络信号强度
        if (TelephonyTools.getPhoneType(curContext) == TelephonyManager.PHONE_TYPE_GSM) {
            output.write(0x60);
            output.writeShort(TelephonyTools.getLAC(curContext));
            output.writeInt(TelephonyTools.getCID(curContext));
        }else if (TelephonyTools.getPhoneType(curContext) == TelephonyManager.PHONE_TYPE_CDMA) {
            output.write(0x61);
            output.writeShort(TelephonyTools.getSID(curContext));
            output.writeShort(TelephonyTools.getNID(curContext));
            output.writeShort(TelephonyTools.getBID(curContext));
        }else {
            output.write(0);
        }
        write(0x8004,out.toByteArray());
    }
    public static void queryOBDID() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(out);
        //content  not include the header
        //serial number for obd parter request  2 bytes
        output.writeShort(0x01);
        //parameter number
        output.write(1);
        //function id
        output.writeShort(0x0003); //sim information
        //content length
        output.write(0x00);

        write(0x8004,out.toByteArray());
    }
    
}
