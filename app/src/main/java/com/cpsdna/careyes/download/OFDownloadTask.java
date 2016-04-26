package com.cpsdna.careyes.download;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.TextUtils;

/**
 * TODO
 * 
 * @author wangwenbin 2013-1-27
 */
public class OFDownloadTask extends AsyncTask<String, Integer, String> {

	public static String TAG = "OFDownloadTask";

	private OFNotificationHelper mNotificationHelper;
	private OFDownloadListener mListener;
	Context mContext;

	public OFDownloadTask(Context context) {
		mNotificationHelper = new OFNotificationHelper(context);
		mContext = context;
	}

	public void setDownloadListener(OFDownloadListener listener) {
		this.mListener = listener;
	}

	protected void onPreExecute() {
		mNotificationHelper.createNotification();
	}

	@Override
	protected String doInBackground(String... params) {
		String path = params[0];
		String fileName = params[1];
		File filedir = new File(getCacheDirectory(mContext), fileName);
		if (filedir.exists()) {
			filedir.delete();
		}
		File file = new File(getCacheDirectory(mContext), fileName);
		FileOutputStream fos = null;
		InputStream is = null;
		HttpURLConnection conn = null;
		try {
			URL url = new URL(path);
			conn = (HttpURLConnection) url.openConnection();
			is = conn.getInputStream();
			fos = new FileOutputStream(file);
			conn.connect();
			if (conn.getResponseCode() >= 400) {
				return null;
			} else {
				int temp = 0;
				byte data[] = new byte[256];
				double readTotal = 0;
				int percent = 0;
				int lastPercent = 0;
				int fileSize = conn.getContentLength();
				while ((temp = is.read(data)) != -1) {
					fos.write(data, 0, temp);
					readTotal += temp;

					percent = (int) (readTotal * 100 / fileSize);
					if (percent != lastPercent) {
						lastPercent = percent;

						publishProgress(percent);
					}
				}
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			if (conn != null)
				conn.disconnect();
			try {
				if (fos != null)
					fos.close();
				if (is != null)
					is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return file.getPath();
	}

	protected void onProgressUpdate(Integer... progress) {

		mNotificationHelper.progressUpdate(progress[0]);

	}

	protected void onPostExecute(String filePath) {

		//Logs.e(TAG, "onPostExecute=" + filePath);
		if (TextUtils.isEmpty(filePath)) {
			mNotificationHelper.showError();

		} else {
			mNotificationHelper.completed();

			if (mListener != null) {
				mListener.onDownSucess(filePath);
			}
		}

	}

	/**
	 * @Description: 获取SD卡缓存目录
	 * @param context
	 */
	public static File getCacheDirectory(Context context) {
		File appCacheDir = null;
		if (Environment.getExternalStorageState().equals(
				android.os.Environment.MEDIA_MOUNTED)) {
			appCacheDir = getExternalCacheDir(context, "cache");
		}
		if (appCacheDir == null) {
			appCacheDir = context.getCacheDir();
		}
		return appCacheDir;
	}

	private static File getExternalCacheDir(Context context, String file) {
		File dataDir = new File(new File(
				Environment.getExternalStorageDirectory(), "Android"), "data");
		File appCacheDir = new File(
				new File(dataDir, context.getPackageName()), file);
		if (!appCacheDir.exists()) {
			try {
				new File(dataDir, ".nomedia").createNewFile();
			} catch (IOException e) {
			//	Logs.e(TAG,
				//		"Can't create \".nomedia\" file in application external cache directory");
			}
			if (!appCacheDir.mkdirs()) {
			//	Logs.w(TAG, "Unable to create external cache directory");
				return null;
			}
		}
		return appCacheDir;
	}
}
