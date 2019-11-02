package com.ringdroid.mediacodec;

import android.graphics.SurfaceTexture;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.ringdroid.egl.EGLUtils;
import com.ringdroid.egl.GLFramebuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author shenjb@china
 * @since 2019-07-30
 */
public class DecodeUtils {

    private Handler videoHandler,audioHandler;
    private HandlerThread videoThread,audioThread;
    private long startTime = 0;
    private long endTime = 0;
    private boolean videoInit = false;
    private boolean audioInit = false;
    private MediaEditor mediaEditor;
    private static String KEY_VIDEO = "video/";
    private static String KEY_AUDIO = "audio/";
    private static long AV_TIME_BASE = 1000;
    private static long TIMEOUT_US = 0;
    private Surface surface;
    private static final String TAG = "DecodeUtils";
    private final Object openglObject = new Object();
    private boolean isDraw = false;

    public DecodeUtils(){

    }

    public void init(String videoPath, final long startTime, long endTime, final int cropWidth, final int cropHeight){
        this.startTime = startTime;
        this.endTime = endTime;
        videoInit = false;
        audioInit = false;
        String path ="/storage/emulated/0/DCIM/VideoEdit.mp4";

        File f = new File(path);
        if(f.exists()){
            f.delete();
        }
        mediaEditor = new MediaEditor();
        mediaEditor.setPath(videoPath);
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        mediaEditor.setVideoExtractor(videoExtractor);
        mediaEditor.setAudioExtractor(audioExtractor);
        try {
            videoExtractor.setDataSource(videoPath);
            audioExtractor.setDataSource(videoPath);
        } catch (IOException e) {
            e.printStackTrace();
            videoExtractor.release();
            audioExtractor.release();
            mediaEditor=null;
            return;
        }
        for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
            MediaFormat format = audioExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(KEY_VIDEO)) {//匹配视频对应的轨道
                videoExtractor.selectTrack(i);//选择视频对应的轨道
                mediaEditor.setVideoTrack(i);
                if(startTime != 0){
                    audioExtractor.seekTo(startTime*AV_TIME_BASE,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                }
                long duration = format.getLong(MediaFormat.KEY_DURATION);
                int width = format.getInteger(MediaFormat.KEY_WIDTH);
                int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                int degrees = 0;
                try {
                    degrees = format.getInteger(MediaFormat.KEY_ROTATION);//有些视频没这个参数会空指针
                } catch (Exception e) {
                    e.printStackTrace();
                }
                int num = width;
                if (degrees == 90 || degrees == 270) {
                    width = height;
                    height = num;
                }
                mediaEditor.setDegrees(degrees);
                mediaEditor.setVideoMime(mime);
                mediaEditor.setVideoMediaFormat(format);
                mediaEditor.setVideoDuration(duration);
                mediaEditor.setWidth(width);
                mediaEditor.setHeight(height);
            } else if (mime.startsWith(KEY_AUDIO)) {
                audioExtractor.selectTrack(i);
                mediaEditor.setAudiorack(i);
                if(startTime != 0){
                    audioExtractor.seekTo(startTime*AV_TIME_BASE,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                }
                long duration = format.getLong(MediaFormat.KEY_DURATION);
                int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
                int rateInHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);//不能直接获取KEY_CHANNEL_MASK，所以只能获取声道数量然后再做处理
                int channel = channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
                int minBufferSize = AudioTrack.getMinBufferSize(rateInHz, channel, audioEncoding);
                int maxInputSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                int audioInputBufferSize = minBufferSize > 0 ? minBufferSize * 4 : maxInputSize;
                mediaEditor.setHasAudio(true);
                mediaEditor.setAudioMime(mime);
                mediaEditor.setAudioMediaFormat(format);
                mediaEditor.setAudioDuration(duration);
                mediaEditor.setAudioEncoding(audioEncoding);
                mediaEditor.setRateInHz(rateInHz);
                mediaEditor.setChannelCount(channelCount);
                mediaEditor.setChannel(channel);
                mediaEditor.setMinBufferSize(minBufferSize);
                mediaEditor.setMaxInputSize(maxInputSize);
                mediaEditor.setAudioInputBufferSize(audioInputBufferSize);
            }
        }

        if (videoThread==null){
            videoThread = new HandlerThread("VideoMediaCodec");
            videoThread.start();
        }

        if (audioThread==null){
            audioThread = new HandlerThread("AudioMediaCodec");
            audioThread.start();
        }

