package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jcifs.smb.SmbException;
import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class BufferedSMBDataSource2 extends MediaDataSource {
    private static final int BUFFER_SIZE = 500 * 1024; // 1024KB缓冲块
    private static final int NUM_BUFFERS = 20; // 缓冲块数量
    private static final int PRELOAD_AHEAD = 4; // 预读块数
    private final AtomicLong lastReadPos = new AtomicLong(-1);
    private final SmbRandomAccessFile mFile;
    private final long mFileSize;
    // 关键优化：使用饱和策略为 DiscardOldestPolicy 的线程池，防止任务无限堆积
    private final ThreadPoolExecutor mFetchExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(20),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    Future<?> mPreloadTask = null;
    private final BufferBlock[] mBuffers = new BufferBlock[NUM_BUFFERS];
    private int mCurrentBufferIndex = 0;
    private int lastUseBufferIdx = 0;

    private final AtomicBoolean mClosed = new AtomicBoolean(false);

    public BufferedSMBDataSource2(SmbRandomAccessFile smbFile, long size) {
        this.mFile = smbFile;
        this.mFileSize = size;

        // 初始化缓冲池
        for (int i = 0; i < NUM_BUFFERS; i++) {
            mBuffers[i] = new BufferBlock(-1, new byte[BUFFER_SIZE], 0);
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
        if(lastReadPos.get() == -1L){
            lastReadPos.set(position + size);
        }else{

        }

        int totalRead = 0;

        while (totalRead < bytesToRead && !mClosed.get()) {
            long currentPos = position + totalRead;
            int remaining = bytesToRead - totalRead;

            // 1. 尝试从缓存读取
            BufferBlock cached = findInCache(currentPos, remaining);
            if (cached != null) {
                if(cached.needPreload){
                    cached.needPreload = false;
                    startPreload(position + BUFFER_SIZE);
                }
                int bufferOffset = (int) (currentPos - cached.startPos);
                int bytesToCopy = Math.min(remaining, cached.length - bufferOffset);

                System.arraycopy(cached.data, bufferOffset,
                        buffer, offset + totalRead, bytesToCopy);
                totalRead += bytesToCopy;
                if(bytesToCopy <= remaining) {
                    if(cached.length == bufferOffset || (bytesToCopy+bufferOffset >= cached.length)){
                        cached.hasData = -1;
                        lastUseBufferIdx = (lastUseBufferIdx + 1) % NUM_BUFFERS;
                    }
                    break;
                }else {
                    cached.hasData = -1;
                    lastUseBufferIdx = (lastUseBufferIdx + 1) % NUM_BUFFERS;
                    continue;
                }
            }

            // 读取一个完整的缓冲块
            BufferBlock newBlock = readBlockFromNetwork(currentPos);
            if (newBlock == null || newBlock.length <= 0) {
                break; // 读取失败或EOF
            }
            newBlock.cacheIndex = 0;
            newBlock.hasData = 1;
            newBlock.needPreload = false;
            // 放入缓存
            addToCache(newBlock);

            // 从新读取的块中复制数据
            int bufferOffset = (int) (currentPos - newBlock.startPos);
            int bytesToCopy = Math.min(remaining, newBlock.length - bufferOffset);

            System.arraycopy(newBlock.data, bufferOffset,
                    buffer, offset + totalRead, bytesToCopy);

            totalRead += bytesToCopy;

            // 3. 异步预读后续数据
            if (mPreloadTask == null || mPreloadTask.isDone()) {
                startPreload(currentPos + bytesToCopy);
            }
        }
        return totalRead;
    }

    private BufferBlock readBlockFromNetwork(long position) throws IOException {
        synchronized (mFile) {
            if (position != mFile.getFilePointer()) {
                mFile.seek(position);
            }

            byte[] data = new byte[BUFFER_SIZE];
            int bytesRead = 0;
            int totalRead = 0;

            // 读取直到填满缓冲块或到达文件末尾
            while (totalRead < BUFFER_SIZE && (bytesRead = mFile.read(data, totalRead, BUFFER_SIZE - totalRead)) != -1) {
                totalRead += bytesRead;
            }

            if (totalRead > 0) {
                BufferBlock block = mBuffers[mCurrentBufferIndex];
                block.startPos = position;
                block.data = data;
                block.hasData = 1;
                block.length = totalRead;
                return block;
//                return new BufferBlock(position, data, totalRead);
            }
        }
        return null;
    }

    private BufferBlock findInCache(long position, int size) {
        if(lastUseBufferIdx >= 0 && lastUseBufferIdx < NUM_BUFFERS){
            BufferBlock block = mBuffers[lastUseBufferIdx];
            if (block.contains(position, size)) {
                return block;
            }
        }

        int len = NUM_BUFFERS;
        int halfLen = len/2;
        int start = halfLen;
        int right = halfLen + 1;
        while(start >= 0 || right < len){
            if(start >= 0 && start != lastUseBufferIdx) {
                BufferBlock block1 = mBuffers[start];
                if (block1.contains(position, size)) {
                    lastUseBufferIdx = start;
                    return block1;
                }
            }
            if(right < len && right != lastUseBufferIdx) {
                BufferBlock block2 = mBuffers[right];
                if (block2.contains(position, size)) {
                    lastUseBufferIdx = right;
                    return block2;
                }
            }
            start -= 1;
            right += 1;
        }
        return null;
    }

    private void addToCache(BufferBlock block) {
        // 使用简单的循环替换策略
//        mBuffers[mCurrentBufferIndex] = block;
        mCurrentBufferIndex = (mCurrentBufferIndex + 1) % NUM_BUFFERS;
    }

    private void startPreload(long startPosition) throws IOException {
        if (mClosed.get() || startPosition >= mFileSize) {
            return;
        }
        for (int i = 0; i < PRELOAD_AHEAD; i++) {
            long preloadPos = startPosition + (i * BUFFER_SIZE);
            if (preloadPos >= mFileSize || mClosed.get()) {
                break;
            }
            // 检查是否已在缓存中
            boolean alreadyCached = false;
            for (BufferBlock block : mBuffers) {
                if (block != null && block.startPos <= preloadPos &&
                        (block.startPos + block.length) > preloadPos) {
                    alreadyCached = true;
                    break;
                }
            }

            if (!alreadyCached) {
                final int outIdx = i;
                mPreloadTask = mFetchExecutor.submit(() -> {
                    try {
                        BufferBlock block = readBlockFromNetwork(preloadPos);
                        if (block != null && block.length > 0) {
                            block.cacheIndex = (outIdx+1);
                            block.needPreload = ((outIdx+1) == PRELOAD_AHEAD);
                            addToCache(block);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    @Override
    public long getSize() throws IOException {
        return mFileSize;
    }

    @Override
    public void close() throws IOException {
        if (mClosed.compareAndSet(false, true)) {
            mFetchExecutor.shutdownNow();
            new Thread(){
                @Override
                public void run() {
                    super.run();
                    synchronized (mFile) {
                        try {
                            mFile.close();
                        } catch (SmbException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }.start();

            // 打印缓存统计
            printStats();
        }
    }

    private void printStats() {
//        double hitRate = mTotalBytesRead > 0 ?
//                (double) mCacheHits / (mCacheHits + mCacheMisses) * 100 : 0;

//        System.out.println("SMBDataSource Statistics:");
//        System.out.println("  Total Bytes Read: " + mTotalBytesRead);
//        System.out.println("  Cache Hits: " + mCacheHits);
//        System.out.println("  Cache Misses: " + mCacheMisses);
//        System.out.println("  Cache Hit Rate: " + String.format("%.2f", hitRate) + "%");
    }
}