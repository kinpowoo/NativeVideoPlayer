package com.jhkj.gl_player.data_source_imp;

import android.annotation.SuppressLint;
import android.media.MediaDataSource;

import java.io.IOException;

import jcifs.smb.SmbException;
import jcifs.smb.SmbRandomAccessFile;

@SuppressLint("NewApi")
public class SMBDataSource extends MediaDataSource {
    private SmbRandomAccessFile mFile; // must not Main UI thread.
    private long mFileSize;

    public SMBDataSource(SmbRandomAccessFile smbFile, long size) throws SmbException {
        this.mFile = smbFile;
        mFileSize = size;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (mFile.getFilePointer() != position)
            mFile.seek(position);

        if (size <= 0) {
            return 0;
        }

        return mFile.read(buffer, 0, size);
    }

    @Override
    public long getSize() throws IOException {
        return mFileSize;
    }

    @Override
    public void close() throws IOException {
        mFileSize = 0;
        if (mFile != null) {
            mFile.close();
            mFile = null;
        }
    }
}
