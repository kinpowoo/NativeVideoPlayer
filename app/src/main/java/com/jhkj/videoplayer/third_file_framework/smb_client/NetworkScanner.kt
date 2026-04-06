package com.jhkj.videoplayer.third_file_framework.smb_client

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.IOException
import java.net.InetAddress


class NetworkScanner {

    private var wifiManager:WifiManager

     constructor(context:Context) {
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as (WifiManager)
     }

    fun scanDevices() : List<String>{
        val devices = mutableListOf<String>()
        // 获取本设备的IP地址
        val ipAddress = getLocalIPAddress()
        val subIp = getSubnet(ipAddress ?: "")

        // 假设192.168.1.x网络
        for ( i in 1 ..< 255) {
            val host = "$subIp.$i"
            try {
                val address = InetAddress.getByName(host)
                if (address.isReachable(100)) { // 200ms超时
                    // 设备在线，进行DNS反向解析
                    val hostName = address.canonicalHostName
                    Log.d("Device Found", host)
                    devices.add(host)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return devices
    }

    private fun getLocalIPAddress(): String? {
        return try {
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress
            "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
        } catch (e: Exception) {
            null
        }
    }

    private fun getSubnet(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size >= 3) {
            "${parts[0]}.${parts[1]}.${parts[2]}"
        } else {
            "192.168.1"
        }
    }
}