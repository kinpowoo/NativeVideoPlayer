package com.jhkj.gl_player.util

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import java.lang.reflect.Method


/**
 * @ClassName: StatusBarTool
 * @Description: .
 * @Author: JJ
 * @CreateDate: 2021/11/11 12:41
 */
object StatusBarTool {


    //安卓10 以下 切换状态栏文字颜色
    fun dialogToggleStatusBarTextColor(dialog:Dialog,resources:Resources){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
//            val uiMode: Int = resources.configuration.uiMode
//            val isDarkMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK
//                    == Configuration.UI_MODE_NIGHT_YES)
            val isDarkMode = false
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (isDarkMode) {
                dialog.window?.decorView?.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE)
            } else {
                dialog.window?.decorView?.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            }
        }
    }


    /**
     * 通过style设置状态栏透明后，布局会延伸到状态栏，为了保留状态栏原本的位置
     * 通过设置fitsSystemWindow，在顶部预留出状态栏高度的padding。
     * @param activity  activity（在baseActivity中无效）
     * @param value 是否设置fitsSystemWindow
     */
    fun setFitsSystemWindow(activity: Activity, value: Boolean) {
        //拿到整个布局
        val contentFrameLayout = activity.findViewById<ViewGroup>(android.R.id.content)
        val parentView = contentFrameLayout.getChildAt(0)
        if (parentView != null) {
            parentView.fitsSystemWindows = value
        }
    }


    /**
     * 其中各个属性的作用：
    View.SYSTEM_UI_FLAG_FULLSCREEN：Activity全屏显示，且状态栏被隐藏覆盖掉。
    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN：Activity全屏显示，但状态栏不会被隐藏覆盖，状态栏依然可见，
    Activity顶端布局部分会被状态栏遮住。
    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION：效果同View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION：隐藏虚拟按键(导航栏)。有些手机会用虚拟按键来代替物理按键。
    View.SYSTEM_UI_FLAG_LAYOUT_STABLE：维持布局稳定。
    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY：配合着 SYSTEM_UI_FLAG_HIDE_NAVIGATION 和
    SYSTEM_UI_FLAG_FULLSCREEN 使用, 如果没有此项，那么会导致点击屏幕之后状态栏和导航栏弹出。
     */
    //将页面设为全屏
    fun fullScreen(activity: Activity,
                   keepActionBar:Boolean,isStatusTranslucent:Boolean){
        //允许设置status bar background 的颜色
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        val statusBarTextFlags = View.SYSTEM_UI_FLAG_VISIBLE //恢复状态栏白色字体

        //注释掉的是将底部虚拟键盘隐藏的代码，如果要在有
        var uiOptions = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
//                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
//                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        statusBarTextFlags or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
//        val virtualNavBarHeight = getNavigationBarHeight(activity)
        val hasVirtualKey = checkDeviceHasNavigationBar(activity)
        if(hasVirtualKey){
            //如果有虚拟导航键，需要加入隐藏虚拟导航栏标识
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
        activity.window.decorView.systemUiVisibility = uiOptions
        if(!keepActionBar) {  //如果不保留actionBar,直接隐藏它
            activity.actionBar?.hide()
        }
        if(isStatusTranslucent){
            activity.window.statusBarColor = Color.TRANSPARENT
//            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    fun exitFullScreen(activity: Activity,originStatusBarColor:Int){
        //允许设置status bar background 的颜色
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        val statusBarTextFlags = View.SYSTEM_UI_FLAG_VISIBLE //恢复状态栏白色字体
        //注释掉的是将底部虚拟键盘隐藏的代码，如果要在有
        val uiOptions = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
//                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
//                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        statusBarTextFlags or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        activity.window.decorView.systemUiVisibility = uiOptions
        activity.actionBar?.show()
        activity.window.statusBarColor = originStatusBarColor
    }


    fun fullScreenWhite(window:Window,activity: AppCompatActivity,
                   keepActionBar:Boolean,isStatusTranslucent:Boolean){
        //允许设置status bar background 的颜色
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        val statusBarTextFlags = View.SYSTEM_UI_FLAG_VISIBLE //恢复状态栏白色字体
        //注释掉的是将底部虚拟键盘隐藏的代码，如果要在有
        val uiOptions = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        statusBarTextFlags or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        window.decorView.systemUiVisibility = uiOptions
        if(!keepActionBar) {  //如果不保留actionBar,直接隐藏它
            activity.supportActionBar?.hide()
            activity.actionBar?.hide()
        }
        if(isStatusTranslucent){
            window.statusBarColor = Color.TRANSPARENT
//            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun checkDeviceHasNavigationBar(activity: Activity): Boolean {
        //通过判断设备是否有返回键、菜单键(不是虚拟键,是手机屏幕外的按键)来确定是否有navigation bar
        val hasMenuKey = ViewConfiguration.get(activity).hasPermanentMenuKey()
        val hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
        return !hasMenuKey && !hasBackKey
        // 有虚拟按键返回 false
    }

    fun setKeyboard(window:Window){
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        val isDarkMode = false
        val statusBarTextFlags = if (isDarkMode) {
            View.SYSTEM_UI_FLAG_VISIBLE //恢复状态栏白色字体
        } else {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR //设置状态栏黑色字体
        }
        val uiOptions = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                statusBarTextFlags or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        window.decorView.systemUiVisibility = uiOptions
    }

    fun setStatusColor(decorView: View?,resources: Resources?) {
        //安卓4.4
        /**
         * 1.View.SYSTEM_UI_FLAG_VISIBLE ：状态栏和Activity共存，Activity不全屏显示。
        也就是应用平常的显示画面
        2. View.SYSTEM_UI_FLAG_FULLSCREEN ：Activity 全屏显示，且状态栏被覆盖掉
        3. View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN ：Activity全屏显示，但是状态栏不会被覆盖掉
        而是正常显示，只是Activity顶端布局会被覆盖住
        4.View.INVISIBLE ： Activity全屏显示，隐藏状态栏
         */
        if(decorView == null)return
        //深色模式的值为:0x21
        //浅色模式的值为:0x11
//        val uiMode = resources.configuration.uiMode
//        val isDarkMode =
//            uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
//
        val isDarkMode = false
        val flag = if (isDarkMode) {
            View.SYSTEM_UI_FLAG_VISIBLE //恢复状态栏白色字体
        } else {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR //设置状态栏黑色字体
        }
        val uiOptions =  (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                flag or
//                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        decorView.systemUiVisibility = uiOptions
    }


    //获取虚拟按键的高度
    fun getNavigationBarHeight(context: Context):Int{
        var result = 0
        if (hasNavBar(context)) {
            val res:Resources = context.resources
            val resourceId = res.getIdentifier("navigation_bar_height",
                "dimen", "android")
            if (resourceId > 0) {
                result = res.getDimensionPixelSize(resourceId)
            }
        }
        return result
    }

    /**
     * 检查是否存在虚拟按键栏
     *
     * @return
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    fun hasNavBar(context:Context):Boolean{
        val res:Resources = context.resources
        val resourceId = res.getIdentifier("config_showNavigationBar",
            "bool", "android")
        if (resourceId != 0) {
            var hasNav = res.getBoolean(resourceId)
            // check override flag
            val sNavBarOverride = getNavBarOverride()
            if ("1" == sNavBarOverride) {
                hasNav = false
            } else if ("0" == sNavBarOverride) {
                hasNav = true
            }
            return hasNav
        } else { // fallback
            return !ViewConfiguration.get(context).hasPermanentMenuKey()
        }
    }
    /**
     * 判断虚拟按键栏是否重写
     *
     */
    @SuppressLint("PrivateApi")
    private fun getNavBarOverride():String{
        var sNavBarOverride = ""
        try {
            val c:Class<*> = Class.forName("android.os.SystemProperties")
            val m: Method = c.getDeclaredMethod("get", String::class.java)
            m.isAccessible = true
            sNavBarOverride =  m.invoke(null, "qemu.hw.mainkeys") as? String ?: ""
        } catch (e:Throwable) {
            sNavBarOverride = ""
        }
        return sNavBarOverride
    }

    //获得状态栏高度
    fun getStatusBarHeight(mActivity: Activity): Int {
        val resources = mActivity.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

    fun getToolbarHeight(mActivity: Activity):Int{
        val tv = TypedValue()
        if (mActivity.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            return TypedValue.complexToDimensionPixelSize(
                tv.data,
                mActivity.resources.displayMetrics
            )
        }
        return 0
    }

    fun setToolbarMargin(context: Activity,toolbar: Toolbar){
        val layoutParams = toolbar.layoutParams as? ConstraintLayout.LayoutParams
        if(layoutParams == null){
            val a = toolbar.layoutParams as? LinearLayout.LayoutParams
            if(a == null){
                val b = toolbar.layoutParams as? FrameLayout.LayoutParams
                if(b == null){
                    val c = toolbar.layoutParams as? CoordinatorLayout.LayoutParams
                    if(c == null){
                        val d = toolbar.layoutParams as? RelativeLayout.LayoutParams
                        d?.topMargin = getStatusBarHeight(context)
                    }else{
                        c.topMargin = getStatusBarHeight(context)
                    }
                }else{
                    b.topMargin = getStatusBarHeight(context)
                }
            }else{
                a.topMargin = getStatusBarHeight(context)
            }
        }else{
            layoutParams.topMargin = getStatusBarHeight(context)
        }
    }

}