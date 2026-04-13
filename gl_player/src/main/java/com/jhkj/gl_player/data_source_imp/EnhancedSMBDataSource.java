package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class EnhancedSMBDataSource extends MediaDataSource {
    private static final String TAG = "EnhancedSMB";

    // 配置参数
    private static final int BLOCK_SIZE = 1024 * 1024; // 每块 1MB
    private static final int MAX_LRU_BLOCKS = 12;      // 动态缓存 12MB
    private static final int SPECIAL_CACHE_SIZE = 1024 * 1024; // 头尾固定缓存各 1MB

    private final SmbRandomAccessFile mSmbFile;
    private final long mFileSize;
    
    // 缓存容器
    private byte[] mHeadCache; // 文件头缓存
    private byte[] mTailCache; // 文件尾缓存
    private final LruCache<Long, byte[]> mDynamicCache; 
    
    private final ReentrantLock mNetLock = new ReentrantLock();
    private final AtomicBoolean mIsClosed = new AtomicBoolean(false);

    private final ThreadPoolExecutor mFetchExecutor = new ThreadPoolExecutor(
            1, 2, 5L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public EnhancedSMBDataSource(SmbRandomAccessFile smbFile, long size) {
        this.mSmbFile = smbFile;
        this.mFileSize = size;
        
        // 初始化 LRU 缓存
        mDynamicCache = new LruCache<Long, byte[]>(MAX_LRU_BLOCKS) {
            @Override
            protected void entryRemoved(boolean evicted, Long key, byte[] oldValue, byte[] newValue) {
                // 如果需要对象池回收 byte[]，可以在这里处理
            }
        };

        // 异步预加载头尾
        preloadSpecialSections();
    }

    private void preloadSpecialSections() {
        mFetchExecutor.execute(() -> {
            try {
                mNetLock.lock();
                // 加载头部
                mHeadCache = new byte[(int) Math.min(SPECIAL_CACHE_SIZE, mFileSize)];
                mSmbFile.seek(0);
                mSmbFile.read(mHeadCache, 0, mHeadCache.length);
                
                // 加载尾部
                if (mFileSize > SPECIAL_CACHE_SIZE) {
                    mTailCache = new byte[SPECIAL_CACHE_SIZE];
                    mSmbFile.seek(mFileSize - SPECIAL_CACHE_SIZE);
                    mSmbFile.read(mTailCache, 0, SPECIAL_CACHE_SIZE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Preload head/tail failed", e);
            } finally {
                mNetLock.unlock();
            }
        });
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (mIsClosed.get() || position >= mFileSize) return -1;

        int bytesToRead = (int) Math.min(size, mFileSize - position);

        // 1. 尝试从固定头缓存读取
        if (position < SPECIAL_CACHE_SIZE && mHeadCache != null) {
            int copySize = Math.min(bytesToRead, (int) (mHeadCache.length - position));
            if (copySize > 0) {
                System.arraycopy(mHeadCache, (int) position, buffer, offset, copySize);
                return copySize;
            }
        }

        // 2. 尝试从固定尾缓存读取
        if (position >= mFileSize - SPECIAL_CACHE_SIZE && mTailCache != null) {
            int tailOffset = (int) (position - (mFileSize - SPECIAL_CACHE_SIZE));
            int copySize = Math.min(bytesToRead, mTailCache.length - tailOffset);
            if (copySize > 0) {
                System.arraycopy(mTailCache, tailOffset, buffer, offset, copySize);
                return copySize;
            }
        }

        // 3. 尝试从 LRU 动态缓存读取
        long blockId = position / BLOCK_SIZE;
        byte[] blockData = mDynamicCache.get(blockId);
        
        if (blockData != null) {
            int innerOffset = (int) (position % BLOCK_SIZE);
            int copySize = Math.min(bytesToRead, BLOCK_SIZE - innerOffset);
            System.arraycopy(blockData, innerOffset, buffer, offset, copySize);
            
            // 顺便触发下一块的异步预取
            triggerAsyncFetch((blockId + 1) * BLOCK_SIZE);
            return copySize;
        }

        // 4. 缓存未命中：执行同步读取并填充 LRU
        return syncReadAndCache(position, buffer, offset, bytesToRead);
    }

    private int syncReadAndCache(long pos, byte[] buf, int off, int size) {
        try {
            // 设置较短的超时，避免播放器 ANR
            if (mNetLock.tryLock(3000, TimeUnit.MILLISECONDS)) {
                try {
                    if (mIsClosed.get()) return 0;
                    
                    long blockId = pos / BLOCK_SIZE;
                    long blockStartPos = blockId * BLOCK_SIZE;
                    
                    mSmbFile.seek(blockStartPos);
                    byte[] newBlock = new byte[BLOCK_SIZE];
                    int totalRead = mSmbFile.read(newBlock, 0, BLOCK_SIZE);
                    
                    if (totalRead > 0) {
                        mDynamicCache.put(blockId, newBlock);
                        
                        // 从刚读到的块中返回请求的数据
                        int innerOffset = (int) (pos - blockStartPos);
                        int copySize = Math.min(size, totalRead - innerOffset);
                        if (copySize > 0) {
                            System.arraycopy(newBlock, innerOffset, buf, off, copySize);
                            return copySize;
                        }
                    }
                } finally {
                    mNetLock.unlock();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Sync read failed: " + e.getMessage());
        }
        return 0; 
    }

    private void triggerAsyncFetch(long pos) {
        if (mIsClosed.get() || pos >= mFileSize) return;
        
        long blockId = pos / BLOCK_SIZE;
        if (mDynamicCache.get(blockId) != null) return;

        mFetchExecutor.execute(() -> {
            // tryLock(0) 表示如果现在有人在用连接，就不预取，避免抢占正常播放的 IO
            if (mNetLock.tryLock()) {
                try {
                    if (mIsClosed.get() || mDynamicCache.get(blockId) != null) return;
                    
                    mSmbFile.seek(blockId * BLOCK_SIZE);
                    byte[] newBlock = new byte[BLOCK_SIZE];
                    int read = mSmbFile.read(newBlock, 0, BLOCK_SIZE);
                    if (read > 0) {
                        mDynamicCache.put(blockId, newBlock);
                    }
                } catch (Exception ignored) {
                } finally {
                    mNetLock.unlock();
                }
            }
        });
    }

    @Override public long getSize() { return mFileSize; }

    @Override
    public void close() throws IOException {
        if (mIsClosed.compareAndSet(false, true)) {
            mFetchExecutor.shutdownNow();
            mDynamicCache.evictAll();
            new Thread(() -> {
                try {
                    if (mNetLock.tryLock(2000, TimeUnit.MILLISECONDS)) {
                        try {
                            mSmbFile.close();
                        } finally {
                            mNetLock.unlock();
                        }
                    } else {
                        mSmbFile.close(); // 强行关闭
                    }
                } catch (Exception ignored) {}
            }).start();
        }
    }
}