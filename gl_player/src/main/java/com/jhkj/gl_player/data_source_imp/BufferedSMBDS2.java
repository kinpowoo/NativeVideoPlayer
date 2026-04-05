package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class BufferedSMBDS2 extends MediaDataSource {
    private static final int BUFFER_SIZE = 512 * 1024; // 512KB缓冲块
    private static final int NUM_BUFFERS = 8; // 缓冲块数量
    private static final int PRELOAD_AHEAD = 2; // 预读块数

    private final SmbRandomAccessFile mFile;
    private final long mFileSize;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);

    // 使用数组管理缓冲块，简化查找逻辑
    private final BufferBlock[] mBuffers = new BufferBlock[NUM_BUFFERS];

    // 读取统计
    private long mTotalBytesRead = 0;
    private long mCacheHits = 0;
    private long mCacheMisses = 0;
    private final AtomicBoolean mClosed = new AtomicBoolean(false);
    private Future<?> mPreloadTask;

    // 缓冲块结构
    private static class BufferBlock {
        final long startPos;
        final byte[] data;
        final int length;
        final long timestamp;

        BufferBlock(long startPos, byte[] data, int length) {
            this.startPos = startPos;
            this.data = data;
            this.length = length;
            this.timestamp = System.currentTimeMillis();
        }

        boolean contains(long position, int size) {
            return position >= startPos && (position + size) <= (startPos + length);
        }

        // 获取从指定位置开始的字节
        int getBytes(long position, byte[] dest, int destOffset, int maxLength) {
            int bufferOffset = (int)(position - startPos);
            int bytesToCopy = Math.min(maxLength, length - bufferOffset);
            if (bytesToCopy > 0) {
                System.arraycopy(data, bufferOffset, dest, destOffset, bytesToCopy);
            }
            return bytesToCopy;
        }
    }

    public BufferedSMBDS2(SmbRandomAccessFile smbFile, long size) {
        this.mFile = smbFile;
        this.mFileSize = size;

        // 初始化空缓冲块
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

        int totalRead = 0;

        while (totalRead < bytesToRead && !mClosed.get()) {
            long currentPos = position + totalRead;
            int remaining = bytesToRead - totalRead;

            // 1. 尝试从缓存读取
            BufferBlock cachedBlock = findInCache(currentPos, remaining);
            if (cachedBlock != null && cachedBlock.startPos >= 0) {
                int bytesCopied = cachedBlock.getBytes(currentPos, buffer, offset + totalRead, remaining);
                if (bytesCopied > 0) {
                    totalRead += bytesCopied;
                    mCacheHits++;
                    continue;
                }
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
            int bytesCopied = newBlock.getBytes(currentPos, buffer, offset + totalRead, remaining);
            if (bytesCopied > 0) {
                totalRead += bytesCopied;
            } else {
                break; // 没有读取到数据
            }

            // 3. 异步预读后续数据
            if (mPreloadTask == null || mPreloadTask.isDone()) {
                startPreload(currentPos + bytesCopied);
            }
        }

        mTotalBytesRead += totalRead;
        return totalRead;
    }

    private BufferBlock readBlockFromNetwork(long position) throws IOException {
        synchronized (mFile) {
            if (mFile.getFilePointer() != position) {
                mFile.seek(position);
            }

            byte[] data = new byte[BUFFER_SIZE];
            int bytesRead = 0;
            int totalRead = 0;

            // 读取直到填满缓冲块或到达文件末尾
            while (totalRead < BUFFER_SIZE) {
                int toRead = Math.min(BUFFER_SIZE - totalRead, 8192); // 每次读取8KB
                bytesRead = mFile.read(data, totalRead, toRead);

                if (bytesRead == -1) {
                    break; // 到达文件末尾
                }

                totalRead += bytesRead;
            }

            if (totalRead > 0) {
                return new BufferBlock(position, data, totalRead);
            }
        }
        return null;
    }

    private BufferBlock findInCache(long position, int size) {
        BufferBlock bestMatch = null;
        long oldestTimestamp = Long.MAX_VALUE;

        // 查找包含请求范围的缓冲块，优先使用最近使用的
        for (BufferBlock block : mBuffers) {
        if (block != null && block.startPos >= 0 && block.contains(position, size)) {
            // 如果有多个块包含请求，选择最近使用的（时间戳最大的）
            if (bestMatch == null || block.timestamp > bestMatch.timestamp) {
                bestMatch = block;
            }
        }
    }

        if (bestMatch != null) {
            // 更新时间戳，表示最近使用
            // 注意：这里我们创建新对象来更新时间戳，实际上可以优化
            return bestMatch;
        }

        return null;
    }

    private void addToCache(BufferBlock block) {
        // 使用循环替换策略，替换最旧的块
        int oldestIndex = 0;
        long oldestTimestamp = Long.MAX_VALUE;

        for (int i = 0; i < NUM_BUFFERS; i++) {
        if (mBuffers[i] == null || mBuffers[i].startPos < 0) {
            // 找到空槽
            mBuffers[i] = block;
            return;
        }

        if (mBuffers[i].timestamp < oldestTimestamp) {
            oldestTimestamp = mBuffers[i].timestamp;
            oldestIndex = i;
        }
    }

        // 替换最旧的块
        mBuffers[oldestIndex] = block;
    }

    private void startPreload(long startPosition) {
        if (mClosed.get() || startPosition >= mFileSize) {
            return;
        }

        mPreloadTask = mExecutor.submit(() -> {
        try {
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
                    BufferBlock block = readBlockFromNetwork(preloadPos);
                    if (block != null && block.length > 0) {
                        addToCache(block);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
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

            // 清空缓冲块
            Arrays.fill(mBuffers, null);

            synchronized (mFile) {
                mFile.close();
            }

            // 打印缓存统计
            printStats();
        }
    }

    private void printStats() {
        double hitRate = (mCacheHits + mCacheMisses) > 0 ?
        (double) mCacheHits / (mCacheHits + mCacheMisses) * 100 : 0;

        System.out.println("SMBDataSource Statistics:");
        System.out.println("  Total Bytes Read: " + mTotalBytesRead);
        System.out.println("  Cache Hits: " + mCacheHits);
        System.out.println("  Cache Misses: " + mCacheMisses);
        System.out.println("  Cache Hit Rate: " + String.format("%.2f", hitRate) + "%");
    }
}