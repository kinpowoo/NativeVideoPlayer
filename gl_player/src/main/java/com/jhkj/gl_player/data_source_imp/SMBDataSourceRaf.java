package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jcifs.smb.SmbException;
import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class SMBDataSourceRaf extends MediaDataSource {
    private static final int BUFFER_SIZE = 10 * 1024 * 1024; // 1024KB缓冲块
    private SmbRandomAccessFile mFile; // must not Main UI thread.
    private long mFileSize;
    private RandomAccessFile tmpFileReader;
    private RandomAccessFile tmpFileWriter;
    private final AtomicBoolean isStop = new AtomicBoolean(false);
    private Thread writeThread = null;
    private final byte[] tmp = new byte[BUFFER_SIZE];

    private final Object mux = new Object();
    private final AtomicLong readPos = new AtomicLong(0);
    private final AtomicLong readPosEnd = new AtomicLong(0);

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
            tmpFileWriter = new RandomAccessFile(cacheFile,"rw");
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
        if(position >= readPos.get() && (position+size) <= readPosEnd.get()){

        }else{
            readPos.set(position);
            try {
                mux.wait(2000);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        if(tmpFileReader != null) {
            if (tmpFileReader.getFilePointer() != position) {
                tmpFileReader.seek(position);
            }
            int readLen = tmpFileReader.read(buffer, offset, size);
            if(readLen >= 0) return readLen;
        }
        return 1;
    }

    private void readBuf(long position) throws IOException {

    }

    //在子线程写入
    private void writeUnder(){
        try {
            while (!isStop.get()) {
                if(tmpFileWriter != null) {
                    long lastPos = readPos.get();
                    mFile.seek(lastPos);
                    tmpFileWriter.seek(lastPos);
                    int bytesRead;
                    long byteTotalRead = 0;
                    while((bytesRead = mFile.read(tmp,0,BUFFER_SIZE)) != -1) {
                        tmpFileWriter.write(tmp, 0, bytesRead);
                        byteTotalRead += bytesRead;
                        readPosEnd.set(lastPos+byteTotalRead);
                        mux.notify();
                        if(lastPos != readPos.get()){
                            break;
                        }
                        Thread.sleep(10);
                    }
                }
                Thread.sleep(10);
            }

            if(tmpFileReader != null){
                tmpFileReader.close();
                tmpFileReader = null;
            }
            if(tmpFileWriter != null){
                tmpFileWriter.close();
                tmpFileWriter = null;
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
//        isStop.set(true);
        if (mFile != null) {
            mFile.close();
            mFile = null;
        }
        if(writeThread != null){
            try {
                writeThread.interrupt();
            }catch (Exception e){
                e.printStackTrace();
            }
            writeThread = null;
        }
    }
}
