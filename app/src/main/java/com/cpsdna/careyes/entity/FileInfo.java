package com.cpsdna.careyes.entity;

import java.io.File;

public class FileInfo
{
    
    public String fileName;
    public String fileCreateTime;
    public String memo;
    
    public String bussinessType; //1=瞄一眼;2=用户主动上传(精彩相册);3=车机主动上报事件
    public String eventType; //0=没有事件(bussinessType=1或2);1=碰撞(bussinessType=3);2=熄火(bussinessType=3)
    public String deviceEventId; //可选：车机的事件流水id(bussinessType=3)
    
    public String taskId; //可选：平台任务ID
    public boolean isLocation = false;
    public double posLng; //可选：定位到的经纬度
    public double posLat; //可选：定位到的经纬度
    public String posAddr; //可选：定位到的位置
    
    public File path;
    
}
