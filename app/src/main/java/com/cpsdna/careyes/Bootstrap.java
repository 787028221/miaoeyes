package com.cpsdna.careyes;

import com.cpsdna.careyes.manager.AudioManager;

import xcoding.commons.ui.GenericActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class Bootstrap extends BroadcastReceiver
{
    
    private final static String SP_NAME = "Bootstrap";
    
    @Override
    public void onReceive(Context arg0, Intent arg1)
    {
        String action = arg1.getAction();
        if("com.cayboy.action.ACC_POWER_OFF".equals(action)) {
            setAccPowerOff(arg0,true); //与守护进程是同一个进程，并且都在主线程，所以该方法保证了同步
            AudioManager.getInstance(arg0).play(R.raw.car_stop);
            GenericActivity.sendRefresh(arg0, WelcomeActivity.REFRESHTYPE_ACC_POWER_OFF, null); //关闭Activity
            //不主动停止服务，由于复杂度的问题，停止服务就是简单的终止服务进程，这一操作在休眠时系统会进行执行
        }else {
            if("com.cayboy.action.ACC_POWER_ON".equals(action)) {
                setAccPowerOff(arg0,false);
                AudioManager.getInstance(arg0).play(R.raw.car_start);
            }
            if(isAccPowerOff(arg0)) {
                return;
            }
            arg0.startService(new Intent(arg0, DaemonService.class));
        }
    }
    
    public static boolean isAccPowerOff(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean("ACC_POWER_OFF", false);
    }
    
    public static void setAccPowerOff(Context context,boolean isAccPowerOff) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean("ACC_POWER_OFF", isAccPowerOff).commit();
    }
    
}
