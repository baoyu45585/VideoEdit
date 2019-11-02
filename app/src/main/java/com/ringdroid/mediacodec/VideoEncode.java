package com.ringdroid.mediacodec;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import com.ringdroid.egl.EGLUtils;
import com.ringdroid.egl.GLFramebuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by 海米 on 2018/7/4.
 */

public class VideoEncode {
    private static final String VIDEO = "video/";
    private MediaExtractor videoExtractor;
    private MediaCodec videoDecoder;
    private MediaCodec videoEncode;


    private static final String AUDIO = "audio/";
    private MediaExtractor audioExtractor;
    private MediaCodec audioDecoder;
    private MediaCodec audioEncode;

    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;

    private int audioTrack;
    private int videoTrack;
    private Handler videoHandler;
    private HandlerThread videoThread;


    private Handler eglHandler;
    private HandlerThread eglThread;


    private Handler audioDecoderHandler;
    private HandlerThread audioDecoderThread;
    private Handler audioEncodeHandler;
    private HandlerThread audioEncodeThread;

    /**
     * 编码合成 码率
     */
    int COMPOSE_VIDEO_BITRATE_30 = 30 * 1024 * 1024;
    int COMPOSE_VIDEO_BITRATE_20 = 20 * 1024 * 1024;
    int COMPOSE_VIDEO_BITRATE_10 = 10 * 1024 * 1024;
    int COMPOSE_VIDEO_BITRATE_8 = 8 * 1024 * 1024;
    int COMPOSE_VIDEO_BITRATE_7 = 7 * 1024 * 1024;
    int COMPOSE_VIDEO_BITRATE_6 = 6 * 1024 * 1024;
    int COMPOSE_VIDEO_BITRATE_4 = 4 * 1024 * 1024;

    private long startTime = 0;
    private long endTime = 0;

    private long duration;


    private EGLUtils mEglUtils;
    private GLFramebuffer mFramebuffer;

    public VideoEncode(){
        videoThread = new HandlerThread("VideoMediaCodec");
        videoThread.start();
        videoHandler = new Handler(videoThread.getLooper());

        eglThread = new HandlerThread("OpenGL");
        eglThread.start();
        eglHandler = new Handler(eglThread.getLooper());


        audioDecoderThread = new HandlerThread("AudioDecoderMediaCodec");
        audioDecoderThread.start();
        audioDecoderHandler = new Handler(audioDecoderThread.getLooper());

        audioEncodeThread = new HandlerThread("AudioEncodeMediaCodec");
        audioEncodeThread.start();
        audioEncodeHandler = new Handler(audioEncodeThread.getLooper());
    }


