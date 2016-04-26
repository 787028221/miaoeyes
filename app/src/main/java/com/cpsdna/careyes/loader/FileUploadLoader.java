package com.cpsdna.careyes.loader;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONObject;

import com.cpsdna.careyes.entity.FileInfo;
import com.cpsdna.careyes.manager.OBDManager;

import android.content.Context;
import xcoding.commons.net.http.HttpConnectionManager;
import xcoding.commons.net.http.MultipartParam;
import xcoding.commons.ui.BaseTaskLoader;
import xcoding.commons.util.CodeException;

public class FileUploadLoader extends BaseTaskLoader<JSONObject>
{

    // private static final String UPLOAD_URL = "http://58.215.50.61:48080/fileserver/roadlens/upload";
    private static final String UPLOAD_URL = "http://58.215.166.9:19080/fileserver/roadlens/upload";

    private FileInfo            file;

    public FileUploadLoader(Context context, FileInfo file)
    {
        super(context, false);
        this.file = file;
        HttpConnectionManager.bindApplicationContext(context);
    }

    @Override
    protected JSONObject loadInBackgroundImpl(boolean arg0) throws Exception
    {
        long fileSize = file.path.length();
        byte fileType;
        InputStream input = new FileInputStream(file.path);
        if(file.path.getName().toLowerCase().endsWith(".mp4")) {
            fileType = 2;
        }else {
            fileType = 1;
        }

        try
        {
            List<MultipartParam> prams = new LinkedList<MultipartParam>();
            prams.add(new MultipartParam("fileSize", (fileSize + "").getBytes("UTF-8")));
            prams.add(new MultipartParam("fileName", file.fileName.getBytes("UTF-8")));
            if (file.taskId != null)
            {
                prams.add(new MultipartParam("requestId", file.taskId.getBytes("UTF-8")));
            }
            prams.add(new MultipartParam("fileType", (fileType + "").getBytes("UTF-8")));
            prams.add(new MultipartParam("bussinessType", file.bussinessType.getBytes("UTF-8")));
            prams.add(new MultipartParam("eventType", file.eventType.getBytes("UTF-8")));
            prams.add(new MultipartParam("deviceId", (OBDManager.getDeviceId() + "").getBytes("UTF-8")));
            prams.add(new MultipartParam("fileCreateTime", file.fileCreateTime.getBytes("UTF-8")));
            prams.add(new MultipartParam("memo", file.memo.getBytes("UTF-8")));
            prams.add(new MultipartParam("file", input, file.fileName));
            if (file.isLocation)
            {
                prams.add(new MultipartParam("posLongitude", (file.posLng + "").getBytes("UTF-8")));
                prams.add(new MultipartParam("posLatitude", (file.posLat + "").getBytes("UTF-8")));
                prams.add(new MultipartParam("location", file.posAddr.getBytes("UTF-8")));
            }
            if (file.deviceEventId != null)
            {
                prams.add(new MultipartParam("deviceEventId", file.deviceEventId.getBytes("UTF-8")));
            }
            String result = HttpConnectionManager.doPost(UPLOAD_URL, true, 15000, null, prams, "UTF-8").getDataString("UTF-8");
            JSONObject rst = new JSONObject(result);
            String sign = rst.getString("result");
            if (!"0".equals(sign))
                throw new CodeException(sign, rst.getString("resultNote"));
            return rst;
        } finally
        {
            input.close();
        }
    }

    @Override
    protected void onReleaseData(JSONObject arg0)
    {
    }

}
