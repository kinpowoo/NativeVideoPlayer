package com.jhkj.videoplayer.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.jhkj.gl_player.AppCore
import com.jhkj.videoplayer.compose_pages.room_dto.AppDatabase
import com.jhkj.videoplayer.third_file_framework.smb_client.BySMB
import com.jhkj.videoplayer.utils.multi_lan.TextResManager
import com.tencent.mmkv.MMKV
import com.topjohnwu.superuser.Shell
import jcifs.context.SingletonContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.Properties


class MainApplication : Application() {
    companion object{

        init {
            // Override Android's built-in BC provider with our own dependency
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
            Security.insertProviderAt( BouncyCastleProvider(), 1);

            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER));

        }
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

        // 高级配置（可选）
        BySMB.initProperty(
            soTimeout = "5000",  // Socket超时时间(毫秒)
            responseTimeout = "5000"  // 响应超时时间(毫秒)
        )

        TextResManager.get().intTextResource(this)

        /**
         * 初始化上下文
         */
        Thread{
            createCIFSContext()
        }.start()
    }

    private fun createCIFSContext() {
        // 默认超时时间（毫秒）
        val DEFAULT_TIMEOUT = 10000L
        // 配置属性
        val properties = Properties().apply {
            // 启用 SMB2/3 支持
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.enableSMB3", "true")

            // 超时设置
            setProperty("jcifs.smb.client.responseTimeout", DEFAULT_TIMEOUT.toString())
            setProperty("jcifs.smb.client.soTimeout", DEFAULT_TIMEOUT.toString())
            setProperty("jcifs.smb.client.connTimeout", "5000")

            // 禁用签名（如果需要）
            setProperty("jcifs.smb.client.signingRequired", "false")

            // 启用长文件名支持
            setProperty("jcifs.smb.client.useExtendedSecurity", "true")

            // 设置最大缓冲大小
            setProperty("jcifs.smb.client.maxBufferSize", "65536")
        }
        SingletonContext.init(properties)
    }

}