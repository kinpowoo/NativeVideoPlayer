package com.jhkj.videoplayer.utils

import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.pages.ConnType
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.io.IOException

object RemoteConnTest {
    private fun getFullUrl(conn:ConnInfo):String{
        val protocol = conn.protocol.lowercase()
        val host = conn.domain
        val port = conn.port
        return "$protocol://$host:$port"
    }

    fun testConn(conn: ConnInfo): Boolean{
        var url = getFullUrl(conn)
        url += "/"
        if(conn.connType == ConnType.WEBDAV.ordinal) {
            val client: Sardine = OkHttpSardine()
            client.setCredentials(conn.username,conn.pass)
            try {
                val files: List<DavResource>? = client.list(url)
                if (files != null) {
                    return true
                }
                return false
            } catch (e: IOException) {
                // 连接已断开
                return false
            }
        }
        return false
    }
}