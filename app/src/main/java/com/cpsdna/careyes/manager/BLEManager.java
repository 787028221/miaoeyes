package com.cpsdna.careyes.manager;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.cpsdna.careyes.WelcomeActivity;
import com.cpsdna.careyes.entity.CommandMediaBle;
import com.cpsdna.careyes.entity.CommandMediaLocal;

import java.util.UUID;

import xcoding.commons.ui.GenericActivity;

/**
 * Created by adminstor on 2016/3/8.
 */
public class BLEManager {
    final private String mDeviceName = "MacroGiga";//蓝牙设备名称
    private static final String TAG = "BLEManager";

    final private long PictureTimeinterval = 1000;//拍照片时间间隔，由于广播次数太多所以需要忽略一些
    final private long VedioTimeinterval = 10000;//录视频时间间隔
    final private long ShortPress = 2000;//短按的阈值
    final private long LongPress = 6000;//长按的阈值
    final byte PictrueOperating = 48;//拍照片操作码
    final byte VedioOperating = 32;//录视频操作码

    private BluetoothAdapter mBluetoothAdapter = null;//蓝牙适配器
    private Context mContext = null;//容器

    private boolean IsSuportBLE = false;//是否支持BLE标志位
    private boolean mScanning = false;//是否正在扫描标志位
    private boolean mBound = false;//是否绑定蓝牙标志位


    public String mDeviceAddress = "";//过滤设备的地址
    private static final int REQUEST_ENABLE_BT = 1;

    private long PreVedioTime = 0;//当收到开始录像的时间点
    private long PrePictureTime = 0;//当收到开始拍照的时间点
    private long NowTime = 0;//当拍照和录视频的结束时间点

    //
//    private long a = 0;//当拍照和录视频的结束时间点
//    private long newb = 0;//当拍照和录视频的结束时间点
//    private long oldb = 0;//当拍照和录视频的结束时间点
//    private boolean k = false;//是否绑定蓝牙标志位

    //初始化蓝牙设备
    public BLEManager(Context context) {
        mContext = context.getApplicationContext();

        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return;
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            return;
        }
        IsSuportBLE = true;
        Log.d(TAG, "蓝牙初始化成功");
        ReadBLEConfig();

    }

    //打开蓝牙设备并且扫描设备
    public void BLEStart() {
        if (!IsSuportBLE)
            return;
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        scanLeDevice(true);
        Log.d(TAG, "蓝牙开始扫描");
    }

    //停止设备扫描
    public void BLEStop() {
        if (!IsSuportBLE)
            return;
        scanLeDevice(false);
        Log.d(TAG, "蓝牙停止扫描");
    }

    //扫描设备和停止设备函数
    private void scanLeDevice(final boolean enable) {

        if (!IsSuportBLE)
            return;
        if (enable) {

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScaleCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScaleCallback);
        }
    }

    //读取配置文件
    public int ReadBLEConfig() {

        SharedPreferences sharedPreferences = mContext.getSharedPreferences("BLECONFIG",
                Activity.MODE_PRIVATE);
        String StrBleAddress = sharedPreferences.getString("BLEAddress", "");
        if (StrBleAddress == "") {
            mBound = false;
        } else {
            mBound = true;
            mDeviceAddress = StrBleAddress;
        }
        Log.d(TAG, "读取蓝牙配置文件蓝牙地址" + mDeviceAddress);
        return 1;
    }

    //绑定设备地址并且写入配置文件
    public int WriteBLEConfig(String BLEAddress) {

        if (BLEAddress.length() == 17 && (BLEAddress.charAt(2) == ':'
                && BLEAddress.charAt(5) == ':' && BLEAddress.charAt(8) == ':'
                && BLEAddress.charAt(11) == ':' && BLEAddress.charAt(14) == ':')) {
            //实例化SharedPreferences对象（第一步）
            SharedPreferences mySharedPreferences = mContext.getSharedPreferences("BLECONFIG",
                    Activity.MODE_PRIVATE);
            //实例化SharedPreferences.Editor对象（第二步）
            SharedPreferences.Editor editor = mySharedPreferences.edit();
            //用putString的方法保存数据
            editor.putString("BLEAddress", BLEAddress);
            //提交当前数据
            editor.commit();
            mBound = true;
            mDeviceAddress = BLEAddress;
            Log.d(TAG, "写入蓝牙配置文件蓝牙地址" + mDeviceAddress);
            return 1;
        } else if (BLEAddress == "") {//清空蓝牙地址
            //实例化SharedPreferences对象（第一步）
            SharedPreferences mySharedPreferences = mContext.getSharedPreferences("BLECONFIG",
                    Activity.MODE_PRIVATE);
            //实例化SharedPreferences.Editor对象（第二步）
            SharedPreferences.Editor editor = mySharedPreferences.edit();
            //用putString的方法保存数据
            editor.putString("BLEAddress", "");
            //提交当前数据
            editor.commit();
            mBound = false;
            mDeviceAddress = "";
            Log.d(TAG, "写入蓝牙配置文件蓝牙地址" + mDeviceAddress);
            return 2;
        } else {
            return -2;
        }
    }

    //蓝牙回调函数
    private BluetoothAdapter.LeScanCallback mLeScaleCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord) {
                    //如果设备不是厂家设备直接返回

                    if (device.getName() == null || !device.getName().equals(mDeviceName))
                        return;
                    Log.d(TAG, "扫描到设备" + device.getAddress());
                    //如果没有绑定设备扫描到设备以后直接绑定
                    if (!mBound) {
                        if (WriteBLEConfig(device.getAddress().toUpperCase()) > 0) {
                            //操作可以广播信息或者回调
                            Log.d(TAG, "自动绑定设备" + mDeviceAddress);
                        }
                        ;
                    }
                    //如果搜索到地址并且匹配开始处理函数
                    if (device.getAddress().toUpperCase().equals(mDeviceAddress)) {// "88:0F:10:10:AF:B3"
                        //  Log.d(TAG, "收到的指令码为："+scanRecord[16]);
                        NowTime = System.currentTimeMillis();

                        if (scanRecord[16] == PictrueOperating && (NowTime - PrePictureTime) > PictureTimeinterval) {

                            PrePictureTime = System.currentTimeMillis();
                            //操作可以广播信息或者回调
                            Log.d(TAG, "收到拍照请求");
                            CommandMediaBle local = new CommandMediaBle();
                            local.type = 1;
                            local.taskId = UUID.randomUUID().toString();
                            Bundle bundle = new Bundle();
                            bundle.putSerializable("INFO", local);
                            GenericActivity.sendRefresh(mContext, WelcomeActivity.REFRESHTYPE_COMMAND_REQUEST, bundle);
                            LocalTaskManager.addTask(local.taskId);
                        } else if (scanRecord[16] == VedioOperating && (NowTime - PreVedioTime) > VedioTimeinterval) {
                            PreVedioTime = System.currentTimeMillis();
                            //操作可以广播消息或者回调
                            Log.d(TAG, "收到录视频请求");
                            CommandMediaBle local = new CommandMediaBle();
                            local.type = 2;
                            local.taskId = UUID.randomUUID().toString();
                            Bundle bundle = new Bundle();
                            bundle.putSerializable("INFO", local);
                            GenericActivity.sendRefresh(mContext, WelcomeActivity.REFRESHTYPE_COMMAND_REQUEST, bundle);
                            LocalTaskManager.addTask(local.taskId);
                        }
                    }

                }
            };
}

