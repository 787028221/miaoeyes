package com.example.okl_home_v3_hs.service;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

public class Uart_Device {

	private static final String TAG = "Uart_Device";
	private FileDescriptor mFd;
	private FileInputStream mFileInputStream;
	private FileOutputStream mFileOutputStream;

	public Uart_Device(File device, int baudrate, int flags) throws IOException {

		if (!device.canRead() || !device.canWrite()) {
			try {
				Process su;
				su = Runtime.getRuntime().exec("/system/bin/su");
				String cmdString = "chmod 666 " + device.getAbsolutePath() + "\n" + "exit \n";
				su.getOutputStream().write(cmdString.getBytes());
				if (su.waitFor() != 0 || !device.canRead() || !device.canWrite()) {
					throw new IOException("can't chmod device read write mode");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new IOException(e);
			}
		}

		Log.v(TAG, "device.getAbsolutePath()= " + device.getAbsolutePath());
		Log.v(TAG, "device.baudrate= " + baudrate);

		mFd = open(device.getAbsolutePath(), baudrate, flags);
		if (mFd == null) {
			Log.e(TAG, "native open returns null");
			throw new IOException();
		}

		mFileInputStream = new FileInputStream(mFd);
		mFileOutputStream = new FileOutputStream(mFd);

	}

	public InputStream getInputStream() {
		return mFileInputStream;
	}

	public OutputStream getOutputStream() {
		return mFileOutputStream;
	}

	public void CloseLight() {
		closeLight();
	}

	public void TurnOffPower() {
		turnOffPower();

	}

	public void TurnOnPower() {
		turnOnPower();
	}

	// JNI
	public native FileDescriptor open(String path, int baudrate, int flags);

	public native void close();

	public native int closeLight();

	public native int turnOffPower();

	public native int turnOnPower();

	static {
		// Log.d(TAG, "*****Load lib serial_port*****");
		System.loadLibrary("serial_port");
	}

}
