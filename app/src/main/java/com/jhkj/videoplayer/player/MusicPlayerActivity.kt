package com.jhkj.videoplayer.player

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.jhkj.gl_player.BufferingListener
import com.jhkj.gl_player.PlayStateListener
import com.jhkj.gl_player.data_source_imp.BufferedSMBDataSource2
import com.jhkj.gl_player.model.WebResourceFile
import com.jhkj.gl_player.util.ContentUriUtil
import com.jhkj.gl_player.util.StatusBarTool
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.databinding.MusicPlayerLayoutBinding
import com.jhkj.videoplayer.player.cover_bg_gen.BitmapUtils
import com.jhkj.videoplayer.player.cover_bg_gen.MyGLRenderer
import com.jhkj.videoplayer.utils.file_recursive.FileItem
import jcifs.CIFSContext
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale


class MusicPlayerActivity : AppCompatActivity(), ServiceConnection, BufferingListener,
    PlayStateListener {
    private lateinit var binding: MusicPlayerLayoutBinding
    private var isServiceRunning = false
    private var service: MusicPlayService? = null
    private var fileInfo: FileItem? = null
    private var audioHelper: AudioVolumeHelper? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var renderer: MyGLRenderer? = null
    private var isRendererSet = false
    private var startY = 0f
    private val dragThreshold = 800f // 下拉阈值（像素）


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MusicPlayerLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 启用 Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        supportActionBar?.hide()
        // 在Activity中检查OpenGL ES版本

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configurationInfo = activityManager.deviceConfigurationInfo
        val supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000
        Log.d(
            "GLRender",
            "OpenGL ES supported: $supportsEs2, version: ${Integer.toHexString(configurationInfo.reqGlEsVersion)}"
        )

        // 创建GLSurfaceView
        glSurfaceView = binding.blurBg
        // ✅ 添加这一行，防止切后台销毁 Context
        glSurfaceView?.preserveEGLContextOnPause = true
        glSurfaceView?.setEGLContextClientVersion(2)
        // 创建渲染器
        renderer = MyGLRenderer(glSurfaceView, this)
        glSurfaceView?.setRenderer(renderer)

        // 3. 设置渲染模式：仅在数据变化时渲染（省电）
        // 如果要做动态模糊动画，可以设为 RENDERMODE_CONTINUOUSLY
        glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        // 保持屏幕常亮
        glSurfaceView?.keepScreenOn = true
        isRendererSet = true
        // 初始渲染一次
        glSurfaceView?.requestRender()


        setupDragListener()

        binding.dismissBtn.setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }
        val statusHeight = StatusBarTool.getStatusBarHeight(this)
        val param = binding.toolbar.layoutParams as? ConstraintLayout.LayoutParams
        param?.topMargin = statusHeight
        binding.toolbar.layoutParams = param

        audioHelper = AudioVolumeHelper(this)
        val maxVol = audioHelper?.getMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val curVol = audioHelper?.getCurrentVolume(AudioManager.STREAM_MUSIC) ?: 0
        binding.volumeSlider.max = maxVol
        binding.volumeSlider.progress = curVol
        if (curVol == 0) {
            binding.voiceBtn.setImageResource(com.jhkj.gl_player.R.drawable.baseline_volume_off_24)
        }

        // 启用 Edge-to-Edge
//        WindowCompat.setDecorFitsSystemWindows(window, false)

        supportActionBar?.hide()
        actionBar?.hide()
