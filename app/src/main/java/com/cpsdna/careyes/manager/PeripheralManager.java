package com.cpsdna.careyes.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Log;

import com.amap.api.location.AMapLocation;
import com.cpsdna.careyes.ControlService;
import com.cpsdna.careyes.WelcomeActivity;
import com.okl_sysctrl.sysctrlproxy.SysCtrlManager;
import com.okl_sysctrl.sysctrlproxy.aidl.SysCtrlListener;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import xcoding.commons.lbs.amap.LocationManager;
import xcoding.commons.util.LogManager;

/**
 * Created by adminstor on 2016/3/24.
 */
public class PeripheralManager {
	private static final String TAG = "PeripheralManager";
	private File path = new File("/mnt/external_sdio");
	private final String BROADCAST_ACTION = "com.action.state";
	private Timer timer = null;
	private final int TimerInterval = 50;
	private int AddTimerNum = 0;
	private int SDAddTimerNum = 0;
	private int RedFlickerTimes = 0;
	private boolean IsRedOn = false;
	private int RedTimerState = 0;
	private MyThread newThread = null;
	private boolean IsOKLDevice = true;
	private boolean MyThreadflag = false;
	private boolean[] State = new boolean[4];
	private SysCtrlManager mSysCtrlManager = null;
	private Context mContext = null;

	private void RedLedOn() {
		try {
			mSysCtrlManager.openRedLed(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void RedLedOff() {
		try {
			mSysCtrlManager.openRedLed(false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void BlueLedOn() {
		try {
			mSysCtrlManager.openBlueLed(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void BlueLedOff() {
		try {
			mSysCtrlManager.openBlueLed(false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public PeripheralManager(Context context) {

		mContext = context.getApplicationContext();
		try {
			Class.forName("com.okl_sysctrl.sysctrlproxy.SysCtrlManager");
		} catch (ClassNotFoundException e) {
			IsOKLDevice = false;
		}
		if (!IsOKLDevice)
			return;
		try {
			mSysCtrlManager = SysCtrlManager.getSysCtrlManager();
		} catch (Exception e) {
			e.printStackTrace();
		}

		Log.i(TAG, "PeripheralManager init OK");
	}

	TimerTask task = new TimerTask() {
		public void run() {
			AddTimerNum++;
			SDAddTimerNum++;
			if (SDAddTimerNum >= 40) {
				if (!GetSDState()) {
					RedTimerState = 1;
				} else {
					if (RedTimerState != 2) {
						RedTimerState = 0;
					}
				}
				SDAddTimerNum = 0;
			}

			switch (RedTimerState) {
			case 0:
				IsRedOn = true;
				break;
			case 1:// sd lost
				if (AddTimerNum >= 1) {
					IsRedOn = !IsRedOn;
					AddTimerNum = 0;
				}
				break;
			case 2:// upload data
				if (AddTimerNum >= 4) {
					IsRedOn = !IsRedOn;
					AddTimerNum = 0;
					RedFlickerTimes++;
					if (RedFlickerTimes > 10) {
						RedFlickerTimes = 0;
						RedTimerState = 0;
					}
				}
				break;
			}
			if (IsRedOn) {
				RedLedOn();
			} else {
				RedLedOff();
			}
		}
	};

	public void StartRedLightTask() {
		if (!IsOKLDevice)
			return;
		timer = new Timer(true);
		timer.schedule(task, TimerInterval, TimerInterval);
	}

	public void StopRedlightTask() {
		if (!IsOKLDevice)
			return;
		timer.cancel();
	}

	public void RedLightUpload() {
		if (!IsOKLDevice)
			return;
		if (RedTimerState != 1) {
			RedTimerState = 2;
			AddTimerNum = 0;
			RedFlickerTimes = 0;
		}
	}

	public void StartBlueLightTask() {
		if (!IsOKLDevice)
			return;
		IntentFilter myIntentFilter = new IntentFilter();
		myIntentFilter.addAction(BROADCAST_ACTION);
		mContext.registerReceiver(mBroadcastReceiver, myIntentFilter);

		newThread = new MyThread();
		MyThreadflag = true;
		newThread.start();
	}

	public void StopBluelightTask() {
		if (!IsOKLDevice)
			return;
		mContext.unregisterReceiver(mBroadcastReceiver);
		MyThreadflag = false;
		try {
			newThread.interrupt();
		} catch (Exception e) {

		}
	}

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(BROADCAST_ACTION)) {
				State[1] = intent.getBooleanExtra("netwatchstate", false);
				State[0] = intent.getBooleanExtra("obdwatchstate", false);
				State[2] = intent.getBooleanExtra("gpswatchstate", false);
				State[3] = true;
			}
		}

	};

	private boolean GetSDState() {
		if (Environment.getExternalStorageState(path).equals(
				Environment.MEDIA_MOUNTED)) {
			return true;
		} else {
			return false;
		}
	}

	public void UpdataApp(String path)
	{
		
		try {
			mSysCtrlManager.appUpdate(path, true, "com.cpsdna.careyes", "com.cpsdna.careyes.DaemonService", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private class MyThread extends Thread {
		private void BlueLightOperate(int time, boolean state) {
			if (state) {
				BlueLedOn();
			} else {
				BlueLedOff();
			}
			try {
				Thread.sleep(time);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			super.run();
			while (MyThreadflag) {

				if (State[0] && State[1] && State[2] && State[3]) {
					BlueLightOperate(3000, true);
				} else {
					for (int i = 0; i < 4; i++) {
						if (State[i]) {
							BlueLightOperate(1000, true);
						} else {
							BlueLightOperate(200, true);
						}
						BlueLightOperate(200, false);
					}
					BlueLightOperate(3000, false);
				}
			}
		}
	}

}
