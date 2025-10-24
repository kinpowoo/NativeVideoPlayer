package com.jhkj.videoplayer.compose_pages.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object ConnConfig {

    /**
     * 参数-name
     */
    const val PARAMS_NAME = "ConnDto"

    /**
     * 参数-age
     */
    const val PARAMS_FLAG = "isEdit"
}


@Parcelize
data class ConnInfo(
    val displayName:String,
    val domain:String,
    val path:String?,
    val protocol:String,
    val port:Int,
    val username:String,
    val pass:String,
    val connType:Int
):Parcelable