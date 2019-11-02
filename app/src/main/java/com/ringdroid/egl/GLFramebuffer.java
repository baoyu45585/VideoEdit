package com.ringdroid.egl;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by 海米 on 2017/8/16.
 */

public class GLFramebuffer {

    private int width,height;

    private final float[] vertexData = {
            1f, -1f, 0f,
            -1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f
    };

    private final float[] textureVertexData = {
            1f, 0f,
            0f, 0f,
            1f, 1f,
            0f, 1f,
    };
    private FloatBuffer vertexBuffer;

    private FloatBuffer textureVertexBuffer;

    private int programId = -1;
    private int aPositionHandle;
    private int uTextureSamplerHandle;
    private int aTextureCoordHandle;
    private int uSTMMatrixHandle;

    private float[] mSTMatrix = new float[16];

    private int[] textures;


    private int[] vertexBuffers;

    private SurfaceTexture surfaceTexture;


    private String fragmentShader = "#extension GL_OES_EGL_image_external : require\n" +
            "varying highp vec2 vTexCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform highp mat4 uSTMatrix;\n" +
            "void main() {\n" +
            "    highp vec2 tx_transformed = (uSTMatrix * vec4(vTexCoord, 0, 1.0)).xy;\n" +
            "    highp vec4 centralColor = texture2D(sTexture, tx_transformed);\n" +
            "    gl_FragColor = centralColor;\n" +
            "}";
    private  String vertexShader = "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    vTexCoord = aTexCoord;\n" +
            "    gl_Position = aPosition;\n" +
            "}";
    public GLFramebuffer(){

        vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData);
        vertexBuffer.position(0);

        textureVertexBuffer = ByteBuffer.allocateDirect(textureVertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureVertexData);
        textureVertexBuffer.position(0);
    }
    public void initFramebuffer(int width,int height){
        this.width = width;
        this.height = height;
        programId = ShaderUtils.createProgram(vertexShader, fragmentShader);
        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition");
        uSTMMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix");
        uTextureSamplerHandle = GLES20.glGetUniformLocation(programId, "sTexture");
        aTextureCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord");

        vertexBuffers = new int[2];
        GLES20.glGenBuffers(2,vertexBuffers,0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length*4, vertexBuffer,GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffers[1]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8*4, textureVertexBuffer,GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);



        textures = new int[1];

        GLES20.glGenTextures(1, textures, 0);



        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        surfaceTexture = new SurfaceTexture(textures[0]);
    }

    private Rect rect = new Rect();
    public SurfaceTexture getSurfaceTexture(int width,int height) {

        int screenWidth = this.width;
        int screenHeight = this.height;

        int left,top,viewWidth,viewHeight;
        float sh = screenWidth*1.0f/screenHeight;
        float vh = width *1.0f/ height;
        if(sh < vh){
            left = 0;
            viewWidth = screenWidth;
            viewHeight = (int)(height *1.0f/ width *viewWidth);
            top = (screenHeight - viewHeight)/2;
        }else{
            top = 0;
            viewHeight = screenHeight;
            viewWidth = (int)(width *1.0f/ height *viewHeight);
            left = (screenWidth - viewWidth)/2;
        }
        rect.left = left;
        rect.top = top;
        rect.right = viewWidth;
        rect.bottom = viewHeight;


        return surfaceTexture;
    }
    int mFrameCount=0;
    public void drawFrameBuffer(){

        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(mSTMatrix);

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glViewport(rect.left, rect.top, rect.right, rect.bottom);

        GLES20.glUseProgram(programId);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffers[0]);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                12, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffers[1]);
        GLES20.glEnableVertexAttribArray(aTextureCoordHandle);
        GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glUniform1i(uTextureSamplerHandle,0);
        GLES20.glUniformMatrix4fv(uSTMMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);


        mFrameCount++;
        String path ="/storage/emulated/0/DCIM/bb/";
        File file=new File(path);
        if (!file.exists()){
            file.mkdirs();
        }
        if (mFrameCount>20&&mFrameCount<25){
            file=new File(path+"/bb_"+mFrameCount+".png");
            try {
                file.createNewFile();
                saveFrame(file);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }


    }

//    public void drawFrame(){
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
//    }




    public void saveFrame(File file) throws IOException {
        String filename = file.toString();
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        buf.rewind();
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(filename));
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            bmp.recycle();
        } finally {
            if (bos != null) bos.close();
        }

    }


}
