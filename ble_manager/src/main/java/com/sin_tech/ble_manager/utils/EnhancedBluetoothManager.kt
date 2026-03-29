package com.sin_tech.ble_manager.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.sin_tech.ble_manager.utils.MTUNegotiationManager.Companion.DEFAULT_MTU

interface ConnectionCallback{
    fun onConnected(gatt:BluetoothGatt,mtu:Int)
}

class EnhancedBluetoothManager {
    private val mtuManagers = mutableMapOf<String, MTUNegotiationManager>()
    private val deviceCapabilities = mutableMapOf<String, DeviceMtuInfo>()
    
    data class DeviceMtuInfo(
        var lastNegotiatedMtu: Int = DEFAULT_MTU,
        var maxSupportedMtu: Int = DEFAULT_MTU,
        var isMtuExtensionSupported: Boolean = false,
        var lastNegotiationTime: Long = 0
    )
    
    // 连接时的MTU协商
    @SuppressLint("MissingPermission")
    fun connectWithMtuNegotiation(
        context: Context,
        device: BluetoothDevice,
        callback: ConnectionCallback
    ) {
        val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // 连接成功后立即协商MTU
                    val mtuManager = MTUNegotiationManager(gatt)
                    mtuManagers[device.address] = mtuManager
                    
                    mtuManager.negotiateOptimalMTU(device, object : 
                        MTUNegotiationManager.MTUNegotiationCallback {
                        override fun onMTUNegotiated(mtu: Int) {
                            // MTU协商完成，通知上层连接就绪
                            callback.onConnected(gatt, mtu)
                        }
                        
                        override fun onNegotiationFailed(reason: String) {
                            Log.w("Connection", "MTU协商失败: $reason，使用默认MTU")
                            callback.onConnected(gatt, DEFAULT_MTU)
                        }
                    })
                }
            }
            
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                mtuManagers[device.address]?.onMtuChanged(gatt, mtu, status)
            }
        })
    }
    
    // 智能重协商策略
    fun renegotiateMtuIfNeeded(deviceAddress: String) {
        val deviceInfo = deviceCapabilities[deviceAddress] ?: return
        val mtuManager = mtuManagers[deviceAddress] ?: return
        
        val now = System.currentTimeMillis()
        val hoursSinceLastNegotiation = (now - deviceInfo.lastNegotiationTime) / (1000 * 3600)
        
        // 重协商条件：
        // 1. 超过24小时
        // 2. MTU太小且可能支持更大
        // 3. 当前信号质量更好
        if (hoursSinceLastNegotiation > 24 || 
            (deviceInfo.lastNegotiatedMtu < 100 && deviceInfo.isMtuExtensionSupported)) {
            
            mtuManager.requestMTU(deviceInfo.maxSupportedMtu,object: MTUNegotiationManager.MTUNegotiationCallback{
                override fun onMTUNegotiated(mtu: Int) {
                    deviceInfo.lastNegotiatedMtu = mtu
                    deviceInfo.lastNegotiationTime = now
                }

                override fun onNegotiationFailed(reason: String) {

                }
            })
        }
    }
    
    // 自适应分片发送
    fun sendDataAdaptive(
        deviceAddress: String,
        data: ByteArray,
        sendFunction: (ByteArray) -> Boolean
    ): Boolean {
        val deviceInfo = deviceCapabilities[deviceAddress] ?: DeviceMtuInfo()
        val effectiveMtu = deviceInfo.lastNegotiatedMtu
        
        if (data.size <= effectiveMtu - 3) {
            // 可以直接发送
            return sendFunction(data)
        } else {
            // 需要分片
            val chunkSender = MTUNegotiationManager.ChunkedDataSender(effectiveMtu)
            return chunkSender.sendLargeData(data) { chunk ->
                sendFunction(chunk)
            }
        }
    }
}