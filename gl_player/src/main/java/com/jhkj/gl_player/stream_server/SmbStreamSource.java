package com.jhkj.gl_player.stream_server;

import java.io.IOException;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;

// SmbStreamSource 实现
class SmbStreamSource implements NIOStreamServer.StreamSource {
    private final SmbFile smbFile;
    private SmbRandomAccessFile randomAccessFile;
    
    public SmbStreamSource(SmbFile smbFile) {
        this.smbFile = smbFile;
    }
    
    @Override
    public void open() throws IOException {
        randomAccessFile = new SmbRandomAccessFile(smbFile, "r");
    }
    
    @Override
    public void close() throws IOException {
        if (randomAccessFile != null) {
            randomAccessFile.close();
            randomAccessFile = null;
        }
    }
    
    @Override
    public int read(byte[] buffer) throws IOException {
        if (randomAccessFile == null) {
            throw new IOException("Stream not opened");
        }
        return randomAccessFile.read(buffer);
    }
    
    @Override
    public void moveTo(long position) throws IOException {
        if (randomAccessFile == null) {
            throw new IOException("Stream not opened");
        }
        randomAccessFile.seek(position);
    }
    
    @Override
    public long length() throws IOException {
        if (randomAccessFile == null) {
            open();
        }
        return randomAccessFile.length();
    }
}