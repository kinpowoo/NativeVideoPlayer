package com.jhkj.videoplayer.player.cover_bg_gen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


class CoverBackgroundRender3 {
    private var preProcessProg = -1 // Pass 1: 去黑提亮
    private var blurProg = -1 // Pass 2: 高斯模糊

    private var textureId = -1
    private var fboId = -1
    private var fboTextureId = -1 // 存储预处理后图片的纹理
    private var uTexelOffsetLocation = -1

    private val vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer
    private val vertexData = floatArrayOf(-1f, 1f, -1f, -1f, 1f, 1f, 1f, -1f)

    // 纹理坐标这里使用默认，翻转逻辑在 Shader 顶点处理中完成
    private val textureData = floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f)

    private var currentFboWidth = 0
    private var currentFboHeight = 0
    private var resampleSize = 0

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertexData)
        vertexBuffer.position(0)
        textureBuffer = ByteBuffer.allocateDirect(textureData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(textureData)
        textureBuffer.position(0)
    }

    fun init(context: Context) {
        val vPass: String = readAssets(context, "vert_glsl/vertex_shader.glsl")
        preProcessProg = createProgram(vPass, readAssets(context,
            "frag_glsl/pass1_fragment.glsl"))
        blurProg = createProgram(vPass, readAssets(context,
            "frag_glsl/pass2.glsl"))
    }
    // --- 工具方法：读取资源 ---
    private fun readAssets(context: Context, fileName: String): String {
        val sb = StringBuilder()
        try {
            BufferedReader(InputStreamReader(context.getAssets().open(fileName))).use { reader ->
                var line: String?
                while ((reader.readLine().also { line = it }) != null) sb.append(line).append("\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sb.toString()
    }

    private fun createProgram(vSource: String?, fSource: String?): Int {
        val vs: Int = loadShader(GLES20.GL_VERTEX_SHADER, vSource)
        val fs: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun loadShader(type: Int, source: String?): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    /**
     * ✅ 这里的 FBO 使用真实宽高，保持高清大轮廓
     */
    fun setupFBO(width: Int, height: Int) {
        releaseFBO()
        currentFboWidth = width
        currentFboHeight = height

        val fbos = IntArray(1)
        val textures = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        GLES20.glGenTextures(1, textures, 0)

        fboId = fbos[0]
        fboTextureId = textures[0]
        resampleSize = 6

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            resampleSize,
            resampleSize,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            fboTextureId,
            0
        )

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /**
     * 公共释放方法：释放 FBO 及其关联纹理
     */
    fun releaseFBO() {
        if (fboId != -1) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = -1
        }
        if (fboTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
            fboTextureId = -1
        }
    }

    fun updateCover(bitmap: Bitmap?) {
        if (textureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
        if (bitmap == null || bitmap.isRecycled) return

        // ✅ CPU端：彻底解决 Y 轴翻转问题
        // 创建一个 Y 轴翻转的 Matrix
        val flipMatrix = Matrix()
        flipMatrix.preScale(1f, -1f) // X保持100%，Y缩放至-100%（即翻转）

        // 生成一个翻转后的新 Bitmap
        val flippedBitmap: Bitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            flipMatrix,
            false
        )
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, flippedBitmap, 0)
        // 释放 flippedBitmap，防止内存泄漏（原始 bitmap 不需要我们处理）
        if (!flippedBitmap.isRecycled) {
            flippedBitmap.recycle()
        }
    }

    fun draw(screenWidth: Int, screenHeight: Int) {
        if (textureId == -1 || fboId == -1 || preProcessProg == -1 || blurProg == -1) return

        // ✅ Step 1: 渲染去黑预处理结果到高清 FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glViewport(0, 0, resampleSize, resampleSize)
        drawPass(preProcessProg, textureId)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        // ✅ Step 2: 全屏拉伸 FBO 的纹理，应用高清大采样 Box Blur
        GLES20.glViewport(0, 0, screenWidth, screenHeight)
        drawBlurPass(fboTextureId, currentFboWidth, currentFboHeight)
    }

    private fun drawPass(prog: Int, tex: Int) {
        GLES20.glUseProgram(prog)
        val pos = GLES20.glGetAttribLocation(prog, "vPosition")
        val texLoc = GLES20.glGetAttribLocation(prog, "vTexCoord")

        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTexture"), 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawBlurPass(tex: Int, w: Int, h: Int) {
        val prog: Int = blurProg
        GLES20.glUseProgram(prog)
        val pos = GLES20.glGetAttribLocation(prog, "vPosition")
        val texLoc = GLES20.glGetAttribLocation(prog, "vTexCoord")

        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTexture"), 0)

        // 关键：将当前 FBO 像素大小传给模糊 Shader
        val offsetLoc = GLES20.glGetUniformLocation(prog, "uTexelOffset")
        GLES20.glUniform2f(offsetLoc, 1.0f / w, 1.0f / h)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    } // (readAssets, createProgram, releaseAll 等辅助释放方法保持不变...)



    /**
     * 公共释放方法：手动释放封面纹理
     */
    fun releaseTexture() {
        if (textureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = -1
        }
    }

    /**
     * 终极释放：在 Renderer 销毁或 Context 丢失时调用
     */
    fun releaseAll() {
        releaseTexture()
        releaseFBO()
        if (preProcessProg != -1) {
            GLES20.glDeleteProgram(preProcessProg)
            preProcessProg = -1
        }
        if (blurProg != -1) {
            GLES20.glDeleteProgram(blurProg)
            blurProg = -1
        }
    }
}