package com.jhkj.videoplayer.player.cover_bg_gen;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CoverBackgroundRender {

    private int program;
    private int textureId = -1;

    // 全屏顶点坐标
    private final float[] vertexData = {
        -1f,  1f, 0.0f, // 左上
        -1f, -1f, 0.0f, // 左下
         1f,  1f, 0.0f, // 右上
         1f, -1f, 0.0f  // 右下
    };

    // 纹理坐标 (注意：Android Bitmap 坐标系通常需要 Y 轴翻转)
    private final float[] textureData = {
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    };

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;

    public CoverBackgroundRender() {
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertexData);
        vertexBuffer.position(0);

        textureBuffer = ByteBuffer.allocateDirect(textureData.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().put(textureData);
        textureBuffer.position(0);
    }

    public void init() {
        String vertexShader = "attribute vec4 vPosition; attribute vec2 vTexCoord; " +
                "varying vec2 varTexCoord; void main() { gl_Position = vPosition; varTexCoord = vTexCoord; }";
        
        // 这里的 Shader 加入了简单的模糊算法
        String fragmentShader = "precision mediump float; uniform sampler2D uTexture; " +
                "varying vec2 varTexCoord; void main() { " +
                "vec4 color = texture2D(uTexture, varTexCoord) * 0.4; " +
                "color += texture2D(uTexture, varTexCoord + 0.02) * 0.15; " +
                "color += texture2D(uTexture, varTexCoord - 0.02) * 0.15; " +
                "color += texture2D(uTexture, varTexCoord + vec2(0.02, -0.02)) * 0.15; " +
                "color += texture2D(uTexture, varTexCoord + vec2(-0.02, 0.02)) * 0.15; " +
                "gl_FragColor = vec4(color.rgb, 1.0); }";

        program = loadProgram(vertexShader, fragmentShader);
    }

    /**
     * 更新背景纹理
     * @param bitmap 封面图
     */
    public void updateCover(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        
        // 关键：设置线性过滤，产生自然的拉伸模糊
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
    }

    public void draw() {
        if (textureId == -1) return;

        GLES20.glUseProgram(program);

        int posLoc = GLES20.glGetAttribLocation(program, "vPosition");
        int texLoc = GLES20.glGetAttribLocation(program, "vTexCoord");
        int samplerLoc = GLES20.glGetUniformLocation(program, "uTexture");

        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(texLoc);
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(samplerLoc, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    private int loadProgram(String vSource, String fSource) {
        int vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vShader, vSource);
        GLES20.glCompileShader(vShader);

        int fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fShader, fSource);
        GLES20.glCompileShader(fShader);

        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vShader);
        GLES20.glAttachShader(prog, fShader);
        GLES20.glLinkProgram(prog);
        return prog;
    }
}