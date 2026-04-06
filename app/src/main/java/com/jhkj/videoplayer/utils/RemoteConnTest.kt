package com.jhkj.videoplayer.utils

import com.hierynomus.smbj.SMBClient
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.pages.ConnType
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object RemoteConnTest {
    private fun getFullUrl(conn:ConnInfo):String{
        val protocol = conn.protocol.lowercase()
        val host = conn.domain
        val port = conn.port
        return "$protocol://$host:$port"
    }

    suspend fun testConn(conn: ConnInfo): Boolean{
        if(conn.connType == ConnType.WEBDAV.ordinal){
            return testWebdav(conn)
        }else{
            return testSmb(conn)
        }
    }

    suspend fun testWebdav(conn: ConnInfo): Boolean{
        val url = getFullUrl(conn)
//        url += "/"
        val res = withContext(Dispatchers.IO) {
            val client: Sardine = OkHttpSardine()
            client.setCredentials(conn.username, conn.pass)
            try {
                val files: List<DavResource>? = client.list(url)
                if (files != null) {
                    true
                }else {
                    false
                }
            } catch (e: IOException) {
                // 连接已断开
                false
            }
        }
        return res
    }

    suspend fun testSmb(conn: ConnInfo): Boolean{
        val url = getFullUrl(conn)
        val res = withContext(Dispatchers.IO) {
            try {
                SMBClient().connect(conn.domain).isConnected
            }catch (e: IOException){
                false
            }
        }
        return res
    }
}