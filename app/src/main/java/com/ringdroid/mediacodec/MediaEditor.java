package com.ringdroid.mediacodec;

import android.media.MediaExtractor;
import android.media.MediaFormat;


/**
 * @author shenjb@china
 * @since 2019-06-11
 */
public class MediaEditor {

    private boolean isUseVideoTime;//是否使用视频的时间为总时间
    private MediaExtractor videoExtractor;
    private MediaExtractor audioExtractor;
    private long duration;//最长的时间当总时间
    private boolean isHasAudio;//是否有音频
    private long videoDuration;
    private long audioDuration;
    private long videoCurrentPosition;
    private long audioCurrentPosition;
    private int cropStartTime;//裁剪
    private int cropEndTime;
    private String videoMime;
    private String audioMime;
    private MediaFormat videoMediaFormat;
    private MediaFormat audioMediaFormat;
    private int width;
    private int height;
    private int audioEncoding;
    private int rateInHz;//频率
    private int channelCount ;//不能直接获取KEY_CHANNEL_MASK，所以只能获取声道数量然后再做处理
    private int channel;//通道
    private int minBufferSize;
    private int maxInputSize;
    private int audioInputBufferSize ;
    private String path;//路径
    private int videoTrack;//视频轨道
    private int audiorack;//音轨
    private int degrees;//旋转角度
    private boolean isMediaEnd;
    public boolean isHasAudio() {
        return isHasAudio;
    }

    public void setHasAudio(boolean hasAudio) {
        isHasAudio = hasAudio;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public MediaExtractor getVideoExtractor() {
        return videoExtractor;
    }

    public void setVideoExtractor(MediaExtractor videoExtractor) {
        this.videoExtractor = videoExtractor;
    }

    public MediaExtractor getAudioExtractor() {
        return audioExtractor;
    }

    public void setAudioExtractor(MediaExtractor audioExtractor) {
        this.audioExtractor = audioExtractor;
    }

    public long getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(long videoDuration) {
        this.videoDuration = videoDuration;
    }

    public long getAudioDuration() {
        return audioDuration;
    }

    public void setAudioDuration(long audioDuration) {
        this.audioDuration = audioDuration;
    }

    public long getVideoCurrentPosition() {
        return videoCurrentPosition;
    }

    public void setVideoCurrentPosition(long videoCurrentPosition) {
        this.videoCurrentPosition = videoCurrentPosition;
    }

    public long getAudioCurrentPosition() {
        return audioCurrentPosition;
    }

    public void setAudioCurrentPosition(long audioCurrentPosition) {
        this.audioCurrentPosition = audioCurrentPosition;
    }

    public int getCropStartTime() {
        return cropStartTime;
    }

    public void setCropStartTime(int cropStartTime) {
        this.cropStartTime = cropStartTime;
    }

    public int getCropEndTime() {
        return cropEndTime;
    }

    public void setCropEndTime(int cropEndTime) {
        this.cropEndTime = cropEndTime;
    }

    public String getVideoMime() {
        return videoMime;
    }

    public void setVideoMime(String videoMime) {
        this.videoMime = videoMime;
    }

    public String getAudioMime() {
        return audioMime;
    }

    public void setAudioMime(String audioMime) {
        this.audioMime = audioMime;
    }

    public MediaFormat getVideoMediaFormat() {
        return videoMediaFormat;
    }

    public void setVideoMediaFormat(MediaFormat videoMediaFormat) {
        this.videoMediaFormat = videoMediaFormat;
    }

    public MediaFormat getAudioMediaFormat() {
        return audioMediaFormat;
    }

    public void setAudioMediaFormat(MediaFormat audioMediaFormat) {
        this.audioMediaFormat = audioMediaFormat;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getAudioEncoding() {
        return audioEncoding;
    }

    public void setAudioEncoding(int audioEncoding) {
        this.audioEncoding = audioEncoding;
    }

    public int getRateInHz() {
        return rateInHz;
    }

    public void setRateInHz(int rateInHz) {
        this.rateInHz = rateInHz;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public int getMinBufferSize() {
        return minBufferSize;
    }

    public void setMinBufferSize(int minBufferSize) {
        this.minBufferSize = minBufferSize;
    }

    public int getMaxInputSize() {
        return maxInputSize;
    }

    public void setMaxInputSize(int maxInputSize) {
        this.maxInputSize = maxInputSize;
    }

    public int getAudioInputBufferSize() {
        return audioInputBufferSize;
    }

    public void setAudioInputBufferSize(int audioInputBufferSize) {
        this.audioInputBufferSize = audioInputBufferSize;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getVideoTrack() {
        return videoTrack;
    }

    public void setVideoTrack(int videoTrack) {
        this.videoTrack = videoTrack;
    }

    public int getAudiorack() {
        return audiorack;
    }

    public void setAudiorack(int audiorack) {
        this.audiorack = audiorack;
    }

    public int getDegrees() {
        return degrees;
    }

    public boolean isUseVideoTime() {
        return isUseVideoTime;
    }

    public void setUseVideoTime(boolean useVideoTime) {
        isUseVideoTime = useVideoTime;
    }

    public void setDegrees(int degrees) {
        this.degrees = degrees;
    }

    public boolean isMediaEnd() {
        return isMediaEnd;
    }

    public void setMediaEnd(boolean mediaEnd) {
        isMediaEnd = mediaEnd;
    }
}
