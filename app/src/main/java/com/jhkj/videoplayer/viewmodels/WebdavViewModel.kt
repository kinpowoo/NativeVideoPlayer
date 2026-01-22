package com.jhkj.videoplayer.viewmodels

import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.utils.file_recursive.FileItem
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.gson.gson
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException


sealed interface PostState {
    data object Idle : PostState
    data object Loading : PostState
    data class Success(val post: Post) : PostState
    data class Error(val message: String) : PostState
}


data class Post(val userId:Int,val id:Int,val title:String,val body:String)


class WebdavViewModel : ViewModel(), RemoteProvider{
    private val _state = MutableLiveData<PostState>(PostState.Idle)

    private val ktorClient = HttpClient(Android) {
        install(ContentNegotiation) {
            gson()
        }
    }

    fun fetchPost(id: Int) {
        viewModelScope.launch {
            _state.value = PostState.Loading
            try {
                val post = ktorClient.get("https://jsonplaceholder.typicode.com/posts/$id").body<Post>()
                _state.value = PostState.Success(post)
            } catch (e: Exception) {
                _state.value = PostState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun getFullUrl(conn:ConnInfo):String{
        val protocol = conn.protocol.lowercase()
        val host = conn.domain
        val port = conn.port
        return "$protocol://$host:$port"
    }

    private val client: Sardine = OkHttpSardine()

    override fun checkUrl(conn:ConnInfo):Boolean{
        client.setCredentials(conn.username,conn.pass)
        var url = getFullUrl(conn)
        url += "/"
        try {
            val files:List<DavResource>? = client.list(url)
            if (files != null) {
                return true
            }
            return false
        } catch (e: IOException) {
            // 连接已断开
            return false
        }
    }

   override fun listFiles(conn:ConnInfo,subpath:String):List<FileItem>?{
        client.setCredentials(conn.username,conn.pass)
        val baseUrl = getFullUrl(conn)
        val url = baseUrl + subpath
        try {
            val files:List<DavResource>? = client.list(url)
            val items = mutableListOf<FileItem>()
            files?.forEach{
                if(it.path != subpath) {
                    val fileItem = convertFile(baseUrl, it, conn)
                    items.add(fileItem)
                }
            }
            return items
        } catch (e: IOException) {
            // 连接已断开
            return null
        }
    }

    override fun deleteFile(conn: ConnInfo, subpath: String): Boolean {
        client.setCredentials(conn.username,conn.pass)
        val baseUrl = getFullUrl(conn)
        val url = baseUrl + subpath
        try {
            client.delete(url)
            return true
        } catch (e: IOException) {
            // 连接已断开
            return false
        }
    }

    private fun convertFile(baseURL:String,file:DavResource,conn: ConnInfo): FileItem{
        val isDir = file.isDirectory
        val name = file.name
        val lastDotIdx = name.lastIndexOf(".")
        var subname = if(lastDotIdx > 0) {
            file.name.substring(0, lastDotIdx)
        }else{
            ""
        }
        val isHidden = (TextUtils.isEmpty(subname) && name.startsWith("."))
        if(TextUtils.isEmpty(subname)){
            subname = name
        }
        val suffix = name.split(subname).lastOrNull() ?: ""
        val mimeType = suffix.removePrefix(".")
        val fileItem = FileItem(isDir,file.name,
            subname,file.contentType,
            file.creation.time,file.modified.time,
            file.contentLength,0,"",
            baseURL+file.path,
            "",isHidden,
            true, true,conn.username,conn.pass)
        return fileItem
    }


    override fun onCleared() {
        super.onCleared()
        ktorClient.close()
    }


}