package com.cpsdna.careyes.entity;

import java.io.File;
import java.io.Serializable;

public class Collision implements Serializable
{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    public String eventId;
    public boolean isLocation = false;
    public double posLng; //可选：定位到的经纬度
    public double posLat; //可选：定位到的经纬度
    public String posAddr; //可选：定位到的位置
    
    public boolean confirm = false;
    
    public File pic1;
    public String pic1CreateTime;
    public File pic2;
    public String pic2CreateTime;
    public File pic3;
    public String pic3CreateTime;
    
    public File videoPath;
    public String videoCreateTime;
    
}
