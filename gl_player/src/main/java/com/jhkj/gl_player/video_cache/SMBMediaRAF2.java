package com.jhkj.gl_player.video_cache;

import android.media.MediaDataSource;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.*;

import jcifs.smb.SmbRandomAccessFile;

@RequiresApi(api = Build.VERSION_CODES.M)
public class SMBMediaRAF2 extends MediaDataSource implements Closeable {
    
    // 缓存块结构
    private static class CacheBlock {
        long start;      // 块在文件中的起始位置
        long end;        // 块在文件中的结束位置
        byte[] data;     // 块数据
        final AtomicBoolean loaded = new AtomicBoolean(false); // 是否已加载
        
        CacheBlock(long start, long end, int size) {
            this.start = start;
            this.end = end;
            this.data = new byte[size];
        }
    }
    
    private final SmbRandomAccessFile smbFile;
    private final File cacheFile;
    private final RandomAccessFile cacheRAF;
    private final FileChannel cacheChannel;
    
    // 环形缓冲区
    private final CacheBlock[] buffer;
    private final int bufferSize;           // 缓冲区大小（块数）
    private final int blockSize;            // 每个块的大小
    private final AtomicInteger head = new AtomicInteger(0);    // 缓冲区头指针
    private final AtomicInteger tail = new AtomicInteger(0);    // 缓冲区尾指针
    private final AtomicLong readPosition = new AtomicLong(0);  // 当前读取位置
    private final AtomicLong writePosition = new AtomicLong(0); // 当前写入位置
    private final AtomicLong fileSize;                          // 文件总大小
    
    // 并发控制
    private final ExecutorService downloadExecutor;
    private final ReentrantLock[] blockLocks;  // 每个块的锁
    private final Condition[] blockConditions; // 每个块的条件变量
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    
    // 预读取线程
    private Thread prefetchThread;
    
    // 统计信息
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicLong totalReadBytes = new AtomicLong(0);
    
    public SMBMediaRAF2(SmbRandomAccessFile smbFile, File cacheDir)
            throws IOException {
        this(smbFile, cacheDir, 16, 512 * 1024); // 默认16个块，每个512KB
    }
    
    public SMBMediaRAF2(SmbRandomAccessFile smbFile, File cacheDir,
                        int bufferSize, int blockSize) throws IOException {
        this.smbFile = smbFile;
        this.bufferSize = bufferSize;
        this.blockSize = blockSize;
        
        // 获取文件大小
        long size = smbFile.length();
        fileSize = new AtomicLong(size);
        
        // 创建缓存文件
        cacheFile = File.createTempFile("media_cache_", ".dat", cacheDir);
        cacheFile.deleteOnExit();
        cacheRAF = new RandomAccessFile(cacheFile, "rw");
        cacheChannel = cacheRAF.getChannel();
        
        // 初始化环形缓冲区
        buffer = new CacheBlock[bufferSize];
        blockLocks = new ReentrantLock[bufferSize];
        blockConditions = new Condition[bufferSize];
        
        for (int i = 0; i < bufferSize; i++) {
            buffer[i] = new CacheBlock(-1, -1, blockSize);
            blockLocks[i] = new ReentrantLock();
            blockConditions[i] = blockLocks[i].newCondition();
        }
        
        // 创建下载线程池
        downloadExecutor = Executors.newFixedThreadPool(Math.min(4, Runtime.getRuntime().availableProcessors()));
        
        // 启动预读取线程
        startPrefetchThread();
    }
    
    private void startPrefetchThread() {
        prefetchThread = new Thread(() -> {
            while (isRunning.get()) {
                try {
                    prefetchData();
                    Thread.sleep(10); // 稍微休息，避免CPU占用过高
                } catch (Exception e) {
                    if (isRunning.get()) {
                        e.printStackTrace();
                    }
                }
            }
        }, "MediaDataSource-Prefetch");
        prefetchThread.setPriority(Thread.MIN_PRIORITY);
        prefetchThread.start();
    }
    
    /**
     * 预读取数据策略
     */
    private void prefetchData() {
        long currentReadPos = readPosition.get();
        
        // 预读取未来4个块的数据
        for (int i = 1; i <= 4; i++) {
            long prefetchPos = currentReadPos + (long) i * blockSize;
            if (prefetchPos >= fileSize.get()) {
                break;
            }
            
            if (!isBlockInBuffer(prefetchPos)) {
                prefetchBlock(prefetchPos);
            }
        }
    }
    
