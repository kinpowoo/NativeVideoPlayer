package com.jhkj.videoplayer.utils.file_recursive

import java.io.Serializable

data class FileItem(
    var isDirectory: Boolean,
    var fileName:String,
    var nameWithoutExtension:String,
    var mimeType:String,
    var modifyTime:Long,
    var createTime:Long,
    var size:Long,
    var childCount:Int,
    var thumbnail:String,
    var path:String,
    var parentPath:String,
    var isHide: Boolean,
    var isWritable: Boolean,
    var isReadable: Boolean,
    var credentialUser:String?,
    var credentialPass:String?,
) : Serializable
