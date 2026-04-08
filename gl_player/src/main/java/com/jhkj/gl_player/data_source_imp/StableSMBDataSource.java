package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;
import android.util.Log;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class StableSMBDataSource extends MediaDataSource {
    private static final String TAG = "StableSMB";
    private static final int BLOCK_SIZE = 256 * 1024;
    private static final int MAX_BLOCKS = 64;

    private final SmbRandomAccessFile mSmbFile;
    private final long mFileSize;
    private final BufferBlock[] mCache;
    private int mNextWriteIndex = 0;

    private final ReentrantLock mNetLock = new ReentrantLock();
    private final AtomicBoolean mIsClosed = new AtomicBoolean(false);

    // 关键优化：使用饱和策略为 DiscardOldestPolicy 的线程池，防止任务无限堆积
    private final ThreadPoolExecutor mFetchExecutor = new ThreadPoolExecutor(
            2, 4, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(20),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public StableSMBDataSource(SmbRandomAccessFile smbFile, long size) {
        this.mSmbFile = smbFile;
        this.mFileSize = size;
        this.mCache = new BufferBlock[MAX_BLOCKS];
        for (int i = 0; i < MAX_BLOCKS; i++) {
            mCache[i] = new BufferBlock();
        }
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (mIsClosed.get() || position >= mFileSize) return -1;

        int bytesToRead = (int) Math.min(size, mFileSize - position);
        BufferBlock hit = findInCache(position);

        if (hit == null) {
            triggerAsyncFetch(position);

            // 优化：减少轮询时间，增加退出检查
            for (int i = 0; i < 50 && !mIsClosed.get(); i++) {
                try {
                    Thread.sleep(10);
                    hit = findInCache(position);
                    if (hit != null) break;
                } catch (InterruptedException e) {
                    return -1;
                }
            }
        }

        if (hit != null) {
            int blockOffset = (int) (position - hit.startPos);
            int available = (int) (hit.length - blockOffset);
            int copySize = Math.min(bytesToRead, available);
            if (copySize > 0) {
                System.arraycopy(hit.data, blockOffset, buffer, offset, copySize);
                // 顺便触发预取
                if (hit.startPos + hit.length < mFileSize) {
                    triggerAsyncFetch(hit.startPos + hit.length);
                }
                return copySize;
            }
        }

        // 依然没命中，执行有限制的同步读
        return safeSyncRead(position, buffer, offset, bytesToRead);
    }

    private synchronized BufferBlock findInCache(long pos) {
        for (BufferBlock b : mCache) {

            if (b.isReady && pos >= b.startPos && pos < b.startPos + b.length) {
                return b;
            }
        }
        return null;
    }

    private void triggerAsyncFetch(long pos) {
        if (mIsClosed.get()) return;
        mFetchExecutor.execute(() -> {
            try {
                if (mIsClosed.get() || findInCache(pos) != null) return;

                // 关键优化：tryLock 增加超时，防止线程死在锁上
                if (mNetLock.tryLock(500, TimeUnit.MILLISECONDS)) {
                    try {
                        if (mIsClosed.get()) return;
                        mSmbFile.seek(pos);
                        BufferBlock block = mCache[mNextWriteIndex];
                        block.isReady = false;
                        int read = mSmbFile.read(block.data, 0, BLOCK_SIZE);
                        if (read > 0) {
                            block.startPos = pos;
                            block.length = read;
                            block.isReady = true;
                            mNextWriteIndex = (mNextWriteIndex + 1) % MAX_BLOCKS;
                        }
                    } finally {
                        mNetLock.unlock();
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private int safeSyncRead(long pos, byte[] buf, int off, int size) {
        // 同步读也增加超时，绝对不能阻塞主线程太久
        try {
            if (mNetLock.tryLock(1000, TimeUnit.MILLISECONDS)) {
                try {
                    if (mIsClosed.get()) return 0;
                    mSmbFile.seek(pos);
                    return mSmbFile.read(buf, off, size);
                } finally {
                    mNetLock.unlock();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Sync read failed or timeout");
        }
        return 0; // 返回 0 触发播放器缓冲，而不是卡死
    }

    @Override public long getSize() { return mFileSize; }

    @Override public void close() throws IOException {
        if (mIsClosed.compareAndSet(false, true)) {
            // 1. 先停止线程池，不再接受新任务
            mFetchExecutor.shutdownNow();

            // 2. 尝试在子线程关闭文件，防止 UI 线程在 close() 上阻塞导致的 ANR
            new Thread(() -> {
                try {
                    // 给一定的缓冲时间让正在进行的 IO 结束
                    if (mNetLock.tryLock(2000, TimeUnit.MILLISECONDS)) {
                        try {
                            mSmbFile.close();
                            Log.d(TAG, "SMB File closed safely");
                        } finally {
                            mNetLock.unlock();
                        }
                    } else {
                        // 如果 2 秒都拿不到锁，说明 IO 彻底卡死，强制关闭
                        mSmbFile.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Close error", e);
                }
            }).start();
        }
    }

    private static class BufferBlock {
        byte[] data = new byte[BLOCK_SIZE];
        long startPos = -1;
        int length = 0;
        volatile boolean isReady = false;
    }
}