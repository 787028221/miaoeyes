package com.cpsdna.careyes;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Bundle;

import com.cpsdna.careyes.NanoHTTPD.Response.Status;
import com.cpsdna.careyes.entity.CommandMediaLocal;
import com.cpsdna.careyes.manager.FileManager;
import com.cpsdna.careyes.manager.LocalTaskManager;
import com.cpsdna.careyes.manager.OBDManager;

import xcoding.commons.ui.GenericActivity;
import xcoding.commons.util.DateUtilities;
import xcoding.commons.util.LogManager;
import xcoding.commons.util.StringUtilities;
import xcoding.commons.util.TimeoutException;

public class HttpServer extends NanoHTTPD
{
    
    public static final String MIME_TYPE_JSON = "application/json";
    public static final String MIME_TYPE_JPG = "image/jpeg";
    public static final String MIME_TYPE_MP4 = "video/mp4";
    
    public static final int RESPONSE_CODE_SUCCESS = 0;
    public static final String RESPONSE_DESC_SUCCESS = "成功";
    public static final int RESPONSE_CODE_DEVICEID_INVALID = 1;
    public static final String RESPONSE_DESC_DEVICEID_INVALID = "设备与客户端用户不匹配";
    public static final int RESPONSE_CODE_TOKEN_INVALID = 2;
    public static final String RESPONSE_DESC_TOKEN_INVALID = "非法请求，请先认证";
    public static final int RESPONSE_CODE_EXEC_TIMEOUT = 3;
    public static final String RESPONSE_DESC_EXEC_TIMEOUT = "指令执行超时，请稍后再试";
    public static final int RESPONSE_CODE_WAITTING = 1000;
    public static final String RESPONSE_DESC_WAITTING = "请继续等待";
    
    private String token;
    
    private Context context;
    
    public HttpServer(Context context) {
        super(7777);
        this.context = context.getApplicationContext();
    }
    
