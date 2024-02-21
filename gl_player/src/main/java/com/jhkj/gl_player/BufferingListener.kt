package com.jhkj.gl_player

interface BufferingListener {
    fun bufferingStart()
    fun bufferingStop()
    fun bufferingProgress(progress:Int)
}