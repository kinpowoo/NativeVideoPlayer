package com.jhkj.videoplayer.compose_pages.viewmodel

import android.system.Os.socket
import android.text.TextUtils
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
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
import java.io.BufferedInputStream
import java.io.IOException


sealed interface PostState {
    data object Idle : PostState
    data object Loading : PostState
    data class Success(val post: Post) : PostState
    data class Error(val message: String) : PostState
}

/**
{
"userId": 1,
"id": 1,
"title": "sunt aut facere repellat provident occaecati excepturi optio reprehenderit",
"body": "quia et suscipit\nsuscipit recusandae consequuntur expedita et cum\nreprehenderit molestiae ut ut quas totam\nnostrum rerum est autem sunt rem eveniet architecto"
}
 */
data class Post(val userId:Int,val id:Int,val title:String,val body:String)

class WebdavViewModel : ViewModel() {
    private val _state = mutableStateOf<PostState>(PostState.Idle)
    val state: State<PostState> = _state

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

    fun checkUrl(conn:ConnInfo):Boolean{
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

    fun listPath(conn:ConnInfo,path:String):List<DavResource>?{
        client.setCredentials(conn.username,conn.pass)
        val baseUrl = getFullUrl(conn)
        val url = baseUrl + path
        try {
            val files:List<DavResource>? = client.list(url)
            return files
        } catch (e: IOException) {
            // 连接已断开
            return null
        }
    }


    override fun onCleared() {
        super.onCleared()
        ktorClient.close()
    }


}