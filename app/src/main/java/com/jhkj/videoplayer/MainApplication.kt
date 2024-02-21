package com.jhkj.videoplayer

import android.app.Application
import android.util.Log
import com.jhkj.gl_player.AppCore

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val path = System.getProperty("java.class.path")
        Log.e("APPLICATION","PATH IS $path")
        AppCore.getInstance().init(this)
    }
}