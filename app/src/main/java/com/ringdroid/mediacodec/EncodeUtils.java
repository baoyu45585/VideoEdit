package com.ringdroid.mediacodec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.ringdroid.entity.MediaEditor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author shenjb@china
 * @since 2019-07-30
 */
public class EncodeUtils {

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


    private MediaCodec videoEncode;
    private MediaCodec audioEncode;
    private Handler audioEncodeHandler;
    private HandlerThread audioEncodeThread;
    private MediaMuxer mediaMuxer;
    private MediaEditor editor;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean audioInit = false;
    private boolean videoInit = false;
    private boolean muxerStart = false;
    private Surface surface;

    public Surface getSurface() {
        return surface;
    }

    public EncodeUtils(){
        audioEncodeThread = new HandlerThread("AudioEncodeMediaCodec");
        audioEncodeThread.start();
        audioEncodeHandler = new Handler(audioEncodeThread.getLooper());
    }


    public void init(MediaEditor editor,final int cropWidth, final int cropHeight){
        this.editor=editor;
        String path ="/storage/emulated/0/DCIM/VideoEdit.mp4";
        File f = new File(path);
        if(f.exists()){
            f.delete();
        }
        try {
            mediaMuxer = new MediaMuxer(path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoEncode = MediaCodec.createEncoderByType(editor.getVideoMime());
            audioEncode = MediaCodec.createEncoderByType(editor.getAudioMime());
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(editor.getVideoMime(), cropWidth, cropHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, COMPOSE_VIDEO_BITRATE_8);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, editor.getVideoMediaFormat().getInteger(MediaFormat.KEY_FRAME_RATE));
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaFormat encodeFormat = MediaFormat.createAudioFormat(editor.getAudioMime(), 44100, 2);//参数对应-> mime type、采样率、声道数
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);//比特率
        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100 * 1024);

        videoEncode.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        surface = videoEncode.createInputSurface();
        if (surfaceListener!=null){
            surfaceListener.onSurface(surface);
        }
        videoEncode.start();
        audioEncode.start();

    }


    private void encodeInputBuffer(byte[] data,int size, MediaCodec mediaCodec, MediaCodec.BufferInfo info){
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
                mediaCodec.queueInputBuffer(inputIndex, 0, size, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }else{
                mediaCodec.queueInputBuffer(inputIndex, 0, size, info.presentationTimeUs, 0);
            }

        }
    }


    public void encodeAudio(final long startTime,final long endTime){
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
                                mediaMuxer.writeSampleData(editor.getAudiorack(), byteBuffer, bufferInfo);
                            }
                            audioEncode.releaseOutputBuffer(audioTrackIndex, false);
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

    int frameNum=0;
    private void encodeVideoOutputBuffer( long startTime,final long endTime,MediaCodec mediaCodec,MediaCodec.BufferInfo info,long presentationTimeUs){
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

    private VideoEncode.OnEncoderListener encoderListener;

    public void setEncoderListener(VideoEncode.OnEncoderListener encoderListener) {
        this.encoderListener = encoderListener;
    }

    public interface OnEncoderListener{
        void onStart();
        void onStop();
        void onProgress(int progress);
    }


    private SurfaceListener surfaceListener;
    public void setOnFrameAvailableListener(SurfaceListener surfaceListener) {
        this.surfaceListener = surfaceListener;
    }
    public interface SurfaceListener{
        void onSurface(Surface surface);
    }


}
