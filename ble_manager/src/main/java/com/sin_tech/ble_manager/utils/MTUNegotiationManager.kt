package com.sin_tech.ble_manager.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.os.Build
import android.util.Log
import kotlin.math.ceil
import kotlin.math.min

// MTU协商管理器
class MTUNegotiationManager(
    private val gatt: BluetoothGatt,
    private val localMtuCapability: Int = MAX_MTU
) {
    companion object {
        // Android MTU标准值
        const val DEFAULT_MTU = 23  // 标准BLE MTU (20字节有效载荷 + 3字节头)
        const val MIN_MTU = 23
        const val MAX_MTU = 517     // BLE 5.0最大支持

        // 常见设备的MTU能力
        val DEVICE_MTU_CAPABILITIES = mapOf(
            "iPhone" to 185,        // iOS通常支持185
            "Android Default" to 23, // 老版本Android
            "Android 8+" to 512,     // 支持扩展MTU
            "ESP32" to 247,          // 常见IoT设备
            "NRF52" to 247           // Nordic芯片
        )
    }

    private var negotiatedMtu: Int = DEFAULT_MTU
    private var isNegotiating = false
    private var negotiationCallback: MTUNegotiationCallback? = null
    
    interface MTUNegotiationCallback {
        fun onMTUNegotiated(mtu: Int)
        fun onNegotiationFailed(reason: String)
    }
    
    // 1. 发起MTU请求
    @SuppressLint("MissingPermission")
    fun requestMTU(requestedMtu: Int, callback: MTUNegotiationCallback) {
        if (isNegotiating) {
            callback.onNegotiationFailed("正在协商中")
            return
        }
        
        // 验证请求的MTU是否在有效范围内
        val validMtu = when {
            requestedMtu < MIN_MTU -> {
                Log.w("MTU", "请求MTU($requestedMtu)太小，使用最小值$MIN_MTU")
                MIN_MTU
            }
            requestedMtu > MAX_MTU -> {
                Log.w("MTU", "请求MTU($requestedMtu)超限，使用最大值$MAX_MTU")
                MAX_MTU
            }
            requestedMtu > localMtuCapability -> {
                Log.w("MTU", "本地只支持$localMtuCapability，调整请求MTU")
                localMtuCapability
            }
            else -> requestedMtu
        }
        
        isNegotiating = true
        negotiationCallback = callback
        
        // 实际发起MTU请求
        val requestSuccess = gatt.requestMtu(validMtu)
        
        if (!requestSuccess) {
            isNegotiating = false
            callback.onNegotiationFailed("MTU请求失败")
        }
    }
    
    // 2. 处理MTU变更回调
    fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        isNegotiating = false
        
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                negotiatedMtu = mtu
                val effectiveMtu = mtu - 3  // 减去3字节ATT头
                
                Log.i("MTU", "MTU协商成功: $mtu (有效载荷: $effectiveMtu 字节)")
                negotiationCallback?.onMTUNegotiated(mtu)
                
                // 记录协商结果
//                saveNegotiatedMtu(gatt.device.address, mtu)

                negotiatedMtu = mtu
            }
            else -> {
                Log.e("MTU", "MTU协商失败: status=$status")
                negotiationCallback?.onNegotiationFailed("GATT错误: $status")
            }
        }
        
        negotiationCallback = null
    }
    
    // 3. 智能MTU协商策略
    fun negotiateOptimalMTU(
        device: BluetoothDevice,
        callback: MTUNegotiationCallback
    ) {
        val strategy = when {
            // 已知设备类型
//            isKnownDevice(device) -> getKnownDeviceMtu(device)
//
//            // 根据信号强度推测
//            getRssiStrength(device) > -60 -> 512  // 信号好，尝试大MTU
            
            // 根据Android版本推测
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> 517
            
            else -> 23  // 保守策略
        }
        
        requestMTU(strategy, callback)
    }
    
    // 4. 分块传输适配器
    class ChunkedDataSender(private val mtu: Int) {
        private val headerSize = 3  // ATT头部大小
        private val maxPayloadSize = mtu - headerSize
        
        fun sendLargeData(data: ByteArray, sendBlock: (ByteArray) -> Boolean): Boolean {
            if (data.size <= maxPayloadSize) {
                // 无需分片
                return sendBlock(data)
            }
            
            // 计算分片数量
            val totalChunks = ceil(data.size.toDouble() / maxPayloadSize).toInt()
            
            for (chunkIndex in 0 until totalChunks) {
                val start = chunkIndex * maxPayloadSize
                val end = min(start + maxPayloadSize, data.size)
                val chunk = data.copyOfRange(start, end)
                
                // 添加分片头
                val packet = createChunkPacket(chunk, chunkIndex, totalChunks)
                
                if (!sendBlock(packet)) {
                    Log.e("ChunkSender", "分片 $chunkIndex 发送失败")
                    return false
                }
                
                // 可选：添加延迟避免拥塞
                if (chunkIndex < totalChunks - 1) {
                    Thread.sleep(5)  // 5ms间隔
                }
            }
            
            return true
        }
        
        private fun createChunkPacket(
            data: ByteArray,
            chunkIndex: Int,
            totalChunks: Int
        ): ByteArray {
            // 创建带分片信息的数据包
            val packet = ByteArray(data.size + 4)
            
            // 分片头：2字节索引 + 2字节总数
            packet[0] = (chunkIndex shr 8).toByte()
            packet[1] = chunkIndex.toByte()
            packet[2] = (totalChunks shr 8).toByte()
            packet[3] = totalChunks.toByte()
            
            // 复制数据
            System.arraycopy(data, 0, packet, 4, data.size)
            return packet
        }
    }
}