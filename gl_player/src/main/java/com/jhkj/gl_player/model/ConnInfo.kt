package com.jhkj.gl_player.model

import java.io.Serializable

data class WebdavResource(
    val displayName:String,
    val domain:String,
    val path:String?,
    val protocol:String,
    val port:Int,
    val username:String,
    val pass:String
): Serializable