package com.ringdroid.entity;

/**
 * @author shenjb@chinaduration
 * @since 2019-06-09
 */
public class CropEntity {
    private int cropStart;//裁剪位置
    private int  cropEnd;//

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
}
