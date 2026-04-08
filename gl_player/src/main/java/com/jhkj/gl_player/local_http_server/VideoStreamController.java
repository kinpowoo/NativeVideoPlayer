package com.jhkj.gl_player.local_http_server;

import android.text.TextUtils;

import com.yanzhenjie.andserver.annotation.GetMapping;
import com.yanzhenjie.andserver.annotation.QueryParam;
import com.yanzhenjie.andserver.annotation.RestController;
import com.yanzhenjie.andserver.framework.body.StreamBody;
import com.yanzhenjie.andserver.framework.body.StringBody;
import com.yanzhenjie.andserver.http.HttpRequest;
import com.yanzhenjie.andserver.http.HttpResponse;
import com.yanzhenjie.andserver.util.MediaType;


import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;

import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;

@RestController
public class VideoStreamController {

    @GetMapping(path = "/play")
    public void streamSmb(HttpRequest request, HttpResponse response,
                          @QueryParam("url") String smbUrl,
                          @QueryParam("username") String username,
                          @QueryParam("password") String password
                          ) throws IOException {
        // 1. 关键：解码 URL。防止出现 %3A %2F 等字符导致 SmbFile 解析失败
        String decodedUrl = URLDecoder.decode(smbUrl, "UTF-8");
        String decodeUser = URLDecoder.decode(username, "UTF-8");
        String decodePass = URLDecoder.decode(password, "UTF-8");

        // 2. 过滤错误参数：如果 decodedUrl 包含了 http，说明前端传错了
        if (decodedUrl.startsWith("http")) {
            // 尝试截取真正的 smb 部分，或者直接报错
            int smbIndex = decodedUrl.indexOf("smb://");
            if (smbIndex != -1) {
                decodedUrl = decodedUrl.substring(smbIndex);
            } else {
                response.setStatus(400);
                response.setBody(new StringBody("Invalid SMB URL"));
                return;
            }
        }
        // 1. 初始化 SMB 文件
        // 注意：生产环境建议这里通过 smbUrl 获取预先缓存好的 SmbRandomAccessFile
        CIFSContext context;
        if (!TextUtils.isEmpty(username)) {
            // 如果域为空，可以传入空字符串
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                    null,
                    decodeUser,decodePass,
                    NtlmPasswordAuthenticator.AuthenticationType.USER
            );
            context = SingletonContext.getInstance().withCredentials(auth);
        } else {
            context = SingletonContext.getInstance().withGuestCrendentials();
        }
        SmbFile smbF = new SmbFile(smbUrl,context);
        final SmbRandomAccessFile smbFile = new SmbRandomAccessFile(smbF, "r");
        final long fileSize = smbFile.length();

        String rangeHeader = request.getHeader("Range");
        long start = 0;
        long end = fileSize - 1;

        // 1. 处理 Range
        if (!TextUtils.isEmpty(rangeHeader) && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring(6).split("-");
            try {
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !TextUtils.isEmpty(ranges[1])) {
                    end = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException ignored) {}

            response.setStatus(206);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
        } else {
            response.setStatus(200);
        }

        // 2. 计算当前分段长度
        long contentLength = end - start + 1;
        smbFile.seek(start);

        // 3. 关键：设置正确的响应头 (必须转为 String 避免溢出)
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Type", "video/mp4");
        response.setHeader("Content-Length", String.valueOf(contentLength));

        // 使用刚才定义的自定义 Body
        InputStream wrappedStream = createWrappedStream(smbFile, contentLength);
        response.setBody(new SmbStreamBody(wrappedStream, -1, MediaType.parseMediaType("video/mp4")));
    }

    // 简单的内部包装，确保 read 不会读过界
    private InputStream createWrappedStream(final SmbRandomAccessFile smbFile, final long limit) {
        return new InputStream() {
            private long remaining = limit;
            @Override
            public int read() throws IOException {
                if (remaining <= 0) return -1;
                int b = smbFile.read();
                if (b != -1) remaining--;
                return b;
            }
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (remaining <= 0) return -1;
                int maxRead = (int) Math.min(len, remaining);
                int read = smbFile.read(b, off, maxRead);
                if (read > 0) remaining -= read;
                return read;
            }
            @Override
            public void close() throws IOException {
                smbFile.close();
            }
        };
    }
}

