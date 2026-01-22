package com.jhkj.gl_player

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.opengl.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.Surface
import com.jhkj.gl_player.model.WebResourceFile
import com.jhkj.gl_player.util.GLDataUtil
import com.jhkj.gl_player.util.ResReadUtils
import com.jhkj.gl_player.util.ShaderUtils
import java.io.IOException
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min
import androidx.core.net.toUri

/**
 * 创建日期：6/23/21 7:08 AM
 * @author daijun
 * @version 1.0
 * @des：mediaplayer 视频播放render
 */
class MediaGLRenderer(ctx:Context?,listener: SurfaceTexture.OnFrameAvailableListener?):GLSurfaceView.Renderer {
    private var mContext: Context? = null
    //透视矩阵、相机矩阵定义放在基类中，方便传给其他绘制对象
    private val mMVPMatrix = FloatArray(16)
    private val mTempMatrix = FloatArray(16)
    private var mProjectMatrix = FloatArray(16)
    private var mCameraMatrix = FloatArray(16)
    private var mProgram = 0
    private var playUrl:String? = null
    private var playUri:Uri? = null
    private var webdavResource: WebResourceFile? = null

    private var vDuration:Int = 0  //视频的总时长（毫秒）
    private var vProgressTime:Int = 0  //视频的当前位置（毫秒）
    private var vWidth = 0
    private var vHeight = 0
    private var screeW = 0
    private var screenH = 0
    private var isMediaPrepared = false
    private var isBuffering = false
    private var isMediaPlaying = false
    private var isVolumeMuted = false
    private var bufferingListener:BufferingListener? = null
    private var playStateListener:PlayStateListener? = null
    private var volumeStateListener:VolumeStateListener? = null
    private var isVideoRotated = false
    private var isReleased = false
    private var isFullScreen = false
    private var mHandler = Handler(Looper.getMainLooper())

    private val mPosCoordinate = floatArrayOf(
        -1f, -1f,0f,
        -1f, 1f,0f,
        1f, 1f,0f,
        -1f, -1f,0f,
        1f, 1f,0f,
        1f, -1f,0f)
    private val mTexCoordinate = floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f,0f, 0f, 1f, 1f, 1f, 0f)

    private var mPosBuffer: FloatBuffer? = null
    private var mTexBuffer: FloatBuffer? = null
    private var mPlayer: MediaPlayer
    //!!! 此路径需根据自己情况，改为自己手机里的视频路径
    private var textureId = 0
    private lateinit var surfaceTexture:SurfaceTexture
    private var listener: SurfaceTexture.OnFrameAvailableListener? = null

    private var uPosHandle = 0
    private var aTexHandle = 0
    private var mMVPMatrixHandle = 0
    private var mTexRotateMatrixHandle = 0
    // 旋转矩阵
    private val rotateOriMatrix = FloatArray(16)

    fun setBufferingListener(listener: BufferingListener){
        this.bufferingListener = listener
    }

    fun setPlayStateListener(listener: PlayStateListener){
        this.playStateListener = listener
    }

    fun setVolumeStateListener(listener: VolumeStateListener){
        this.volumeStateListener = listener
    }

    init {
        this.mContext = ctx
        Matrix.setIdentityM(mProjectMatrix, 0)
        Matrix.setIdentityM(mCameraMatrix, 0)
        Matrix.setIdentityM(mMVPMatrix, 0)
        Matrix.setIdentityM(mTempMatrix, 0)

        mPlayer = MediaPlayer()
        if(Build.VERSION.SDK_INT >=23){
            //配置播放器
            val aa = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
            mPlayer.setAudioAttributes(aa)
        }else{
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        }

        mPlayer.setOnPreparedListener {
            isMediaPrepared = true
            //已准备好播放
            vDuration = mPlayer.duration
            vProgressTime = mPlayer.currentPosition
            if(isVideoRotated) {
                vWidth = mPlayer.videoHeight
                vHeight = mPlayer.videoWidth
            }else{
                vWidth = mPlayer.videoWidth
                vHeight = mPlayer.videoHeight
            }
            calculateVideoPosition()
            mPlayer.start()
            bufferingListener?.bufferingStop()
            isBuffering = false
            playStateListener?.playStarted(vProgressTime,vDuration)
            isMediaPlaying = true
        }
        mPlayer.setOnCompletionListener {
            isMediaPlaying = false
            isMediaPrepared = false
            playStateListener?.playFinished()
        }
        mPlayer.setOnBufferingUpdateListener(object :MediaPlayer.OnBufferingUpdateListener{
            override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
                bufferingListener?.bufferingProgress(percent)
            }
        })
        mPlayer.setOnErrorListener(object : MediaPlayer.OnErrorListener{
            override fun onError(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
                playStateListener?.playError()
                return true
            }
        })
        mPlayer.setOnSeekCompleteListener {
            bufferingListener?.bufferingStop()
            isBuffering = false
        }
        //设置播放时常亮
        mPlayer.setScreenOnWhilePlaying(true)
        //调用硬件解码
