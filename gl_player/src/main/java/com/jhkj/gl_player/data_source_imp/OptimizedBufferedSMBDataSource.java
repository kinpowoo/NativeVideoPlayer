package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class OptimizedBufferedSMBDataSource extends MediaDataSource {
    private static final int BUFFER_SIZE = 200 * 1024; // 1MB缓冲块
    private static final int NUM_BUFFERS = 40; // 缓冲块数量
    private static final int MAX_PRELOAD_TASKS = 4; // 最大并发预读取任务数
    private static final int MIN_READ_SIZE = 512 * 1024; // 最小读取大小

    private static class BufferBlock {
        long startPos = -1;
        byte[] data;
        int length = 0;
        long lastAccessTime = 0;
        
        BufferBlock() {
            this.data = new byte[BUFFER_SIZE];
        }
        
        boolean contains(long position, int size) {
            return startPos >= 0 && 
                   position >= startPos && 
                   position + size <= startPos + length;
        }
        
        int read(long position, byte[] dest, int destOffset, int size) {
            if (startPos < 0 || position < startPos || position >= startPos + length) {
                return 0;
            }
            
            int bufferOffset = (int)(position - startPos);
            int bytesToCopy = Math.min(size, length - bufferOffset);
            
            if (bytesToCopy <= 0) {
                return 0;
            }
            
            System.arraycopy(data, bufferOffset, dest, destOffset, bytesToCopy);
            lastAccessTime = System.currentTimeMillis();
            return bytesToCopy;
        }
        
        void clear() {
            startPos = -1;
            length = 0;
        }
    }
    
    private final SmbRandomAccessFile mFile;
    private final long mFileSize;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);
    private final BufferBlock[] mBuffers = new BufferBlock[NUM_BUFFERS];
    
    // LRU管理
    private final LinkedHashMap<Long, Integer> mLruMap = new LinkedHashMap<Long, Integer>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            return size() > NUM_BUFFERS;
        }
    };
    
    // 快速查找映射
    private final Map<Long, Integer> mPositionToIndex = new HashMap<>();
    
    // 统计和状态
    private final AtomicLong mTotalBytesRead = new AtomicLong(0);
    private final AtomicLong mCacheHits = new AtomicLong(0);
    private final AtomicLong mCacheMisses = new AtomicLong(0);
    private final AtomicLong mLastReadPosition = new AtomicLong(0);
    private final AtomicBoolean mClosed = new AtomicBoolean(false);
    private final ReentrantLock mCacheLock = new ReentrantLock();
    
    // 网络速度测量
    private long mLastDownloadTime = 0;
    private long mLastDownloadBytes = 0;
    private double mDownloadSpeed = 0; // bytes/ms

    public OptimizedBufferedSMBDataSource(SmbRandomAccessFile smbFile, long size) {
        this.mFile = smbFile;
        this.mFileSize = size;
        
        // 初始化缓冲池
        for (int i = 0; i < NUM_BUFFERS; i++) {
            mBuffers[i] = new BufferBlock();
        }
    }
    
    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (mClosed.get()) {
            return -1;
        }
        
        if (position >= mFileSize) {
            return -1; // EOF
        }
        
        // 确保请求不会超过文件大小
        int bytesToRead = (int) Math.min(size, mFileSize - position);
        if (bytesToRead <= 0) {
            return 0;
        }
        
        mLastReadPosition.set(position);
        int totalRead = 0;
        long startTime = System.currentTimeMillis();
        
        while (totalRead < bytesToRead && !mClosed.get()) {
            long currentPos = position + totalRead;
            int remaining = bytesToRead - totalRead;
            
            // 1. 尝试从缓存读取
            BufferBlock cached = findInCache(currentPos, remaining);
            if (cached != null) {
                int read = cached.read(currentPos, buffer, offset + totalRead, remaining);
                if (read > 0) {
                    totalRead += read;
                    mCacheHits.incrementAndGet();
                    continue;
                }
            }
            
            // 2. 缓存未命中，从网络读取
            mCacheMisses.incrementAndGet();
            
            // 计算要读取的块大小（至少读取MIN_READ_SIZE，避免小碎片）
            int readSize = Math.max(remaining, MIN_READ_SIZE);
            readSize = (int) Math.min(readSize, mFileSize - currentPos);
            readSize = Math.min(readSize, BUFFER_SIZE);
            
            // 读取块
            BufferBlock newBlock = readBlockFromNetwork(currentPos, readSize);
            if (newBlock == null || newBlock.length <= 0) {
                break; // 读取失败或EOF
            }
            
            // 放入缓存
            addToCache(newBlock);
            
            // 从新块中复制数据
            int read = newBlock.read(currentPos, buffer, offset + totalRead, remaining);
            if (read <= 0) {
                break;
            }
            
            totalRead += read;
            
            // 3. 异步预读后续数据（智能预读）
            scheduleSmartPreload(currentPos + read);
        }
        
        mTotalBytesRead.addAndGet(totalRead);
        
        // 测量网络速度
        if (totalRead > 0) {
            long now = System.currentTimeMillis();
            if (mLastDownloadTime > 0) {
                long timeDiff = now - mLastDownloadTime;
                if (timeDiff > 0) {
                    mDownloadSpeed = (totalRead * 1.0) / timeDiff;
                }
            }
            mLastDownloadTime = now;
            mLastDownloadBytes = totalRead;
        }
        
        return totalRead;
    }
    
    private BufferBlock readBlockFromNetwork(long position, int size) throws IOException {
        if (size <= 0 || position >= mFileSize) {
            return null;
        }
        
        synchronized (mFile) {
            if (position != mFile.getFilePointer()) {
                mFile.seek(position);
            }
            
            int bytesToRead = (int) Math.min(size, mFileSize - position);
            BufferBlock block = getAvailableBufferBlock();
            
            int bytesRead = 0;
            while (bytesRead < bytesToRead) {
                int read = mFile.read(block.data, bytesRead, bytesToRead - bytesRead);
                if (read <= 0) {
                    break;
                }
                bytesRead += read;
            }
            
            if (bytesRead > 0) {
                block.startPos = position;
                block.length = bytesRead;
                block.lastAccessTime = System.currentTimeMillis();
                return block;
            }
        }
        
        return null;
    }
    
    private BufferBlock findInCache(long position, int size) {
        mCacheLock.lock();
        try {
            // 快速查找：通过位置映射
            Long blockStart = position - (position % BUFFER_SIZE);
            Integer index = mPositionToIndex.get(blockStart);
            
            if (index != null && index >= 0 && index < NUM_BUFFERS) {
                BufferBlock block = mBuffers[index];
                if (block.contains(position, size)) {
                    // 更新LRU
                    mLruMap.remove(blockStart);
                    mLruMap.put(blockStart, index);
                    return block;
                }
            }
            
            // 线性搜索后备
            for (int i = 0; i < NUM_BUFFERS; i++) {
                BufferBlock block = mBuffers[i];
                if (block.contains(position, size)) {
                    // 更新映射
                    mPositionToIndex.put(block.startPos, i);
                    mLruMap.remove(block.startPos);
                    mLruMap.put(block.startPos, i);
                    return block;
                }
            }
            
            return null;
        } finally {
            mCacheLock.unlock();
        }
    }
    
    private BufferBlock getAvailableBufferBlock() {
        mCacheLock.lock();
        try {
            // 1. 首先尝试找到空块
            for (int i = 0; i < NUM_BUFFERS; i++) {
                if (mBuffers[i].startPos < 0) {
                    return mBuffers[i];
                }
            }
            
            // 2. LRU替换：找到最久未访问的块
            Map.Entry<Long, Integer> eldest = null;
            for (Map.Entry<Long, Integer> entry : mLruMap.entrySet()) {
                eldest = entry;
                break; // 第一个就是最旧的
            }
            
            if (eldest != null) {
                int index = eldest.getValue();
                // 清理旧映射
                mPositionToIndex.remove(mBuffers[index].startPos);
                mLruMap.remove(eldest.getKey());
                
                BufferBlock block = mBuffers[index];
                block.clear();
                return block;
            }
            
            // 3. 如果没有LRU记录，随机替换第一个
            BufferBlock block = mBuffers[0];
            mPositionToIndex.remove(block.startPos);
            mLruMap.remove(block.startPos);
            block.clear();
            return block;
        } finally {
            mCacheLock.unlock();
        }
    }
    
    private void addToCache(BufferBlock block) {
        if (block == null || block.startPos < 0) {
            return;
        }
        
        mCacheLock.lock();
        try {
            Long blockStart = block.startPos;
            
            // 检查是否已存在
            if (mPositionToIndex.containsKey(blockStart)) {
                return;
            }
            
            // 获取可用块
            BufferBlock targetBlock = getAvailableBufferBlock();
            if (targetBlock != null) {
                // 复制数据
                System.arraycopy(block.data, 0, targetBlock.data, 0, block.length);
                targetBlock.startPos = block.startPos;
                targetBlock.length = block.length;
                targetBlock.lastAccessTime = block.lastAccessTime;
                
                // 更新映射
                for (int i = 0; i < NUM_BUFFERS; i++) {
                    if (mBuffers[i] == targetBlock) {
                        mPositionToIndex.put(blockStart, i);
                        mLruMap.put(blockStart, i);
                        break;
                    }
                }
            }
        } finally {
            mCacheLock.unlock();
        }
    }
    
    private void scheduleSmartPreload(long startPosition) {
        if (mClosed.get() || startPosition >= mFileSize) {
            return;
        }
        
        // 动态计算预读取数量
        int preloadCount = calculatePreloadCount();
        
        for (int i = 1; i <= preloadCount; i++) {
            long preloadPos = startPosition + (i * BUFFER_SIZE);
            if (preloadPos >= mFileSize) {
                break;
            }
            
            // 检查是否已在缓存中
            boolean alreadyCached = false;
            mCacheLock.lock();
            try {
                Long blockStart = preloadPos - (preloadPos % BUFFER_SIZE);
                alreadyCached = mPositionToIndex.containsKey(blockStart);
            } finally {
                mCacheLock.unlock();
            }
            
            if (!alreadyCached) {
                final long pos = preloadPos;
                mExecutor.submit(() -> {
                    try {
                        BufferBlock block = readBlockFromNetwork(pos, BUFFER_SIZE);
                        if (block != null && block.length > 0) {
                            addToCache(block);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                
                // 限制并发预读取任务数
                if (i >= MAX_PRELOAD_TASKS) {
                    break;
                }
            }
        }
    }
    
    private int calculatePreloadCount() {
        // 根据网络速度和播放位置动态调整
        int baseCount = 4; // 基础预读取数
        
        if (mDownloadSpeed > 0) {
            // 如果网络速度快，增加预读取
            if (mDownloadSpeed > 1024 * 1024 / 1000.0) { // > 1MB/s
                return baseCount * 2;
            } else if (mDownloadSpeed < 100 * 1024 / 1000.0) { // < 100KB/s
                return Math.max(1, baseCount / 2);
            }
        }
        
        // 根据文件剩余大小调整
        long remaining = mFileSize - mLastReadPosition.get();
        if (remaining < 10 * BUFFER_SIZE) {
            return Math.max(1, baseCount / 2); // 文件快结束了，减少预读取
        }
        
        return baseCount;
    }
    
    @Override
    public long getSize() throws IOException {
        return mFileSize;
    }
    
    @Override
    public void close() throws IOException {
        if (mClosed.compareAndSet(false, true)) {
            mExecutor.shutdownNow();
            synchronized (mFile) {
                mFile.close();
            }
            printStats();
        }
    }
    
    private void printStats() {
        long hits = mCacheHits.get();
        long misses = mCacheMisses.get();
        long total = hits + misses;
        
        if (total > 0) {
            double hitRate = (double) hits / total * 100;
            System.out.println(String.format(
                "Cache Stats: Hits=%d, Misses=%d, HitRate=%.2f%%, DownloadSpeed=%.2fKB/s",
                hits, misses, hitRate, mDownloadSpeed
            ));
        }
    }
}