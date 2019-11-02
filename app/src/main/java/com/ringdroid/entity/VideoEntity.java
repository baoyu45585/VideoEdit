package com.ringdroid.entity;

/**
 * @author shenjb@china
 * @since 2019-07-02
 */
public class VideoEntity {
    private String path;
    private int cropStart;//裁剪位置
    private int  cropEnd;//
    private boolean isHasCrop;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getCropStart() {
        return cropStart;
    }

    public void setCropStart(int cropStart) {
        this.cropStart = cropStart;
    }

    public int getCropEnd() {
        return cropEnd;
    }

    public void setCropEnd(int cropEnd) {
        this.cropEnd = cropEnd;
    }

    public boolean isHasCrop() {
        return isHasCrop;
    }

    public void setHasCrop(boolean hasCrop) {
        isHasCrop = hasCrop;
    }
}
