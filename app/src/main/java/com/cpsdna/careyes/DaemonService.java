package com.cpsdna.careyes;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

public class DaemonService extends Service {
    
    private Handler handler = new Handler();
    private Runnable runnable;
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        runnable = new Runnable()
        {
            @Override
            public void run()
            {
                if(!Bootstrap.isAccPowerOff(DaemonService.this)) {
                    Intent intent = new Intent(DaemonService.this, WelcomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    startService(new Intent(DaemonService.this, ControlService.class));
                }
                handler.postDelayed(this, 15000);
            }
        };
        handler.post(runnable);
    }
    
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        handler.removeCallbacks(runnable);
    }
    
    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        return Service.START_REDELIVER_INTENT;
    }
    
}
