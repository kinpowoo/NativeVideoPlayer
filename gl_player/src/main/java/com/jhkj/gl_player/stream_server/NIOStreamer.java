package com.jhkj.gl_player.stream_server;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import jcifs.smb.SmbFile;

public class NIOStreamer extends NIOStreamServer {
    
    public static final int PORT = 7871;
    public static final String URL = "http://127.0.0.1:" + PORT;
    private SmbFile file;
    protected List<SmbFile> extras;
    private static NIOStreamer instance;
    private static final Pattern pattern = Pattern.compile("^.*\\.(?i)(mp3|wma|wav|aac|ogg|m4a|flac|mp4|avi|mpg|mpeg|3gp|3gpp|mkv|flv|rmvb)$");

    public NIOStreamer(int port) throws IOException {
        super(port);
    }

    public static NIOStreamer getInstance() {
        if (instance == null) {
            try {
                instance = new NIOStreamer(PORT);
                instance.start();
            } catch (IOException e) {
                Log.e("NIOStreamer", "启动服务器失败", e);
            }
        }
        return instance;
    }

    public static boolean isStreamMedia(SmbFile file) {
        return pattern.matcher(file.getName()).matches();
    }

    public void setStreamSrc(SmbFile file, List<SmbFile> extraFiles) {
        this.file = file;
        this.extras = extraFiles;
    }

    @Override
    public Response serve(String uri, String method, Properties header, 
                         Properties parms, Properties files) {
        Response res = null;
        SmbFile sourceFile = null;
        String name = getNameFromPath(uri);

        if (file != null && file.getName().equals(name)) {
            sourceFile = file;
        } else if (extras != null) {
            for (SmbFile i : extras) {
                if (i != null && i.getName().equals(name)) {
                    sourceFile = i;
                    break;
                }
            }
        }

        if (sourceFile == null) {
            res = new Response(HTTP_NOTFOUND, MIME_PLAINTEXT, null);
        } else {
            final StreamSource source = new SmbStreamSource(sourceFile);
            res = new Response(HTTP_OK, getMimeType(sourceFile.getName()), source);

            // 添加内容长度头（父类会自动处理）
            try {
                res.addHeader("Content-Length", String.valueOf(source.length()));
            } catch (IOException e) {
                Log.e("NIOStreamer", "获取文件长度失败", e);
            }
        }
        return res;
    }

    private String getMimeType(String filename) {
        if (filename.endsWith(".mp4") || filename.endsWith(".m4v")) {
            return "video/mp4";
        } else if (filename.endsWith(".avi")) {
            return "video/x-msvideo";
        } else if (filename.endsWith(".mkv")) {
            return "video/x-matroska";
        } else if (filename.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (filename.endsWith(".flac")) {
            return "audio/flac";
        } else {
            return MIME_DEFAULT_BINARY;
        }
    }

    public static String getNameFromPath(String path) {
        if (path == null || path.length() < 2) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        if (slash == -1) {
            return path;
        } else {
            return path.substring(slash + 1);
        }
    }
    
    // 停止服务器
    public void shutdown() {
        stop();
        instance = null;
    }
}