    public void init(String videoPath, final long startTime, long endTime,
                     final int cropWidth, final int cropHeight, final float[] textureVertexData){
        this.startTime = startTime;
        this.endTime = endTime;
        videoInit = false;
        audioInit = false;
        String path ="/storage/emulated/0/DCIM/VideoEdit.mp4";

        File f = new File(path);
        if(f.exists()){
            f.delete();
        }

        videoExtractor = new MediaExtractor();
        audioExtractor = new MediaExtractor();
        try {
            videoExtractor.setDataSource(videoPath);

            audioExtractor.setDataSource(videoPath);
            mediaMuxer = new MediaMuxer(path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            muxerStart = false;

            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(AUDIO)) {
                    audioExtractor.selectTrack(i);
                    audioTrack = i;
                    if(startTime != 0){
                        audioExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    }
                    audioDecoder = MediaCodec.createDecoderByType(mime);
                    audioDecoder.configure(format, null, null, 0 /* Decoder */);

                    int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
                    int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
                    int bitRate = format.containsKey(MediaFormat.KEY_BIT_RATE) ?
                            format.getInteger(MediaFormat.KEY_BIT_RATE) : 128000;

                    audioEncode = MediaCodec.createEncoderByType(mime);
                    MediaFormat encodeFormat = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);//参数对应-> mime type、采样率、声道数
                    encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);//比特率
                    encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                    encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100 * 1024);
                    audioEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    if(endTime == 0){
                        this.endTime = format.getLong(MediaFormat.KEY_DURATION);
                    }
                    audioDecoder.start();
                    audioEncode.start();
                    break;
                }
            }
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                final MediaFormat format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(VIDEO)) {
                    videoExtractor.selectTrack(i);
                    videoTrack = i;
                    if(startTime != 0){
                        videoExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    }
                    duration = format.getLong(MediaFormat.KEY_DURATION);


                    videoEncode = MediaCodec.createEncoderByType(mime);
                    MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, cropWidth, cropHeight);
                    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, COMPOSE_VIDEO_BITRATE_8);
                    int z=format.getInteger(MediaFormat.KEY_FRAME_RATE);
                    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, z);
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                    mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                    //设置编码模式
                    MediaCodecInfo.CodecCapabilities capabilities =
                            videoEncode.getCodecInfo().getCapabilitiesForType("video/avc");
                    int support_level = MediaCodecInfo.CodecProfileLevel.AVCLevel1;
                    for (MediaCodecInfo.CodecProfileLevel profileLevel : capabilities.profileLevels) {
                        if (profileLevel.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline) {
                            if (profileLevel.level > support_level) {
                                support_level = profileLevel.level;
                            }
                        }
                    }
                    mediaFormat.setInteger(MediaFormat.KEY_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
                    mediaFormat.setInteger("level", support_level);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        MediaCodecInfo.EncoderCapabilities capabilities1=  capabilities.getEncoderCapabilities();
                        boolean  flag = capabilities1.isBitrateModeSupported( MediaCodecInfo.EncoderCapabilities
                                .BITRATE_MODE_VBR);
                        if (flag){
                            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities
                                    .BITRATE_MODE_VBR);
                        }
                    }

                    videoEncode.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

                    videoDecoder = MediaCodec.createDecoderByType(mime);
                    eglHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Surface surface = videoEncode.createInputSurface();
                            videoEncode.start();
                            isDraw = false;

                            mEglUtils = new EGLUtils();
                            mEglUtils.initEGL(surface);
                            mFramebuffer = new GLFramebuffer();
                            mFramebuffer.initFramebuffer(cropWidth,cropHeight);
                            SurfaceTexture surfaceTexture = mFramebuffer.getSurfaceTexture(cropWidth,cropHeight);
                            surfaceTexture.setDefaultBufferSize(cropWidth,cropHeight);
                            surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                                @Override
                                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                                    mFramebuffer.drawFrameBuffer();
                                    mEglUtils.swap();
                                    synchronized (decoderObject){
                                        isDraw = true;
                                        decoderObject.notifyAll();
                                    }
                                }
                            });
                            videoDecoder.configure(format, new Surface(surfaceTexture), null, 0 /* Decoder */);
                            videoDecoder.start();
                            if(encoderListener != null){
                                encoderListener.onStart();
                            }
                            start();
                        }
                    }) ;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private long presentationTimeUs;
    private final Object decoderObject = new Object();
    private final Object audioObject = new Object();
    private final Object videoObject = new Object();

    private boolean isDraw = false;

    private boolean muxerStart = false;
    private boolean videoInit = false;
    private boolean audioInit = false;


    private void start(){
        muxerStart = false;
        videoInit = false;
        audioInit = false;
        videoHandler.post(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
                    if(!audioInit){
                        synchronized (videoObject){
                            try {
                                videoObject.wait(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        continue;
                    }
                    int run = extractorVideoInputBuffer(videoExtractor,videoDecoder);
                    if(run == 1){
                        int outIndex = videoDecoder.dequeueOutputBuffer(info, 0);
                        presentationTimeUs = info.presentationTimeUs;
                        if(outIndex >= 0){
                            Log.e("===========","=====presentationTimeUs==="+presentationTimeUs);
                            videoDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);
                            boolean s = false;
                            synchronized (decoderObject){
                                try {
                                    decoderObject.wait(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if(isDraw){
                                    s = true;
                                }
                            }
                            if(s){
                                encodeVideoOutputBuffer(videoEncode,info,presentationTimeUs);
                            }
                            if(presentationTimeUs > endTime){
                                videoEncode.signalEndOfInputStream();
                                break;
                            }
                        }

                    }else if(run == -1){
                        videoEncode.signalEndOfInputStream();
                        break;
                    }else{
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }


                }

                eglHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mEglUtils.release();
                    }
                });
                videoDecoder.stop();
                videoDecoder.release();
                videoDecoder = null;
                videoExtractor.release();
                videoExtractor = null;
                videoEncode.stop();
                videoEncode.release();
                videoEncode = null;
                muxerRelease();
            }
        });
        audioDecoderHandler.post(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
                    if(!muxerStart){
                        synchronized (audioObject){
                            try {
                                audioObject.wait(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        continue;
                    }
                    extractorInputBuffer(audioExtractor,audioDecoder);
                    int outIndex = audioDecoder.dequeueOutputBuffer(info, 10000);
                    if(outIndex >= 0){
                        ByteBuffer data;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                            data = audioDecoder.getOutputBuffer(outIndex);
                        }else{
                            data = audioDecoder.getOutputBuffers()[outIndex];
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0;
                        }
                        if (info.size != 0) {
                            if(info.presentationTimeUs >= startTime){
                                data.position(info.offset);
                                data.limit(info.offset + info.size);
                                encodeInputBuffer(data,audioEncode,info);
                            }
                            audioDecoder.releaseOutputBuffer(outIndex, false);
                            if(info.presentationTimeUs > endTime){
                                break;
                            }
                        }
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
                audioDecoder.stop();
                audioDecoder.release();
                audioExtractor.release();
                audioExtractor = null;
                audioDecoder = null;
            }
        });
        audioEncodeHandler.post(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (true){
                    int inputIndex = audioEncode.dequeueOutputBuffer(bufferInfo, 1000);
                    if(inputIndex >= 0){
                        ByteBuffer byteBuffer;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                            byteBuffer = audioEncode.getOutputBuffer(inputIndex);
                        }else{
                            byteBuffer = audioEncode.getOutputBuffers()[inputIndex];
                        }
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0;
                        }
                        if (bufferInfo.size != 0) {
                            long presentationTimeUs = bufferInfo.presentationTimeUs;
                            if(presentationTimeUs >= startTime && presentationTimeUs <= endTime){
                                bufferInfo.presentationTimeUs = presentationTimeUs - startTime;
                                byteBuffer.position(bufferInfo.offset);
                                byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
                                mediaMuxer.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo);
                            }
                            audioEncode.releaseOutputBuffer(inputIndex, false);
                            if(presentationTimeUs > endTime){
                                break;
                            }
                        }
                    }else if(inputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        if(videoTrackIndex == -1){
                            MediaFormat mediaFormat = audioEncode.getOutputFormat();
                            audioTrackIndex = mediaMuxer.addTrack(mediaFormat);
                            audioInit = true;
                        }
                    }
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
                audioEncode.stop();
                audioEncode.release();
                audioEncode = null;
                muxerRelease();
            }
        });
    }
    private synchronized void initMuxer(){
        muxerStart = true;
        mediaMuxer.start();
    }
    private synchronized void muxerRelease(){
        if(audioEncode == null && videoEncode == null && mediaMuxer != null){
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
            if(encoderListener != null){
                encoderListener.onStop();
            }
        }

    }


    private int extractorVideoInputBuffer(MediaExtractor mediaExtractor,MediaCodec mediaCodec){
        int inputIndex = mediaCodec.dequeueInputBuffer(0);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                inputBuffer = mediaCodec.getInputBuffer(inputIndex);
            }else{
                inputBuffer = mediaCodec.getInputBuffers()[inputIndex];
            }
            long sampleTime = mediaExtractor.getSampleTime();
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            if (mediaExtractor.advance()) {
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                return 1;
            } else {
                if(sampleSize > 0){
                    mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                    return 1;
                }else{
                    return -1;
                }

            }
        }
        return 0;
    }
    int frameNum=0;
    private void encodeVideoOutputBuffer(MediaCodec mediaCodec,MediaCodec.BufferInfo info,long presentationTimeUs){
        if (presentationTimeUs<startTime){
           return;
        }
        int encoderStatus = mediaCodec.dequeueOutputBuffer(info, 0);
        while (encoderStatus==-1){
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            encoderStatus = mediaCodec.dequeueOutputBuffer(info, 0);
        }

        if(encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            if(videoTrackIndex == -1){
                MediaFormat mediaFormat = mediaCodec.getOutputFormat();
                videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
                videoInit = true;
                initMuxer();
                encoderStatus = mediaCodec.dequeueOutputBuffer(info, 0);
                startTime= presentationTimeUs;
                frameNum=0;
            }
        }

        Log.e("==============","======encoderStatus===="+encoderStatus);
        if (encoderStatus >= 0) {
            ByteBuffer encodedData;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                encodedData = mediaCodec.getOutputBuffer(encoderStatus);
            }else{
                encodedData = mediaCodec.getOutputBuffers()[encoderStatus];
            }
            Log.e("==============","======info.size===="+info.size);
//            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {//sps和pps
//                info.size = 0;
//            }
            Log.e("==============","======info.size===="+info.size);
            if(presentationTimeUs >= startTime && presentationTimeUs<= endTime){
                frameNum++;
                Log.e("=========","====presentationTimeUs===="+presentationTimeUs
                        +"====frameNum=="+frameNum
                        +"====startTime=="+startTime);
                encodedData.position(info.offset);
                encodedData.limit(info.offset + info.size);
                info.presentationTimeUs = presentationTimeUs - startTime;
                mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info);
            }
            if(encoderListener != null){
                encoderListener.onProgress((int) ((presentationTimeUs-startTime)*100.0f/(endTime-startTime)));
            }
            mediaCodec.releaseOutputBuffer(encoderStatus, false);
        }else if(encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            if(videoTrackIndex == -1){
                MediaFormat mediaFormat = mediaCodec.getOutputFormat();
                videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
                videoInit = true;
                initMuxer();
            }
        }
    }

    private void extractorInputBuffer(MediaExtractor mediaExtractor,MediaCodec mediaCodec){
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                inputBuffer = mediaCodec.getInputBuffer(inputIndex);
            }else{
                inputBuffer = mediaCodec.getInputBuffers()[inputIndex];
            }
            long sampleTime = mediaExtractor.getSampleTime();