    /**
     * 预读取一个块
     */
    private void prefetchBlock(long position) {
        int blockIndex = findBlockForPosition(position);
        if (blockIndex == -1) {
            return; // 没有可用块
        }
        
        CacheBlock block = buffer[blockIndex];
        if (!block.loaded.get()) {
            // 异步加载块
            downloadExecutor.submit(() -> loadBlockAsync(blockIndex, position));
        }
    }
    
    /**
     * 异步加载块数据
     */
    private void loadBlockAsync(int bufferIndex, long position) {
        CacheBlock block = buffer[bufferIndex];
        ReentrantLock lock = blockLocks[bufferIndex];
        
        lock.lock();
        try {
            // 再次检查是否已加载
            if (block.loaded.get()) {
                return;
            }
            
            // 计算要读取的范围
            long start = (position / blockSize) * blockSize;
            long end = Math.min(start + blockSize, fileSize.get());
            
            // 从SMB文件读取数据
            byte[] data = readFromSmb(start, (int)(end - start));
            
            // 更新块信息
            block.start = start;
            block.end = end;
            System.arraycopy(data, 0, block.data, 0, data.length);
            block.loaded.set(true);
            
            // 同时写入缓存文件（持久化缓存）
            cacheChannel.position(start);
            ByteBuffer byteBuffer = ByteBuffer.wrap(data, 0, data.length);
            cacheChannel.write(byteBuffer);
            
            // 唤醒等待此块的线程
            blockConditions[bufferIndex].signalAll();
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 从SMB文件读取数据
     */
    private byte[] readFromSmb(long position, int length) throws IOException {
        synchronized (smbFile) {
            smbFile.seek(position);
            byte[] data = new byte[length];
            int totalRead = 0;
            
            while (totalRead < length) {
                int read = smbFile.read(data, totalRead, length - totalRead);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }
            
            return data;
        }
    }
    
    /**
     * 查找适合位置的块
     */
    private int findBlockForPosition(long position) {
        long blockStart = (position / blockSize) * blockSize;
        
        // 首先检查是否已经有这个块
        for (int i = 0; i < bufferSize; i++) {
            CacheBlock block = buffer[i];
            if (block.loaded.get() && block.start == blockStart) {
                return i;
            }
        }
        
        // 找到最近最少使用的块（简单的LRU实现）
        // 使用环形缓冲区的尾部
        int tailIndex = tail.get();
        int newIndex = tailIndex;
        
        // 移动尾指针
        tail.set((tailIndex + 1) % bufferSize);
        
        // 清空旧块
        buffer[newIndex].loaded.set(false);
        buffer[newIndex].start = -1;
        buffer[newIndex].end = -1;
        
        return newIndex;
    }
    
    /**
     * 检查位置是否在缓冲区中
     */
    private boolean isBlockInBuffer(long position) {
        long blockStart = (position / blockSize) * blockSize;
        
        for (CacheBlock block : buffer) {
            if (block.loaded.get() && block.start == blockStart) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * MediaDataSource 接口实现
     */
    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) 
            throws IOException {
        
        if (position >= fileSize.get()) {
            return -1; // EOF
        }
        
        // 更新读取位置
        readPosition.set(position);
        
        int remaining = size;
        int totalRead = 0;
        long currentPos = position;
        
        while (remaining > 0 && currentPos < fileSize.get()) {
            int bytesRead = readFromCacheOrDownload(currentPos, buffer, offset + totalRead, remaining);
            
            if (bytesRead <= 0) {
                break;
            }
            
            totalRead += bytesRead;
            remaining -= bytesRead;
            currentPos += bytesRead;
        }
        
        totalReadBytes.addAndGet(totalRead);
        return totalRead;
    }
    
    /**
     * 从缓存或下载中读取数据
     */
    private int readFromCacheOrDownload(long position, byte[] dest, int destOffset, int length) 
            throws IOException {
        
        // 计算当前块的信息
        long blockStart = (position / blockSize) * blockSize;
        long blockEnd = Math.min(blockStart + blockSize, fileSize.get());
        long offsetInBlock = position - blockStart;
        int bytesInBlock = (int) Math.min(blockEnd - position, length);
        
        // 查找块在缓冲区中的位置
        int bufferIndex = -1;
        for (int i = 0; i < bufferSize; i++) {
            CacheBlock block = buffer[i];
            if (block.loaded.get() && block.start == blockStart) {
                bufferIndex = i;
                cacheHits.incrementAndGet();
                break;
            }
        }
        
        // 如果块不在缓冲区中
        if (bufferIndex == -1) {
            cacheMisses.incrementAndGet();
            bufferIndex = findBlockForPosition(position);
            
            // 同步加载块（因为readAt必须立即返回数据）
            loadBlockSync(bufferIndex, blockStart);
        }
        
        // 从块中读取数据
        CacheBlock block = buffer[bufferIndex];
        ReentrantLock lock = blockLocks[bufferIndex];
        
        lock.lock();
        try {
            // 等待块加载完成
            while (!block.loaded.get()) {
                try {
                    blockConditions[bufferIndex].await(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 0;
                }
            }
            
            // 确保请求的位置在块范围内
            if (position < block.start || position >= block.end) {
                return 0;
            }
            
            // 从块中复制数据
            int blockOffset = (int)(position - block.start);
            int bytesToCopy = Math.min(bytesInBlock, block.data.length - blockOffset);
            
            if (bytesToCopy <= 0) {
                return 0;
            }
            
            System.arraycopy(block.data, blockOffset, dest, destOffset, bytesToCopy);
            return bytesToCopy;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 同步加载块
     */
    private void loadBlockSync(int bufferIndex, long start) {
        CacheBlock block = buffer[bufferIndex];
        ReentrantLock lock = blockLocks[bufferIndex];
        
        lock.lock();
        try {
            if (block.loaded.get()) {
                return;
            }
            
            // 计算要读取的范围
            long end = Math.min(start + blockSize, fileSize.get());
            
            // 首先尝试从缓存文件读取
            try {
                if (readFromCacheFile(start, (int)(end - start), block.data)) {
                    block.start = start;
                    block.end = end;
                    block.loaded.set(true);
                    cacheHits.incrementAndGet();
                    return;
                }
            } catch (IOException e) {
                // 缓存文件读取失败，继续从SMB读取
            }
            
            // 从SMB文件读取
            byte[] data = readFromSmb(start, (int)(end - start));
            
            // 更新块信息
            block.start = start;
            block.end = end;
            System.arraycopy(data, 0, block.data, 0, data.length);
            block.loaded.set(true);
            
            // 写入缓存文件
            cacheChannel.position(start);
            ByteBuffer byteBuffer = ByteBuffer.wrap(data, 0, data.length);
            cacheChannel.write(byteBuffer);
            
            blockConditions[bufferIndex].signalAll();
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 从缓存文件读取
     */
    private boolean readFromCacheFile(long position, int length, byte[] dest) 
            throws IOException {
        if (position >= cacheChannel.size()) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(dest, 0, Math.min(length, dest.length));
        cacheChannel.position(position);
        int read = cacheChannel.read(buffer);
        
        return read == length;
    }
    
    @Override
    public long getSize() throws IOException {
        return fileSize.get();
    }
    
    @Override
    public void close() throws IOException {
        isRunning.set(false);
        
        if (prefetchThread != null && prefetchThread.isAlive()) {
            prefetchThread.interrupt();
            try {
                prefetchThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭所有资源
        try {
            if (cacheChannel != null) {
                cacheChannel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        try {
            if (cacheRAF != null) {
                cacheRAF.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        try {
            if (smbFile != null) {
                smbFile.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 删除缓存文件
        if (cacheFile != null && cacheFile.exists()) {
            cacheFile.delete();
        }
    }
    
    /**
     * 获取缓存命中率
     */
    public float getCacheHitRate() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int total = hits + misses;
        
        if (total == 0) {
            return 0f;
        }
        
        return (float) hits / total;
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("Cache Hits: %d, Misses: %d, Hit Rate: %.2f%%, Total Read: %d bytes",
                cacheHits.get(), cacheMisses.get(), getCacheHitRate() * 100, totalReadBytes.get());
    }
    
    /**
     * 清空缓存
     */
    public void clearCache() {
        for (int i = 0; i < bufferSize; i++) {
            buffer[i].loaded.set(false);
            buffer[i].start = -1;
            buffer[i].end = -1;
        }
        
        cacheHits.set(0);
        cacheMisses.set(0);
    }
    
    /**
     * 设置预读取策略
     */
    public void setPrefetchStrategy(int lookAheadBlocks) {
        // 可以扩展以支持不同的预读取策略
    }
}