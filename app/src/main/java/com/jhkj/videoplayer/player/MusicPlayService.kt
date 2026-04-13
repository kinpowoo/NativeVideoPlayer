package com.jhkj.videoplayer.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.jhkj.gl_player.BufferingListener
import com.jhkj.gl_player.PlayStateListener
import com.jhkj.gl_player.data_source_imp.BufferedSMBDataSource2
import com.jhkj.gl_player.model.WebResourceFile
import com.jhkj.gl_player.util.MD5
import com.sin_tech.ble_manager.R
import com.sin_tech.ble_manager.ble_server.BleServerActivity
import jcifs.CIFSContext
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * WiFi Direct 前台服务
 * 保证服务端在后台持续运行
 */
class MusicPlayService : Service() {
    private val TAG = "MusicPlayService"
    // 服务器实例
    var mPlayer: MediaPlayer? = null

    private val binder = MusicBinder()

    private var ref: WeakReference<MusicPlayerActivity>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val playThread = Executors.newSingleThreadExecutor()

    private var vDuration: Int = 0  //视频的总时长（毫秒）
    private var vProgressTime: Int = 0  //视频的当前位置（毫秒）
    private var lastSeekTo = -1f
    // 唤醒锁
    private var wakeLock: PowerManager.WakeLock? = null
    private var isMediaPrepared = false
    private var isBuffering = false
    private var isMediaPlaying = false
    private var isReleased = false
    private var bufferingListener: BufferingListener? = null
    private var playStateListener: PlayStateListener? = null

    private var mCurrentDataSource: MediaDataSource? = null

    private var playUrl: String? = null
    private var playUri: Uri? = null
    private var webdavResource: WebResourceFile? = null