//            if(sampleTime >= endTime){
//                return;
//            }
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            if (mediaExtractor.advance()) {
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
            } else {
                if(sampleSize > 0){
                    mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }else{
                    mediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }
        }
    }



    private void encodeInputBuffer(ByteBuffer data,MediaCodec mediaCodec,MediaCodec.BufferInfo info){
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                inputBuffer = mediaCodec.getInputBuffer(inputIndex);
            }else{
                inputBuffer = mediaCodec.getInputBuffers()[inputIndex];
            }
            inputBuffer.clear();
            inputBuffer.put(data);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mediaCodec.queueInputBuffer(inputIndex, 0, data.limit(), info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }else{
                mediaCodec.queueInputBuffer(inputIndex, 0, data.limit(), info.presentationTimeUs, 0);
            }

        }
    }

    public long getContentPosition(){
        return presentationTimeUs/1000;
    }
    public long getDuration() {
        return duration/1000;
    }
    private OnEncoderListener encoderListener;

    public void setEncoderListener(OnEncoderListener encoderListener) {
        this.encoderListener = encoderListener;
    }

    public interface OnEncoderListener{
        void onStart();
        void onStop();
        void onProgress(int progress);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private byte[] getDataFromImage(Image image) {
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height + 1;
                    outputStride = 2;
                    break;
                case 2:
                    channelOffset = width * height;
                    outputStride = 2;
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }
}
