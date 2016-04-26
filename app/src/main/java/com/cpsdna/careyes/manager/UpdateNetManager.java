package com.cpsdna.careyes.manager;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;

import javax.net.SocketFactory;

import xcoding.commons.lbs.LocationConvert;
import xcoding.commons.lbs.LocationConvert.GeoPoint;
import xcoding.commons.lbs.amap.AMapLocationWrapper;
import xcoding.commons.util.LogManager;

/**
 * <p>除了start()和stop()外，其他方法基本为耗时方法，需在子线程调用
 * @author zhangwendell
 *
 */
public final class UpdateNetManager
{
    
    private static String REMOTE_IP = "221.226.93.118"; //"58.215.50.50";
    private static int REMOTE_PORT = 31498; //4008; 
    
    private static boolean isStarted = false;
    private static boolean isCanceled = true;
    
    private static DataOutputStream output = null;
    
    private static final byte[] LOCKER_START = new byte[0]; // 同步锁
    private static final byte[] LOCKER_WRITE = new byte[0]; // 同步锁
    
    private static byte count = 0; // 包序列
    
    private static Handler CALLBACK_HANDLER = null;
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
    
    private static NetCallback CALLBACK = null;
    
    private UpdateNetManager(){}
    
    public static void start(NetCallback callback) {
        start(REMOTE_IP,REMOTE_PORT,callback);
    }
    
    public static void start(String ip, int port ,NetCallback callback) {
        if(callback == null) throw new NullPointerException();
        CALLBACK = callback;
        boolean shouldStart = shouldStart();
        if(shouldStart) {
            startImpl(ip,port);
        }
    }
    
