package com.jhkj.gl_player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.jhkj.gl_player.fragment.PlayerBaseFragment
import com.jhkj.gl_player.util.DensityUtil
import com.jhkj.gl_player.util.ImmersiveStatusBarUtils
import com.jhkj.gl_player.util.StatusBarTool
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs


class PlayerFragment : PlayerBaseFragment(),View.OnTouchListener {
    private var glPlayer:PlayerGLSurface? = null  //播放控件
    private var controlPanel:ConstraintLayout? = null  //控制面板
    private var toolbarBox:ConstraintLayout? = null

    //快进快退
    private var forwardBox:ConstraintLayout? = null
    private var forwardIcon:ImageView? = null
    private var targetTime:TextView? = null

    //亮度和音量
    private var brightnessBox:ConstraintLayout? = null
    private var brightnessIcon:ImageView? = null
    private var brightnessText:TextView? = null
    private var brightnessSlider:SeekBar? = null

    private var seekBar:SeekBar? = null
    private var backBtn:ImageView? = null
    private var playBtn:ImageView? = null
    private var muteBtn:ImageView? = null
    private var scaleBtn:ImageView? = null
    private var progressText:TextView? = null
    private var progressBar:ProgressBar? = null
    private var oldW = 0
    private var oldH = 0
    private var isFullScreen = false
    private var parentContainerId:Int = 0
    private var originStatusBarColor = Color.WHITE  //原始的状态栏颜色
    private var statusBarHeight = 0  //导航栏高度
    private var lastShowControlPanelTime = 0L
    private var isControlPanelVisible = true
    private var vw:Int = 0
    private var vh:Int = 0
    private var sw:Int = 0
    private var sh:Int = 0
    private var audioManager:AudioManager? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        parentContainerId = container?.id ?: 0
        return inflater.inflate(R.layout.player_fragment_layout, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dp240 = DensityUtil.dip2px(requireContext(), 240f).toInt()
        setVideoViewScale(ViewGroup.LayoutParams.MATCH_PARENT, dp240)

        activity?.let {
            // 1. 设置沉浸式状态栏
//            ImmersiveStatusBarUtils.setImmersiveStatusBar(it)
//            // 2. 设置状态栏文字颜色为深色（适合浅色背景）
//            ImmersiveStatusBarUtils.setStatusBarTextColor(it, false)
//            // 3. 设置状态栏背景颜色
//            ImmersiveStatusBarUtils.setStatusBarColor(it, Color.RED)
//            // 4. 隐藏状态栏
//            ImmersiveStatusBarUtils.showOrHideStatusBar(it, false)
        }

        originStatusBarColor = requireActivity().window.statusBarColor
        statusBarHeight = StatusBarTool.getStatusBarHeight(requireActivity())
        sw = DensityUtil.getScreenWidth(requireContext())
        sh = DensityUtil.getScreenHeight(requireContext())

        vw = sw
        vh = dp240
        initView(view)
        setListener(view)
    }

    private fun initView(view:View){
        glPlayer = view.findViewById(R.id.gl_player)
        controlPanel = view.findViewById(R.id.control_bar)
        toolbarBox = view.findViewById(R.id.toolbar_box)
        forwardBox = view.findViewById(R.id.forward_box)
        forwardIcon = view.findViewById(R.id.direction_icon)
        targetTime = view.findViewById(R.id.target_time)
        brightnessBox = view.findViewById(R.id.brightness_box)
        brightnessIcon = view.findViewById(R.id.brightness_icon)
        brightnessText = view.findViewById(R.id.brightness_val)
        brightnessSlider = view.findViewById(R.id.brightness_slider)
        brightnessSlider?.isEnabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            brightnessSlider?.focusable = View.NOT_FOCUSABLE
        }
        brightnessBox?.isClickable = false
        brightnessBox?.isClickable = false


