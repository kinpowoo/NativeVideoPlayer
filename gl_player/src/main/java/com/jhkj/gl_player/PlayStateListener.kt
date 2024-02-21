package com.jhkj.gl_player

interface PlayStateListener {
    fun playStarted(progress:Int,duration:Int)
    fun playPaused()
    fun playStopped()
    fun playFinished()
    fun playOnGoing(progress: Int,duration: Int)
    fun playError()
}