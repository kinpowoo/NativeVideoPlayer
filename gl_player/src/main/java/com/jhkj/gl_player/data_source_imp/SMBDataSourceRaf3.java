package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class SMBDataSourceRaf3 extends MediaDataSource {
    private final SmbRandomAccessFile smbFile;
    private final RandomAccessFile cacheFile;
    private final long fileSize;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    // 使用读写锁分离读写
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    // 独立指针
    private long readPos = 0;
    private long writePos = 0;

    public SMBDataSourceRaf3(File cacheFile, SmbRandomAccessFile smbFile, long size) throws IOException {
        this.smbFile = smbFile;
        this.fileSize = size;
        this.cacheFile = new RandomAccessFile(cacheFile, "rw");

        // 启动后台写入线程
        Thread writer = new Thread(this::continuousWrite);
        writer.start();
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (position >= fileSize) return -1;

        int bytesToRead = (int) Math.min(size, fileSize - position);

        readLock.lock();
        try {
            // 等待数据可用
            waitForData(position, bytesToRead);

            // 从缓存读取
            cacheFile.seek(position);
            int bytesRead = cacheFile.read(buffer, offset, bytesToRead);

            if (bytesRead > 0) {
                readPos = position + bytesRead;
            }

            return bytesRead;
        } finally {
            readLock.unlock();
        }
    }

    private void waitForData(long position, int size) {
        long endPos = position + size;
        int maxWait = 100; // 最多等待100ms

        writeLock.lock();
        try {
            while (endPos > writePos && maxWait-- > 0) {
                try {
                    // 通知写入线程当前位置
                    readPos = position;
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void continuousWrite() {
        byte[] buffer = new byte[4 * 1024 * 1024]; // 4MB缓冲区
        int readSize = 0;

        try {
            while (!stopped.get() && writePos < fileSize) {
                writeLock.lock();
                try {
                    // 从当前写入位置开始
                    smbFile.seek(writePos);

                    // 计算要读取的大小
                    int toRead = (int) Math.min(buffer.length, fileSize - writePos);
                    readSize = smbFile.read(buffer, 0, toRead);

                    if (readSize > 0) {
                        // 写入缓存文件
                        cacheFile.seek(writePos);
                        cacheFile.write(buffer, 0, readSize);
                        writePos += readSize;

                        // 如果落后太多，跳到读取位置
                        if (writePos < readPos - (10 * 1024 * 1024)) { // 落后超过10MB
                            writePos = readPos;
                        }
                    } else if (readSize == -1) {
                        break; // EOF
                    }
                } finally {
                    writeLock.unlock();
                }

                // 短暂休眠避免CPU占用过高
                Thread.sleep(10);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getSize() {
        return fileSize;
    }

    @Override
    public void close() {
        stopped.set(true);
        try {
            if (smbFile != null) smbFile.close();
            if (cacheFile != null) cacheFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}