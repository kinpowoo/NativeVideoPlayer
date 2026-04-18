package com.jhkj.videoplayer.player.cover_bg_gen

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


class CoverBackgroundRender2 {
    private var program = 0
    private var textureId = -1

    // FBO 相关
    private var fboId = -1
    private var fboTextureId = -1

    private val vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer

    private val vertexData = floatArrayOf(-1f, 1f, -1f, -1f, 1f, 1f, 1f, -1f)
    private val textureData = floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f)
    private var screenWidth:Int = 0
    private var screenHeight:Int = 0
    private var lowResSize:Int = 0

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertexData)
        vertexBuffer.position(0)

        textureBuffer = ByteBuffer.allocateDirect(textureData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(textureData)
        textureBuffer.position(0)
    }

    /**
     * 初始化 Shader 和 FBO
     */
    fun init(context: Context) {
        val vSource: String = readShaderFromAssets(context, "vert_glsl/vertex_shader.glsl")
        val fSource: String = readShaderFromAssets(context, "frag_glsl/frag_shader.glsl")
        program = createProgram(vSource, fSource)
    }

    /**
     * 配置 FBO (在 onSurfaceChanged 中调用)
     */
    /**
     * 配置 FBO (在 onSurfaceChanged 中调用)
     * 每次屏幕尺寸变化时，先释放旧 FBO 再创建新 FBO
     */
    fun setupFBO(width: Int, height: Int) {
        // 1. 习惯性释放旧资源
        releaseFBO()
        screenWidth = width
        screenHeight = height

        // --- 关键改进：降采样 ---
        // 将 FBO 尺寸固定在很小的值（如 16x16 或 32x32）
        // 这样原始图片拉伸到 16x16 时，细节已经全部丢失，只剩下主要色块
        // 关键：尺寸越小，轮廓越模糊，颜色越平滑
        // 12x12 可以完美抹除像截图中那种复杂的唱片机和封面文字轮廓
        lowResSize = 32

        // 使用真实宽高，保持高清
        val fbos = IntArray(1)
        val textures = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        GLES20.glGenTextures(1, textures, 0)

        fboId = fbos[0]
        fboTextureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            lowResSize,
            lowResSize,
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

    fun updateCover(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return

        // ✅ 核心改进：更新前释放旧纹理，防止显存爆炸
        releaseTexture()

        if (textureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    fun draw() {
        if (textureId == -1 || fboId == -1) return

        // 第一步：绘制到 12x12 的 FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glViewport(0, 0, lowResSize, lowResSize)
        renderToQuad(textureId)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        // 第二步：全屏拉伸显示
        GLES20.glViewport(0, 0, screenWidth, screenHeight)
        renderToQuad(fboTextureId)
    }

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

    /**
     * 终极释放：在 Renderer 销毁或 Context 丢失时调用
     */
    fun releaseAll() {
        releaseTexture()
        releaseFBO()
        if (program != -1) {
            GLES20.glDeleteProgram(program)
            program = -1
        }
    }


    private fun renderToQuad(tex: Int) {
        GLES20.glUseProgram(program)
        val posLoc = GLES20.glGetAttribLocation(program, "vPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "vTexCoord")

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    // --- 工具方法：读取资源 ---
    private fun readShaderFromAssets(context: Context, fileName: String): String {
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
}