        seekBar = view.findViewById(R.id.seek_bar)
        backBtn = view.findViewById(R.id.back_btn)
        playBtn = view.findViewById(R.id.start_btn)
        muteBtn = view.findViewById(R.id.voice_btn)
        scaleBtn = view.findViewById(R.id.scale_btn)
        progressText = view.findViewById(R.id.time)
        progressBar = view.findViewById(R.id.progress_bar)
        oldW = view.layoutParams.width
        oldH = view.layoutParams.height
    }

    private fun setListener(view:View){
        view.setOnTouchListener(this)
        glPlayer?.setPlayStateListener(object:PlayStateListener{
            override fun playFinished() {
                activity?.runOnUiThread {
                    visibleControlPanel()
                    playBtn?.setImageResource(R.drawable.baseline_replay_24)
                }
            }
            override fun playPaused() {
                activity?.runOnUiThread {
                    visibleControlPanel()
                    playBtn?.setImageResource(R.drawable.baseline_play_arrow_24)
                }
            }
            override fun playStarted(progress:Int,duration:Int) {
                val pText = genProgressText(progress,duration)
                activity?.runOnUiThread {
                    visibleControlPanel()
                    progressText?.text = pText
                    val percent = ((progress*1f/duration)*100).toInt()
                    seekBar?.progress = percent
                    progressBar?.progress = percent
                    playBtn?.setImageResource(R.drawable.baseline_pause_24)
                }
            }
            override fun playStopped() {
                activity?.runOnUiThread {
                    playBtn?.setImageResource(R.drawable.baseline_play_arrow_24)
                }
            }

            override fun playOnGoing(progress: Int, duration: Int) {
                val nowTime = SystemClock.elapsedRealtime()
                activity?.runOnUiThread {
                    if(isControlPanelVisible && (nowTime - lastShowControlPanelTime > 3000L)){  //播放三秒后，隐藏控制面板
                        dismissControlPanel()
                    }
                    val pText = genProgressText(progress, duration)
                    progressText?.text = pText
                    val percent = ((progress * 1f / duration) * 100).toInt()
                    seekBar?.progress = percent
                    progressBar?.progress = percent
                }
            }

            override fun playError() {

            }
        })
        glPlayer?.setBufferingListener(object:BufferingListener{
            override fun bufferingProgress(progress: Int) {
                seekBar?.secondaryProgress = progress
            }

            override fun bufferingStart() {

            }

            override fun bufferingStop() {

            }
        })
        glPlayer?.setVolumeStateListener(object:VolumeStateListener{
            override fun volumeMuted() {
                muteBtn?.setImageResource(R.drawable.baseline_volume_off_24)
            }

            override fun volumeResumed() {
                muteBtn?.setImageResource(R.drawable.baseline_volume_up_24)
            }
        })

        seekBar?.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                glPlayer?.pausePlay()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                val percent = progress / 100f
                glPlayer?.seekTo(percent)
            }
        })

        loadUrl("https://stream7.iqilu.com/10339/upload_transcode/202002/18/20200218114723HDu3hhxqIT.mp4")
        playBtn?.setOnClickListener {
            lastShowControlPanelTime = SystemClock.elapsedRealtime()
            glPlayer?.switchPlayState()
        }
        muteBtn?.setOnClickListener {
            lastShowControlPanelTime = SystemClock.elapsedRealtime()
            glPlayer?.switchVolume()
        }
        scaleBtn?.setOnClickListener {
            lastShowControlPanelTime = SystemClock.elapsedRealtime()
            if(isDoubleClick(it))return@setOnClickListener
            if(isFullScreen){
                isFullScreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED  //切换竖屏
                StatusBarTool.exitFullScreen(requireActivity(),originStatusBarColor)
                adjustToolbar()
                toolbarBox?.visibility = View.GONE
            }
            else{
                isFullScreen = true
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE  //切换横屏
                StatusBarTool.fullScreen(requireActivity(),false,true)
                adjustToolbar()
                toolbarBox?.visibility = View.VISIBLE
            }
        }
        backBtn?.setOnClickListener {
            if(isDoubleClick(it))return@setOnClickListener
            if(isFullScreen){
                isFullScreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED  //切换竖屏

                StatusBarTool.exitFullScreen(requireActivity(),originStatusBarColor)
                adjustToolbar()
                toolbarBox?.visibility = View.GONE
            }
        }
    }

    fun loadUrl(url:String){
        glPlayer?.loadUrl(url)
    }
    fun loadUri(uri: Uri){
        glPlayer?.loadUri(uri)
    }

    private fun visibleControlPanel(){
        isControlPanelVisible = true
        lastShowControlPanelTime = SystemClock.elapsedRealtime()
        controlPanel?.visibility = View.VISIBLE
        progressBar?.visibility = View.GONE
    }

    private fun dismissControlPanel(){
        controlPanel?.visibility = View.GONE
        isControlPanelVisible = false
        progressBar?.visibility = View.VISIBLE
    }

    override fun onInvisible() {
        super.onInvisible()
        glPlayer?.pausePlay()
    }

    private fun replaceFragmentContainer(containerId:Int){
        val manager = parentFragmentManager
        manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val copyInstance = recreateFragment(this)
        manager.beginTransaction().remove(this).commit()
        manager.executePendingTransactions()
        manager.beginTransaction()
            .add(containerId, copyInstance, "PlayerFragment")
            .commit()
    }

    private fun recreateFragment(f: Fragment): Fragment {
        val manager = parentFragmentManager
        return try {
            val savedState: SavedState? = manager.saveFragmentInstanceState(f)
            val newInstance: Fragment = f.javaClass.newInstance()
            newInstance.setInitialSavedState(savedState)
            newInstance
        } catch (e: Exception) { // InstantiationException, IllegalAccessException
            throw RuntimeException("Cannot reinstantiate fragment " + f.javaClass.name, e)
        }
    }

    private fun formatMills(mills:Int):String{
        val s = mills / 1000
        val m = s/60
        val sec = s % 60
        if( m > 99*60){
            return String.format(Locale.US,"%03d:%02d",m,sec)
        }else{
            return String.format(Locale.US,"%02d:%02d",m,sec)
        }
    }
    private fun genProgressText(p:Int,d:Int):String{
        val pText = formatMills(p)
        val dText = formatMills(d)
        return String.format("%s / %s",pText,dText)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) { //当屏幕方向为横屏的时候
            setVideoViewScale(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            setVideoViewScale(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DensityUtil.dip2px(requireContext(), 240f).toInt()
            )
        }
    }

    private fun setVideoViewScale(width: Int, height: Int) {
        val layoutParams: ViewGroup.LayoutParams? = view?.layoutParams
        layoutParams?.width = width
        layoutParams?.height = height
        view?.layoutParams = layoutParams
    }

    private fun adjustToolbar(){
//        val layoutParams: ConstraintLayout.LayoutParams? = toolbarBox?.layoutParams as? ConstraintLayout.LayoutParams
//        if(isFullScreen){
//            layoutParams?.topMargin = statusBarHeight
//        }else{
//            layoutParams?.topMargin = 0
//        }
//        toolbarBox?.layoutParams = layoutParams


        // 5. 设置全屏模式
        activity?.let {
//            ImmersiveStatusBarUtils.setFullScreen(it, isFullScreen)
        }

    }


    private var first: Long = 0 //第一次点击的时间
    private var second: Long = 0 //第二次点击的时间
    private var lastMoveX = 0f
    private var lastMoveY = 0f //当前多边形整体移动的距离
    private var lastOriginX = -1
    private var lastOriginY = -1
    private val NONE = 0
    private val DRAG = 1
    private val ZOOM = 2
    private var mode = NONE
    private var x_down = 0f
    private var y_down = 0f
    private var isLeftSide = false
    private var dx = 0f
    private var dy = 0f
    private var actionMode:ActionMode = ActionMode.NONE  //当前的意图操作，0为调整亮度，1为调整音量，2为快进，3为快退
    private var screenAlpha = 1f
    private var currentVolume = 1
    private var maxVolume = 100
    private var curPlayPos = 0
    private var afterPlayPos = 0
    private var videoDuration = 0

    enum class ActionMode(val p:Int){
        NONE(-1),
        AdjustBrightness(0),
        AdjustVolume(1),
        FastForward(2),
        FastBackward(3)
    }
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if(event == null)return false
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mode = DRAG
                lastOriginX = -1
                lastOriginY = -1
                x_down = event.x
                y_down = event.y
                lastMoveX = x_down
                lastMoveY = y_down
                actionMode = ActionMode.NONE
                dx = 0f
                dy = 0f
                curPlayPos = glPlayer?.getCurrentPlayPos() ?: 0
                afterPlayPos = curPlayPos
                videoDuration = glPlayer?.getDuration() ?: 0
                if(audioManager == null){
                    audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                }
                screenAlpha = abs(activity?.window?.attributes?.screenBrightness ?:1f)
                maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 100
                currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                if(isFullScreen){
                    isLeftSide = x_down <= vw/2
                }else{
                    isLeftSide = x_down <= sw/2
                }
                if(isLeftSide){
                    brightnessIcon?.setImageResource(R.drawable.baseline_brightness_4_24)
                    updateBrightnessOrVolume(1,screenAlpha)
                }else{
                    brightnessIcon?.setImageResource(R.drawable.baseline_volume_up_24)
                    val newVolumePercent = currentVolume * 1f / maxVolume
                    updateBrightnessOrVolume(2,newVolumePercent)
                }
                if (first == 0L) {
                    first = System.currentTimeMillis()
                } else {
                    second = System.currentTimeMillis()
                }
                Log.i("CLICK","click first:$first, second:$second")
                Log.i("CLICK","click interval:${first - second}")
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = ZOOM
                lastMoveX = -1f
                lastMoveY = -1f
                lastOriginX = -1
                lastOriginY = -1
            }
            MotionEvent.ACTION_MOVE ->{
                if (mode == ZOOM) {

                } else if (mode == DRAG) {
                    lastMoveX = event.x
                    lastMoveY = event.y
                    dx = lastMoveX - x_down
                    dy = lastMoveY - y_down
                    if(abs(dx) > abs(dy) && abs(dx) > 5) {  // x 轴称动距离超过10，响应它
                        if(actionMode == ActionMode.NONE) {
                            if (dx > 3) {
                                actionMode = ActionMode.FastForward
                                forwardBox?.visibility = View.VISIBLE
                            }
                            if (dx < -3) {
                                actionMode = ActionMode.FastBackward
                                forwardBox?.visibility = View.VISIBLE
                            }
                        }
                        handleTouchEvent()
                    }else if(abs(dy) > abs(dx) && abs(dy) > 5){ // y 轴称动距离超过10，响应它
                        if(actionMode == ActionMode.NONE) {
                            if (isLeftSide) {
                                actionMode = ActionMode.AdjustBrightness
                                brightnessBox?.visibility = View.VISIBLE
                            } else {
                                actionMode = ActionMode.AdjustVolume
                                brightnessBox?.visibility = View.VISIBLE
                            }
                        }
                        handleTouchEvent()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                actionMode = ActionMode.NONE
            }
            MotionEvent.ACTION_UP -> {
                if(actionMode == ActionMode.FastForward ||
                    actionMode == ActionMode.FastBackward){
                    if(videoDuration > 0) {
                        val newPercent = afterPlayPos * 1f / videoDuration
                        glPlayer?.seekTo(newPercent)
                    }
                }

                //mode == DRAG && isPolygonMoved &&
                //如果两次点击事件差小于100ms,则为双击事件
                if ((second - first) in 1..300) {
                    //双击事件回调
                    first = 0
                    second = 0
                    cancelTimeTask()
                    doubleClickHandle()
                } else { //如果小于0则为单击事件
                    //三星手机太过灵敏，每次单击事件都会触发 Action_Move，
                    // 导致点击位置与抬起位置有少许偏差，在这里容错
                    val endDownX = event.x
                    val endDownY = event.y
                    if (Math.abs(x_down - endDownX) < 5 &&
                        Math.abs(y_down - endDownY) < 5) {   //如果没有触发滑动事件，就是单击事件
                        startSingleTimeTask()
                    }
                }
                dismissAllControlWindow()
                actionMode = ActionMode.NONE
                afterPlayPos = 0
                mode = NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                //隐藏所有当前操作的
            }
        }
        return true
    }

    private class SingleClickTask(private val o:WeakReference<PlayerFragment>):TimerTask(){
        override fun run() {
            o.get()?.singleClickHandle()
        }
    }
    private var timer: Timer? = null
    private var timerTask:SingleClickTask? = null
    private fun startSingleTimeTask(){
        cancelTimeTask()
        timer = Timer()
        timerTask = SingleClickTask(WeakReference(this))
        timer?.schedule(timerTask,310)
    }

    private fun cancelTimeTask(){
        timerTask?.cancel()
        timerTask = null
        timer?.cancel()
        timer = null
    }

    private fun handleTouchEvent(){
        when(actionMode){
            ActionMode.AdjustBrightness ->{
                val adjustPercent:Float
                if(isFullScreen){
                    adjustPercent = dy / sw
                }else{
                    adjustPercent = dy / vh
                }
                var newBrightness = screenAlpha - adjustPercent
                if(newBrightness > 1f){
                    newBrightness = 1f
                }
                if(newBrightness < 0f){
                    newBrightness = 0f
                }
                val lp = activity?.window?.attributes
                lp?.screenBrightness = newBrightness
                activity?.window?.attributes = lp
                updateBrightnessOrVolume(1,newBrightness)
            }
            ActionMode.AdjustVolume ->{
                val adjustPercent:Float
                if(isFullScreen){
                    adjustPercent = dy / sw
                }else{
                    adjustPercent = dy / vh
                }
                val adjustValue = (adjustPercent * maxVolume).toInt()
                var afterVolume = currentVolume - adjustValue
                if(afterVolume > maxVolume){
                    afterVolume = maxVolume
                }
                if(afterVolume < 0){
                    afterVolume = 0
                }
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, afterVolume, 0)
                val newVolumePercent = afterVolume * 1f / maxVolume
                updateBrightnessOrVolume(2,newVolumePercent)
            }
            ActionMode.FastForward,ActionMode.FastBackward ->{
                if(actionMode == ActionMode.FastForward){
                    forwardIcon?.setImageResource(R.drawable.baseline_fast_forward_24)
                }else{
                    forwardIcon?.setImageResource(R.drawable.sharp_fast_rewind_24)
                }
                val adjustPercent:Float
                if(isFullScreen){
                    adjustPercent = dx / sh
                }else{
                    adjustPercent = dx / vw
                }
                val forwardValue = (adjustPercent * videoDuration).toInt()
                var afterPos = curPlayPos + forwardValue
                if(afterPos > videoDuration){
                    afterPos = videoDuration
                }
                if(afterPos < 0){
                    afterPos = 0
                }
                val pText = genProgressText(afterPos,videoDuration)
                targetTime?.text = pText
                afterPlayPos = afterPos
            }
            else->{

            }
        }
    }

    private fun updateBrightnessOrVolume(type:Int,percent:Float){
        val percentInt = (percent * 100).toInt()
        if(type == 1){ //亮度
            brightnessIcon?.setImageResource(R.drawable.baseline_brightness_4_24)
        }else{
            if(percentInt > 0) {
                brightnessIcon?.setImageResource(R.drawable.baseline_volume_up_24)
            }else{
                brightnessIcon?.setImageResource(R.drawable.baseline_volume_off_24)
            }
        }
        brightnessText?.text = String.format(Locale.US,"%d%%",percentInt)
        brightnessSlider?.progress = percentInt
    }
    private fun dismissAllControlWindow(){
        brightnessBox?.visibility = View.GONE
        forwardBox?.visibility = View.GONE
    }

    //单击手势处理
    private fun singleClickHandle(){
        first = 0
        second = 0
        activity?.runOnUiThread {
            if(!isControlPanelVisible){
                visibleControlPanel()
            }else{
                dismissControlPanel()
            }
        }
    }
    //双击手势处理
    private fun doubleClickHandle(){
        glPlayer?.pauseOrResume()
    }
}