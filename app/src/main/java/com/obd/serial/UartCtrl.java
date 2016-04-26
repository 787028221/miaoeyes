package com.obd.serial;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

import com.cpsdna.careyes.manager.OBDManager;
import com.cpsdna.careyes.utility.UtilityTools;
import com.example.okl_home_v3_hs.service.Uart_Device;

public class UartCtrl {

	public static final String TAG = "SerialPort";

	private Uart_Device mUart_Device;
	public OutputStream mOutputStream;
	private InputStream mInputStream;

	public InputStream openSerialPort() throws IOException {
	    if(mInputStream != null) return mInputStream;
	    mUart_Device = new Uart_Device(new File("/dev/ttyS0"), 19200, 0);
              mOutputStream = mUart_Device.getOutputStream();
              mInputStream = mUart_Device.getInputStream();
              return mInputStream;
	}

	public void closeSerialPort() throws IOException {
	    if(mInputStream != null) {
	        mInputStream.close();
	        mInputStream = null;
	    }
	    if(mOutputStream != null) {
	        mOutputStream.close();
	        mOutputStream = null;
	    }
	    if(mUart_Device != null) {
	        mUart_Device.close();
	        mUart_Device = null;
	    }
	}

	public void sendDataPackage(byte[] data) throws IOException {
	    if(mOutputStream == null) throw new IOException("not opened.");
//	    Log.w("--------", "最后写给OBD的数据"+UtilityTools.bytesToHexString(data));
	    mOutputStream.write(data);
              mOutputStream.flush();
	}
}
