package com.cpsdna.careyes;

import xcoding.commons.util.LogManager;
import android.app.Application;

public class MyApplication extends Application
{
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread arg0, Throwable arg1)
            {
                LogManager.logE(MyApplication.class, "oh no,app crash...", arg1);
                System.exit(-1);
            }
        });
    }
    
}
