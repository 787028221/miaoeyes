package com.cpsdna.careyes.manager;

import java.io.File;
import java.io.IOException;

import xcoding.commons.telephony.TelephonyMgr;
import xcoding.commons.util.FileUtilities;

public final class FileManager
{
    
    private static final File ROOT_PATH = TelephonyMgr.getExternalStorageDirectory();
    
    public static final File RECORDER_PATH = new File(ROOT_PATH,"CarEyes/Recorder");
    
    public static final File OTHER_PATH = new File(ROOT_PATH,"CarEyes/Other");
    public static final File OTHER_BLUETOOTH_PATH = new File(ROOT_PATH,"CarEyes/Other/Bluetooth"); //蓝牙拍摄目录
    public static final File OTHER_COLLISION_PATH = new File(ROOT_PATH,"CarEyes/Other/Collision"); //碰撞拍摄目录
    public static final File OTHER_KEY_PATH = new File(ROOT_PATH,"CarEyes/Other/Key"); //行车记录仪设备按键拍摄目录
    public static final File OTHER_WIFI_PATH = new File(ROOT_PATH,"CarEyes/Other/Wifi"); //WIFI直连APP指令拍摄目录
    public static final File OTHER_PARK_PATH = new File(ROOT_PATH,"CarEyes/Other/Park"); //停车拍摄目录
    
    private static final long SIZE_RECORDER_MAX = 90 * 1024 * 1024; //行车仪最大90M
    
    private static final long SIZE_VIDEO_MAX = 4 * 1024 * 1024; //短视频最大4M
    
    private static final long SIZE_PICTURE_MAX = (long)(1.5 * 1024 * 1024); //拍照最大1.5M
    
    private static final long SIZE_RESERVED = 100 * 1024 * 1024; //保留100M
    
    private FileManager() {
    }
    
    public synchronized static File createNewRecorderFile() throws SpaceNotEnoughException,IOException {
        long availableSize = TelephonyMgr.getFileStorageAvailableSize(ROOT_PATH) - SIZE_RESERVED;
        long recorderSize = FileUtilities.getDirectoryLength(RECORDER_PATH);
        long otherSize = FileUtilities.getDirectoryLength(OTHER_PATH);
        long disposableSize = (long)((availableSize + recorderSize + otherSize) * 0.7);
        if(recorderSize + SIZE_RECORDER_MAX <= disposableSize) { //add
            while(availableSize < SIZE_RECORDER_MAX) {
                long delSize = delEarliestOther();
                if(delSize == -1) throw new SpaceNotEnoughException();
                availableSize = availableSize + delSize;
            }
        }else {
            File[] recorderFiles = FileUtilities.sortByLastModified(RECORDER_PATH, true);
            int index = 0;
            while(recorderSize + SIZE_RECORDER_MAX > disposableSize) {
                if(index >= recorderFiles.length) throw new SpaceNotEnoughException();
                long fileSize = FileUtilities.getDirectoryLength(recorderFiles[index]);
                FileUtilities.delDirectory(recorderFiles[index]);
                recorderSize = recorderSize - fileSize;
                index = index + 1;
            }
        }
        if(!RECORDER_PATH.exists()) RECORDER_PATH.mkdirs();
        long name = System.currentTimeMillis();
        File val1 = new File(RECORDER_PATH,name + "");
        File val2 = new File(RECORDER_PATH,name + ".mp4");
        while(val1.isFile() || val2.isFile()) {
            name = name + 1;
            val1 = new File(RECORDER_PATH,name + "");
            val2 = new File(RECORDER_PATH,name + ".mp4");
        }
        return val1;
    }
    
    private static long delEarliestOther() throws IOException {
        File[] otherFiles = FileUtilities.sortByLastModified(OTHER_PATH, true);
        if(otherFiles.length == 0) return -1;
        if(otherFiles[0].isDirectory()) {
            File[] files = FileUtilities.sortByLastModified(otherFiles[0], true);
            if(files.length == 0) {
                long len = FileUtilities.getDirectoryLength(otherFiles[0]);
                FileUtilities.delDirectory(otherFiles[0]);
                return len;
            }
            long len = FileUtilities.getDirectoryLength(files[0]);
            FileUtilities.delDirectory(files[0]);
            return len;
        }else {
            long len = otherFiles[0].length();
            FileUtilities.delDirectory(otherFiles[0]);
            return len;
        }
    }
    
    public synchronized static File createNewMiaoFile(boolean isVideo) throws SpaceNotEnoughException,IOException {
        allocationOtherSpace(isVideo ? SIZE_VIDEO_MAX : SIZE_PICTURE_MAX);
        if(!OTHER_PATH.exists()) OTHER_PATH.mkdirs();
        String type = isVideo ? ".mp4" : ".jpg";
        long name = System.currentTimeMillis();
        File val1 = new File(OTHER_PATH,name + "");
        File val2 = new File(OTHER_PATH,name + type);
        while(val1.isFile() || val2.isFile()) {
            name = name + 1;
            val1 = new File(OTHER_PATH,name + "");
            val2 = new File(OTHER_PATH,name + type);
        }
        return val1;
    }
    