//        mPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
        mPosBuffer = GLDataUtil.createFloatBuffer(mPosCoordinate)
        mTexBuffer = GLDataUtil.createFloatBuffer(mTexCoordinate)
    }

    private fun initMediaPlayer(){
        val texture = IntArray(1)
        GLES30.glGenTextures(1, texture, 0) //生成一个OpenGl纹理
        textureId = texture[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]) //申请纹理存储区域并设置相关参数
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR.toFloat())
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0)
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener(listener)


        val surface = Surface(surfaceTexture)
        mPlayer.setSurface(surface)
        surface.release()
    }


    fun loadUrl(url:String){
        playUrl = url
        playUri = null
        webdavResource = null
        isVideoRotated = false
        startPlay()
    }

    fun loadUri(uri: Uri){
        playUrl = null
        playUri = uri
        webdavResource = null
        val rotation = getVideoRotation(uri)
        isVideoRotated = (rotation == 90)
        startPlay()
    }

    fun loadWebResource(conn: WebResourceFile){
        playUrl = null
        playUri = null
        webdavResource = conn
         // Base64 编码认证信息
        val credentials = conn.user + ":" + conn.pass
        val auth = "Basic " + Base64.encodeToString(credentials.toByteArray(),
            Base64.NO_WRAP)
        val headers =  mapOf("Authorization" to auth)
        val rotation = getVideoRotation(conn.path,headers)
        isVideoRotated = (rotation == 90)
        startPlay()
    }

    fun getVideoRotation(filePath: String): Int {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            return rotation?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
        return 0
    }

    fun getVideoRotation(uri: String,headers:Map<String,String>): Int {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(uri,headers)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            return rotation?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
        return 0
    }

    fun getVideoRotation(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(mContext,uri)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            return rotation?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
        return 0
    }

    fun startPlay(){
        try {
            isMediaPlaying = false
            isMediaPrepared = false
            mPlayer.reset()
            if(playUrl != null) {
                mPlayer.setDataSource(playUrl)
            }
            if(playUri != null) {
                mPlayer.setDataSource(mContext!!,playUri!!)
            }
            webdavResource?.let { conn ->
                val url = conn.path.toUri()
                // Base64 编码认证信息
                val credentials = conn.user + ":" + conn.pass
                val auth = "Basic " + Base64.encodeToString(credentials.toByteArray(),
                    Base64.NO_WRAP)
                val headers =  mapOf("Authorization" to auth)
                mPlayer.setDataSource(mContext!!,url,headers)
            }
            bufferingListener?.bufferingStart()
            isBuffering = true
            mPlayer.prepareAsync()
        }catch (e:IOException){
            isBuffering = false
            playStateListener?.playError()
            Log.e(TAG, "MediaPlayer prepare: $e")
        }
    }

    fun pausePlay(){
        if(!isMediaPrepared || !isMediaPlaying)return
        try {
            mPlayer.pause()
            playStateListener?.playPaused()
            isMediaPlaying = false
        }catch (e:Exception){
            playStateListener?.playError()
            Log.e(TAG, "MediaPlayer pause: $e")
        }
    }

    fun resumePlay(){
        if(!isMediaPrepared || isMediaPlaying)return
        try {
            mPlayer.start()
            isMediaPlaying = true
            vProgressTime = mPlayer.currentPosition
            playStateListener?.playStarted(vProgressTime,vDuration)
        }catch (e:Exception){
            playStateListener?.playError()
            Log.e(TAG, "MediaPlayer pause: $e")
        }
    }


    fun stopPlay(){
        if(!isMediaPrepared)return
        try {
            mPlayer.stop()
            playStateListener?.playStopped()
            isMediaPlaying = false
            isMediaPrepared = false
        }catch (e:Exception){
            playStateListener?.playError()
            Log.e(TAG, "MediaPlayer pause: $e")
        }
    }

    fun releasePlay(){
        try {
            mPlayer.release()
            isMediaPlaying = false
            isMediaPrepared = false
            isReleased = true
        }catch (e:Exception){
            Log.e(TAG, "MediaPlayer pause: $e")
        }
    }

    fun muteVoice(){
        if(isVolumeMuted || !isMediaPrepared)return
        mPlayer.setVolume(0f,0f)
        isVolumeMuted = true
        volumeStateListener?.volumeMuted()
    }

    fun resumeVoice(){
        if(!isVolumeMuted || !isMediaPrepared)return
        mPlayer.setVolume(1f,1f)
        isVolumeMuted = false
        volumeStateListener?.volumeResumed()
    }

    fun isPlaying():Boolean{
        return isMediaPlaying
    }

    fun isBuffering():Boolean{
        return isBuffering
    }

    fun isSpeedSupport():Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            true
        } else {
            false
        }
    }

    fun setPlaybackSpeed(speed:Float){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = mPlayer.playbackParams
                params.audioFallbackMode = PlaybackParams.AUDIO_FALLBACK_MODE_MUTE
                params.speed = speed
                params.pitch = 1.0f // 保持原始音调
                mPlayer.playbackParams = params
            } catch (e:Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isPrepared():Boolean{
        return isMediaPrepared
    }

    fun isMuted():Boolean{
        return isVolumeMuted
    }

    fun getCurrentPlayPos():Int{
        if(!isMediaPrepared)return 0
        return mPlayer.currentPosition
    }

    fun getDuration():Int{
        if(!isMediaPrepared)return 0
        return mPlayer.duration
    }


    fun seekTo(percent:Float){
        if(isReleased)return
        if(!isMediaPrepared){
            startPlay()
            mHandler.postDelayed({
                try {
                    bufferingListener?.bufferingStart()
                    isBuffering = true
                    val duration = mPlayer.duration  //获取时间的毫秒数
                    val pos = (duration * percent).toInt()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mPlayer.seekTo(pos.toLong(), MediaPlayer.SEEK_CLOSEST)
                    } else {
                        mPlayer.seekTo(pos)
                    }
                    resumePlay()
                } catch (e: IllegalStateException) {
                    isBuffering = false
                    e.printStackTrace()
                } catch (e2: IllegalArgumentException) {
                    isBuffering = false
                    e2.printStackTrace()
                }
            },500)
        }else {
            try {
                bufferingListener?.bufferingStart()
                isBuffering = true
                val duration = mPlayer.duration  //获取时间的毫秒数
                val pos = (duration * percent).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mPlayer.seekTo(pos.toLong(), MediaPlayer.SEEK_CLOSEST)
                } else {
                    mPlayer.seekTo(pos)
                }
                resumePlay()
            } catch (e: IllegalStateException) {
                isBuffering = false
                e.printStackTrace()
            } catch (e2: IllegalArgumentException) {
                isBuffering = false
                e2.printStackTrace()
            }
        }
    }

    fun forwardOrBackward(seconds:Int,forward: Boolean){
        if(!isMediaPrepared)return
        try {
            bufferingListener?.bufferingStart()
            isBuffering = true
            val duration = mPlayer.duration  //获取时间的毫秒数
            val curPos = mPlayer.currentPosition
            val afterPos = if(forward){
                min(curPos+seconds*1000,duration)
            }else{
                max(0,curPos - seconds*1000)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mPlayer.seekTo(afterPos.toLong(),MediaPlayer.SEEK_CLOSEST)
            }else{
                mPlayer.seekTo(afterPos)
            }
            resumePlay()
        }catch (e:IllegalStateException){
            isBuffering = false
            e.printStackTrace()
        }catch (e2:IllegalArgumentException){
            isBuffering = false
            e2.printStackTrace()
        }
    }



    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        //编译顶点着色程序
        val vertexShaderStr = ResReadUtils.readResource(R.raw.vertex_media_player_shade)
        val vertexShaderId = ShaderUtils.compileVertexShader(vertexShaderStr)
        //编译片段着色程序
        // fragment_media_player_normal_shade  --正常
        // fragment_media_player_nostalgia_shade  -- 怀旧滤镜
        // fragment_media_player_negative_shade  -- 负面滤镜
        val fragmentShaderStr = ResReadUtils.readResource(R.raw.fragment_media_player_normal_shade)
        val fragmentShaderId = ShaderUtils.compileFragmentShader(fragmentShaderStr)
        //连接程序
        mProgram = ShaderUtils.linkProgram(vertexShaderId, fragmentShaderId)
        uPosHandle = GLES20.glGetAttribLocation(mProgram, "position")
        aTexHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate")
        GLES30.glVertexAttribPointer(uPosHandle, 3, GLES30.GL_FLOAT, false, 12, mPosBuffer)
        GLES30.glVertexAttribPointer(aTexHandle, 2, GLES30.GL_FLOAT, false, 8, mTexBuffer)
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "textureTransform")
        mTexRotateMatrixHandle = GLES20.glGetUniformLocation(mProgram,"uTextRotateMatrix")

        initMediaPlayer()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screeW = width
        screenH = height
        calculateVideoPosition()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glEnable(GLES20.GL_DEPTH_TEST)
        GLES30.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES30.glViewport(0,0,screeW,screenH)

        GLES30.glUseProgram(mProgram)
        // 将前面计算得到的mMVPMatrix(frustumM setLookAtM 通过multiplyMM 相乘得到的矩阵) 传入vMatrix中，与顶点矩阵进行相乘
        GLES30.glUniformMatrix4fv(mMVPMatrixHandle, 1, true, mMVPMatrix, 0)
        surfaceTexture.updateTexImage()

        if(isPlaying()) {
            vProgressTime = mPlayer.currentPosition
            playStateListener?.playOnGoing(vProgressTime, vDuration)
        }

        //?? 为何要取矩阵？？!!!
        surfaceTexture.getTransformMatrix(rotateOriMatrix)
        GLES30.glUniformMatrix4fv(mTexRotateMatrixHandle, 1, false, rotateOriMatrix, 0)

