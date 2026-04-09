package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jcifs.smb.SmbException;
import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class SMBDataSourceRaf2 extends MediaDataSource {
    private static final int BUFFER_SIZE = 10 * 1024 * 1024; // 10MB缓冲块

    // 文件相关的成员变量
    private SmbRandomAccessFile mFile;
    private long mFileSize;
    private RandomAccessFile tmpFileReader;
    private RandomAccessFile tmpFileWriter;

    // 控制变量
    private final AtomicBoolean isStop = new AtomicBoolean(false);
    private Thread writeThread = null;
    private final byte[] readBuffer = new byte[BUFFER_SIZE];

    // 同步和状态变量
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private final AtomicLong readPos = new AtomicLong(0);
    private final AtomicLong writePos = new AtomicLong(0);
    private final AtomicLong cachedEndPos = new AtomicLong(0);

    // 预读控制
    private long lastRequestedPos = -1;
    private final int PRELOAD_SIZE = 2 * 1024 * 1024; // 预读2MB

    public SMBDataSourceRaf2(File cacheFile, SmbRandomAccessFile smbFile, long size) throws RuntimeException {
        this.mFile = smbFile;
        this.mFileSize = size;

        try {
            // 确保缓存文件存在且大小合适
            if (!cacheFile.exists()) {
                cacheFile.createNewFile();
            }

            // 分别打开读写文件句柄
            tmpFileReader = new RandomAccessFile(cacheFile, "r");
            tmpFileWriter = new RandomAccessFile(cacheFile, "rw");

            // 启动后台预读线程
            writeThread = new Thread(this::preloadData);
            writeThread.setPriority(Thread.MIN_PRIORITY);
            writeThread.start();

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize cache file", e);
        }
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (size <= 0 || position >= mFileSize) {
            return -1; // MediaDataSource规范：返回-1表示EOF
        }

        // 确保不超出文件边界
        if (position + size > mFileSize) {
            size = (int) (mFileSize - position);
        }

        int totalRead = 0;

        // 检查是否已经在缓存中
        if (position < cachedEndPos.get()) {
            synchronized (readLock) {
                // 从缓存文件读取
                tmpFileReader.seek(position);
                int readFromCache = tmpFileReader.read(buffer, offset, size);

                if (readFromCache > 0) {
                    totalRead = readFromCache;
                    readPos.set(position + totalRead);

                    // 触发预读
                    triggerPreload(position + totalRead);
                }
            }
        }

        // 如果缓存中没有足够数据，需要等待
        if (totalRead < size && position >= cachedEndPos.get()) {
            // 通知后台线程需要新的数据
            synchronized (writeLock) {
                readPos.set(position);
                lastRequestedPos = position;
                writeLock.notify();
            }

            // 等待数据
            int retryCount = 0;
            while (position >= cachedEndPos.get() && retryCount < 10) {
                try {
                    synchronized (writeLock) {
                        writeLock.wait(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for data", e);
                }
                retryCount++;
            }

            // 再次尝试从缓存读取
            if (position < cachedEndPos.get()) {
                synchronized (readLock) {
                    tmpFileReader.seek(position);
                    int readFromCache = tmpFileReader.read(buffer, offset, size);
                    if (readFromCache > 0) {
                        totalRead = readFromCache;
                        readPos.set(position + totalRead);
                    }
                }
            }
        }

        // 如果还是无法从缓存读取，尝试直接从SMB读取（降级处理）
        if (totalRead == 0) {
            synchronized (this) {
                mFile.seek(position);
                int directRead = mFile.read(buffer, offset, size);
                if (directRead > 0) {
                    totalRead = directRead;
                    // 异步写入缓存
                    writeToCacheAsync(position, buffer, offset, directRead);
                }
            }
        }

        return totalRead;
    }

    private void triggerPreload(long fromPosition) {
        synchronized (writeLock) {
            lastRequestedPos = Math.max(lastRequestedPos, fromPosition);
            writeLock.notify();
        }
    }

    private void writeToCacheAsync(long position, byte[] data, int offset, int length) {
        new Thread(() -> {
            try {
                synchronized (writeLock) {
                    tmpFileWriter.seek(position);
                    tmpFileWriter.write(data, offset, length);
                    // 更新缓存结束位置
                    if (position + length > cachedEndPos.get()) {
                        cachedEndPos.set(position + length);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void preloadData() {
        try {
            while (!isStop.get() && !Thread.currentThread().isInterrupted()) {
                long currentReadPos = readPos.get();
                long targetPos = Math.max(currentReadPos, lastRequestedPos);

                // 计算需要预读的数据范围
                long cacheEnd = cachedEndPos.get();

                if (targetPos < cacheEnd) {
                    // 已经有缓存，等待下一次请求
                    synchronized (writeLock) {
                        try {
                            writeLock.wait(1000);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    continue;
                }

                // 需要从SMB读取
                long startPos = cacheEnd;
                long endPos = Math.min(mFileSize, startPos + PRELOAD_SIZE);

                if (startPos >= endPos) {
                    // 文件已读取完成
                    synchronized (writeLock) {
                        try {
                            writeLock.wait(5000);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    continue;
                }

                // 从SMB读取数据
                mFile.seek(startPos);
                int totalRead = 0;

                synchronized (writeLock) {
                    tmpFileWriter.seek(startPos);

                    while (totalRead < (endPos - startPos)) {
                        int bytesToRead = (int) Math.min(
                                readBuffer.length,
                                endPos - startPos - totalRead
                        );

                        int bytesRead = mFile.read(readBuffer, 0, bytesToRead);
                        if (bytesRead <= 0) {
                            break; // EOF or error
                        }

                        tmpFileWriter.write(readBuffer, 0, bytesRead);
                        totalRead += bytesRead;

                        // 更新缓存结束位置
                        cachedEndPos.set(startPos + totalRead);

                        // 通知等待的读取线程
                        synchronized (writeLock) {
                            writeLock.notifyAll();
                        }

                        // 检查是否需要停止
                        if (isStop.get()) {
                            break;
                        }
                    }
                }

                // 短暂休眠，避免CPU占用过高
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        try {
            if (tmpFileReader != null) {
                tmpFileReader.close();
            }
            if (tmpFileWriter != null) {
                tmpFileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getSize() throws IOException {
        return mFileSize;
    }

    @Override
    public void close() throws IOException {
        isStop.set(true);

        if (writeThread != null) {
            writeThread.interrupt();
            try {
                writeThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (this) {
            if (mFile != null) {
                mFile.close();
                mFile = null;
            }
        }

        cleanup();
    }
}