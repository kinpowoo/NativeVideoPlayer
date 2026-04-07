package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import jcifs.smb.SmbException;
import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class SMBDataSourceRaf extends MediaDataSource {
    private static final int BUFFER_SIZE = 600 * 1024; // 1024KB缓冲块
    private final ByteBuffer byteBuf = ByteBuffer.allocate(BUFFER_SIZE);
    private final byte[] bufArr = new byte[BUFFER_SIZE];
    private SmbRandomAccessFile mFile; // must not Main UI thread.
    private long mFileSize;
    private RandomAccessFile tmpFileReader;
    private RandomAccessFile tmpFileWriter;
    private FileChannel writeChannel;
    private final AtomicBoolean isStop = new AtomicBoolean(false);
    private Thread writeThread = null;
    // 无界队列
    private final BlockingQueue<Long> unboundedQueue = new LinkedBlockingQueue<>();
    private final ExecutorService cacheThread = Executors.newSingleThreadExecutor();

    public SMBDataSourceRaf(File cacheFile, SmbRandomAccessFile smbFile, long size) throws SmbException {
        this.mFile = smbFile;
        // 如果文件不存在，创建它
        if (!cacheFile.exists()) {
            try {
                cacheFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            tmpFileReader = new RandomAccessFile(cacheFile,"r");
            tmpFileWriter = new RandomAccessFile(cacheFile,"w");
            writeChannel = tmpFileReader.getChannel();
            writeThread = new Thread(this::writeUnder);
            writeThread.start();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mFileSize = size;
    }


    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (size <= 0) {
            return 0;
        }
        try {
            if(!unboundedQueue.isEmpty()) {
                Long peek = unboundedQueue.peek();
                if(peek != null && peek != position) {
                    unboundedQueue.put(position);
                }
            }else{
                unboundedQueue.put(position);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (NullPointerException e2) {
            e2.printStackTrace();
        }

        if(tmpFileReader != null) {
            if (tmpFileReader.getFilePointer() != position){
                tmpFileReader.seek(position);
            }
            int readLen = tmpFileReader.read(buffer, offset , size);
            return readLen;
        }else{
            if (mFile.getFilePointer() != position){
                mFile.seek(position);
            }
            return mFile.read(buffer, offset , size);
        }
    }

    private void readBuf(long position) throws IOException {
        if(writeChannel != null) {
            if (mFile.getFilePointer() != position) {
                mFile.seek(position);
            }
            int bytesRead;
//            byteBuf.clear();
            byte[] tmp = new byte[BUFFER_SIZE];
            bytesRead = mFile.read(tmp);
            if(bytesRead != -1){
                tmpFileWriter.write(tmp,0,bytesRead);
//                byteBuf.put(bufArr,0,bytesRead);
//                byteBuf.flip();
//                writeChannel.position(position);
//                int writeLen = writeChannel.write(byteBuf);
//                if(writeLen2 != -1){
//
//                }
            }
        }
    }

    //在子线程写入
    private void writeUnder(){
        try {
            while (!isStop.get()) {
                long pos = unboundedQueue.take();
                readBuf(pos);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public long getSize() throws IOException {
        return mFileSize;
    }

    @Override
    public void close() throws IOException {
        mFileSize = 0;
        isStop.set(true);
        if (mFile != null) {
            mFile.close();
            mFile = null;
        }
        if(tmpFileReader != null){
            tmpFileReader.close();
            tmpFileReader = null;
        }
        if(writeChannel != null){
            writeChannel.close();
            writeChannel = null;
        }
        if(tmpFileWriter != null){
            tmpFileWriter.close();
            tmpFileWriter = null;
        }
        if(writeThread != null){
            try {
                writeThread.interrupt();
            }catch (Exception e){
                e.printStackTrace();
            }
            writeThread = null;
        }
        cacheThread.shutdown();
    }
}
