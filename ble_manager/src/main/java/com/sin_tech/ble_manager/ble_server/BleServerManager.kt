package com.sin_tech.ble_manager.ble_server

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile
import kotlin.math.ceil

class BleServerManager(private val context: Context,
    private val cacheDir:String,
                       private val callback: ServerCallback?,
                       private val fileCallback: ServerFileTransferCallback?
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    
    // 用于保存已连接的设备及其状态
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    // 文件传输管理
    private val fileTransfers = ConcurrentHashMap<String, FileTransfer>()
    private val running = AtomicBoolean(false)
    private val sessionById = ConcurrentHashMap<String, ClientSession>()

    // 业务处理线程池
    private val workerPool: ExecutorService = Executors.newCachedThreadPool(
        object : ThreadFactory {
            private val counter = AtomicInteger(0)
            override fun newThread(r: Runnable?): Thread {
                val thread = Thread(r, "Worker-" + counter.incrementAndGet())
                thread.isDaemon = true
                return thread
            }
        }
    )

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
        val CHAR_READ_WRITE_UUID: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
    }

    /**
     * 1. 启动服务与广播
     */
    @SuppressLint("MissingPermission")
    fun startServer() {
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Log.e("BLE", "设备不支持外设模式")
            return
        }
        check(running.compareAndSet(false, true)) { "Server already running" }
        // 配置 GATT 服务回调
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        setupGattService()

        // 开始广播
        startAdvertising()
    }

    /**
     * 2. 配置 GATT 服务和特征
     */
    @SuppressLint("MissingPermission")
    private fun setupGattService() {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // 添加一个可读、可写、可通知的特征
        val characteristic = BluetoothGattCharacteristic(
            CHAR_READ_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or 
            BluetoothGattCharacteristic.PROPERTY_WRITE or 
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
    }

    /**
     * 3. 配置广播参数
     */
    private fun startAdvertising() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    /**
     * 4. GATT 服务端回调：核心逻辑
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        
        // 设备连接/断开回调
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(device)

                val session = ClientSession(device.address, device,
                    WeakReference(this@BleServerManager))
                sessionById[device.address] = session
                workerPool.submit({
                    callback?.onClientConnected(device.address)
                })
                Log.i("BLE", "设备连接: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sessionById.remove(device.address)
                // 清理相关文件传输
                cleanupFileTransfers(device.address)

                connectedDevices.remove(device)
                Log.i("BLE", "设备断开: ${device.address}")
                workerPool.submit({
                    callback?.onClientDisconnected(device.address,"")
                })
            }
        }

        // 处理读请求
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            Log.d("BLE", "客户端请求读取数据")
            // 响应客户端，此处返回一段自定义字节数组
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, "Hello BLE".toByteArray())
        }

        // 处理写请求（客户端发送数据过来）
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
//            Log.i("BLE", "收到客户端数据: $receivedData")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            // 收到数据后的逻辑处理...
            val session = sessionById[device.address]
            if(session != null) {
                val byteBuf = ByteBuffer.wrap(value)
                processReceivedData(session, byteBuf)
            }
        }
    }

    /**
     * 5. 主动向所有连接的客户端推送数据 (Notify)
     */
    @SuppressLint("MissingPermission")
    fun notifyData(blueDev: BluetoothDevice, data: ByteArray) {
        val service = gattServer?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHAR_READ_WRITE_UUID)
        characteristic?.value = data
        // confirm 为 false 表示 Notify (不需要确认)，true 为 Indicate (需要确认)
        gattServer?.notifyCharacteristicChanged(blueDev, characteristic, false)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i("BLE", "广播开启成功")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        // 关闭所有客户端连接
        if (running.compareAndSet(true, false)) {
            println("Shutting down server...")

            sessionById.clear()
            fileTransfers.clear()
            workerPool.shutdownNow()

            gattServer?.close()
            bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        }
    }


    /**
     * 处理接收到的数据
     */
    private fun processReceivedData(
        session: ClientSession,
        data: ByteBuffer
    ) {
        session.appendToBuffer(data)

        val buffer: ByteBuffer = session.bufferAsByteBuffer

        while (buffer.remaining() >= 10) {  // 至少需要头部长度
            buffer.mark()
            val message: WiFiDirectProtocol.DecodedMessage? =
                WiFiDirectProtocol.decode(buffer)

            if (message == null) {
                // 数据不完整，重置并等待更多数据
                buffer.reset()
                break
            }

            if (message.type == WiFiDirectProtocol.TYPE_ERROR) {
                // 协议错误
                System.err.println(
                    "Protocol error: " + String(
                        message.data,
                        StandardCharsets.UTF_8
                    )
                )
                session.clearBuffer() // 清空缓冲区
                break
            }
            // 处理完整消息
            handleMessage(session, message)
            // 从缓冲区移除已处理的数据
            session.removeFromBuffer(10 + message.length)
        }
    }

    /**
     * 处理消息
     */
    private fun handleMessage(session: ClientSession, message: WiFiDirectProtocol.DecodedMessage) {
        when (message.type) {
            WiFiDirectProtocol.TYPE_STRING -> handleStringMessage(session, message.data)
            WiFiDirectProtocol.TYPE_FILE_META -> handleFileMeta(session, message.data)
            WiFiDirectProtocol.TYPE_FILE_DATA -> handleFileData(session, message.data)
            WiFiDirectProtocol.TYPE_FILE_ACK -> handleFileAck(session, message.data)
            else -> System.err.println("Unknown message type: " + message.type)
        }
    }

    /**
     * 处理字符串消息
     */
    private fun handleStringMessage(
        session: ClientSession,
        data: ByteArray
    ) {
        val message: String = WiFiDirectProtocol.decodeString(data)

        workerPool.submit({
            callback?.onMessageReceived(session.clientId, message)
        })
    }

    /**
     * 处理文件元数据
     */
    private fun handleFileMeta(
        session: ClientSession,
        data: ByteArray
    ) {
        try {
            val meta: WiFiDirectProtocol.FileMeta = WiFiDirectProtocol.decodeFileMeta(data)

            val transfer = FileTransfer(
                    meta.fileId,
                    session.clientId,
                    meta.fileName,
                    meta.fileSize,
                    meta.totalChunks,
                    cacheDir
                )

            fileTransfers[meta.fileId] = transfer


            // 发送文件接收确认
            sendFileAck(session, meta.fileId, 0, "START")

            workerPool.submit(Runnable {
                fileCallback?.onFileTransferStarted(
                    session.clientId,
                    meta.fileId,
                    meta.fileName,
                    meta.fileSize
                )
            })
        } catch (e: IOException) {
            e.printStackTrace()
            sendFileError(session, "Invalid file metadata")
        }
    }

    /**
     * 处理文件数据
     */
    private fun handleFileData(
        session: ClientSession,
        data: ByteArray
    ) {
        try {
            val chunk: WiFiDirectProtocol.FileChunk =
                WiFiDirectProtocol.decodeFileChunk(data)
            val fileId: String = chunk.fileId
            val transfer: FileTransfer? = fileTransfers[fileId]
            if (transfer == null) {
                sendFileError(session, "File transfer not found: $fileId")
                return
            }

            // 保存数据块
            workerPool.submit(object : Runnable {
                override fun run() {
                    try {
                        transfer.addChunk(chunk.chunkIndex, chunk.chunkData)
                        // 检查是否完成
                        if (chunk.isLast && transfer.isComplete) {
                            completeFileTransfer(session, transfer)
                        }
                    } catch (e: IOException) {
                        sendFileError(session, "write file chunk data to local temp file failed")
                    }
                }
            })
            // 发送确认
            sendFileAck(session, fileId, chunk.chunkIndex + 1, "CHUNK")
            if (fileCallback != null) {
                workerPool.submit(Runnable {
                    fileCallback.onFileChunkReceived(
                        session.clientId,
                        fileId,
                        chunk.chunkIndex,
                        chunk.chunkData.size
                    )
                }
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
            sendFileError(session, "Invalid file chunk data")
        }
    }

    /**
     * 完成文件传输
     */
    private fun completeFileTransfer(session: ClientSession, transfer: FileTransfer) {
        try {
            val filePath: String = transfer.filePath
            workerPool.submit(Runnable {
                fileCallback?.onFileTransferCompleted(
                    session.clientId,
                    transfer.fileId,
                    transfer.fileName, filePath
                )
            })

            // 发送完成确认
            sendFileAck(session, transfer.fileId, transfer.totalChunks, "COMPLETE")


            // 清理
            fileTransfers.remove(transfer.fileId)
        } catch (e: IOException) {
            e.printStackTrace()
            sendFileError(session, "Failed to assemble file: " + e.message)
        }
    }

    /**
     * 发送文件确认
     */
    private fun sendFileAck(session: ClientSession, fileId: String,
        receivedChunks: Int, status: String
    ) {
        try {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)

            dos.writeUTF(fileId)
            dos.writeInt(receivedChunks)
            dos.writeUTF(status)
            dos.writeLong(System.currentTimeMillis())

            dos.flush()

            val ack: ByteBuffer = WiFiDirectProtocol.encode(
                WiFiDirectProtocol.TYPE_FILE_ACK,
                baos.toByteArray()
            )

            enqueueWrite(session, ack)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 发送文件错误
     */
    private fun sendFileError(session: ClientSession, error: String) {
        val errorMsg: ByteBuffer = WiFiDirectProtocol.encode(
            WiFiDirectProtocol.TYPE_FILE_ERROR,
            error.toByteArray(StandardCharsets.UTF_8)
        )
        enqueueWrite(session, errorMsg)
    }

    /**
     * 处理文件确认
     */
    private fun handleFileAck(
        session: ClientSession,
        data: ByteArray
    ) {
        // 文件传输确认处理
        val ack = String(data, StandardCharsets.UTF_8)
        workerPool.submit(
            { callback?.onFileAckReceived(session.clientId, ack) }
        )
    }


    /**
     * 发送数据到客户端
     */
    fun sendToClient(clientId:String, data: ByteBuffer) {
        val session: ClientSession? = sessionById[clientId]
        if (session != null) {
            enqueueWrite(session, data)
        }
    }

    /**
     * 发送字符串到客户端
     */
    fun sendString(clientId: String, message: String) {
        val data = WiFiDirectProtocol.encodeString(message)
        sendToClient(clientId, data)
    }

    /**
     * 广播消息到所有客户端
     */
    fun broadcast(data: ByteBuffer) {
        for (session in sessionById.values) {
            enqueueWrite(session, data.duplicate())
        }
    }

    /**
     * 广播字符串
     */
    fun broadcastString(message: String) {
        val data = WiFiDirectProtocol.encodeString(message)
        broadcast(data)
    }

    /**
     * 发送文件到客户端
     */
    @Throws(IOException::class)
    fun sendFile(clientId: String, file: File) {
        if (!file.exists() || !file.isFile) {
            throw IOException("File not found: " + file.path)
        }

        val fileId = WiFiDirectProtocol.generateFileId()
        val fileSize = file.length()
        val totalChunks = ceil(fileSize.toDouble() / WiFiDirectProtocol.MAX_CHUNK_SIZE).toInt()


        // 发送文件元数据
        val meta = WiFiDirectProtocol.encodeFileMeta(
            fileId, file.name, fileSize, totalChunks
        )
        sendToClient(clientId, meta)

        // 分片发送文件数据
        try {
            RandomAccessFile(file, "r").use { raf ->
                val chunkBuffer = ByteArray(WiFiDirectProtocol.MAX_CHUNK_SIZE)
                var chunkIndex = 0
                var bytesRead: Int
                while ((raf.read(chunkBuffer).also { bytesRead = it }) != -1) {
                    val chunkData = chunkBuffer.copyOf(bytesRead)
                    val isLast = (raf.filePointer >= fileSize)

                    val chunk = WiFiDirectProtocol.encodeFileData(
                        fileId, chunkIndex, chunkData, isLast
                    )
                    sendToClient(clientId, chunk)
                    chunkIndex++

                    // 控制发送速率
                    Thread.sleep(1)
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 入队写数据
     */
    private fun enqueueWrite(session: ClientSession, data: ByteBuffer) {
        session.enqueueWrite(data)
    }

    /**
     * 清理文件传输
     */
    private fun cleanupFileTransfers(clientId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fileTransfers.entries.removeIf { entry: MutableMap.MutableEntry<String, FileTransfer> -> entry.value.clientId == clientId }
        }
    }

    /**
     * 断开指定客户端
     */
    @SuppressLint("MissingPermission")
    fun disconnectClient(clientId: String) {
        val dev: BluetoothDevice? = connectedDevices.firstOrNull { it.address == clientId }
        if (dev != null) {
            gattServer?.cancelConnection(dev)
            workerPool.submit({
                callback?.onClientDisconnected(dev.address,"主动断开连接")
            })
        }
    }


    /**
     * 客户端会话类
     */
    class ClientSession internal constructor(val clientId: String, val dev: BluetoothDevice,
        val ref: WeakReference<BleServerManager>) {
        val dataBuffer: ByteArrayOutputStream = ByteArrayOutputStream()

        @Volatile
        var lastActivityTime: Long = System.currentTimeMillis()

        @Volatile
        var lastHeartbeatTime: Long = System.currentTimeMillis()

        @Volatile
        var lastHeartbeatSent: Long = 0

        fun appendToBuffer(data: ByteBuffer) {
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            dataBuffer.write(bytes, 0, bytes.size)
        }

        val bufferAsByteBuffer: ByteBuffer
            get() {
                val data = dataBuffer.toByteArray()
                return ByteBuffer.wrap(data)
            }

        fun removeFromBuffer(length: Int) {
            val current = dataBuffer.toByteArray()
            if (length >= current.size) {
                dataBuffer.reset()
            } else {
                dataBuffer.reset()
                dataBuffer.write(current, length, current.size - length)
            }
        }

        fun clearBuffer() {
            dataBuffer.reset()
        }

        fun enqueueWrite(data: ByteBuffer) {
            val readBytes = data.array()
            ref.get()?.notifyData(dev,readBytes)
        }

        fun updateLastActivity() {
            lastActivityTime = System.currentTimeMillis()
        }

        fun updateLastHeartbeat() {
            lastHeartbeatTime = System.currentTimeMillis()
        }

        fun updateLastHeartbeatSent() {
            lastHeartbeatSent = System.currentTimeMillis()
        }
    }

    /**
     * 文件传输类
     */
    class FileTransfer internal constructor(
        val fileId: String, val clientId: String, var fileName: String,
        val fileSize: Long, totalChunks: Int, parentDirPath: String
    ) {
        val totalChunks: Int
        private var writeFile: File
        val receivedChunks: AtomicInteger = AtomicInteger(0)
        val startTime: Long = System.currentTimeMillis()

        init {
            this.writeFile = File(parentDirPath, fileName)
            if (!writeFile.exists()) {
                try {
//                      writeFile.mkdir();
                    writeFile.createNewFile()
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
            this.totalChunks = totalChunks
        }

        @Throws(IOException::class)
        fun addChunk(chunkIndex: Int, chunkData: ByteArray) {
            var raf: RandomAccessFile? = null
            try {
                raf = RandomAccessFile(writeFile, "rw")
                val offset = chunkIndex * WiFiDirectProtocol.MAX_CHUNK_SIZE
                raf.seek(offset.toLong())
                raf.write(chunkData, 0, chunkData.size)
                receivedChunks.incrementAndGet()
            } catch (e: IOException) {
                Thread.currentThread().interrupt()
                throw e
            } finally {
                raf?.close()
            }
        }

        val isComplete: Boolean
            get() = receivedChunks.get() >= totalChunks

        @get:Throws(IOException::class)
        val filePath: String
            get() {
                if (!this.isComplete) {
                    throw IOException("File not complete")
                }
                return writeFile.absolutePath
                //            ByteArrayOutputStream baos = new ByteArrayOutputStream((int) fileSize);
                //            for (int i = 0; i < totalChunks; i++) {
                //                byte[] chunk = chunks.get(i);
                //                if (chunk != null) {
                //                    baos.write(chunk);
                //                }
                //            }
                //            return baos.toByteArray();
            }
    }
}