        if (videoHandler==null){
            videoHandler = new Handler(videoThread.getLooper());
        }
        if (audioHandler==null){
            audioHandler = new Handler(audioThread.getLooper());
        }
    }


    public void onFrameAvailable(){
        synchronized (openglObject){
            isDraw = true;
            openglObject.notifyAll();
        }
    }


    private void start( final Surface surface,Object object){
        this.surface=surface;
        videoHandler.post(new Runnable() {
            MediaExtractor videoExtractor;
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                videoExtractor=mediaEditor.getVideoExtractor();
                if (videoSizeCallBack != null) {
                    videoSizeCallBack.onVideoSizeChanged(mediaEditor.getWidth(), mediaEditor.getHeight());
                }
                MediaCodec mediaCodec = null;
                try {
                    mediaCodec = MediaCodec.createDecoderByType(mediaEditor.getVideoMime());
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
                mediaCodec.configure(mediaEditor.getVideoMediaFormat(),surface, null, 0 /* Decoder */);
                mediaCodec.start();
                boolean readEnd=false;
                while (true) {
                    if (!readEnd){
                        readEnd = putBufferToCoder(mediaEditor.getVideoExtractor(),mediaCodec);
                    }
                    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, TIMEOUT_US);
                    switch (outputBufferIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.v(TAG, "format changed");
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.v(TAG, "视频解码当前帧超时");
                            try {
                                // wait 10ms
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                            }
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.v(TAG, "output buffers changed");
                            break;
                        default:
                            mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                            boolean s = false;
                            synchronized (openglObject){//确保先渲染后编码
                                try {
                                    openglObject.wait(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if(isDraw){//必须第一次渲染完成后才编码
                                    s = true;
                                }
                            }
                            if(s){
                                if (videoEncodeListener!=null){
                                    videoEncodeListener.onVideoEncode(info,info.presentationTimeUs);
                                }
                            }
                            break;
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
                mediaCodec.stop();
                mediaCodec.release();
                videoExtractor.release();
                videoExtractor = null;

            }
        });
        audioHandler.post(new Runnable() {
            MediaExtractor mediaExtractor;
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                mediaEditor.getVideoExtractor();
                MediaCodec mediaCodec = null;
                try {
                    mediaCodec = MediaCodec.createDecoderByType(mediaEditor.getAudioMime());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mediaCodec.configure(mediaEditor.getAudioMediaFormat(), null, null, 0);
                boolean readEnd = false;
                while (true) {
                    if (!readEnd) {
                        readEnd = putBufferToCoder(mediaExtractor, mediaCodec);
                    }
                    int outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
                    if(outIndex >= 0){
                        ByteBuffer data;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                            data = mediaCodec.getOutputBuffer(outIndex);
                        }else{
                            data = mediaCodec.getOutputBuffers()[outIndex];
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0;
                        }
                        if (info.size != 0) {
                            if(info.presentationTimeUs >= startTime){
                                data.position(info.offset);
                                data.limit(info.offset + info.size);
                                if (audioEncodeListener!=null){
                                    audioEncodeListener.onAudioEncode(data,info);
                                }
                                //编码
                            }
                            mediaCodec.releaseOutputBuffer(outIndex, false);
                            if(info.presentationTimeUs > endTime){
                                break;
                            }
                        }
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
                mediaCodec.stop();
                mediaCodec.release();
                mediaExtractor.release();
                mediaExtractor = null;

            }
        });
    }

    /**
     * 将缓冲区传递至解码器
     *
     * @param extractor
     * @param decoder
     * @return 如果到了文件末尾，返回true;否则返回false
     */
    private boolean putBufferToCoder(MediaExtractor extractor, MediaCodec decoder) {
        boolean isMediaEnd = false;
        int inputBufferIndex =1;
        try {
            //华为手机开启分屏，关闭分屏，快速切换分屏会崩溃
            inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
        }catch (Exception e){}

        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                inputBuffer = decoder.getInputBuffer(inputBufferIndex);
            }else{
                inputBuffer = decoder.getInputBuffers()[inputBufferIndex];
            }
            inputBuffer.clear();

            int sampleSize = extractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                isMediaEnd = true;
            } else {
                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                extractor.advance();
            }
        }
        return isMediaEnd;
    }

    private  OnVideoSizeChangedListener videoSizeCallBack;
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener videoSizeCallBack) {
        this.videoSizeCallBack = videoSizeCallBack;
    }
    public interface OnVideoSizeChangedListener {
         void onVideoSizeChanged(int width, int height);
    }

    private OnVideoEncodeListener videoEncodeListener;
    public void setOnVideoEncodeListener(OnVideoEncodeListener videoEncodeListener) {
        this.videoEncodeListener = videoEncodeListener;
    }
    public interface OnVideoEncodeListener {
        void onVideoEncode(MediaCodec.BufferInfo info,long presentationTimeUs);
    }
    private OnAudioEncodeListener audioEncodeListener;
    public void setOnAudioEncodeListener(OnAudioEncodeListener videoEncodeListener) {
        this.audioEncodeListener = audioEncodeListener;
    }
    public interface OnAudioEncodeListener {
        void onAudioEncode( ByteBuffer data,MediaCodec.BufferInfo info);
    }


}
