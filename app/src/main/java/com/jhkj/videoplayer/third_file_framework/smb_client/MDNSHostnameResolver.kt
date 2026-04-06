package com.jhkj.videoplayer.third_file_framework.smb_client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.text.TextUtils

interface SMBDeviceScanListener{
    fun onDeviceScanned(dev: SMBDevice)
    fun onScanFailed(reason:String)
}

data class SMBDevice(val serverName:String,val ip:String,val port:Int)

class MDNSHostnameResolver(private val context: Context) {
    private val mHandler = Handler(Looper.getMainLooper())
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    private var scanListener: SMBDeviceScanListener? = null
    private var isRegisterListener = false

    fun setScanListener(listener: SMBDeviceScanListener){
        this.scanListener = listener
    }

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            scanListener?.onScanFailed("serviceType : $serviceType , errorCode:$errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            scanListener?.onScanFailed("serviceType : $serviceType , errorCode:$errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            // 发现开始
        }

        override fun onDiscoveryStopped(serviceType: String?) {

        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    // 解析失败
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (!TextUtils.isEmpty(serviceInfo.host?.hostAddress)) {
                        val serviceName = serviceInfo.serviceName ?: "UNKNOWN"
                        val ip = serviceInfo.host.hostName
                        val port = serviceInfo.port
                        val dev = SMBDevice(serviceName,ip,port)
                        scanListener?.onDeviceScanned(dev)
                    }
                }
            })
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            scanListener?.onScanFailed("service lost")
            // 服务丢失
        }
    }

    private val stopDiscoveryRun = Runnable { stopDiscovery() }

    fun stopDiscovery(){
        if(isRegisterListener) {
            nsdManager.stopServiceDiscovery(listener)
            mHandler.removeCallbacks(stopDiscoveryRun)
            isRegisterListener = false
        }
    }

    fun startDiscovery(timeoutSeconds: Long = 5000) {
        stopDiscovery()
        // 开始发现服务
        //nsdManager.discoverServices("_workstation._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        //nsdManager.discoverServices("_device-info._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        nsdManager.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        mHandler.postDelayed(stopDiscoveryRun,timeoutSeconds)
        isRegisterListener = true
    }
}