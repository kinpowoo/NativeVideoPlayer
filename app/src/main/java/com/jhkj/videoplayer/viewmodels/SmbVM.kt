package com.jhkj.videoplayer.viewmodels

import android.text.TextUtils
import androidx.lifecycle.ViewModel
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.third_file_framework.smb.BySMB
import com.jhkj.videoplayer.third_file_framework.smb.OnListFileCallback
import com.jhkj.videoplayer.third_file_framework.smb.OnReadFileListNameCallback
import com.jhkj.videoplayer.utils.FileMimeType
import com.jhkj.videoplayer.utils.file_recursive.FileItem
import com.thegrizzlylabs.sardineandroid.DavResource
import java.io.IOException


class SmbVM: ViewModel(), RemoteProvider{

    private fun getFullUrl(conn:ConnInfo):String{
        val protocol = conn.protocol.lowercase()
        val host = conn.domain
        val port = conn.port
        return "$protocol://$host:$port"
    }

    private var client: BySMB? = null

    fun initClient(conn: ConnInfo):BySMB?{
//        val url = getFullUrl(conn)
        client = BySMB.with()
            .setConfig(
                ip = conn.domain,         // 电脑IP地址
                username = conn.username,           // 电脑用户名
                password = conn.pass,          // 电脑密码
                folderName = (conn.path ?: "")   // 共享文件夹名称
            )
            .setReadTimeOut(5)               // 读取超时(秒)
            .setSoTimeOut(10)                // Socket超时(秒)
            .build()
        return client
    }


    private fun isDirectory(fileAttributes:Long): Boolean{
        // FILE_ATTRIBUTE_DIRECTORY 的值为 0x10
        if ((fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L) {
            println("这是一个目录")
            return true
        } else {
            println("这是一个文件")
            return false
        }
    }

    override fun checkUrl(conn: ConnInfo): Boolean {
        return false
    }

    override fun listFiles(conn:ConnInfo,subpath:String):List<FileItem>?{
        val baseUrl = getFullUrl(conn)
//        val url = baseUrl + subpath
        val fileList = client?.listShareFolderPath(subpath,null)
        val files:List<FileIdBothDirectoryInformation>? = fileList
        val items = mutableListOf<FileItem>()
        files?.forEach{
//                    if(it.fileName != subpath) {
//
//                    }
            val fileItem = convertSmbFile(baseUrl, it, conn)
            items.add(fileItem)
        }
        return items
    }

    override fun deleteFile(conn: ConnInfo, subpath: String): Boolean {
        val baseUrl = getFullUrl(conn)
        val url = baseUrl + subpath
        return client?.deleteFile(url,null) ?: false
    }


    private fun convertSmbFile(baseURL:String,file:FileIdBothDirectoryInformation,conn: ConnInfo): FileItem{
        val isDir = isDirectory(file.fileAttributes)
        val name = file.fileName
        val lastDotIdx = name.lastIndexOf(".")
        var subname = if(lastDotIdx > 0) {
            file.fileName.substring(0, lastDotIdx)
        }else{
            ""
        }
        val isHidden = (TextUtils.isEmpty(subname) && name.startsWith("."))
        if(TextUtils.isEmpty(subname)){
            subname = name
        }
        val suffix = name.split(subname).lastOrNull() ?: ""
        val mimeType = FileMimeType.getFileType(name)
        val fileItem = FileItem(isDir,file.fileName,
            subname,mimeType,
            file.creationTime.toEpochMillis(),
            file.changeTime.toEpochMillis(),
            file.endOfFile,0,"",
            baseURL+file.fileName,
            "",isHidden,
            true, true,conn.username,conn.pass)
        return fileItem
    }


    override fun onCleared() {
        super.onCleared()
    }

}