    private static void allocationOtherSpace(long newMaxSize) throws SpaceNotEnoughException,IOException {
        long availableSize = TelephonyMgr.getFileStorageAvailableSize(ROOT_PATH) - SIZE_RESERVED;
        long recorderSize = FileUtilities.getDirectoryLength(RECORDER_PATH);
        long otherSize = FileUtilities.getDirectoryLength(OTHER_PATH);
        long disposableSize = (long)((availableSize + recorderSize + otherSize) * 0.3);
        if(otherSize + newMaxSize <= disposableSize) { //add
            File[] recorderFiles = FileUtilities.sortByLastModified(RECORDER_PATH, true);
            int index = 0;
            while(availableSize < newMaxSize) {
                if(index >= recorderFiles.length) throw new SpaceNotEnoughException();
                long fileSize = FileUtilities.getDirectoryLength(recorderFiles[index]);
                FileUtilities.delDirectory(recorderFiles[index]);
                availableSize = availableSize + fileSize;
                index = index + 1;
            }
        }else {
            while(otherSize + newMaxSize > disposableSize) {
                long delSize = delEarliestOther();
                if(delSize == -1) throw new SpaceNotEnoughException();
                otherSize = otherSize - delSize;
            }
        }
    }
    
    public synchronized static File createNewBlueToothFile(boolean isVideo) throws SpaceNotEnoughException,IOException {
        allocationOtherSpace(isVideo ? SIZE_VIDEO_MAX : SIZE_PICTURE_MAX);
        if(!OTHER_BLUETOOTH_PATH.exists()) OTHER_BLUETOOTH_PATH.mkdirs();
        String type = isVideo ? ".mp4" : ".jpg";
        long name = System.currentTimeMillis();
        File val1 = new File(OTHER_BLUETOOTH_PATH,name + "");
        File val2 = new File(OTHER_BLUETOOTH_PATH,name + type);
        while(val1.isFile() || val2.isFile()) {
            name = name + 1;
            val1 = new File(OTHER_BLUETOOTH_PATH,name + "");
            val2 = new File(OTHER_BLUETOOTH_PATH,name + type);
        }
        return val1;
    }
    
    public synchronized static File createNewCollisionFile(boolean isVideo) throws SpaceNotEnoughException,IOException {
        allocationOtherSpace(isVideo ? SIZE_VIDEO_MAX : SIZE_PICTURE_MAX);
        if(!OTHER_COLLISION_PATH.exists()) OTHER_COLLISION_PATH.mkdirs();
        String type = isVideo ? ".mp4" : ".jpg";
        long name = System.currentTimeMillis();
        File val1 = new File(OTHER_COLLISION_PATH,name + "");
        File val2 = new File(OTHER_COLLISION_PATH,name + type);
        while(val1.isFile() || val2.isFile()) {
            name = name + 1;
            val1 = new File(OTHER_COLLISION_PATH,name + "");
            val2 = new File(OTHER_COLLISION_PATH,name + type);
        }
        return val1;
    }
    
    public synchronized static File createNewKeyFile(boolean isVideo) throws SpaceNotEnoughException,IOException {
        allocationOtherSpace(isVideo ? SIZE_VIDEO_MAX : SIZE_PICTURE_MAX);
        if(!OTHER_KEY_PATH.exists()) OTHER_KEY_PATH.mkdirs();
        String type = isVideo ? ".mp4" : ".jpg";
        long name = System.currentTimeMillis();
        File val1 = new File(OTHER_KEY_PATH,name + "");
        File val2 = new File(OTHER_KEY_PATH,name + type);
        while(val1.isFile() || val2.isFile()) {
            name = name + 1;
            val1 = new File(OTHER_KEY_PATH,name + "");
            val2 = new File(OTHER_KEY_PATH,name + type);
        }
        return val1;
    }
    
    public synchronized static File createNewWifiFile(boolean isVideo) throws SpaceNotEnoughException,IOException {
        allocationOtherSpace(isVideo ? SIZE_VIDEO_MAX : SIZE_PICTURE_MAX);
        if(!OTHER_WIFI_PATH.exists()) OTHER_WIFI_PATH.mkdirs();
        String type = isVideo ? ".mp4" : ".jpg";
        long name = System.currentTimeMillis();
        File val1 = new File(OTHER_WIFI_PATH,name + "");
        File val2 = new File(OTHER_WIFI_PATH,name + type);
        while(val1.isFile() || val2.isFile()) {
            name = name + 1;
            val1 = new File(OTHER_WIFI_PATH,name + "");
            val2 = new File(OTHER_WIFI_PATH,name + type);
        }
        return val1;
    }
    
    public synchronized static File createNewParkFile(boolean isVideo) throws SpaceNotEnoughException,IOException {
        allocationOtherSpace(isVideo ? SIZE_VIDEO_MAX : SIZE_PICTURE_MAX);
        if(!OTHER_PARK_PATH.exists()) OTHER_PARK_PATH.mkdirs();
        String type = isVideo ? ".mp4" : ".jpg";
        long name = System.currentTimeMillis();
        File val1 = new File(OTHER_PARK_PATH,name + "");
        File val2 = new File(OTHER_PARK_PATH,name + type);
        while(val1.isFile() || val2.isFile()) {
            name = name + 1;
            val1 = new File(OTHER_PARK_PATH,name + "");
            val2 = new File(OTHER_PARK_PATH,name + type);
        }
        return val1;
    }
    
}
