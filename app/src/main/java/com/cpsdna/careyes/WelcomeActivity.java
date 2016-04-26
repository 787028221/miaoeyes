package com.cpsdna.careyes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.Loader;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.cpsdna.careyes.entity.Collision;
import com.cpsdna.careyes.entity.CommandMedia;
import com.cpsdna.careyes.entity.CommandMediaBle;
import com.cpsdna.careyes.entity.CommandMediaCollision;
import com.cpsdna.careyes.entity.CommandMediaKey;
import com.cpsdna.careyes.entity.CommandMediaLocal;
import com.cpsdna.careyes.entity.FileInfo;
import com.cpsdna.careyes.loader.FileUploadLoader;
import com.cpsdna.careyes.loader.NV21ToJPGLoader;
import com.cpsdna.careyes.manager.AudioManager;
import com.cpsdna.careyes.manager.FileManager;
import com.cpsdna.careyes.manager.NetManager;
import com.cpsdna.careyes.manager.PeripheralManager;
import com.cpsdna.careyes.manager.SpaceNotEnoughException;
import com.sly.vdrsdk.MonetService;
import com.sly.video.AvcRecorder;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import xcoding.commons.media.MediaManager;
import xcoding.commons.ui.BaseLoaderCallbacks;
import xcoding.commons.ui.LoaderResult;
import xcoding.commons.ui.ToastManager;
import xcoding.commons.util.DateUtilities;
import xcoding.commons.util.LogManager;

public class WelcomeActivity extends BaseActivity
{
    
    public static final String REFRESHTYPE_COMMAND_REQUEST = "COMMAND_REQUEST";
    public static final String REFRESHTYPE_COMMAND_COLLISION = "COMMAND_COLLISION";
    public static final String REFRESHTYPE_ACC_POWER_OFF = "ACC_POWER_OFF";
    private PeripheralManager mPeripheralManager =null;

    private Camera camera;

    private SurfaceHolder holder;
    
    private MediaRecorder mainRecorder;
    private File mainRecorderFile;
    private Runnable mainRecorderRunnable;
    
    private AvcRecorder recorder;
    private File avcFile;
    
    private Timer timer = new Timer();
    private Handler handler = new Handler();
    
    private List<CommandMedia> recorderCache = new LinkedList<CommandMedia>();
    
    private int cameraId = MediaManager.getCameraBackId();
    
    private Runnable videoLoginRunnable;
    private boolean isVideoRunning = false;
    
    private byte[] curOriginalData;
    private Size previewSize;
    
    private Collision curCollision;
    
    private BroadcastReceiver otherReceiver;
    
    private TelephonyManager telManager;
    private MyPhoneStateListener phoneStateListener;

