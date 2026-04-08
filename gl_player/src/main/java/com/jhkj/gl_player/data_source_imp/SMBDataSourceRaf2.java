package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class SMBDataSourceRaf2 extends MediaDataSource {
    private static final String TAG = "SMBDataSource";

    private SmbRandomAccessFile mSmbFile;
    private final long mFileSize;

    private final File mCacheFile;
    private RandomAccessFile mReadRaf;
    private RandomAccessFile mWriteRaf;

    private final AtomicLong mDownloadedSize = new AtomicLong(0);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private long mCurrentRequestPos = -1;
    private final Object mLock = new Object();
    private Thread mDownloadThread;

    public SMBDataSourceRaf2(File cacheFile, SmbRandomAccessFile smbFile, long size) throws IOException {
        this.mCacheFile = cacheFile;
        this.mSmbFile = smbFile;
        this.mFileSize = size;

        if (mCacheFile.exists()) mCacheFile.delete();
        mCacheFile.createNewFile();

        this.mReadRaf = new RandomAccessFile(mCacheFile, "r");
        this.mWriteRaf = new RandomAccessFile(mCacheFile, "rw");

        // 预设文件大小，避免频繁触发文件系统分配空间
        mWriteRaf.setLength(mFileSize);

        startDownloadThread();
    }

    private void startDownloadThread() {
        mDownloadThread = new Thread(() -> {
            byte[] buffer = new byte[1024 * 512]; // 512KB buffer
            try {
                while (!isClosed.get()) {
                    long targetPos;
                    synchronized (mLock) {
                        targetPos = mCurrentRequestPos;
                        // 如果当前下载进度已经超过或等于请求进度，且没有新请求，可以适当等待或继续顺序下载
                        if (mDownloadedSize.get() >= mFileSize) break;
                    }

                    // 核心逻辑：支持 Seek。如果播放器跳到了后面，下载线程立刻重定向
                    if (targetPos != -1 && Math.abs(targetPos - mDownloadedSize.get()) > 1024 * 1024) {
                        mDownloadedSize.set(targetPos);
                        mSmbFile.seek(targetPos);
                        mWriteRaf.seek(targetPos);
                        mCurrentRequestPos = -1;
                        Log.d(TAG, "Jump download to: " + targetPos);
                    }

                    int bytesRead = mSmbFile.read(buffer);
                    if (bytesRead == -1) break;

                    mWriteRaf.write(buffer, 0, bytesRead);
                    mDownloadedSize.addAndGet(bytesRead);

                    // 唤醒正在等待数据的 readAt 线程
                    synchronized (mLock) {
                        mLock.notifyAll();
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Download error", e);
            }
        });
        mDownloadThread.start();
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (position >= mFileSize) return -1;
        if (size <= 0) return 0;

        synchronized (mLock) {
            // 如果请求的数据还没下载到，更新下载目标并等待
            if (position + size > mDownloadedSize.get()) {
                mCurrentRequestPos = position;
                // 唤醒下载线程去处理新位置
                mLock.notifyAll();

                // 阻塞等待，直到数据足够
                while (position + size > mDownloadedSize.get() && !isClosed.get()) {
                    try {
                        // 局域网环境下通常很快，设置超时防止死锁
                        mLock.wait(1000);
                    } catch (InterruptedException e) {
                        return -1;
                    }
                }
            }
        }

        // 此时数据已在本地临时文件中
        synchronized (this) {
            mReadRaf.seek(position);
            return mReadRaf.read(buffer, offset, size);
        }
    }

    @Override
    public long getSize() throws IOException {
        return mFileSize;
    }

    @Override
    public synchronized void close() throws IOException {
        isClosed.set(true);
        synchronized (mLock) {
            mLock.notifyAll();
        }
        if (mDownloadThread != null) mDownloadThread.interrupt();
        if (mReadRaf != null) mReadRaf.close();
        if (mWriteRaf != null) mWriteRaf.close();
        if (mSmbFile != null) mSmbFile.close();
        if (mCacheFile.exists()) mCacheFile.delete();
    }
}