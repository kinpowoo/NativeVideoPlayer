package com.jhkj.videoplayer.third_file_framework.smb

import android.text.TextUtils
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.NtlmPasswordAuthenticator.AuthenticationType
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import java.net.InetAddress

/**
 * SMB 扫描结果数据类
 */
data class SMBScanResult(
    val host: String,
    val hostname: String? = null,
    val shares: List<SMBShare> = emptyList(),
    val error: String? = null,
    val scanTime: Long = System.currentTimeMillis()
)

data class SMBShare(
    val name: String,
    val path: String,
    val isDirectory: Boolean = true,
    val createTime: Long = 0L,
    val lastModified: Long = 0L,
    val size: Long = 0L,
    val comment: String? = null
)

/**
 * 使用 jcifs-ng 的 SMB 扫描器
 */
class JcifsNgScanner {
    /**
     * 连接到单个 SMB 主机并列出所有共享
     */
    fun listFiles(conn:ConnInfo,subpath:String): Array<SmbFile>? {
        try {
            // 创建 NtlmCredentials
            val username = conn.username
            val pass = conn.pass
            val context = if(TextUtils.isEmpty(username)){
                SingletonContext.getInstance().withGuestCrendentials()
            }else{
                val auth = NtlmPasswordAuthenticator(null, username, pass,
                    AuthenticationType.USER)
                SingletonContext.getInstance().withCredentials(auth)
            }
            // 3. 连接到主机根目录
            val host = conn.domain
            val smbUrl = "smb://$host/$subpath"
            val rootDir = SmbFile(smbUrl, context)
            // 4. 列出所有共享文件夹
            val files:Array<SmbFile> = rootDir.listFiles()
            return files
        } catch (e: SmbException) {
            e.printStackTrace()
        }
        return null
    }


    fun deleteFile(conn:ConnInfo,subpath:String): Boolean {
        try {
            // 创建 NtlmCredentials
            val username = conn.username
            val pass = conn.pass

            val context = if(TextUtils.isEmpty(username)){
                SingletonContext.getInstance().withGuestCrendentials()
            }else{
                val auth = NtlmPasswordAuthenticator(null, username, pass,
                    AuthenticationType.USER)
                SingletonContext.getInstance().withCredentials(auth)
            }
            // 3. 连接到主机根目录
            val host = conn.domain
            val smbUrl = "smb://$host/$subpath"
            val rootDir = SmbFile(smbUrl, context)
            rootDir.delete()
            return true
        } catch (e: SmbException) {
            e.printStackTrace()
        }
        return false
    }


    /**
     * 检查主机是否可达
     */
    private fun isHostReachable(host: String, timeout: Int): Boolean {
        return try {
            InetAddress.getByName(host).isReachable(timeout)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取本地 IP 地址
     */
    private fun getLocalIPAddress(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // 跳过回环接口和未启用的接口
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // 只处理 IPv4 地址
                    if (address is java.net.Inet4Address) {
                        val hostAddress = address.hostAddress
                        // 跳过本地回环地址
                        if (!hostAddress.startsWith("127.") && !hostAddress.startsWith("169.254.")) {
                            return hostAddress
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}