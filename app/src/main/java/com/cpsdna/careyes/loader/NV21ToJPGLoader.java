package com.cpsdna.careyes.loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.cpsdna.careyes.entity.CommandMedia;
import com.cpsdna.careyes.entity.CommandMediaBle;
import com.cpsdna.careyes.entity.CommandMediaCollision;
import com.cpsdna.careyes.entity.CommandMediaKey;
import com.cpsdna.careyes.entity.CommandMediaLocal;
import com.cpsdna.careyes.manager.FileManager;
import com.cpsdna.careyes.manager.SpaceNotEnoughException;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import xcoding.commons.ui.BaseTaskLoader;

public class NV21ToJPGLoader extends BaseTaskLoader<File>
{
    
    private Class<? extends CommandMedia> type;
    private byte[] data;
    private int width;
    private int height;
    
    public NV21ToJPGLoader(Context context,Class<? extends CommandMedia> type, byte[] data,int width,int height) {
        super(context);
        this.type = type;
        this.data = data;
        this.width = width;
        this.height = height;
    }
    
    @Override
    protected File loadInBackgroundImpl(boolean arg0) throws Exception
    {
        return nv21ToJPG(type,data,width,height);
    }
    
    @Override
    protected void onReleaseData(File arg0)
    {
    }
    
    public static File nv21ToJPG(Class<? extends CommandMedia> type, byte[] data,int width,int height) throws IOException,SpaceNotEnoughException {
        File path;
        if(type == CommandMediaBle.class) {
            path = FileManager.createNewBlueToothFile(false);
        }else if(type == CommandMediaLocal.class) {
            path = FileManager.createNewWifiFile(false);
        }else if(type == CommandMediaCollision.class) {
            path = FileManager.createNewCollisionFile(false);
        }else if(type == CommandMediaKey.class) {
            path = FileManager.createNewKeyFile(false);
        }else {
            path = FileManager.createNewMiaoFile(false);
        }
        FileOutputStream out = null;
        try {
            YuvImage im = new YuvImage(data, ImageFormat.NV21, width, height, null);
            Rect r = new Rect(0, 0, width, height);
            out = new FileOutputStream(path);
            im.compressToJpeg(r, 90, out);
        }finally {
            if(out != null) out.close();
        }
        File newPath = new File(path.getAbsolutePath()+".jpg");
        path.renameTo(newPath);
        return newPath;
    }
    
}
