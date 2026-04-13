package com.jhkj.videoplayer.player;

import android.content.Context
import android.media.AudioManager

/**
 * 音频音量管理工具类
 * 注意：需要添加权限 <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
 */
class AudioVolumeHelper(private val context: Context) {
    
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    companion object {
        const val STREAM_MUSIC = AudioManager.STREAM_MUSIC
        const val STREAM_RING = AudioManager.STREAM_RING
        const val STREAM_ALARM = AudioManager.STREAM_ALARM
        const val STREAM_NOTIFICATION = AudioManager.STREAM_NOTIFICATION
        const val STREAM_VOICE_CALL = AudioManager.STREAM_VOICE_CALL
        const val STREAM_SYSTEM = AudioManager.STREAM_SYSTEM
        
        const val FLAG_SHOW_UI = AudioManager.FLAG_SHOW_UI
        const val FLAG_PLAY_SOUND = AudioManager.FLAG_PLAY_SOUND
    }
    
    // region 获取音量相关方法
    
    /**
     * 获取当前音量
     * @param streamType 音频流类型，默认为STREAM_MUSIC
     * @return 当前音量值
     */
    fun getCurrentVolume(streamType: Int = STREAM_MUSIC): Int {
        return audioManager.getStreamVolume(streamType)
    }
    
    /**
     * 获取最大音量
     * @param streamType 音频流类型，默认为STREAM_MUSIC
     * @return 最大音量值
     */
    fun getMaxVolume(streamType: Int = STREAM_MUSIC): Int {
        return audioManager.getStreamMaxVolume(streamType)
    }
    
    /**
     * 获取最小音量
     * @param streamType 音频流类型，默认为STREAM_MUSIC
     * @return 最小音量值（通常是0）
     */
    fun getMinVolume(streamType: Int = STREAM_MUSIC): Int = 0
    
    /**
     * 获取当前音量百分比
     * @param streamType 音频流类型
     * @return 音量百分比（0.0-1.0）
     */
    fun getVolumePercent(streamType: Int = STREAM_MUSIC): Float {
        val current = getCurrentVolume(streamType)
        val max = getMaxVolume(streamType)
        return if (max > 0) current.toFloat() / max else 0f
    }
    
    /**
     * 判断是否静音
     * @param streamType 音频流类型
     * @return 是否静音
     */
    fun isMute(streamType: Int = STREAM_MUSIC): Boolean {
        return audioManager.getStreamVolume(streamType) == 0
    }
    
    // endregion
    
    // region 设置音量相关方法
    
    /**
     * 设置绝对音量
     * @param volume 音量值
     * @param streamType 音频流类型
     * @param flags 标志位
     */
    fun setVolume(
        volume: Int,
        streamType: Int = STREAM_MUSIC,
        flags: Int = 0
    ) {
        val maxVolume = getMaxVolume(streamType)
        val safeVolume = volume.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(streamType, safeVolume, flags)
    }
    
    /**
     * 设置音量百分比
     * @param percent 音量百分比（0.0-1.0）
     * @param streamType 音频流类型
     * @param flags 标志位
     */
    fun setVolumePercent(
        percent: Float,
        streamType: Int = STREAM_MUSIC,
        flags: Int = 0
    ) {
        val maxVolume = getMaxVolume(streamType)
        val volume = (maxVolume * percent.coerceIn(0f, 1f)).toInt()
        setVolume(volume, streamType, flags)
    }
    
    /**
     * 增加音量
     * @param increment 增加的值
     * @param streamType 音频流类型
     * @param flags 标志位
     */
    fun increaseVolume(
        increment: Int = 1,
        streamType: Int = STREAM_MUSIC,
        flags: Int = 0
    ) {
        val current = getCurrentVolume(streamType)
        val max = getMaxVolume(streamType)
        val newVolume = (current + increment).coerceAtMost(max)
        setVolume(newVolume, streamType, flags)
    }
    
