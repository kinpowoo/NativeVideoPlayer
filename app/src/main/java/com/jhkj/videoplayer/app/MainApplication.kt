package com.jhkj.videoplayer.app

import android.app.Application
import android.util.Log
import com.jhkj.gl_player.AppCore
import com.jhkj.videoplayer.compose_pages.room_dto.AppDatabase


class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val path = System.getProperty("java.class.path")
        Log.e("APPLICATION","PATH IS $path")
        AppCore.getInstance().init(this)

        // 提前初始化数据库（可选）
        AppDatabase.getInstance(this)
    }

}