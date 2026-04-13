package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;
import android.util.Log;
import android.util.LruCache;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class NonBlockingSMBDataSource extends MediaDataSource {
    private static final String TAG = "NonBlockingSMB";

    private static final int BLOCK_SIZE = 512 * 1024; // 降回 512KB 减少单次 IO 时间
    private static final int MAX_LRU_BLOCKS = 16;
    private static final int HEAD_SIZE = 256 * 1024;  // 256K 足够 MP4 头部
    private static final int TAIL_SIZE = 512 * 1024;  // 512K 尾部

    private final SmbRandomAccessFile mSmbFile;
    private final long mFileSize;
    
    private byte[] mHeadCache;
    private byte[] mTailCache;
    private final LruCache<Long, byte[]> mDynamicCache;
    
    private final ReentrantLock mNetLock = new ReentrantLock();
    private final AtomicBoolean mIsClosed = new AtomicBoolean(false);

    // 使用单线程池，确保 SMB 命令是顺序发出的，避免在网络层竞争
    private final ExecutorService mFetchExecutor = Executors.newSingleThreadExecutor();

    public NonBlockingSMBDataSource(SmbRandomAccessFile smbFile, long size) {
        this.mSmbFile = smbFile;
        this.mFileSize = size;
        this.mDynamicCache = new LruCache<>(MAX_LRU_BLOCKS);
        preloadSpecialSections();
    }

    private void preloadSpecialSections() {
        mFetchExecutor.execute(() -> {
            try {
                if (mNetLock.tryLock(5, TimeUnit.SECONDS)) {
                    try {
                        mHeadCache = new byte[(int) Math.min(HEAD_SIZE, mFileSize)];
                        mSmbFile.seek(0);
                        mSmbFile.read(mHeadCache, 0, mHeadCache.length);

                        if (mFileSize > TAIL_SIZE) {
                            mTailCache = new byte[TAIL_SIZE];
                            mSmbFile.seek(mFileSize - TAIL_SIZE);
                            mSmbFile.read(mTailCache, 0, TAIL_SIZE);
                        }
                    } finally {
                        mNetLock.unlock();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Preload failed");
            }
        });
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (mIsClosed.get() || position >= mFileSize) return -1;
        int bytesToRead = (int) Math.min(size, mFileSize - position);

        // 1. 命中头/尾固定缓存
        if (position < HEAD_SIZE && mHeadCache != null) {
            int copy = Math.min(bytesToRead, mHeadCache.length - (int)position);
            System.arraycopy(mHeadCache, (int)position, buffer, offset, copy);
            return copy;
        }
        if (position >= mFileSize - TAIL_SIZE && mTailCache != null) {
            int tailOff = (int) (position - (mFileSize - TAIL_SIZE));
            int copy = Math.min(bytesToRead, mTailCache.length - tailOff);
            System.arraycopy(mTailCache, tailOff, buffer, offset, copy);
            return copy;
        }

        // 2. 命中 LRU 缓存
        long blockId = position / BLOCK_SIZE;
        byte[] blockData = mDynamicCache.get(blockId);
        if (blockData != null) {
            int innerOffset = (int) (position % BLOCK_SIZE);
            int copy = Math.min(bytesToRead, BLOCK_SIZE - innerOffset);
            System.arraycopy(blockData, innerOffset, buffer, offset, copy);
            // 预取下一块
            triggerAsyncFetch(blockId + 1);
            return copy;
        }

        // 3. 没命中：触发异步读取，并尝试短时间获取同步锁
        triggerAsyncFetch(blockId);
        
        // 核心改进：极其短的等待，如果拿不到锁，直接返回 0 让播放器等待
        return tryQuickSyncRead(position, buffer, offset, bytesToRead);
    }

    private int tryQuickSyncRead(long pos, byte[] buf, int off, int size) {
        try {
            // 只给 200ms，超过这个时间就说明网络或锁在阻塞，立刻放手
            if (mNetLock.tryLock(200, TimeUnit.MILLISECONDS)) {
                try {
                    mSmbFile.seek(pos);
                    return mSmbFile.read(buf, off, size);
                } finally {
                    mNetLock.unlock();
                }
            }
        } catch (Exception ignored) {}
        return 0; // 返回 0 触发播放器的 Loading 状态，而不是 UI 卡死
    }

    private void triggerAsyncFetch(long blockId) {
        if (mIsClosed.get() || blockId * BLOCK_SIZE >= mFileSize) return;
        if (mDynamicCache.get(blockId) != null) return;

        mFetchExecutor.execute(() -> {
            if (mDynamicCache.get(blockId) != null) return;
            try {
                if (mNetLock.tryLock(100, TimeUnit.MILLISECONDS)) {
                    try {
                        mSmbFile.seek(blockId * BLOCK_SIZE);
                        byte[] data = new byte[BLOCK_SIZE];
                        int r = mSmbFile.read(data, 0, BLOCK_SIZE);
                        if (r > 0) mDynamicCache.put(blockId, data);
                    } finally {
                        mNetLock.unlock();
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    @Override public long getSize() { return mFileSize; }
    @Override public void close() throws IOException {
        mIsClosed.set(true);
        mFetchExecutor.shutdownNow();
        // 异步关闭文件，防止 close() 阻塞主线程
        new Thread(() -> {
            try { mSmbFile.close(); } catch (Exception ignored) {}
        }).start();
    }
}