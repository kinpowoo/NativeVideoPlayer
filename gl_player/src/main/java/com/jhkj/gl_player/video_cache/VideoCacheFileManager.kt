package com.jhkj.gl_player.video_cache

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.O)
class VideoCacheFileManager(
    private val cacheFile: File,
    private val bufferSize: Int = 8192,  // 8KB缓冲区
    private val maxQueueSize: Int = 1000
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var channel: FileChannel? = null
    private val writeQueue = LinkedBlockingQueue<WriteTask>()
    private val readQueue = LinkedBlockingQueue<ReadTask>()
    private val isRunning = AtomicBoolean(true)
    
    data class WriteTask(
        val position: Long,
        val data: ByteArray,
        val callback: ((Boolean, Int) -> Unit)? = null
    )
    
    data class ReadTask(
        val position: Long,
        val size: Int,
        val callback: ((ByteArray?) -> Unit)? = null
    )
    
    init {
        // 初始化文件通道
        executor.submit {
            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.createNewFile()
            }
            
            channel = FileChannel.open(
                cacheFile.toPath(),
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE
            )
        }
        
        // 启动处理线程
        Thread(Processor(), "VideoCacheProcessor").start()
    }
    
    private inner class Processor : Runnable {
        override fun run() {
            while (isRunning.get() || !writeQueue.isEmpty() || !readQueue.isEmpty()) {
                try {
                    // 优先处理写入
                    val writeTask = writeQueue.poll(10, TimeUnit.MILLISECONDS)
                    if (writeTask != null) {
                        processWrite(writeTask)
                        continue
                    }
                    
                    // 然后处理读取
                    val readTask = readQueue.poll(10, TimeUnit.MILLISECONDS)
                    if (readTask != null) {
                        processRead(readTask)
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        private fun processWrite(task: WriteTask) {
            try {
                synchronized(this@VideoCacheFileManager) {
                    val buffer = ByteBuffer.wrap(task.data)
                    var bytesWritten = 0
                    
                    while (buffer.hasRemaining() && channel != null && channel!!.isOpen) {
                        bytesWritten += channel!!.write(buffer, task.position + bytesWritten)
                    }
                    
                    task.callback?.invoke(true, bytesWritten)
                }
            } catch (e: Exception) {
                task.callback?.invoke(false, 0)
            }
        }
        
        private fun processRead(task: ReadTask) {
            try {
                synchronized(this@VideoCacheFileManager) {
                    val buffer = ByteBuffer.allocate(task.size)
                    var bytesRead = 0
                    
                    while (buffer.hasRemaining() && channel != null && channel!!.isOpen) {
                        val read = channel!!.read(buffer, task.position + bytesRead)
                        if (read == -1) {
                            break
                        }
                        bytesRead += read
                    }
                    
                    buffer.flip()
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    
                    task.callback?.invoke(data)
                }
            } catch (e: Exception) {
                task.callback?.invoke(null)
            }
        }
    }
    
    /**
     * 写入视频数据块
     */
    fun writeChunk(position: Long, data: ByteArray, callback: ((Boolean, Int) -> Unit)? = null) {
        if (writeQueue.size >= maxQueueSize) {
            callback?.invoke(false, 0)
            return
        }
        
        writeQueue.offer(WriteTask(position, data, callback))
    }
    
    /**
     * 读取视频数据块
     */
    fun readChunk(position: Long, size: Int, callback: ((ByteArray?) -> Unit)? = null) {
        if (readQueue.size >= maxQueueSize) {
            callback?.invoke(null)
            return
        }
        
        readQueue.offer(ReadTask(position, size, callback))
    }
    
    /**
     * 同步写入
     */
    fun writeChunkSync(position: Long, data: ByteArray): Boolean {
        val latch = CountDownLatch(1)
        var success = false
        var bytesWritten = 0
        
        writeChunk(position, data) { s, b ->
            success = s
            bytesWritten = b
            latch.countDown()
        }
        
        latch.await()
        return success && bytesWritten == data.size
    }
    
    /**
     * 同步读取
     */
    fun readChunkSync(position: Long, size: Int): ByteArray? {
        val latch = CountDownLatch(1)
        var result: ByteArray? = null
        
        readChunk(position, size) { data ->
            result = data
            latch.countDown()
        }
        
        latch.await()
        return result
    }
    
    /**
     * 获取已缓存的文件大小
     */
    fun getCachedSize(): Long {
        val result = CompletableFuture<Long>()
        
        executor.submit {
            synchronized(this) {
                result.complete(channel?.size() ?: 0L)
            }
        }
        
        return result.get()
    }
    
    /**
     * 获取可读范围
     */
    fun getAvailableRange(position: Long, size: Int): LongRange? {
        val result = CompletableFuture<LongRange?>()
        
        executor.submit {
            synchronized(this) {
                val fileSize = channel?.size() ?: 0L
                if (position >= fileSize) {
                    result.complete(null)
                    return@submit
                }
                
                val end = minOf(position + size, fileSize)
                result.complete(position until end)
            }
        }
        
        return result.get()
    }
    
    /**
     * 关闭
     */
    fun close() {
        isRunning.set(false)
        
        // 等待队列处理完成
        Thread.sleep(100)
        
        executor.submit {
            synchronized(this) {
                try {
                    channel?.force(true)
                    channel?.close()
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }
        
        executor.shutdown()
    }
}