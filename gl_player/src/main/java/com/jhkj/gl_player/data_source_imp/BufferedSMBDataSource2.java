package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class BufferedSMBDataSource2 extends MediaDataSource {
    static final int BUFFER_SIZE = 600 * 1024; // 1024KB缓冲块
    private static final int NUM_BUFFERS = 32; // 缓冲块数量
    private static final int PRELOAD_AHEAD = 10; // 预读块数

    private final SmbRandomAccessFile mFile;
    private final long mFileSize;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);
    private int mCurrentBufferIndex = 0;
    private final BufferBlock[] mBuffers = new BufferBlock[NUM_BUFFERS];

    // 读取统计
    private long mTotalBytesRead = 0;
    private long mCacheHits = 0;
    private long mCacheMisses = 0;
    private final AtomicBoolean mClosed = new AtomicBoolean(false);
    private Future<?> mPreloadTask;

    public static class BufferBlock {
        long startPos = 0;
        ByteBuffer data;
        int length = 0;
        long timestamp;
        BufferBlock() {
            this.data = ByteBuffer.allocateDirect(BUFFER_SIZE);
            this.timestamp = System.currentTimeMillis();
        }

        boolean contains(long position, int size) {
            return position >= startPos && (position + size) <= (startPos + length);
        }
    }


    public BufferedSMBDataSource2(SmbRandomAccessFile smbFile, long size) {
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

        int totalRead = 0;

        while (totalRead < bytesToRead && !mClosed.get()) {
            long currentPos = position + totalRead;
            int remaining = bytesToRead - totalRead;

            // 1. 尝试从缓存读取
            BufferBlock cached = findInCache(currentPos, remaining);
            if (cached != null) {
                int bufferOffset = (int) (currentPos - cached.startPos);
                int bytesToCopy = Math.min(remaining, cached.length - bufferOffset);


//                System.arraycopy(cached.data, bufferOffset,
//                        buffer, offset + totalRead, bytesToCopy);
                // 直接从 ByteBuffer 的指定位置读取到字节数组
                cached.data.flip();
                // 保存原始position
                int originalPos = cached.data.position();
                // 定位到要读取的位置
                cached.data.position(bufferOffset);
                // 从当前position开始读取
                cached.data.get(buffer, offset + totalRead, bytesToCopy);
                // 恢复position
                cached.data.position(originalPos);

                totalRead += bytesToCopy;
                mCacheHits++;
                continue;
            }

            // 2. 缓存未命中，从网络读取
            mCacheMisses++;

            // 读取一个完整的缓冲块
            BufferBlock newBlock = readBlockFromNetwork(currentPos);
            if (newBlock == null || newBlock.length <= 0) {
                break; // 读取失败或EOF
            }

            // 放入缓存
            addToCache(newBlock);

            // 从新读取的块中复制数据
            int bufferOffset = (int) (currentPos - newBlock.startPos);
            int bytesToCopy = Math.min(remaining, newBlock.length - bufferOffset);

            newBlock.data.flip();
            // 保存原始position
            int originalPos = newBlock.data.position();
            // 定位到要读取的位置
            newBlock.data.position(bufferOffset);
            // 从当前position开始读取
            newBlock.data.get(buffer, offset + totalRead, bytesToCopy);
            // 恢复position
            newBlock.data.position(originalPos);
//            System.arraycopy(newBlock.data, bufferOffset,
//                    buffer, offset + totalRead, bytesToCopy);

            totalRead += bytesToCopy;

            // 3. 异步预读后续数据
            if (mPreloadTask == null || mPreloadTask.isDone()) {
                startPreload(currentPos + bytesToCopy);
            }
        }

        mTotalBytesRead += totalRead;
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
                block.data.clear();
                block.data.put(data,0,totalRead);
                block.startPos = position;
                block.length = totalRead;
                return block;
            }
        }
        return null;
    }

    private BufferBlock findInCache(long position, int size) {
        int len = NUM_BUFFERS;
        int halfLen = len/2;
        int start = halfLen;
        int right = halfLen + 1;
        while(start >= 0 || right < len){
            if(start >= 0) {
                BufferBlock block1 = mBuffers[start];
                if (block1.contains(position, size)) {
                    return block1;
                }
            }
            if(right < len) {
                BufferBlock block2 = mBuffers[right];
                if (block2.contains(position, size)) {
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

    private void startPreload(long startPosition) {
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
                mPreloadTask = mExecutor.submit(() -> {
                    try {
                        BufferBlock block = readBlockFromNetwork(preloadPos);
                        if (block != null && block.length > 0) {
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
            if (mPreloadTask != null) {
                mPreloadTask.cancel(true);
            }

            mExecutor.shutdownNow();
            synchronized (mFile) {
                mFile.close();
            }

            // 打印缓存统计
            printStats();
        }
    }

    private void printStats() {
        double hitRate = mTotalBytesRead > 0 ?
                (double) mCacheHits / (mCacheHits + mCacheMisses) * 100 : 0;

//        System.out.println("SMBDataSource Statistics:");
//        System.out.println("  Total Bytes Read: " + mTotalBytesRead);
//        System.out.println("  Cache Hits: " + mCacheHits);
//        System.out.println("  Cache Misses: " + mCacheMisses);
//        System.out.println("  Cache Hit Rate: " + String.format("%.2f", hitRate) + "%");
    }
}