package com.jhkj.videoplayer.player.cover_bg_gen;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    public CoverBackgroundRender3 bgRender;
    private final GLSurfaceView glSurfaceView;
    private Bitmap pendingBitmap;
    private Context context;
    private int screeWidth;
    private int screenHeight;

    public MyGLRenderer(GLSurfaceView view, Context context) {
        this.glSurfaceView = view;
        this.context = context;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // ✅ 只有在这里，glCreateShader 才会生效
        bgRender = new CoverBackgroundRender3();
        bgRender.init(context);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        screeWidth = width;
        screenHeight = height;
        bgRender.setupFBO(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 检查是否有新的 Bitmap 需要转换成纹理
        synchronized (this) {
            if (pendingBitmap != null) {
                bgRender.updateCover(pendingBitmap);
                pendingBitmap = null;
            }
        }

        if (bgRender != null) {
            bgRender.draw(screeWidth,screenHeight);
        }
    }

    /**
     * 供 MainActivity 调用，完全线程安全
     */
    public void updateBitmap(final Bitmap bitmap) {
        // 使用同步块防止多线程冲突
        synchronized (this) {
            this.pendingBitmap = bitmap;
        }
        // 通知 GL 线程该刷新了
        glSurfaceView.requestRender();
    }
}