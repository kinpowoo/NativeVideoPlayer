package com.jhkj.videoplayer.utils.file_recursive

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
    var fileType:Int  // 0是本地文件，1是webdav文，2是smb文件
) : java.io.Serializable
