package com.cpsdna.careyes.manager;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Handler;
import android.os.Looper;

public class AudioManager
{
    private MediaPlayer         mp;
    private static AudioManager manager;
    private Context             mContext;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized AudioManager getInstance(Context context)
    {
        if (manager == null)
            manager = new AudioManager(context);
        return manager;
    }

    private AudioManager(Context context)
    {
        mContext = context.getApplicationContext();
    }

    public void play(final int resid)
    {
        Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                stop();
                mp = MediaPlayer.create(mContext, resid);
                mp.setOnCompletionListener(new OnCompletionListener()
                {
                    @Override
                    public void onCompletion(MediaPlayer player)
                    {
                        mp.stop();
                        mp.release();
                        mp = null;
                    }
                });
                mp.start();
            }
        };
        if(Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        }else {
            mainHandler.post(runnable);
        }
    }

    private void stop()
    {
        if (mp != null)
        {
            mp.stop();
            mp.release();
            mp = null;
        }
    }

}
