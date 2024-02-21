package com.jhkj.gl_player

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.net.Uri
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class PlayerGLSurface : GLSurfaceView, OnFrameAvailableListener {
    private var render: MediaGLRenderer? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setEGLContextClientVersion(3)
        setZOrderOnTop(false)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.RGBA_8888)
        render = MediaGLRenderer(context, this)
        setRenderer(render)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        requestRender()
    }

    fun setPlayStateListener(listener: PlayStateListener){
        render?.setPlayStateListener(listener)
    }

    fun setBufferingListener(listener: BufferingListener){
        render?.setBufferingListener(listener)
    }

    fun setVolumeStateListener(listener: VolumeStateListener){
        render?.setVolumeStateListener(listener)
    }

    fun isPlaying():Boolean {
        return render?.isPlaying() ?: false
    }

    fun switchPlayState(){
        if(render?.isPrepared() == true){
            if(render?.isPlaying() == true){
                render?.pausePlay()
            }else{
                render?.resumePlay()
            }
        }else {
            render?.startPlay()
        }
    }

    //在已经播放的状态下双击，进行暂停和恢复操作
    fun pauseOrResume(){
        if(render?.isPrepared() == true){
            if(render?.isPlaying() == true){
                render?.pausePlay()
            }else{
                render?.resumePlay()
            }
        }
    }

    fun loadUrl(url: String){
        render?.loadUrl(url)
    }

    fun loadUri(uri: Uri){
        render?.loadUri(uri)
    }

    fun pausePlay(){
        render?.pausePlay()
    }

    fun seekTo(percent:Float){
        render?.seekTo(percent)
    }

    fun switchVolume(){
        if(render?.isMuted() == true){
            render?.resumeVoice()
        }else{
            render?.muteVoice()
        }
    }

    fun getCurrentPlayPos():Int{
        return render?.getCurrentPlayPos() ?: 0
    }

    fun getDuration():Int{
        return render?.getDuration() ?: 0
    }

    fun stopPlay(){
        render?.stopPlay()
    }

    fun release(){
        render?.release()
    }
}