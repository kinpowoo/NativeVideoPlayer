package com.jhkj.videoplayer.utils

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.jhkj.videoplayer.app.MainApplication

object Res {
    @JvmStatic
    fun string(@StringRes resId:Int):String{
        return MainApplication.get().resources.getString(resId)
    }

    @JvmStatic
    fun stringArray(@ArrayRes resId:Int):Array<String>{
        return MainApplication.get().resources.getStringArray(resId)
    }
}