    static int currentSignalStrength;
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.welcome);
        startService(new Intent(this, ControlService.class));
        ToastManager.showLong(this, "后台服务已经开启...");
        startService(new Intent(this, DaemonService.class));
        mPeripheralManager = new PeripheralManager(this);
        mPeripheralManager.StartRedLightTask();
        mPeripheralManager.StartBlueLightTask();
        try {
            camera = Camera.open(cameraId);
        }catch(RuntimeException e) {
            LogManager.logE(WelcomeActivity.class, "open camera failed.", e);
            ToastManager.showLong(WelcomeActivity.this, "打开摄像头失败");
            finish();
            return;
        }
        camera.setDisplayOrientation(MediaManager.getCameraDisplayOrientation(getWindow(), cameraId));
        Parameters parameters = camera.getParameters();
        List<String> focusModes = parameters.getSupportedFocusModes();
        if(focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        Size picSize = MediaManager.getCameraPictureSize(camera, MediaManager.RESOLUTION_720P);
        parameters.setPictureSize(picSize.width, picSize.height);
        previewSize = MediaManager.getCameraPreviewSize(camera, MediaManager.RESOLUTION_360P);
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        camera.setParameters(parameters);
        
        SurfaceView surface = (SurfaceView)findViewById(R.id.surface);
        holder = surface.getHolder();
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder.addCallback(new SurfaceHolder.Callback()
        {
            @Override
            public void surfaceCreated(SurfaceHolder arg0) {
                mainRecorderRunnable.run();
            }
            
            @Override
            public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
            }
            
            @Override
            public void surfaceDestroyed(SurfaceHolder arg0) {
                handler.removeCallbacks(mainRecorderRunnable);
                if(mainRecorder != null) {
                    mainRecorder.stop();
                    mainRecorder.release();
                    mainRecorderFile.renameTo(new File(mainRecorderFile.getAbsolutePath()+".mp4"));
                    mainRecorderFile = null;
                    camera.stopPreview();
                    camera.unlock();
                    mainRecorder = null;
                    curOriginalData = null;
                }
            }
        });
        
        final Size videoSize = MediaManager.getCameraVideoSize(camera, MediaManager.RESOLUTION_1080P);
        camera.unlock();
        mainRecorderRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                boolean shouldClear = false;
                try {
                    if(mainRecorder != null) {
                        mainRecorder.stop();
                        mainRecorder.release();
                        mainRecorderFile.renameTo(new File(mainRecorderFile.getAbsolutePath()+".mp4"));
                        mainRecorderFile = null;
                        camera.stopPreview();
                        camera.unlock();
                        mainRecorder = null;
                        curOriginalData = null;
                    }
                    mainRecorder = new MediaRecorder();
                    shouldClear = true;
                    mainRecorder.setCamera(camera);
                    mainRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                    mainRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mainRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mainRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                    mainRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    // 设置视频录制的分辨率。必须放在设置编码和格式的后面，否则报错
                    mainRecorder.setVideoSize(videoSize.width, videoSize.height);
                    mainRecorder.setOrientationHint(MediaManager.getCameraDisplayOrientation(getWindow(), cameraId));
                    // 设置录制的视频帧率。必须放在设置编码和格式的后面，否则报错
                    //recorder.setVideoFrameRate(20);
                    mainRecorder.setPreviewDisplay(holder.getSurface());
                    // 设置视频文件输出的路径
                    mainRecorderFile = FileManager.createNewRecorderFile();
                    mainRecorder.setOutputFile(mainRecorderFile.getAbsolutePath());
                    mainRecorder.setVideoEncodingBitRate(3500000);
                    mainRecorder.prepare();
                    mainRecorder.start();
                    camera.setPreviewCallback(new Camera.PreviewCallback()
                    {
                        @Override
                        public void onPreviewFrame(byte[] arg0, Camera arg1)
                        {
                            if(isVideoRunning) {
                                MonetService.onPreviewFrame(previewSize.width, previewSize.height, arg0);
                            }
                            if(recorder != null) {
                                recorder.ProvideCameraFrame(previewSize.width, previewSize.height, arg0, arg0.length);
                            }
                            curOriginalData = arg0;
                        }
                    });
                    handler.postDelayed(this, 1000 * 60 * 3);
                }catch(SpaceNotEnoughException e) {
                    LogManager.logE(WelcomeActivity.class, "create recorder failed.", e);
                    AudioManager.getInstance(WelcomeActivity.this).play(R.raw.tf_none);
                    ToastManager.showLong(WelcomeActivity.this, "录制视频发生错误：空间不足，将稍后再试");
                    if(shouldClear) {
                        mainRecorder.release();
                        mainRecorder = null;
                        if(mainRecorderFile != null) mainRecorderFile.delete();
                    }
                    handler.postDelayed(this, 15000);
                }catch(Exception e) {
                    LogManager.logE(WelcomeActivity.class, "create recorder failed.", e);
                    ToastManager.showLong(WelcomeActivity.this, "录制视频发生错误，将稍后再试");
                    if(shouldClear) {
                        mainRecorder.release();
                        mainRecorder = null;
                        if(mainRecorderFile != null) mainRecorderFile.delete();
                    }
                    handler.postDelayed(this, 15000);
                }
            }
        };
        
        /**SDKInitializer.initialize(this, "");
//      MonetService.start(SDKInitializer.getContext(), "115.28.41.212", 5060);
        MonetService.start(SDKInitializer.getContext(), "139.196.188.147", 5060, "192.168.43.1", 5060);
//      MonetService.start(SDKInitializer.getContext(), "172.19.1.175", 5060);
//      MonetService.start(SDKInitializer.getContext(), "172.19.1.106", 5060);
        videoLoginRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                MonetService.getInstance().login("13166662016", "662016");
            }
        };
        MonetService.setOnLoginStateListener(new MonetService.OnLoginStateListener()
        {
            @Override
            public void onLogoutResult(int arg0, String arg1)
            {
                LogManager.logI(WelcomeActivity.class, "video login out.");
            }
            
            @Override
            public void onLoginResult(int arg0, String arg1)
            {
                if(arg0 == SipUaService.SIPUA_REGISTER_SUCCESS) {
                    LogManager.logI(WelcomeActivity.class, "video login successfully.");
                }else {
                    LogManager.logE(WelcomeActivity.class, "video login failed,would try again...");
                    handler.postDelayed(videoLoginRunnable, 10000);
                }
            }
        });
        videoLoginRunnable.run();
        MonetService.getInstance().setOnCallStateListener(new MonetService.OnCallStateListener()
        {
            @Override
            public void onCallUpdateMedia(int arg0, SurfaceView arg1)
            {
            }
            
            @Override
            public void onCallStart(int arg0, int arg1, SurfaceView arg2)
            {
                isVideoRunning = true;
            }
            
            @Override
            public void onCallRinging(int arg0)
            {
            }
            
            @Override
            public void onCallHanguped(int arg0)
            {
                onCallStopped();
            }
            
            private void onCallStopped() {
                isVideoRunning = false;
            }
            
            @Override
            public void onCallCanceled(int arg0)
            {
                onCallStopped();
            }
            
            @Override
            public void onCallAnswered(int arg0)
            {
            }
        });**/
        
        //用来监听手机的信号强度
		phoneStateListener = new MyPhoneStateListener();
		telManager = (TelephonyManager) getSystemService(getApplicationContext().TELEPHONY_SERVICE);
		telManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }
    
    @Override
    protected void onStart()
    {
        super.onStart();
        otherReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context arg0, Intent arg1)
            {
                CommandMediaKey media = new CommandMediaKey();
                media.type = 2; //视频
                Bundle params = new Bundle();
                params.putSerializable("INFO", media);
                onRefresh(REFRESHTYPE_COMMAND_REQUEST, params);
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.cayboy.action.TAKE_PICTURE");
        registerReceiver(otherReceiver, intentFilter);
    }
    
    @Override
    protected void onStop()
    {
        super.onStop();
        unregisterReceiver(otherReceiver);
        otherReceiver = null;
    }
    
    @Override
    protected String[] getRefreshTypes()
    {
        super.getRefreshTypes();
        return new String[]{ REFRESHTYPE_COMMAND_REQUEST,REFRESHTYPE_COMMAND_COLLISION,REFRESHTYPE_ACC_POWER_OFF };
    }
    
    @Override
    protected void onRefresh(String refreshType, Bundle bundle)
    {
        super.onRefresh(refreshType, bundle);
        if(refreshType.equals(REFRESHTYPE_COMMAND_COLLISION)) { //碰撞
            String eventId = bundle.getString("EVENTID");
            boolean confirm = bundle.getBoolean("CONFIRM", false);
            if(confirm) {
                if(curCollision != null && curCollision.eventId.equals(eventId)) {
                    if(!curCollision.confirm) {
                        curCollision.confirm = true;
                        if(curCollision.pic1 != null && curCollision.pic2 != null && curCollision.pic3 != null) {
                            uploadCollisionPic(curCollision);
                        }
                        if(curCollision.videoPath != null) {
                            final FileInfo info = new FileInfo();
                            info.fileName = curCollision.videoPath.getName();
                            info.fileCreateTime = curCollision.videoCreateTime;
                            info.memo = "";
                            info.isLocation = curCollision.isLocation;
                            info.posLng = curCollision.posLng;
                            info.posLat = curCollision.posLat;
                            info.posAddr = curCollision.posAddr;
                            info.path = curCollision.videoPath;
                            info.bussinessType = "3";
                            info.eventType = "1";
                            info.deviceEventId = curCollision.eventId;
                            LogManager.logI(WelcomeActivity.class, "开始上传视频...");
                            mPeripheralManager.RedLightUpload();
                            final int loaderId = generateLoaderId();
                            getSupportLoaderManager().restartLoader(loaderId, null, new BaseLoaderCallbacks<JSONObject>() {
                                @Override
                                public Loader<LoaderResult<JSONObject>> onCreateLoader(int arg0, Bundle arg1)
                                {
                                    return new FileUploadLoader(WelcomeActivity.this, info);
                                }
                                @Override
                                protected void onLoadSuccess(Loader<LoaderResult<JSONObject>> arg0, JSONObject arg1, boolean arg2)
                                {
                                    LogManager.logI(WelcomeActivity.class, "上传视频成功");
                                    getSupportLoaderManager().destroyLoader(loaderId);
                                }
                                @Override
                                protected void onLoadFailure(Loader<LoaderResult<JSONObject>> arg0, Exception arg1, boolean arg2)
                                {
                                    LogManager.logE(WelcomeActivity.class, "上传视频失败", arg1);
                                    getSupportLoaderManager().destroyLoader(loaderId);
                                }
                            });
                        }
                    }
                }
            }else {
                final Collision collision = new Collision();
                collision.eventId = eventId;
                collision.isLocation = bundle.getBoolean("IS_LOCATION", false);
                if(collision.isLocation) {
                    collision.posLat = bundle.getDouble("POSLAT",0);
                    collision.posLng = bundle.getDouble("POSLNG",0);
                    collision.posAddr = bundle.getString("POSADDR");
                }
                curCollision = collision;
                timer.purge();
                timer.schedule(new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        byte[] picData = curOriginalData;
                        if(picData == null) {
                            LogManager.logE(WelcomeActivity.class, "碰撞拍照失败：预览未就绪");
                            cancel();
                            return;
                        }
                        nv21ToJPG(CommandMediaCollision.class, picData, previewSize.width, previewSize.height, new NV21ToJPGCallback()
                        {
                            @Override
                            public void onSuccess(File path)
                            {
                                AudioManager.getInstance(WelcomeActivity.this).play(R.raw.camera_over);
                                String time = path.getName();
                                int index = time.lastIndexOf(".");
                                if(index != -1) time = time.substring(0, index);
                                String createTime = DateUtilities.getFormatDate(new Date(Long.parseLong(time)), "yyyy-MM-dd HH:mm:ss");
                                if(collision.pic1 == null) { //要使用collision而不是curCollision，curCollision可能是变化的
                                    collision.pic1 = path;
                                    collision.pic1CreateTime = createTime;
                                }else if(collision.pic2 == null) {
                                    collision.pic2 = path;
                                    collision.pic2CreateTime = createTime;
                                }else if(collision.pic3 == null) {
                                    collision.pic3 = path;
                                    collision.pic3CreateTime = createTime;
                                    cancel();
                                    if(collision.confirm) {
                                        handler.post(new Runnable()
                                        {
                                            @Override
                                            public void run()
                                            {
                                                uploadCollisionPic(collision);
                                            }
                                        });
                                    }
                                }
                            }
                            
                            @Override
                            public void onFailure(Exception e)
                            {
                                if(e instanceof SpaceNotEnoughException) {
                                    LogManager.logE(WelcomeActivity.class, "获取视频存储路径失败", e);
                                    AudioManager.getInstance(WelcomeActivity.this).play(R.raw.tf_none);
                                }else {
                                    LogManager.logE(WelcomeActivity.class, "把拍摄的照片保存到磁盘失败", e);
                                }
                                cancel();
                            }
                        });
                    }
                }, 0, 500);
                CommandMediaCollision media = new CommandMediaCollision();
                media.type = 2; //视频
                media.isLocation = collision.isLocation;
                media.posLng = collision.posLng;
                media.posLat = collision.posLat;
                media.posAddr = collision.posAddr;
                media.collision = collision;
                Bundle params = new Bundle();
                params.putSerializable("INFO", media);
                onRefresh(REFRESHTYPE_COMMAND_REQUEST, params);
            }
        }else if(refreshType.equals(REFRESHTYPE_COMMAND_REQUEST)) { //瞄一眼
            final CommandMedia media = (CommandMedia)bundle.getSerializable("INFO");
            if(camera == null) {
                LogManager.logE(WelcomeActivity.class, "多媒体操作失败：摄像头未开启");
            }else {
                if(media.type == 1) { //拍照
                    byte[] data = curOriginalData;
                    if(data == null) {
                        LogManager.logE(WelcomeActivity.class, "多媒体操作失败：预览未就绪");
                    }else {
                        LogManager.logI(WelcomeActivity.class, "开始拍照...");
                        nv21ToJPG(media.getClass(), data, previewSize.width, previewSize.height, new NV21ToJPGCallback()
                        {
                            @Override
                            public void onSuccess(final File path)
                            {
                                AudioManager.getInstance(WelcomeActivity.this).play(R.raw.camera_over);
                                if(media instanceof CommandMediaLocal) {
                                    Intent intent = new Intent(WelcomeActivity.this, ControlService.class);
                                    intent.putExtra("TASK", media.taskId);
                                    intent.putExtra("DATA", path.getAbsolutePath());
                                    startService(intent);
                                }else {
                                    boolean shouldDelete = false;
                                    final FileInfo pic = new FileInfo();
                                    pic.fileName = path.getName();
                                    String time = path.getName();
                                    int index = time.lastIndexOf(".");
                                    if(index != -1) time = time.substring(0, index);
                                    pic.fileCreateTime = DateUtilities.getFormatDate(new Date(Long.parseLong(time)), "yyyy-MM-dd HH:mm:ss");
                                    pic.memo = "";
                                    pic.isLocation = media.isLocation;
                                    pic.posLng = media.posLng;
                                    pic.posLat = media.posLat;
                                    pic.posAddr = media.posAddr;
                                    pic.path = path;
                                    if(media instanceof CommandMediaBle) {
                                        pic.bussinessType = "2";
                                        pic.eventType = "0";
                                    }else if(media instanceof CommandMediaCollision) {
                                        return; //已单独处理，不可能发生
                                    }else if(media instanceof CommandMediaKey) {
                                        pic.bussinessType = "2";
                                        pic.eventType = "0";
                                    }else {
                                        shouldDelete = true;
                                        pic.bussinessType = "1";
                                        pic.eventType = "0";
                                        pic.taskId = media.taskId;
                                    }
                                    LogManager.logI(WelcomeActivity.class, "开始上传图片...");
                                    mPeripheralManager.RedLightUpload();
                                    final int loaderId = generateLoaderId();
                                    final boolean shouldDeleteCopy = shouldDelete;
                                    getSupportLoaderManager().restartLoader(loaderId, null, new BaseLoaderCallbacks<JSONObject>()
                                    {
                                        @Override
                                        public Loader<LoaderResult<JSONObject>> onCreateLoader(int arg0, Bundle arg1)
                                        {
                                            return new FileUploadLoader(WelcomeActivity.this, pic);
                                        }
                                        @Override
                                        protected void onLoadSuccess(Loader<LoaderResult<JSONObject>> arg0, JSONObject arg1, boolean arg2)
                                        {
                                            LogManager.logI(WelcomeActivity.class, "上传图片成功");
                                            getSupportLoaderManager().destroyLoader(loaderId);
                                            if(shouldDeleteCopy) path.delete();
                                        }
                                        @Override
                                        protected void onLoadFailure(Loader<LoaderResult<JSONObject>> arg0, Exception arg1, boolean arg2)
                                        {
                                            LogManager.logE(WelcomeActivity.class, "上传图片失败", arg1);
                                            getSupportLoaderManager().destroyLoader(loaderId);
                                            if(shouldDeleteCopy) path.delete();
                                        }
                                    });
                                }
                            }
                            
                            @Override
                            public void onFailure(Exception e)
                            {
                                if(e instanceof SpaceNotEnoughException) {
                                    LogManager.logE(WelcomeActivity.class, "获取视频存储路径失败", e);
                                    AudioManager.getInstance(WelcomeActivity.this).play(R.raw.tf_none);
                                }else {
                                    LogManager.logE(WelcomeActivity.class, "把拍摄的照片保存到磁盘失败", e);
                                }
                            }
                        });
                    }
                }else if(media.type == 2) { //视频
                    LogManager.logI(WelcomeActivity.class, "开始录制视频...");
                    try {
                        if(media instanceof CommandMediaBle) {
                            if(recorder != null) { //在录制的时候又传来了录制指令
                                LogManager.logE(WelcomeActivity.class, "already in recording,current request has been ignored.");
                                return;
                            }
                            avcFile = FileManager.createNewBlueToothFile(true);
                        }else if(media instanceof CommandMediaLocal) {
                            if(recorder != null) { //在录制的时候又传来了录制指令
                                LogManager.logE(WelcomeActivity.class, "already in recording,current request has been ignored.");
                                return;
                            }
                            avcFile = FileManager.createNewWifiFile(true);
                        }else if(media instanceof CommandMediaCollision) {
                            if(recorder != null) { //在录制的时候又传来了录制指令
                                LogManager.logE(WelcomeActivity.class, "already in recording,current request has been ignored.");
                                return;
                            }
                            avcFile = FileManager.createNewCollisionFile(true);
                        }else if(media instanceof CommandMediaKey) {
                            if(recorder != null) { //在录制的时候又传来了录制指令
                                LogManager.logE(WelcomeActivity.class, "already in recording,current request has been ignored.");
                                return;
                            }
                            avcFile = FileManager.createNewKeyFile(true);
                        }else {
                            if(recorder != null) { //在录制的时候又传来了录制指令
                                recorderCache.add(media);
                                return;
                            }
                            avcFile = FileManager.createNewMiaoFile(true);
                        }
                    }catch(IOException e) {
                        LogManager.logE(WelcomeActivity.class, "获取视频存储路径失败", e);
                        return;
                    }catch(SpaceNotEnoughException e) {
                        LogManager.logE(WelcomeActivity.class, "获取视频存储路径失败", e);
                        AudioManager.getInstance(WelcomeActivity.this).play(R.raw.tf_none);
                        return;
                    }
                    try {
                        recorder = new AvcRecorder(this);
                        boolean result = recorder.createRecorder(previewSize.width,previewSize.height,15,400);
                        if(!result) throw new Exception("create recorder failed.");
                        recorder.start(avcFile.getAbsolutePath(),6000); //第二个参数暂时无效
                        AudioManager.getInstance(WelcomeActivity.this).play(R.raw.shot_start);
                        recorderCache.add(media);
                        timer.purge();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        recorder.stop();
                                        recorder = null;
                                        String time = avcFile.getName();
                                        File newAvcFile = new File(avcFile.getAbsolutePath()+".mp4");
                                        avcFile.renameTo(newAvcFile);
                                        avcFile = newAvcFile;
                                        AudioManager.getInstance(WelcomeActivity.this).play(R.raw.shot_over);
                                        String fileCreateTime = DateUtilities.getFormatDate(new Date(Long.parseLong(time)), "yyyy-MM-dd HH:mm:ss");
                                        final int cacheSize = recorderCache.size();
                                        final int[] count = new int[1];
                                        count[0] = 0;
                                        final boolean[] shouldDelete = new boolean[1];
                                        shouldDelete[0] = true;
                                        for(final CommandMedia media : recorderCache) {
                                            if(media instanceof CommandMediaLocal) {
                                                shouldDelete[0] = false;
                                                Intent intent = new Intent(WelcomeActivity.this, ControlService.class);
                                                intent.putExtra("TASK", media.taskId);
                                                intent.putExtra("DATA", avcFile.getAbsolutePath());
                                                startService(intent);
                                            }else {
                                                boolean couldUpload = true;
                                                final FileInfo info = new FileInfo();
                                                info.fileName = avcFile.getName();
                                                info.fileCreateTime = fileCreateTime;
                                                info.memo = "";
                                                info.isLocation = media.isLocation;
                                                info.posLng = media.posLng;
                                                info.posLat = media.posLat;
                                                info.posAddr = media.posAddr;
                                                info.path = avcFile;
                                                if(media instanceof CommandMediaBle) { //蓝牙
                                                    shouldDelete[0] = false;
                                                    info.bussinessType = "2";
                                                    info.eventType = "0";
                                                }else if(media instanceof CommandMediaCollision) { //碰撞
                                                    shouldDelete[0] = false;
                                                    CommandMediaCollision collisionMedia = (CommandMediaCollision)media;
                                                    info.bussinessType = "3";
                                                    info.eventType = "1";
                                                    info.deviceEventId = collisionMedia.collision.eventId;
                                                    collisionMedia.collision.videoPath = avcFile;
                                                    collisionMedia.collision.videoCreateTime = fileCreateTime;
                                                    couldUpload = collisionMedia.collision.confirm;
                                                }else if(media instanceof CommandMediaKey) {
                                                    shouldDelete[0] = false;
                                                    info.bussinessType = "2";
                                                    info.eventType = "0";
                                                }else {
                                                    info.bussinessType = "1";
                                                    info.eventType = "0";
                                                    info.taskId = media.taskId;
                                                }
                                                if(couldUpload) {
                                                    LogManager.logI(WelcomeActivity.class, "开始上传视频...");
                                                    mPeripheralManager.RedLightUpload();
                                                    final int loaderId = generateLoaderId();
                                                    final File curAvcFile = avcFile;
                                                    getSupportLoaderManager().restartLoader(loaderId, null, new BaseLoaderCallbacks<JSONObject>() {
                                                        @Override
                                                        public Loader<LoaderResult<JSONObject>> onCreateLoader(int arg0, Bundle arg1)
                                                        {
                                                            return new FileUploadLoader(WelcomeActivity.this, info);
                                                        }
                                                        @Override
                                                        protected void onLoadSuccess(Loader<LoaderResult<JSONObject>> arg0, JSONObject arg1, boolean arg2)
                                                        {
                                                            LogManager.logI(WelcomeActivity.class, "上传视频成功");
                                                            getSupportLoaderManager().destroyLoader(loaderId);
                                                            if(shouldDelete[0] && ++count[0] >= cacheSize) curAvcFile.delete(); //全部上传完后删除文件
                                                        }
                                                        @Override
                                                        protected void onLoadFailure(Loader<LoaderResult<JSONObject>> arg0, Exception arg1, boolean arg2)
                                                        {
                                                            LogManager.logE(WelcomeActivity.class, "上传视频失败", arg1);
                                                            getSupportLoaderManager().destroyLoader(loaderId);
                                                            if(shouldDelete[0] && ++count[0] >= cacheSize) curAvcFile.delete(); //全部上传完后删除文件
                                                        }
                                                    });
                                                }
                                            }
                                        }
                                        recorderCache.clear(); //清除缓存
                                    }
                                });
                            }
                        }, 5000);
                    }catch(Exception e) {
                        recorder = null;
                        avcFile.delete();
                        LogManager.logE(WelcomeActivity.class, "录制视频时发生错误", e);
                    }
                }
            }
        }else if(refreshType.equals(REFRESHTYPE_ACC_POWER_OFF)) {
            finish();
        }
    }
    
    private void uploadCollisionPic(final Collision collision) {
        for(int i = 0;i < 3;i++) {
            final FileInfo pic = new FileInfo();
            pic.memo = "";
            pic.bussinessType = "3";
            pic.eventType = "1";
            pic.deviceEventId = collision.eventId;
            pic.isLocation = collision.isLocation;
            pic.posLng = collision.posLng;
            pic.posLat = collision.posLat;
            pic.posAddr = collision.posAddr;
            if(i == 0) {
                pic.fileName = collision.pic1.getName();
                pic.path = collision.pic1;
                pic.fileCreateTime = collision.pic1CreateTime;
            }else if(i == 1) {
                pic.fileName = collision.pic2.getName();
                pic.path = collision.pic2;
                pic.fileCreateTime = collision.pic2CreateTime;
            }else if(i == 2) {
                pic.fileName = collision.pic3.getName();
                pic.path = collision.pic3;
                pic.fileCreateTime = collision.pic3CreateTime;
            }
            final int loaderId = generateLoaderId();
            getSupportLoaderManager().restartLoader(loaderId, null, new BaseLoaderCallbacks<JSONObject>()
            {
                @Override
                public Loader<LoaderResult<JSONObject>> onCreateLoader(int arg0, Bundle arg1)
                {
                    return new FileUploadLoader(WelcomeActivity.this, pic);
                }
                @Override
                protected void onLoadSuccess(Loader<LoaderResult<JSONObject>> arg0, JSONObject arg1, boolean arg2)
                {
                    LogManager.logI(WelcomeActivity.class, "上传碰撞图片成功");
                    getSupportLoaderManager().destroyLoader(loaderId);
                }
                @Override
                protected void onLoadFailure(Loader<LoaderResult<JSONObject>> arg0, Exception arg1, boolean arg2)
                {
                    LogManager.logE(WelcomeActivity.class, "上传碰撞图片失败", arg1);
                    getSupportLoaderManager().destroyLoader(loaderId);
                }
            });
        }
    }
    
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        timer.cancel();
        mPeripheralManager.StopRedlightTask();
        mPeripheralManager.StopBluelightTask();
        if(recorder != null) {
            recorder.stop();
            avcFile.delete();
        }
        if(camera != null) camera.release();
        /**if(videoLoginRunnable != null) handler.removeCallbacks(videoLoginRunnable);
        MonetService.getInstance().setOnCallStateListener(null);
        MonetService.setOnLoginStateListener(null);
        if(isVideoRunning) {
            MonetService.getInstance().stopCall();
        }
        MonetService.getInstance().logout();**/
    }
    
    private int loaderId = -1;
    private int generateLoaderId() {
        if(loaderId == Integer.MAX_VALUE) loaderId = -1;
        return ++loaderId;
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        if(camera != null) {
            camera.setDisplayOrientation(MediaManager.getCameraDisplayOrientation(getWindow(), cameraId));
        }
    }
    
    /**
     * <p>当前的回调线程是不确定的，取决于调用当前方法的线程，在那个线程调用，就在那个线程回调
     * <p>如果在非UI线程调用，可以保证回调是同步的，即当当前方法返回时，回调已经被执行
     * @param type
     * @param data
     * @param previewWidth
     * @param previewHeight
     * @param callback
     */
    private void nv21ToJPG(final Class<? extends CommandMedia> type, final byte[] data,final int previewWidth,final int previewHeight,final NV21ToJPGCallback callback) {
        if(Looper.myLooper() == Looper.getMainLooper()) {
            final int loaderId = generateLoaderId();
            getSupportLoaderManager().restartLoader(loaderId, null, new BaseLoaderCallbacks<File>() {
                @Override
                public Loader<LoaderResult<File>> onCreateLoader(int arg0, Bundle arg1)
                {
                    return new NV21ToJPGLoader(WelcomeActivity.this, type, data, previewWidth, previewHeight);
                }
                @Override
                protected void onLoadSuccess(Loader<LoaderResult<File>> arg0, File arg1, boolean arg2)
                {
                    callback.onSuccess(arg1);
                    getSupportLoaderManager().destroyLoader(loaderId);
                }
                @Override
                protected void onLoadFailure(Loader<LoaderResult<File>> arg0, Exception arg1, boolean arg2)
                {
                    callback.onFailure(arg1);
                    getSupportLoaderManager().destroyLoader(loaderId);
                }
            });
        }else {
            try {
                File path = NV21ToJPGLoader.nv21ToJPG(type, data, previewWidth, previewHeight);
                callback.onSuccess(path);
            }catch(Exception e) {
                callback.onFailure(e);
            }
        }
    }
    
    private static interface NV21ToJPGCallback {
        public void onSuccess(File path);
        public void onFailure(Exception e);
    }
    
	@Override
	protected void onPause() {
		super.onPause();
		telManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		telManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
	}
    
	//手机信号强度监听代理
	private class MyPhoneStateListener extends PhoneStateListener {
		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			super.onSignalStrengthsChanged(signalStrength);
			currentSignalStrength = signalStrength.getGsmSignalStrength();
		}
	};
	
	static public int getCurrentSignalStrength() {
		return currentSignalStrength;
	}

}