    /**
     * 减少音量
     * @param decrement 减少的值
     * @param streamType 音频流类型
     * @param flags 标志位
     */
    fun decreaseVolume(
        decrement: Int = 1,
        streamType: Int = STREAM_MUSIC,
        flags: Int = 0
    ) {
        val current = getCurrentVolume(streamType)
        val newVolume = (current - decrement).coerceAtLeast(0)
        setVolume(newVolume, streamType, flags)
    }
    
    /**
     * 静音/取消静音
     * @param streamType 音频流类型
     * @param flags 标志位
     * @return 静音后的状态
     */
    fun toggleMute(
        streamType: Int = STREAM_MUSIC,
        flags: Int = 0
    ): Boolean {
        return if (isMute(streamType)) {
            // 取消静音，设置为中等音量
            val midVolume = getMaxVolume(streamType) / 2
            setVolume(midVolume, streamType, flags)
            false
        } else {
            // 静音
            setVolume(0, streamType, flags)
            true
        }
    }
    
    /**
     * 设置静音
     * @param mute 是否静音
     * @param streamType 音频流类型
     * @param flags 标志位
     */
    fun setMute(
        mute: Boolean,
        streamType: Int = STREAM_MUSIC,
        flags: Int = 0
    ) {
        if (mute) {
            setVolume(0, streamType, flags)
        } else {
            val current = getCurrentVolume(streamType)
            if (current == 0) {
                val midVolume = getMaxVolume(streamType) / 2
                setVolume(midVolume, streamType, flags)
            }
        }
    }
    
    // endregion
    
    // region 铃声模式相关方法
    
    /**
     * 获取当前铃声模式
     * @return 铃声模式
     */
    fun getRingerMode(): Int {
        return audioManager.ringerMode
    }
    
    /**
     * 获取铃声模式描述
     * @return 描述字符串
     */
    fun getRingerModeDescription(): String {
        return when (getRingerMode()) {
            AudioManager.RINGER_MODE_SILENT -> "静音模式"
            AudioManager.RINGER_MODE_VIBRATE -> "振动模式"
            AudioManager.RINGER_MODE_NORMAL -> "正常模式"
            else -> "未知模式"
        }
    }
    
    /**
     * 设置铃声模式
     * @param mode 模式常量
     */
    fun setRingerMode(mode: Int) {
        audioManager.ringerMode = mode
    }
    
    /**
     * 判断是否为振动模式
     */
    fun isVibrateMode(): Boolean {
        return audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
    }
    
    /**
     * 判断是否为静音模式
     */
    fun isSilentMode(): Boolean {
        return audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }
    
    // endregion
    
    // region 监听音量变化
    
    /**
     * 音量变化监听器接口
     */
    interface OnVolumeChangeListener {
        fun onVolumeChanged(streamType: Int, oldVolume: Int, newVolume: Int)
    }
    
    private val listeners = mutableListOf<OnVolumeChangeListener>()
    
    private val volumeChangeListener = object : AudioManager.OnAudioFocusChangeListener {
        override fun onAudioFocusChange(focusChange: Int) {
            // 这里可以扩展处理音频焦点变化
        }
    }
    
    /**
     * 注册音量变化监听
     */
    fun registerVolumeChangeListener(listener: OnVolumeChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    /**
     * 取消注册音量变化监听
     */
    fun unregisterVolumeChangeListener(listener: OnVolumeChangeListener) {
        listeners.remove(listener)
    }
    
    // 清理资源
    fun release() {
        listeners.clear()
    }
    
    // endregion
    
    // region 音频焦点管理
    
    /**
     * 请求音频焦点
     * @param durationHint 持续时间提示
     * @return 是否成功获取焦点
     */
    fun requestAudioFocus(durationHint: Int = AudioManager.AUDIOFOCUS_GAIN): Boolean {
        val result = audioManager.requestAudioFocus(
            volumeChangeListener,
            AudioManager.STREAM_MUSIC,
            durationHint
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    
    /**
     * 放弃音频焦点
     */
    fun abandonAudioFocus() {
        audioManager.abandonAudioFocus(volumeChangeListener)
    }
    
    // endregion
}