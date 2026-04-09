package com.jhkj.gl_player.local_http_server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yanzhenjie.andserver.http.ResponseBody;
import com.yanzhenjie.andserver.util.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jcifs.smb.SmbRandomAccessFile;

public class SmbStreamBody implements ResponseBody {

    private final InputStream mStream;
    private final long mLength;
    private final MediaType mMediaType;
    private final boolean mIsChunked;

    /**
     * @param stream  之前我们包装的那个受限读取的 InputStream
     * @param length  当前分段需要读取的实际长度 (end - start + 1)
     * @param mediaType 媒体类型
     */
    public SmbStreamBody(InputStream stream, long length, MediaType mediaType) {
        this.mStream = stream;
        this.mLength = length;
        this.mMediaType = mediaType;
        // 如果长度 > 0，关闭 Chunked 模式，强制使用 Content-Length 模式
        this.mIsChunked = (length <= 0);
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public boolean isChunked() {
        return mIsChunked;
    }

    @Override
    public long contentLength() {
        // 关键点：这里返回 Long，确保 4GB 文件不会变负数
        return mLength;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return mMediaType;
    }

    @Override
    public void writeTo(@NonNull OutputStream output) throws IOException {
        // 不要直接用 IOUtils，手动写循环以便观察 SMB 的读取情况
        byte[] buffer = new byte[64 * 1024]; // 64KB 缓冲区
        int read;
        long totalWritten = 0;
        
        try {
            // 严格按照 mLength 限制读取，防止 SmbStream 读过界导致阻塞
            while (totalWritten < mLength) {
                int toRead = (int) Math.min(buffer.length, mLength - totalWritten);
                read = mStream.read(buffer, 0, toRead);
                
                if (read == -1) break;

                output.write(buffer, 0, read);
                totalWritten += read;
                
                // 某些播放器需要定期 flush 才能开始解析帧
                if (totalWritten % (512 * 1024) == 0) {
                    output.flush();
                }
            }
            output.flush();
        } finally {
            // 确保在写完后关闭 SMB 流
            if (mStream != null) {
                try {
                    mStream.close();
                } catch (IOException ignored) {}
            }
        }
    }
}