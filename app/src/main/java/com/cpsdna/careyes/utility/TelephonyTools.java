package com.cpsdna.careyes.utility;

import android.content.Context;
import android.telephony.CellLocation;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;

public class TelephonyTools {
	public static final int getPhoneType(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		return tm.getPhoneType();
	}
	
	public static final int getCountryIso(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		String operator = tm.getNetworkOperator();
		if(TextUtils.isEmpty(operator)) {
		    return 0;
		}else {
		    String s = operator.substring(0, 3);
	              int iso = Integer.parseInt(s);
	              return iso;
		}
	}
	
	public static final int getNetworkType(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE); 
		int networkType = tm.getNetworkType(); 
		int netType = 0;
		switch (networkType) {
		case TelephonyManager.NETWORK_TYPE_GPRS:
		case TelephonyManager.NETWORK_TYPE_EDGE:
		case TelephonyManager.NETWORK_TYPE_CDMA:
		case TelephonyManager.NETWORK_TYPE_1xRTT:
		case TelephonyManager.NETWORK_TYPE_IDEN:
			netType = 2;
			break;
		case TelephonyManager.NETWORK_TYPE_UMTS:
		case TelephonyManager.NETWORK_TYPE_EVDO_0:
		case TelephonyManager.NETWORK_TYPE_EVDO_A:
		case TelephonyManager.NETWORK_TYPE_HSDPA:
		case TelephonyManager.NETWORK_TYPE_HSUPA:
		case TelephonyManager.NETWORK_TYPE_HSPA:
		case TelephonyManager.NETWORK_TYPE_EVDO_B:
		case TelephonyManager.NETWORK_TYPE_EHRPD:
		case TelephonyManager.NETWORK_TYPE_HSPAP:
			netType = 3;
			break;
		case TelephonyManager.NETWORK_TYPE_LTE:
			netType = 4;
			break;
		default:
			netType = 0;
			break;
		}
		if(tm.getNetworkOperatorName().equals("中国移动")){
			if (netType==2) {
				return 1;
			}else if (netType==3) {
				return 4;
			}else if (netType==4) {
				return 7;
			}
		}else if (tm.getNetworkOperatorName().equals("中国联通")) {
			if (netType==2) {
				return 2;
			}else if (netType==3) {
				return 5;
			}else if (netType==4) {
				return 8;
			}
		}else if (tm.getNetworkOperatorName().equals("中国电信")) {
			if (netType==2) {
				return 3;
			}else if (netType==3) {
				return 6;
			}else if (netType==4) {
				return 9;
			}
		}
		return 0;
	}
	
	public static final int getLAC(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		CellLocation cellLocation = tm.getCellLocation();
		if (cellLocation instanceof GsmCellLocation) {
			GsmCellLocation gsmCellLocation = (GsmCellLocation) tm.getCellLocation();
			return gsmCellLocation.getLac();
		}else {
			return 0;
		}
		
	}
	
	public static final int getCID(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		CellLocation cellLocation = tm.getCellLocation();
		if (cellLocation instanceof GsmCellLocation) {
			GsmCellLocation gsmCellLocation = (GsmCellLocation) tm.getCellLocation();
			return gsmCellLocation.getCid();
		}else {
			return 0;
		}
	}
	
	public static final int getBID(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		CellLocation cellLocation = tm.getCellLocation();
		if (cellLocation instanceof CdmaCellLocation) {
			CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) tm.getCellLocation();
			return cdmaCellLocation.getBaseStationId();
		}else {
			return 0;
		}
	}
	
	public static final int getSID(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		CellLocation cellLocation = tm.getCellLocation();
		if (cellLocation instanceof CdmaCellLocation) {
			CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) tm.getCellLocation();
			return cdmaCellLocation.getSystemId();
		}else {
			return 0;
		}

	}
	
	public static final int getNID(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		CellLocation cellLocation = tm.getCellLocation();
		if (cellLocation instanceof CdmaCellLocation) {
			CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) tm.getCellLocation();
			return cdmaCellLocation.getNetworkId();
		}else {
			return 0;
		}
	}
	public static final String getICCID(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		String iccidStr = tm.getSimSerialNumber();
		return iccidStr;
	}
	public static final String getIMSI(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		String imsiStr = tm.getSubscriberId();
		return imsiStr;
	}
	public static final String getIMEI(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		String imeiStr = tm.getDeviceId();
		return imeiStr;
	}
	
	
	
	
}