    private static void startImpl( final String ip, final int port) {
        new Thread(){
            public void run() {
                Timer timer = new Timer();
                Socket client = null;
                boolean shouldCancel = false;
                try {
                    if(shouldCancel = shouldCancel()) {
                        return;
                    }
                    LogManager.logI(UpdateNetManager.class, "连接到服务器...");
                    client = SocketFactory.getDefault().createSocket();
                    SocketAddress remoteaddr = new InetSocketAddress(ip,port);
                    client.connect(remoteaddr, 120000); // 连接120秒超时
                    client.setSoTimeout(120000); // 读写120秒
                    LogManager.logI(UpdateNetManager.class, "升级请求已连接，开始监听...");
                    final DataOutputStream dos = new DataOutputStream(client.getOutputStream());
                    output = dos;
                    if(shouldCancel = shouldCancel()) {
                        return;
                    }
//                    client.setSoTimeout(25000); // 修改为25秒超时以响应心跳，心跳时间为15秒
//                    timer.schedule(new TimerTask()
//                    {
//                        @Override
//                        public void run()
//                        {
//                            LogManager.logI(UpdateNetManager.class, "发送心跳...");
//                            try {
//                                writeSync(dos,0x05,null);
//                            }catch(IOException e) {
//                                LogManager.logE(UpdateNetManager.class, "心跳发送失败", e);
//                            }
//                        }
//                    }, 15000, 15000);
                    DataInputStream dis = new DataInputStream(client.getInputStream());
                    while(true) {
                        if(shouldCancel = shouldCancel()) {
                            return;
                        }
                        int first;
                        while(true) {
                            first = dis.read();
                            if(first == -1) throw new IOException("data is at the end.");
                            if(first == 0xFA) {
                                first = dis.read();
                                if(first == -1) throw new IOException("data is at the end.");
                                if(first == 0xFA) { // 检测到开始
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
                        if(checkSum == -1) throw new IOException("data is at the end.");
                        int end = dis.read(); // 0xFB
                        if(end == -1) throw new IOException("data is at the end.");
                        // check data
                        byte[] curData = byteOut.toByteArray();
                        byte cs = 0x00;
                        for (int i = 2; i < curData.length; i++) {
                                  cs = (byte) (cs ^ curData[i]);
                        }
                        if((0xFF & cs) != checkSum) {
                            LogManager.logE(UpdateNetManager.class, "数据校验失败，将忽略当前数据...");
                            continue;
                        }
                        out.write(checkSum);
                        out.write(end);
                        postCallback(new Runnable() {
                            @Override
                            public void run() {
                                CALLBACK.onReceive(dataFront[1], data, byteOut.toByteArray());
                            }
                        });
                    }
                }catch(Exception e) {
                    LogManager.logE(UpdateNetManager.class, "当前连接发生异常，将在5秒后重试...", e);
                }finally {
                    timer.cancel();
                    try {
                        if(client != null) client.close();
                    }catch(IOException e) {
                        LogManager.logE(UpdateNetManager.class, "", e);
                    }
                    if(!shouldCancel) {
                        try {
                            Thread.sleep(5000); // 等待5秒后重试
                        }catch(InterruptedException e) {
                            LogManager.logE(UpdateNetManager.class, "", e);
                        }
                        startImpl(ip,port);
                    }
                }
            }
        }.start();
    }
    
    private static boolean shouldStart() {
        synchronized (LOCKER_START) {
            if(!isStarted) {
                isStarted = true;
                if(isCanceled) {
                    isCanceled = false;
                    return true;
                }
            }
            return false;
        }
    }
    
    private static boolean shouldCancel() {
        synchronized (LOCKER_START) {
            if(!isStarted) {
                isCanceled = true;
                output = null;
                return true;
            }
            return false;
        }
    }
    
    public static void stop() {
        isStarted = false;
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
    
    private static void writeSync(DataOutputStream dos,int commandId,byte[] data) throws IOException {
        synchronized (LOCKER_WRITE) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream cache = new DataOutputStream(out);
            if(count == Byte.MAX_VALUE) count = 0;
            count = (byte)(count + 1);
            cache.write(count);
            cache.write(commandId);
            ByteArrayOutputStream deviceId = new ByteArrayOutputStream();
            new DataOutputStream(deviceId).writeLong(OBDManager.getDeviceId()); // device id
            byte[] deviceIdArr = deviceId.toByteArray();
            cache.write(deviceIdArr, 2, deviceIdArr.length - 2);
            if(data == null) {
                cache.writeShort(0x0000);
            }else {
                cache.writeShort(data.length);
                cache.write(data);
            }
            byte[] curData = out.toByteArray();
            byte cs = 0x00;
            for (int i = 0; i < curData.length; i++) {
                      cs = (byte) (cs ^ curData[i]);
            }
            cache.write(cs);
            dos.write(new byte[]{(byte)0xFA,(byte)0xFA});
            dos.write(out.toByteArray());
            dos.write(0xFB);
        }
    }
    
    public static void writeInfo(int commandId,byte[] data) throws IOException {
        if(output == null) throw new IOException("当前没有连接到服务器或者没有调用start(callback)方法");
        writeSync(output,commandId,data);
    }
    
    public static void writeDirect(byte[] data) throws IOException {
        if(output == null) throw new IOException("当前没有连接到服务器或者没有调用start(callback)方法");
        synchronized (LOCKER_WRITE) {
            output.write(data);
        }
    }
    
    public static void writeGPSInfo(AMapLocationWrapper arg0) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dOut = new DataOutputStream(out);
        SimpleDateFormat format = new SimpleDateFormat("yy-M-d-H-m-s");
        format.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        String date = format.format(new Date());
        String[] dateArr = date.split("-");
        dOut.write(new byte[]{Byte.parseByte(dateArr[3]),Byte.parseByte(dateArr[4]),Byte.parseByte(dateArr[5]),Byte.parseByte(dateArr[0]),Byte.parseByte(dateArr[1]),Byte.parseByte(dateArr[2])});
        GeoPoint point = LocationConvert.gcj02Decrypt(arg0.getLatitude(),arg0.getLongitude());
        dOut.writeInt((int)(point.lat*1000000));
        dOut.writeInt((int)(point.lng*1000000));
        int altitude = (int)arg0.getAltitude();
        dOut.write(altitude >> 16);
        dOut.writeShort(altitude);
        dOut.writeShort((int)arg0.getSpeed()*60*60/1000);
        dOut.writeShort(0); //Direction?
        dOut.write(4); //卫星个数需要固定为4
        dOut.write(0); //Output?
        dOut.write(0); //Input Status?
        dOut.writeShort(0);
        writeInfo(0x00, out.toByteArray());
    }
    
}
