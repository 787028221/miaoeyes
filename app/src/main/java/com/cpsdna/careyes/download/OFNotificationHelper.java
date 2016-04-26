package com.cpsdna.careyes.download;

import com.cpsdna.careyes.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * TODO
 * 
 * @author wangwenbin 2013-1-27
 */
public class OFNotificationHelper {
	private Context mContext;
	private int NOTIFICATION_ID = 1;
	private int NOTIFICATION_ERROR_ID = 2;
	private Notification mNotification;
	private NotificationManager mNotificationManager;
	private PendingIntent mContentIntent;

	public OFNotificationHelper(Context context) {
		mContext = context;
	}

	/**
	 * Put the notification into the status bar
	 */
	@SuppressWarnings("deprecation")
	public void createNotification() {
		// get the notification manager
		mNotificationManager = (NotificationManager) mContext
				.getSystemService(Context.NOTIFICATION_SERVICE);

		// create the notification
		int icon = android.R.drawable.stat_sys_download;
		CharSequence tickerText = "正在下载";
		long when = System.currentTimeMillis();
		mNotification = new Notification(icon, tickerText, when);

		RemoteViews contentView = new RemoteViews(mContext.getPackageName(),
				R.layout.of_download_notify);
		contentView.setTextViewText(R.id.notify_title, mContext.getResources()
				.getString(R.string.app_name));
		contentView.setTextViewText(R.id.notify_state, mContext.getString(R.string.downfileing));
		contentView.setProgressBar(R.id.notify_processbar, 100, 0, false);

		Intent notificationIntent = new Intent();
		mContentIntent = PendingIntent.getActivity(mContext, 0,
				notificationIntent, 0);
		mNotification.contentView = contentView;
		mNotification.contentIntent = mContentIntent;
		mNotification.flags = Notification.FLAG_ONGOING_EVENT;
		mNotificationManager.notify(NOTIFICATION_ID, mNotification);
	}

	public void progressUpdate(int percentageComplete) {
		mNotification.contentView.setTextViewText(R.id.notify_state, mContext.getString(R.string.downed)
				+ percentageComplete + "%");
		mNotification.contentView.setProgressBar(R.id.notify_processbar, 100,
				percentageComplete, false);
		mNotificationManager.notify(NOTIFICATION_ID, mNotification);
	}

	public void completed() {
		// remove the notification from the status bar
		mNotificationManager.cancel(NOTIFICATION_ID);
	}
	
	public void showError(){
		mNotificationManager.cancel(NOTIFICATION_ID);
		mNotification.contentView.setTextViewText(R.id.notify_state, "下载升级文件失败！");
		mNotification.flags = Notification.FLAG_AUTO_CANCEL;
		mNotificationManager.notify(NOTIFICATION_ERROR_ID, mNotification);
	}
}