    // 更新进度的 Runnable
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mPlayer?.let { player ->
                if (player.isPlaying) {
                    val current = player.currentPosition
                    val total = player.duration
                    playStateListener?.playOnGoing(current, total)

                    // 每100毫秒更新一次（可调整）
                    handler.postDelayed(this, 100)
                }
            }
        }
    }

    fun setWeakRef(actRef: WeakReference<MusicPlayerActivity>?) {
        this.ref = actRef
        if(actRef?.get() != null) {
            this.bufferingListener = actRef.get()
            this.playStateListener = actRef.get()
        }
    }

    // 广播接收器
    private val screenReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (Intent.ACTION_SCREEN_ON == action) {
                acquireLocks()
            } else if (Intent.ACTION_SCREEN_OFF == action) {
                // 保持锁，确保后台运行
            } else if (Intent.ACTION_USER_PRESENT == action) {
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        // 创建通知渠道
        createNotificationChannel()
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        // 注册屏幕状态监听
        registerScreenReceiver()


        // 获取唤醒锁
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMediaServer()
        // 返回START_STICKY，系统会自动重启服务
        return START_STICKY
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Music Play background service"
            channel.setShowBadge(false)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            val manager = getSystemService<NotificationManager?>(NotificationManager::class.java)
            if (manager != null) {
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun startProgressUpdates() {
        handler.post(updateProgressRunnable)
    }

    fun stopProgressUpdates() {
        handler.removeCallbacks(updateProgressRunnable)
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, BleServerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("音频服务运行中")
            .setContentText("点击返回应用")
            .setSmallIcon(R.drawable.ic_blue_notify)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setSound(null)
            .setVibrate(null)
            .build()
    }


    /**
     * 启动WiFi Direct服务器
     */
    private fun startMediaServer() {
        try {
            mPlayer = MediaPlayer()
            //配置播放器
            val aa = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
            mPlayer?.setAudioAttributes(aa)

            mPlayer?.setOnPreparedListener {
                isMediaPrepared = true
                //已准备好播放
                vDuration = mPlayer?.duration ?: 0
                vProgressTime = mPlayer?.currentPosition ?: 0

                mPlayer?.start()
                bufferingListener?.bufferingStop()
                isBuffering = false
                playStateListener?.playStarted(vProgressTime, vDuration)
                isMediaPlaying = true
                updateNotification("播放音频中")
                startProgressUpdates()
                if (lastSeekTo != -1f) {
                    seekTo(lastSeekTo)
                    lastSeekTo = -1f
                }
            }
            mPlayer?.setOnCompletionListener {
                isMediaPlaying = false
                isMediaPrepared = false
                playStateListener?.playFinished()
                releaseDataSource()
            }

            updateNotification("等待播放音频")
        } catch (e: Exception) {
            updateNotification("服务启动失败")
        }
    }

    fun loadUrl(url: String) {
        playUrl = url
        playUri = null
        webdavResource = null
        startPlay()
    }

    fun loadUri(uri: Uri) {
        playUrl = null
        playUri = uri
        webdavResource = null
        startPlay()
    }

    fun loadWebResource(conn: WebResourceFile) {
        playUrl = null
        playUri = null
        webdavResource = conn
        startPlay()
    }

    fun isPlaying(): Boolean {
        return isMediaPlaying
    }

    fun isBuffering(): Boolean {
        return isBuffering
    }

    fun setPlaybackSpeed(speed: Float) {
        try {
            val params = mPlayer?.playbackParams
            params?.audioFallbackMode = PlaybackParams.AUDIO_FALLBACK_MODE_MUTE
            params?.speed = speed
            params?.pitch = 1.0f // 保持原始音调
            if (params != null) {
                mPlayer?.playbackParams = params
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isPrepared(): Boolean {
        return isMediaPrepared
    }

    fun getCurrentPlayPos(): Int {
        if (!isMediaPrepared) return 0
        return mPlayer?.currentPosition  ?: 0
    }

    fun getDuration(): Int {
        if (!isMediaPrepared) return 0
        return mPlayer?.duration ?: 0
    }


    fun seekTo(percent: Float) {
        if (isReleased) return
        if (!isMediaPrepared) {
            startPlay()
            lastSeekTo = percent
        } else {
            try {
                bufferingListener?.bufferingStart()
                isBuffering = true
                val duration = mPlayer?.duration ?: 0  //获取时间的毫秒数
                val pos = (duration * percent).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mPlayer?.seekTo(pos.toLong(), MediaPlayer.SEEK_CLOSEST)
                } else {
                    mPlayer?.seekTo(pos)
                }
            } catch (e: IllegalStateException) {
                isBuffering = false
                e.printStackTrace()
            } catch (e2: IllegalArgumentException) {
                isBuffering = false
                e2.printStackTrace()
            }
        }
    }

    fun seekToPos(pos: Int) {
        if (isReleased) return
        if (!isMediaPrepared) {
            val duration = mPlayer?.duration ?: 0  //获取时间的毫秒数
            if(duration > pos && duration > 0) {
                lastSeekTo = (pos * 1f / duration)
            }
            startPlay()
        } else {
            try {
                bufferingListener?.bufferingStart()
                isBuffering = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mPlayer?.seekTo(pos.toLong(), MediaPlayer.SEEK_CLOSEST)
                } else {
                    mPlayer?.seekTo(pos)
                }
//                resumePlay()
            } catch (e: IllegalStateException) {
                isBuffering = false
                e.printStackTrace()
            } catch (e2: IllegalArgumentException) {
                isBuffering = false
                e2.printStackTrace()
            }
        }
    }

    fun forwardOrBackward(seconds: Int, forward: Boolean) {
        if (!isMediaPrepared) return
        try {
            bufferingListener?.bufferingStart()
            isBuffering = true
            val duration = mPlayer?.duration ?: 0 //获取时间的毫秒数
            val curPos = mPlayer?.currentPosition ?: 0
            val afterPos = if (forward) {
                min(curPos + seconds * 1000, duration)
            } else {
                max(0, curPos - seconds * 1000)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mPlayer?.seekTo(afterPos.toLong(), MediaPlayer.SEEK_CLOSEST)
            } else {
                mPlayer?.seekTo(afterPos)
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


    /**
     * 更新通知
     */
    private fun updateNotification(contentText: String?) {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("音频服务运行中")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_blue_notify)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        manager?.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 获取唤醒锁
     */
    private fun acquireLocks() {
        // 获取唤醒锁（CPU保持运行）
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager?
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or
                        PowerManager.ON_AFTER_RELEASE,
                "WiFiDirect:WakeLock"
            )
            wakeLock?.setReferenceCounted(false)

            if (!(wakeLock?.isHeld ?: false)) {
                wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        }
    }
    /**
     * 释放唤醒锁
     */
    private fun releaseLocks() {
        if (wakeLock != null && wakeLock!!.isHeld) {
            wakeLock?.release()
            wakeLock = null
        }
    }

    /**
     * 注册屏幕状态接收器
     */
    private fun registerScreenReceiver() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_USER_PRESENT)

        registerReceiver(screenReceiver, filter)
    }

    /**
     * 请求电池优化白名单
     */
    @SuppressLint("NewApi")
    fun requestBatteryOptimizationExclusion(activity: Activity) {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager?
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent()
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = ("package:$packageName").toUri()

            try {
                activity.startActivityForResult(intent, 100)
            } catch (e: Exception) {
            }
        }
    }

    fun startPlay() {
        playThread.run {
            try {
                isMediaPlaying = false
                isMediaPrepared = false
                mPlayer?.reset()
                if (playUrl != null) {
                    mPlayer?.setDataSource(playUrl)
                }
                if (playUri != null) {
                    mPlayer?.setDataSource(baseContext, playUri!!)
                }
                webdavResource?.let { conn ->
                    if (conn.path.startsWith("http")) {
                        val url = conn.path.toUri()
                        // Base64 编码认证信息
                        val credentials = conn.user + ":" + conn.pass
                        val auth = "Basic " + Base64.encodeToString(
                            credentials.toByteArray(),
                            Base64.NO_WRAP
                        )
                        val headers = mapOf("Authorization" to auth)
                        mPlayer?.setDataSource(baseContext, url, headers)
                    } else if (conn.path.startsWith("smb")) {
                        val smbUrl = conn.path
                        try {
                            val username = conn.user
                            val context: CIFSContext = if (!TextUtils.isEmpty(username)) {
                                // 如果域为空，可以传入空字符串
                                val auth = NtlmPasswordAuthenticator(
                                    null,
                                    username, conn.pass,
                                    NtlmPasswordAuthenticator.AuthenticationType.USER
                                )
                                SingletonContext.getInstance().withCredentials(auth)
                            } else {
                                SingletonContext.getInstance().withGuestCrendentials()
                            }
                            val smbFile = SmbFile(smbUrl, context)
                            val smbRaf1 = SmbRandomAccessFile(smbFile, "r")
                            val smbRaf2 = SmbRandomAccessFile(smbFile, "r")
//                        // 2. 获取文件输入流
                            mCurrentDataSource = BufferedSMBDataSource2(
                                smbRaf1, smbRaf2,
                                smbFile.length()
                            )
//                        mCurrentDataSource = StableSMBDataSource(randomSmbFile, smbFile.length())
                            val cacheFile = getCacheFile(conn.path)
//                        val cacheDir = mContext!!.externalCacheDir
//                        val smbDataSource = SMBDataSourceRaf2(cacheFile,randomSmbFile, smbFile.length())

                            // 3. 关键步骤：将文件描述符（FD）设置为MediaPlayer的数据源
                            //                            mPlayer.setDataSource(mContext!!,uri)
                            mPlayer?.setDataSource(mCurrentDataSource)
                        } catch (e: SmbException) {
                            e.printStackTrace()
                        }
                    }
                }
                bufferingListener?.bufferingStart()
                isBuffering = true
                mPlayer?.prepareAsync()
            } catch (e: IOException) {
                isBuffering = false
                playStateListener?.playError()
                Log.e(TAG, "MediaPlayer prepare: $e")
            }
        }
    }

    fun getCacheFile(path: String): File {
        val cacheDir = baseContext.externalCacheDir
        val fileName = "${MD5.md5(path)}.tmp"
        val cacheFile = File(cacheDir, fileName)

        // 先检查是否是目录
        if (cacheFile.exists() && cacheFile.isDirectory) {
            Log.e("Cache", "存在同名目录，正在删除: ${cacheFile.absolutePath}")
            // 删除目录及其内容
            cacheFile.deleteRecursively()
        }

        // 确保父目录存在
        if (!(cacheDir?.exists() ?: false)) {
            cacheDir?.mkdirs()
        }

        return cacheFile
    }

    fun setVolume(left:Float,right:Float){
        mPlayer?.setVolume(left,right)
    }

    fun pausePlay() {
        stopProgressUpdates()
        if (!isMediaPrepared || !isMediaPlaying) return
        try {
            mPlayer?.pause()
            playStateListener?.playPaused()
            isMediaPlaying = false
            updateNotification("音频播放暂停中")
        } catch (e: Exception) {
            playStateListener?.playError()
            Log.e(TAG, "MediaPlayer pause: $e")
        }
    }

    fun resumePlay() {
        if (!isMediaPrepared || isMediaPlaying) return
        try {
            mPlayer?.start()
            isMediaPlaying = true
            vProgressTime = mPlayer?.currentPosition ?: 0
            playStateListener?.playStarted(vProgressTime, vDuration)
            updateNotification("播放音频中")
            startProgressUpdates()
        } catch (e: Exception) {
            playStateListener?.playError()
            Log.e(TAG, "MediaPlayer pause: $e")
        }
    }


    fun stopPlay() {
        stopProgressUpdates()
        if (!isMediaPrepared) return
        try {
            mPlayer?.stop()
            playStateListener?.playStopped()
            isMediaPlaying = false
            isMediaPrepared = false
            updateNotification("音频播放已结束")
        } catch (e: Exception) {
            playStateListener?.playError()
        }
    }

    fun releasePlay() {
        stopProgressUpdates()
        try {
            releaseDataSource()
            mPlayer?.release()
            isMediaPlaying = false
            isMediaPrepared = false
            isReleased = true
        } catch (e: Exception) {
        }
    }

    private fun releaseDataSource() {
        // 1. 必须：手动关闭上一个数据源
        Thread {
            if (mCurrentDataSource != null) {
                try {
                    mCurrentDataSource?.close()
                    mCurrentDataSource = null
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }.start()
    }


    override fun onDestroy() {
        stopPlay()
        // 停止服务器
        releasePlay()
        // 释放唤醒锁
        releaseLocks()
        playThread.shutdownNow()
        // 取消广播接收器注册
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // 接收器未注册
        }

        // 停止前台服务
        stopForeground(true)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayService = this@MusicPlayService
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "MusicChannel"
        private const val CHANNEL_NAME = "Music Play Service"

        /**
         * 启动前台服务
         */
        fun startService(context: Context) {
            val serviceIntent = Intent(context, MusicPlayService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        /**
         * 停止前台服务
         */
        fun stopService(context: Context) {
            val serviceIntent = Intent(context, MusicPlayService::class.java)
            context.stopService(serviceIntent)
        }
    }
}