//        GLES30.glBindVertexArray(vao.array()[0])
        GLES30.glEnableVertexAttribArray(uPosHandle)
        GLES30.glEnableVertexAttribArray(aTexHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,6)
        GLES30.glDisableVertexAttribArray(uPosHandle)
        GLES30.glDisableVertexAttribArray(aTexHandle)
//        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    companion object {
        private const val TAG = "MediaGLRenderer"
    }

    fun release(){
        GLES30.glDeleteProgram(mProgram)
        stopPlay()
        releasePlay()
    }


    //改变视频的尺寸自适应
    private fun changeVideoSize(screenW:Int,screenH:Int) {
        var finalVideoW = 0
        var finalVideoH = 0
        // 视频已经填满屏幕，计算视频相对于屏幕的缩放比例
        val srw = 1f/(screenW*1f / vWidth)
        val srh = 1f/(screenH*1f / vHeight)
        if(screenW > screenH){  //横屏
            //计算视频高的与屏幕的高的比例
            var ratio = screenH*1f / vHeight
            val scaleVideoW = vWidth * ratio
            if(scaleVideoW > screenW){
                ratio = screenW*1f / vWidth
                finalVideoW = screenW
                finalVideoH = (vHeight * ratio).toInt()
            }else{
                finalVideoW = scaleVideoW.toInt()
                finalVideoH = screenH
            }
            val offsetX = (screenW - finalVideoW)/2f
            val offsetY = (screenH - finalVideoH)/2f
            Matrix.translateM(mMVPMatrix,0,offsetX,offsetY,1f)
            Matrix.scaleM(mMVPMatrix,0,ratio*srw,ratio*srh,1f)
        }else{  //竖屏
            val ratioW = screenW*1f / vWidth
            val ratioH = screenH*1f / vHeight
            var ratio = ratioW
            if(vWidth >= vHeight){
                finalVideoW = (vWidth * ratioW).toInt()
                finalVideoH = (vHeight * ratioW).toInt()
            }else{
                finalVideoW = (vWidth * ratioH).toInt()
                finalVideoH = screenH
                ratio = ratioH
                if(finalVideoW > screenW){
                    finalVideoW = screenW
                    finalVideoH = (vHeight * ratioW).toInt()
                    ratio = ratioW
                }
            }
            val offsetX = (screenW - finalVideoW)/2f
            val offsetY = (screenH - finalVideoH)/2f
            Matrix.translateM(mMVPMatrix,0,offsetX,offsetY,0f)
            Matrix.scaleM(mMVPMatrix,0,ratio*srw,ratio*srh,1f)
        }
    }

    private fun calculateVideoPosition() {
        if(vWidth == 0 || vHeight == 0)return
        Matrix.setIdentityM(mMVPMatrix, 0)
        changeVideoSize(screeW,screenH)
    }

}