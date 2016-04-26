package com.cpsdna.careyes.entity;

import java.io.Serializable;

public class CommandMedia implements Serializable
{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    public int id; //服务侧指令Id
    public byte type; //1 表示拍照2 表示录像
    public short command; //0 表示停止拍摄；0xFFFF表示录像；其它表示拍照张数
    public short totalTime; //秒，0表示按最小间隔拍照或一直录像
    public byte saveSign; //1：保存； 0：实时上传
    public byte resolution; //0x01:320*240； 0x02:640*480； 0x03:800*600； 0x04:1024*768; 0x05:176*144;[Qcif]; 0x06:352*288;[Cif]; 0x07:704*288;[HALF D1]; 0x08:704*576;[D1];
    public byte quality; //1-10，1 代表质量损失最小，10表示压缩比最大
    public byte light; //亮度0-255
    public byte contrast; //对比度0-127
    public byte saturation; //饱和度0-127
    public byte chroma; //色度0-255
    public String taskId; //平台任务ID
    
    public boolean isLocation = false;
    public double posLng; //定位到的经纬度
    public double posLat; //定位到的经纬度
    public String posAddr; //定位到的位置
    
}