    @Override
    public Response serve(IHTTPSession session)
    {
        try {
            Map<String, String> files = new HashMap<String, String>();
            session.parseBody(files);
            String uri = session.getUri();
            if(uri.startsWith("/careyes/media/")) {
                String part = uri.substring("/careyes/media/".length());
                int index = part.indexOf("/");
                String fileCategory = part.substring(0, index);
                String name = part.substring(index + 1);
                String mimeType = name.toLowerCase().endsWith(".jpg") ? MIME_TYPE_JPG : MIME_TYPE_MP4;
                File file = new File(getFilePath(fileCategory),name);
                long length = file.length();
                Response response;
                String range = session.getHeaders().get("range");
                if(range != null) {
                    range = range.substring(6);
                    String[] inteval = range.split("-");
                    long begin = Long.parseLong(inteval[0]);
                    long end;
                    if(inteval.length > 1) end = Long.parseLong(inteval[1]);
                    else end = length - 1;
                    long size = end - begin + 1;
                    FileInputStream input = new FileInputStream(file);
                    input.skip(begin);
                    response = new Response(Status.PARTIAL_CONTENT,mimeType,input,size);
                    response.addHeader("Content-Range", "bytes "+begin+"-"+end + "/" + length);
                }else {
                    response = new Response(Status.OK,mimeType,new FileInputStream(file),length);
                    response.addHeader("Accept-Ranges", "bytes");
                }
                return response;
            }else {
                JSONObject data = new JSONObject(files.get("postData"));
                if("/careyes/auth".equals(uri)) {
                    String deviceId = data.getString("deviceId");
                    if((OBDManager.getDeviceId()+"").equals(deviceId)) {
                        synchronized (HttpServer.this) {
                            if(token == null) token = UUID.randomUUID().toString();
                        }
                        JSONObject response = createResponse(RESPONSE_CODE_SUCCESS,RESPONSE_DESC_SUCCESS);
                        response.put("token", token);
                        byte[] result = response.toString().getBytes("UTF-8");
                        return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(result),result.length);
                    }else {
                        byte[] response = createResponse(RESPONSE_CODE_DEVICEID_INVALID,RESPONSE_DESC_DEVICEID_INVALID).toString().getBytes("UTF-8");
                        return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(response),response.length);
                    }
                }else {
                    String curToken = data.getString("token");
                    if(token == null || !token.equals(curToken)) {
                        byte[] response = createResponse(RESPONSE_CODE_TOKEN_INVALID,RESPONSE_DESC_TOKEN_INVALID).toString().getBytes("UTF-8");
                        return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(response),response.length);
                    }
                    if("/careyes/take_media".equals(uri)) {
                        int action = data.getInt("action");
                        if(action == 0) { // 拍照
                            CommandMediaLocal local = new CommandMediaLocal();
                            local.type = 1;
                            local.taskId = UUID.randomUUID().toString();
                            Bundle bundle = new Bundle();
                            bundle.putSerializable("INFO", local);
                            GenericActivity.sendRefresh(context, WelcomeActivity.REFRESHTYPE_COMMAND_REQUEST, bundle);
                            LocalTaskManager.addTask(local.taskId);
                            JSONObject response = createResponse(RESPONSE_CODE_SUCCESS, RESPONSE_DESC_SUCCESS);
                            response.put("task_id", local.taskId);
                            byte[] result = response.toString().getBytes("UTF-8");
                            return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(result),result.length);
                        }else if(action == 1) { // 录像
                            CommandMediaLocal local = new CommandMediaLocal();
                            local.type = 2;
                            local.taskId = UUID.randomUUID().toString();
                            Bundle bundle = new Bundle();
                            bundle.putSerializable("INFO", local);
                            GenericActivity.sendRefresh(context, WelcomeActivity.REFRESHTYPE_COMMAND_REQUEST, bundle);
                            LocalTaskManager.addTask(local.taskId);
                            JSONObject response = createResponse(RESPONSE_CODE_SUCCESS, RESPONSE_DESC_SUCCESS);
                            response.put("task_id", local.taskId);
                            byte[] result = response.toString().getBytes("UTF-8");
                            return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(result),result.length);
                        }
                    }else if("/careyes/query_task".equals(uri)) {
                        String taskId = data.getString("task_id");
                        String path;
                        try {
                            path = LocalTaskManager.getTask(taskId);
                        }catch(TimeoutException e) {
                            byte[] response = createResponse(RESPONSE_CODE_EXEC_TIMEOUT,RESPONSE_DESC_EXEC_TIMEOUT).toString().getBytes("UTF-8");
                            return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(response),response.length);
                        }
                        if(path == null) {
                            byte[] response = createResponse(RESPONSE_CODE_WAITTING,RESPONSE_DESC_WAITTING).toString().getBytes("UTF-8");
                            return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(response),response.length);
                        }else {
                            JSONObject response = createResponse(RESPONSE_CODE_SUCCESS, RESPONSE_DESC_SUCCESS);
                            String name = new File(path).getName();
                            response.put("url", "/careyes/media/0/" + name);
                            response.put("resourceId", name);
                            byte[] result = response.toString().getBytes("UTF-8");
                            return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(result),result.length);
                        }
                    }else if("/careyes/video_list".equals(uri)) {
                        String fileCategory = data.getString("fileCategory");
                        String fileType = data.getString("fileType");
                        File[] videos = listFiles(fileCategory, fileType);
                        Arrays.sort(videos, new Comparator<File>()
                        {
                            @Override
                            public int compare(File f1, File f2) {
                                String f1Name = f1.getName();
                                String f2Name = f2.getName();
                                long f1Time = Long.parseLong(f1Name.substring(0, f1Name.length() - 4));
                                long f2Time = Long.parseLong(f2Name.substring(0, f2Name.length() - 4));
                                long diff = f1Time - f2Time;
                                if(diff == 0) return 0;
                                else if(diff > 0) return -1;
                                else return 1;
                            }
                        });
                        JSONObject response = createResponse(RESPONSE_CODE_SUCCESS, RESPONSE_DESC_SUCCESS);
                        JSONArray array = new JSONArray();
                        response.put("list", array);
                        for(File video : videos) {
                            JSONObject item = new JSONObject();
                            String name = video.getName();
                            long id = Long.parseLong(name.substring(0, name.length() - 4));
                            item.put("resourceId", name);
                            Date dd = new Date(id);
                            item.put("date", DateUtilities.getFormatDate(dd, "yyyy-MM-dd"));
                            item.put("time", DateUtilities.getFormatDate(dd, "HH:mm:ss"));
                            item.put("playUrl", "/careyes/media/" + fileCategory + "/" + name);
                            item.put("fileSize", video.length());
                            if(name.endsWith(".jpg")) {
                                item.put("fileType", "1");
                            }else {
                                item.put("fileType", "2");
                            }
                            array.put(item);
                        }
                        byte[] result = response.toString().getBytes("UTF-8");
                        return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(result),result.length);
                    }else if("/careyes/delete_video".equals(uri)) {
                        JSONArray idArray = data.getJSONArray("id");
                        String fileCategory = data.getString("fileCategory");
                        File path = getFilePath(fileCategory);
                        for(int i = 0;i < idArray.length();i++) {
                            String id = idArray.getString(i);
                            new File(path,id).delete();
                        }
                        byte[] response = createResponse(RESPONSE_CODE_SUCCESS,RESPONSE_DESC_SUCCESS).toString().getBytes("UTF-8");
                        return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(response),response.length);
                    }else if("/careyes/media_count".equals(uri)) {
                        JSONObject rsp = createResponse(RESPONSE_CODE_SUCCESS,RESPONSE_DESC_SUCCESS);
                        JSONArray result = new JSONArray();
                        for(int i = 0;i < 6;i++) {
                            JSONObject item = new JSONObject();
                            item.put("fileCategory", i+"");
                            item.put("count", listFiles(i+"", 0+"").length);
                            result.put(item);
                        }
                        rsp.put("result", result);
                        byte[] response = rsp.toString().getBytes("UTF-8");
                        return new Response(Status.OK,MIME_TYPE_JSON,new ByteArrayInputStream(response),response.length);
                    }
                }
            }
            byte[] notFound = "not found.".getBytes("UTF-8");
            return new Response(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream(notFound), notFound.length);
        }catch(Exception e) {
            LogManager.logE(HttpServer.class, "http error", e);
            byte[] error = "server error.".getBytes(Charset.forName("UTF-8"));
            return new Response(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream(error), error.length);
        }
    }
    
    private JSONObject createResponse(int code,String desc) throws JSONException {
        JSONObject response = new JSONObject();
        response.put("code", code);
        response.put("desc", desc);
        return response;
    }
    
    private File getFilePath(String fileCategory) {
        File path;
        if("0".equals(fileCategory)) {
            path = FileManager.OTHER_WIFI_PATH;
        }else if("1".equals(fileCategory)) {
            path = FileManager.OTHER_BLUETOOTH_PATH;
        }else if("2".equals(fileCategory)) {
            path = FileManager.OTHER_KEY_PATH;
        }else if("3".equals(fileCategory)) {
            path = FileManager.RECORDER_PATH;
        }else if("4".equals(fileCategory)) {
            path = FileManager.OTHER_PARK_PATH;
        }else if("5".equals(fileCategory)) {
            path = FileManager.OTHER_COLLISION_PATH;
        }else {
            throw new IllegalArgumentException("fileCategory is invalid");
        }
        return path;
    }
    
    private File[] listFiles(String fileCategory,String fileType) {
        File path = getFilePath(fileCategory);
        File[] videos;
        if("0".equals(fileType)) {
            videos = path.listFiles(new FilenameFilter()
            {
                @Override
                public boolean accept(File arg0, String arg1)
                {
                    if(arg1.endsWith(".mp4") || arg1.endsWith(".jpg")) {
                        String name = arg1.substring(0, arg1.length() - 4);
                        return StringUtilities.isAllCharDigit(name);
                    }
                    return false;
                }
            });
        }else if("1".equals(fileType)) {
            videos = path.listFiles(new FilenameFilter()
            {
                @Override
                public boolean accept(File arg0, String arg1)
                {
                    if(arg1.endsWith(".jpg")) {
                        String name = arg1.substring(0, arg1.length() - 4);
                        return StringUtilities.isAllCharDigit(name);
                    }
                    return false;
                }
            });
        }else if("2".equals(fileType)) {
            videos = path.listFiles(new FilenameFilter()
            {
                @Override
                public boolean accept(File arg0, String arg1)
                {
                    if(arg1.endsWith(".mp4")) {
                        String name = arg1.substring(0, arg1.length() - 4);
                        return StringUtilities.isAllCharDigit(name);
                    }
                    return false;
                }
            });
        }else {
            throw new IllegalArgumentException("fileType is invalid");
        }
        if(videos == null) videos = new File[]{};
        return videos;
    }
    
}
