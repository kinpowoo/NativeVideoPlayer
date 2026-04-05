package com.jhkj.videoplayer.viewmodels

import android.text.TextUtils
import androidx.lifecycle.ViewModel
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.third_file_framework.smb.BySMB
import com.jhkj.videoplayer.third_file_framework.smb.JcifsNgScanner
import com.jhkj.videoplayer.third_file_framework.smb.OnListFileCallback
import com.jhkj.videoplayer.third_file_framework.smb.OnReadFileListNameCallback
import com.jhkj.videoplayer.third_file_framework.smb.SMBShare
import com.jhkj.videoplayer.utils.FileMimeType
import com.jhkj.videoplayer.utils.file_recursive.FileItem
import com.thegrizzlylabs.sardineandroid.DavResource
import jcifs.smb.SmbFile
import java.io.IOException


class SmbVM: ViewModel(), RemoteProvider{

    private fun getFullUrl(conn:ConnInfo):String{
        val protocol = conn.protocol.lowercase()
        val host = conn.domain
        val port = conn.port
        return "$protocol://$host:$port"
    }

    private lateinit var ngScanner: JcifsNgScanner
    fun initClient(conn: ConnInfo): Boolean{
        ngScanner = JcifsNgScanner()
        return checkUrl(conn)
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
        val fileList = ngScanner.listFiles(conn,conn.path ?: "")
        return fileList != null
    }

    override fun listFiles(conn:ConnInfo,subpath:String):List<FileItem>{
        val baseUrl = getFullUrl(conn)
        val fileList = ngScanner.listFiles(conn,subpath)
        val items = mutableListOf<FileItem>()
        fileList?.forEach{
            if(!it.isHidden) {
                val fileItem = convertSmbFile(baseUrl, it, conn)
                items.add(fileItem)
            }
        }
        return items
    }

    override fun deleteFile(conn: ConnInfo, subpath: String): Boolean {
        return ngScanner.deleteFile(conn,subpath)
    }


    private fun convertSmbFile(baseURL:String, file: SmbFile, conn: ConnInfo): FileItem{
        val isDir = file.isDirectory
        val name = file.name
        val lastDotIdx = name.lastIndexOf(".")
        var subname = if(lastDotIdx > 0) {
            file.name.substring(0, lastDotIdx)
        }else{
            ""
        }
        if(TextUtils.isEmpty(subname)){
            subname = name
        }
        val suffix = name.split(subname).lastOrNull() ?: ""
        val mimeType = FileMimeType.getFileType(name)
        val fileItem = FileItem(isDir,file.name,
            subname,mimeType,
            file.lastModified,
            file.createTime(),
            file.length(),0,
            "",
            (file.parent ?: "") + file.name,
            file.parent,
            file.isHidden,
            file.canWrite(), file.canRead(),
            conn.username,conn.pass)
        return fileItem
    }


    override fun onCleared() {
        super.onCleared()
    }

}