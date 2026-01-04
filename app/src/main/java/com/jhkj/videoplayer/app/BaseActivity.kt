package com.jhkj.videoplayer.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Calendar

open class BaseActivity : AppCompatActivity() {
    var handler:UIHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用窗口内容过渡
//        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)

        handler = UIHandler(this)
    }

    override fun onDestroy() {
        handler?.removeCallbacksAndMessages(null)
        handler = null
        super.onDestroy()
    }

    open fun isDoubleClick(v: View): Boolean {
        val tag = v.getTag(v.id)
        val beforeTimeMiles = if (tag != null) tag as Long else 0
        val timeInMillis = Calendar.getInstance().timeInMillis
        v.setTag(v.id, timeInMillis)
        return timeInMillis - beforeTimeMiles < 800
    }

    class UIHandler(activity: BaseActivity) : Handler(Looper.getMainLooper()) {
        val activityRef = WeakReference(activity)

        override fun handleMessage(msg: Message) {
//            val activity = activityRef.get()
//            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
//                activity.handleMsg(msg)
//            }
        }

        fun runOnUi(task: () -> Unit) {
            activityRef.get()?.lifecycleScope?.launch {
                task()
            }
        }

        fun runOnUiDelay(task: () -> Unit, delay: Long) {
            val activity = activityRef.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                postDelayed({ task() }, delay)
            }
        }
    }

}