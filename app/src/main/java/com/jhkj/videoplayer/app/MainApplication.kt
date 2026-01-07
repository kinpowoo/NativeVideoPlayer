package com.jhkj.videoplayer.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.jhkj.gl_player.AppCore
import com.jhkj.videoplayer.compose_pages.room_dto.AppDatabase
import com.jhkj.videoplayer.utils.multi_lan.TextResManager
import com.tencent.mmkv.MMKV


class MainApplication : Application() {
    companion object{
        private lateinit var instance : MainApplication

        fun get(): MainApplication{
            return instance
        }


        // 获取版本名
        fun getVersionName(context: Context): String? {
            val packageInfo = getPackageInfo(context)
            if (packageInfo != null) {
                return packageInfo.versionName
            } else {
                return ""
            }
        }

        // 获取版本号
        fun getVersionCode(context: Context): Long {
            val packageInfo = getPackageInfo(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (packageInfo != null) {
                    return packageInfo.longVersionCode
                } else {
                    return 0
                }
            } else {
                if (packageInfo != null) {
                    return packageInfo.versionCode.toLong()
                } else {
                    return 0
                }
            }
        }

        private fun getPackageInfo(context: Context): PackageInfo? {
            val pi: PackageInfo
            try {
                val pm = context.packageManager
                pi = pm.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
                return pi
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }


    override fun onCreate() {
        super.onCreate()

        val path = System.getProperty("java.class.path")
        Log.e("APPLICATION","PATH IS $path")

        AppCore.getInstance().init(this)
        MMKV.initialize(this)
        instance = this
        // 提前初始化数据库（可选）
        AppDatabase.getInstance(this)

        TextResManager.get().intTextResource(this)
    }



}