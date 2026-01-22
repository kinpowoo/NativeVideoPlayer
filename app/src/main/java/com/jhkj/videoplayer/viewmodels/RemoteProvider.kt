package com.jhkj.videoplayer.viewmodels

import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.utils.file_recursive.FileItem

interface RemoteProvider {
    fun checkUrl(conn:ConnInfo):Boolean
    fun listFiles(conn: ConnInfo,subpath:String):List<FileItem>?
    fun deleteFile(conn: ConnInfo,subpath:String): Boolean
}