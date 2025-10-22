package com.jhkj.utils

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object ImmersiveStatusBarUtils {

    /**
     * 设置沉浸式状态栏（状态栏透明，内容延伸到状态栏）
     */
    fun setImmersiveStatusBar(activity: Activity) {
        // 设置状态栏透明
        activity.window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
        }

        // 设置内容延伸到状态栏
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    /**
     * 设置状态栏文字和图标的颜色（浅色/深色）
     * @param isLight true: 浅色文字（深色背景） false: 深色文字（浅色背景）
     */
    fun setStatusBarTextColor(activity: Activity, isLight: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val window = activity.window
            val decorView = window.decorView
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用WindowInsetsController
                val controller = decorView.windowInsetsController
                controller?.setSystemBarsAppearance(
                    if (isLight) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0-10 使用View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                var systemUiVisibility = decorView.systemUiVisibility
                systemUiVisibility = if (isLight) {
                    systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
                decorView.systemUiVisibility = systemUiVisibility
            }
        }
    }

    /**
     * 设置状态栏背景颜色
     */
    fun setStatusBarColor(activity: Activity, @ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = color
        }
    }

    /**
     * 显示或隐藏状态栏
     */
    fun showOrHideStatusBar(activity: Activity, show: Boolean) {
        val window = activity.window
        val decorView = window.decorView
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用WindowInsetsController
            val controller = decorView.windowInsetsController
            if (show) {
                controller?.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller?.hide(WindowInsetsCompat.Type.statusBars())
            }
        } else {
            // 兼容旧版本
            val controller = WindowInsetsControllerCompat(window, decorView)
            if (show) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    /**
     * 设置全屏模式（隐藏状态栏和导航栏）
     */
    fun setFullScreen(activity: Activity, fullScreen: Boolean) {
        val window = activity.window
        val decorView = window.decorView
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用WindowInsetsController
            val controller = decorView.windowInsetsController
            if (fullScreen) {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            // 兼容旧版本
            var systemUiVisibility = decorView.systemUiVisibility
            if (fullScreen) {
                systemUiVisibility = systemUiVisibility or 
                    (View.SYSTEM_UI_FLAG_FULLSCREEN or 
                     View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or 
                     View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            } else {
                systemUiVisibility = systemUiVisibility and 
                    (View.SYSTEM_UI_FLAG_FULLSCREEN or 
                     View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or 
                     View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY).inv()
            }
            decorView.systemUiVisibility = systemUiVisibility
        }
    }
}