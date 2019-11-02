package com.ringdroid.mediacodec;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.ringdroid.egl.EGLUtils;
import com.ringdroid.egl.GLFramebuffer;

/**
 * @author shenjb@china
 * @since 2019-09-26
 */
public class EGLRenderUtils {


    private Handler eglHandler;
    private HandlerThread eglThread;
    private EGLUtils mEglUtils;
    private GLFramebuffer mFramebuffer;
    private boolean isDraw = false;

    private final Object object = new Object();
    private SurfaceListener surfaceListener;
    public void setOnSurfacerListenerr(SurfaceListener surfaceListener) {
        this.surfaceListener = surfaceListener;
    }
    public interface SurfaceListener{
        void onSurface(Surface surface);
    }


    private OnFrameAvailableListener onFrameAvailableListener;
    public void setOnFrameAvailableListener(OnFrameAvailableListener onFrameAvailableListener) {
        this.onFrameAvailableListener = onFrameAvailableListener;
    }
    public interface OnFrameAvailableListener{
        void onFrameAvailable();
    }

    public EGLRenderUtils(){
        eglThread = new HandlerThread("OpenGL");
        eglThread.start();
        eglHandler = new Handler(eglThread.getLooper());
    }

    public void start(final Surface surface,final int cropWidth,final int cropHeight){

        eglHandler.post(new Runnable() {
            @Override
            public void run() {
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
                        if (onFrameAvailableListener!=null){
                            onFrameAvailableListener.onFrameAvailable();
                        }
                    }
                });

                if (surfaceListener!=null)surfaceListener.onSurface(new Surface(surfaceTexture));
            }
        }) ;
    }




}