//        supportActionBar?.hide()
        //绑定服务
        bindService(
            Intent(this, MusicPlayService::class.java),
            this,
            BIND_AUTO_CREATE
        )
        // 启动前台服务
        MusicPlayService.startService(this)
        isServiceRunning = true

        setClickListener()
        getIntentData()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener() {
        binding.cover.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y // 记录按下时的 Y 坐标
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val currentY = event.y
                    val deltaY = currentY - startY
                    binding.root.translationY = deltaY
                    // 如果向下移动超过阈值，触发下拉动作
                    if (deltaY > dragThreshold) {
                        onDragDownDetected()
                        startY = currentY // 重置起始点，避免重复触发
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // 可选：手势结束时的处理
                    true
                }

                else -> false
            }
        }
    }

    private fun onDragDownDetected() {
        // 这里实现你的下拉逻辑
        finishAfterTransition()
        overridePendingTransition(0, 0)
    }


    fun updateBackground(bitmap: Bitmap?) {
        glSurfaceView?.queueEvent {
            // renderer 内部已经处理了线程跳转，这里直接调不会崩
            renderer?.updateBitmap(bitmap)
        }
    }

    /**
     * 设置渲染参数
     */
    fun setRenderParameters(
        blurRadius: Float? = null,
        saturation: Float? = null,
        brightness: Float? = null
    ) {
//        glSurfaceView?.queueEvent {
//            blurRadius?.let { backgroundRenderer?.setBlurRadius(it) }
//            saturation?.let { backgroundRenderer?.setSaturation(it) }
//            brightness?.let { backgroundRenderer?.setBrightness(it) }
//        }
    }

    fun setClickListener() {
        binding.startBtn.setOnClickListener {
            if (service == null) return@setOnClickListener
            if (service!!.isPrepared()) {
                if (service?.isPlaying() ?: false) {
                    service?.pausePlay()
                } else {
                    service?.resumePlay()
                }
            } else {
                loadFileAndPlay()
            }
        }
        binding.playProgress.setOnProgressChangeListener { bar, progress, percent, fromUser ->
            if (fromUser) {
                service?.seekToPos(progress)
            }
        }
        binding.voiceBtn.setOnClickListener {
            if (binding.volumeSlider.isVisible) {
                binding.volumeSlider.visibility = View.GONE
            } else {
                binding.volumeSlider.visibility = View.VISIBLE
            }
        }
        binding.volumeSlider.setOnProgressChangeListener { bar, progress, percent, bool ->
//            service?.setVolume(percent, percent)
            audioHelper?.setVolumePercent(percent)
            if (progress == 0) {
                binding.voiceBtn.setImageResource(com.jhkj.gl_player.R.drawable.baseline_volume_off_24)
            } else {
                binding.voiceBtn.setImageResource(com.jhkj.gl_player.R.drawable.baseline_volume_up_24)
            }
        }
    }

    fun extractAudioMetadata(context: Context, localPath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val audioFile = File(localPath)
            if (audioFile.exists()) {
                val fileName = audioFile.nameWithoutExtension
                val audFile = AudioFileIO.getDefaultAudioFileIO().readFile(audioFile)
                val tag = audFile.tag
                val header = audFile.audioHeader
                println("\n--- 音频流参数 ---")
                println("时长: ${header.trackLength} 秒")
                println("比特率: ${header.bitRate} kbps")
                println("采样率: ${header.sampleRateAsNumber} Hz")
                println("声道数: ${header.channels}")
                println("格式: ${header.format}") // 例如: "WAV" 或 "FLAC"
                println("位深: ${header.bitsPerSample} bit")


                // 获取第一张封面图
                val artwork: Artwork? = tag?.firstArtwork
                val coverBitmap = if (artwork != null) {
                    val bytes = artwork.binaryData
                    // 将字节数组转换为 Android Bitmap
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    BitmapUtils.createDefaultCover(512,512) // 文件中没有封面数据
                }
                coverBitmap?.let {
                    updateBackground(coverBitmap)
                }
                withContext(Dispatchers.Main) {
                    coverBitmap?.let {
                        binding.cover.setImageBitmap(coverBitmap)
                    }
                    val artist = tag?.getFirst(FieldKey.ALBUM_ARTIST) ?: ""
                    if (!TextUtils.isEmpty(artist)) {
                        binding.songName.text = String.format("%s%s", fileName, "\n$artist")
                    } else {
                        binding.songName.text = fileName
                    }
                    val pText = genSecondsText(header.trackLength)
                    binding.totalTime.text = pText
                    val info = String.format(
                        Locale.US,
                        "%.1f kHz | %d bits | %s kbps",
                        header.sampleRateAsNumber / 1000f,
                        header.bitsPerSample,
                        header.bitRate
                    )
                    binding.trackInfo.text = info
                }
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri: Uri = localPath.toUri()
                    retriever.setDataSource(context, uri)

                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val artist =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val duration =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0
                    val coverBytes = retriever.embeddedPicture
                    val cover = if(coverBytes != null){
                        BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                    }else{
                        BitmapUtils.createDefaultCover(512,512) // 文件中没有封面数据
                    }
                    val mimeType =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    val bitrate =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                            ?.toIntOrNull() ?: 0
                    val bitPerSample =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                            ?.toIntOrNull()
                    // 采样率和位深度需要额外解析
                    cover?.let { coverBitmap ->
                        updateBackground(coverBitmap)
                    }
                    withContext(Dispatchers.Main) {
                        binding.songName.text = title
                        val pText = genProgressText(duration.toInt())
                        binding.totalTime.text = pText
                        cover?.let { coverBitmap ->
                            binding.cover.setImageBitmap(coverBitmap)
                        }
                        val info = String.format(
                            Locale.US,
                            "%s|%s|%.0fkbps",
                            album,
                            artist,
                            bitrate / 1000f
                        )
                        binding.trackInfo.text = info
                    }
                } finally {
                    retriever.release()
                }
            }
        }
    }

    fun extractAudioMetadata(uri: MediaDataSource,fileName:String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(uri)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val duration =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                val coverBytes = retriever.embeddedPicture
                val cover = if(coverBytes != null){
                    BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                }else{
                    BitmapUtils.createDefaultCover(512,512) // 文件中没有封面数据
                }
                val mimeType =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull() ?: 0
                val bitPerSample =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                        ?.toIntOrNull()


                cover?.let { coverBitmap ->
                    updateBackground(coverBitmap)
                }
                withContext(Dispatchers.Main) {
                    binding.songName.text = fileName.substringBeforeLast(".")
                    cover?.let { coverBitmap ->
                        binding.cover.setImageBitmap(coverBitmap)
                    }
                    val info =
                        String.format(Locale.US, "%s|%s|%.0fkbps", album, artist, bitrate / 1000f)
                    binding.trackInfo.text = info
                }
            } finally {
                uri.close()
                retriever.release()
            }
        }
    }

    private fun loadFileAndPlay() {
        if (fileInfo != null) {
            if (fileInfo?.fileType == 0) {
                val fileUri = fileInfo!!.path.toUri()
                service?.loadUri(fileUri)
                extractAudioMetadata(this, fileInfo!!.path)
            } else {
                val webResFile = WebResourceFile(
                    fileInfo!!.path,
                    fileInfo?.credentialUser ?: "", fileInfo?.credentialPass ?: ""
                )
                lifecycleScope.launch(Dispatchers.IO) {
                    service?.loadWebResource(webResFile)

                    val fileName = fileInfo?.fileName ?: ""
//                    val coverBitmap = BitmapUtils.createDefaultCoverMusicDark(512,512)
                    val coverBitmap = BitmapFactory.decodeResource(resources,R.mipmap.play_empty_holder)
                    updateBackground(coverBitmap)

                    withContext(Dispatchers.Main) {
                        binding.songName.text = fileName.substringBeforeLast(".")
                        coverBitmap?.let {
                            binding.cover.setImageBitmap(coverBitmap)
                        }
                        binding.trackInfo.text = ""
                    }
                }


                if (fileInfo!!.fileType == 2) {
                    if (fileInfo!!.path.startsWith("smb")) {
                        val smbUrl = fileInfo!!.path
                        try {
                            val username = fileInfo?.credentialUser ?: ""
                            val context: CIFSContext = if (!TextUtils.isEmpty(username)) {
                                // 如果域为空，可以传入空字符串
                                val auth = NtlmPasswordAuthenticator(
                                    null,
                                    username, fileInfo?.credentialPass ?: "",
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
                            val mCurrentDataSource = BufferedSMBDataSource2(
                                smbRaf1, smbRaf2,
                                smbFile.length()
                            )
                            extractAudioMetadata(mCurrentDataSource,fileInfo?.fileName ?: "")
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        getIntentData()
    }

//    在用于接收分享的Activity里面加接收代码
//    当APP进程在后台时，会调用Activity的onNewIntent方法
//    当APP进程被杀死时，会调用onCreate方法
//    所以在两个方法中都需要监听事件

    private fun getIntentData() {
        val intent = intent
        var uri = intent.data
        /*
         * API > 16 时，有些系统的文件夹的文件分享，内容会放在 ClipData 中，而不是放在 mData 中
         */
        if (uri == null && intent.clipData != null) {
            val item = intent.clipData!!.getItemAt(0)
            if (item != null) {
                uri = item.uri
            }
        }
        //从其他app跳入逻辑
        if (uri != null) {
            val path = ContentUriUtil.getPath(this, uri)
//            playerFragment?.loadUri(uri)
            if (!TextUtils.isEmpty(path)) {
                val fileName = File(path).name
                if (fileInfo != null) {
                    binding.songName.text = fileName
                }
            }

//            Toast.makeText(this, "获得path:$path", Toast.LENGTH_SHORT).show()
        } else {
//            Toast.makeText(this, "外部传入的uri为null", Toast.LENGTH_SHORT).show()
            fileInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("fileItem", FileItem::class.java)
            } else {
                intent.getSerializableExtra("fileItem") as? FileItem
            }
        }
    }


    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
        glSurfaceView?.requestRender()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
//        if(hasFocus){
//            ImmersiveStatusBarUtils.setFullScreen(this,true)
//        }
    }

    private fun formatMills(mills: Int): String {
        val s = mills / 1000
        val m = s / 60
        val sec = s % 60
        if (m > 99 * 60) {
            return String.format(Locale.US, "%03d:%02d", m, sec)
        } else {
            return String.format(Locale.US, "%02d:%02d", m, sec)
        }
    }

    private fun genSecondsText(sec: Int): String {
        val m = sec / 60
        val sec = sec % 60
        if (m > 99 * 60) {
            return String.format(Locale.US, "%03d:%02d", m, sec)
        } else {
            return String.format(Locale.US, "%02d:%02d", m, sec)
        }
    }

    private fun genProgressText(p: Int): String {
        val pText = formatMills(p)
        return String.format("%s", pText)
    }

    override fun bufferingStart() {

    }

    override fun bufferingStop() {

    }

    override fun bufferingProgress(progress: Int) {

    }

    override fun playStarted(progress: Int, duration: Int) {
        val pText = genProgressText(duration)
        val startTv = genProgressText(progress)
        runOnUiThread {
            binding.curTime.text = startTv
            binding.totalTime.text = pText
            binding.playProgress.max = duration
            binding.playProgress.progress = progress
            binding.startBtn.setImageResource(R.drawable.ic_pause)
        }
    }

    override fun playPaused() {
        binding.startBtn.setImageResource(com.jhkj.gl_player.R.drawable.baseline_play_arrow_24)
    }

    override fun playStopped() {
        binding.startBtn.setImageResource(com.jhkj.gl_player.R.drawable.baseline_play_arrow_24)
    }

    override fun playFinished() {
        binding.startBtn.setImageResource(com.jhkj.gl_player.R.drawable.baseline_play_arrow_24)
    }

    override fun playOnGoing(progress: Int, duration: Int) {
        runOnUiThread {
            val pText = genProgressText(progress)
            binding.curTime.text = pText
            binding.playProgress.progress = progress
            binding.startBtn.setImageResource(R.drawable.ic_pause)
        }
    }

    override fun playError() {
        binding.startBtn.setImageResource(com.jhkj.gl_player.R.drawable.baseline_play_arrow_24)
    }


    // 服务回调
    @SuppressLint("MissingPermission")
    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        val myBinder = binder as? MusicPlayService.MusicBinder
        service = myBinder?.getService()
        service?.setWeakRef(WeakReference(this))
        loadFileAndPlay()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }


    override fun onDestroy() {
        // 清理资源
        glSurfaceView!!.queueEvent(Runnable {
            if (renderer != null && renderer?.bgRender != null) {
                renderer?.bgRender?.releaseAll()
            }
        })
        glSurfaceView?.onPause()

        unbindService(this)
        // 停止前台服务
        MusicPlayService.stopService(this)
        // 更新UI
        isServiceRunning = false
        super.onDestroy()
    }
}