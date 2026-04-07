package com.jhkj.gl_player.stream_server;

import android.net.Uri;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public abstract class NIOStreamServer {
    // ==================================================
    // 常量定义
    // ==================================================
    public static final String
            HTTP_OK = "HTTP/1.1 200 OK\r\n",
            HTTP_PARTIALCONTENT = "HTTP/1.1 206 Partial Content\r\n",
            HTTP_RANGE_NOT_SATISFIABLE = "HTTP/1.1 416 Range Not Satisfiable\r\n",
            HTTP_FORBIDDEN = "HTTP/1.1 403 Forbidden\r\n",
            HTTP_NOTFOUND = "HTTP/1.1 404 Not Found\r\n",
            HTTP_BADREQUEST = "HTTP/1.1 400 Bad Request\r\n",
            HTTP_INTERNALERROR = "HTTP/1.1 500 Internal Server Error\r\n",
            HTTP_NOTIMPLEMENTED = "HTTP/1.1 501 Not Implemented\r\n";

    public static final String
            MIME_PLAINTEXT = "text/plain",
            MIME_HTML = "text/html",
            MIME_DEFAULT_BINARY = "application/octet-stream",
            MIME_XML = "text/xml";

    // 配置参数
    private static final int BUFFER_SIZE = 256 * 1024;          // 256KB 读写缓冲区
    private static final int FILE_BUFFER_SIZE = 512 * 1024;    // 512KB 文件传输缓冲区
    private static final int BACKLOG_SIZE = 100;               // 连接队列大小
    private static final int SELECT_TIMEOUT = 500;             // 选择器超时时间（ms）
    private static final int MAX_CONNECTIONS = 1000;           // 最大连接数
    private static final int IDLE_TIMEOUT = 30 * 1000;         // 空闲超时（30秒）
    private static final int KEEP_ALIVE_TIMEOUT = 15 * 1000;   // Keep-Alive超时（15秒）
    
    // 线程池配置
    private static final int IO_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int WORKER_THREADS = IO_THREADS * 2;
    
    // ==================================================
    // 核心组件
    // ==================================================
    private final int port;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private boolean running = false;
    private Thread acceptThread;
    
    // 线程池
    private final ExecutorService ioExecutor;
    private final ExecutorService workerExecutor;
    
    // 连接管理
    private final Map<SelectionKey, ConnectionContext> connections = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    
    // 响应头缓存
    private static final String COMMON_HEADERS = 
        "Accept-Ranges: bytes\r\n" +
        "Connection: keep-alive\r\n" +
        "Keep-Alive: timeout=" + (KEEP_ALIVE_TIMEOUT / 1000) + "\r\n" +
        "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
        "Access-Control-Allow-Origin: *\r\n" +
        "Access-Control-Allow-Methods: GET, HEAD\r\n" +
        "Access-Control-Expose-Headers: Content-Range\r\n" +
        "Server: NIOStreamServer/1.0\r\n";
    
    // GMT日期格式化
    private static final SimpleDateFormat gmtFrmt;
    
    static {
        gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public NIOStreamServer(int port) {
        this.port = port;
        
        // 创建线程池
        this.ioExecutor = Executors.newFixedThreadPool(IO_THREADS, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "NIO-IO-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        
        this.workerExecutor = Executors.newFixedThreadPool(WORKER_THREADS, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "NIO-Worker-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }
    
    public abstract Response serve(String uri, String method, Properties header, 
                                  Properties parms, Properties files);
    
    // ==================================================
    // 连接上下文
    // ==================================================
    private static class ConnectionContext {
        final SocketChannel channel;
        final ByteBuffer readBuffer;
        ByteBuffer writeBuffer;
        final StringBuilder requestBuilder = new StringBuilder();
        final Properties headers = new Properties();
        final Properties params = new Properties();
        String method;
        String uri;
        long contentLength = 0;
        long lastActivityTime = System.currentTimeMillis();
        boolean keepAlive = true;
        boolean requestComplete = false;
        boolean headersParsed = false;
        boolean isWritingFile = false;
        StreamSource dataSource;
        long bytesWritten = 0;
        long filePosition = 0;
        long fileSize = 0;
        long rangeStart = 0;
        long rangeEnd = -1;
        String contentType;
        final ReentrantLock writeLock = new ReentrantLock();
        
        ConnectionContext(SocketChannel channel) {
            this.channel = channel;
            this.readBuffer = ByteBuffer.allocateDirect(16 * 1024); // 16KB 读取缓冲区
            this.writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }
        
        void resetForNextRequest() {
            requestBuilder.setLength(0);
            headers.clear();
            params.clear();
            method = null;
            uri = null;
            contentLength = 0;
            requestComplete = false;
            headersParsed = false;
            isWritingFile = false;
            dataSource = null;
            bytesWritten = 0;
            filePosition = 0;
            fileSize = 0;
            rangeStart = 0;
            rangeEnd = -1;
            contentType = null;
            readBuffer.clear();
            writeBuffer.clear();
            lastActivityTime = System.currentTimeMillis();
        }
        
        boolean isIdle() {
            return System.currentTimeMillis() - lastActivityTime > IDLE_TIMEOUT;
        }
        
        void updateActivityTime() {
            lastActivityTime = System.currentTimeMillis();
        }
    }
    
    // ==================================================
    // 服务器启动和停止
    // ==================================================
    public void start() throws IOException {
        if (running) {
            return;
        }
        
        // 打开服务器通道
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        ServerSocket socket = serverChannel.socket();
        socket.setReuseAddress(true);
        socket.setReceiveBufferSize(128 * 1024); // 128KB接收缓冲区
        socket.bind(new InetSocketAddress(port), BACKLOG_SIZE);
        
        // 创建选择器
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        running = true;
        
        // 启动接收线程
        acceptThread = new Thread(this::acceptLoop, "NIO-Accept-Thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
        
        // 启动清理线程
        startCleanupThread();
        
        Log.d("NIOStreamServer", "服务器启动在端口: " + port);
    }
    
    public void stop() {
        running = false;
        
        try {
            if (selector != null) {
                selector.wakeup();
                selector.close();
            }
        } catch (IOException e) {
            // 忽略
        }
        
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
        } catch (IOException e) {
            // 忽略
        }
        
        if (acceptThread != null) {
            try {
                acceptThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        ioExecutor.shutdown();
        workerExecutor.shutdown();
        
        // 关闭所有连接
        for (ConnectionContext ctx : connections.values()) {
            closeConnection(ctx);
        }
        connections.clear();
        
        Log.d("NIOStreamServer", "服务器已停止");
    }
    
    // ==================================================
    // 主接收循环
    // ==================================================
    private void acceptLoop() {
        while (running) {
            try {
                // 等待事件
                int readyChannels = selector.select(SELECT_TIMEOUT);
                if (readyChannels == 0) {
                    continue;
                }
                
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    try {
                        if (key.isAcceptable()) {
                            acceptConnection(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (CancelledKeyException e) {
                        // 连接已关闭
                        closeConnection((ConnectionContext) key.attachment());
                    } catch (Exception e) {
                        Log.e("NIOStreamServer", "处理连接错误", e);
                        closeConnection((ConnectionContext) key.attachment());
                    }
                }
            } catch (ClosedSelectorException e) {
                // 选择器已关闭
                break;
            } catch (Exception e) {
                Log.e("NIOStreamServer", "选择器错误", e);
            }
        }
    }
    
    private void acceptConnection(SelectionKey key) throws IOException {
        if (connectionCount.get() >= MAX_CONNECTIONS) {
            Log.w("NIOStreamServer", "连接数超过限制: " + MAX_CONNECTIONS);
            return;
        }
        
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverSocketChannel.accept();
        
        if (clientChannel == null) {
            return;
        }
        
        // 配置客户端通道
        clientChannel.configureBlocking(false);
        Socket socket = clientChannel.socket();
        socket.setTcpNoDelay(true); // 启用TCP_NODELAY，减少小包延迟
        socket.setKeepAlive(true);  // 启用TCP Keep-Alive
        socket.setSoTimeout(0);     // 无限超时（由选择器控制）
        socket.setSendBufferSize(128 * 1024); // 128KB发送缓冲区
        socket.setReceiveBufferSize(128 * 1024); // 128KB接收缓冲区
        socket.setPerformancePreferences(1, 2, 0); // 优化性能偏好
        
        // 创建连接上下文
        ConnectionContext ctx = new ConnectionContext(clientChannel);
        
        // 注册到选择器
        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ, ctx);
        connections.put(clientKey, ctx);
        connectionCount.incrementAndGet();
        
        Log.d("NIOStreamServer", "新连接: " + socket.getRemoteSocketAddress() + 
              "，当前连接数: " + connectionCount.get());
    }
    
    // ==================================================
    // 读取处理
    // ==================================================
    private void handleRead(SelectionKey key) throws IOException {
        ConnectionContext ctx = (ConnectionContext) key.attachment();
        if (ctx == null) {
            key.cancel();
            return;
        }
        
        SocketChannel channel = ctx.channel;
        ByteBuffer buffer = ctx.readBuffer;
        
        try {
            int bytesRead = channel.read(buffer);
            if (bytesRead == -1) {
                // 连接关闭
                closeConnection(ctx);
                return;
            }
            
            if (bytesRead > 0) {
                ctx.updateActivityTime();
                buffer.flip();
                
                // 解析HTTP请求
                if (!ctx.headersParsed) {
                    parseHeaders(ctx, buffer);
                }
                
                // 如果还有剩余数据（POST请求体），跳过
                if (buffer.hasRemaining()) {
                    buffer.compact();
                } else {
                    buffer.clear();
                }
                
                // 如果请求头已解析，处理请求
                if (ctx.headersParsed && !ctx.requestComplete) {
                    ctx.requestComplete = true;
                    processRequest(ctx);
                }
            }
        } catch (IOException e) {
            Log.w("NIOStreamServer", "读取数据错误: " + e.getMessage());
            closeConnection(ctx);
        }
    }
    
    private void parseHeaders(ConnectionContext ctx, ByteBuffer buffer) {
        // 转换为字符串
        String data = StandardCharsets.UTF_8.decode(buffer).toString();
        ctx.requestBuilder.append(data);
        
        String request = ctx.requestBuilder.toString();
        
        // 检查是否收到完整的请求头（以\r\n\r\n结尾）
        int headerEnd = request.indexOf("\r\n\r\n");
        if (headerEnd == -1) {
            // 请求头还不完整
            buffer.position(buffer.limit());
            buffer.limit(buffer.capacity());
            return;
        }
        
        // 解析请求行
        String headerText = request.substring(0, headerEnd);
        String[] lines = headerText.split("\r\n");
        
        if (lines.length == 0) {
            sendError(ctx, HTTP_BADREQUEST, "Bad Request");
            return;
        }
        
        // 解析请求行
        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 2) {
            sendError(ctx, HTTP_BADREQUEST, "Bad Request");
            return;
        }
        
        ctx.method = requestLine[0];
        ctx.uri = requestLine[1];
        
        // 解析请求头
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();
                ctx.headers.put(key, value);
            }
        }
        
        // 检查Connection头
        String connection = ctx.headers.getProperty("connection");
        if (connection != null) {
            ctx.keepAlive = connection.equalsIgnoreCase("keep-alive") || 
                           connection.equalsIgnoreCase("Keep-Alive");
        }
        
        // 检查Content-Length
        String contentLength = ctx.headers.getProperty("content-length");
        if (contentLength != null) {
            try {
                ctx.contentLength = Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
                // 忽略格式错误
            }
        }
        
        ctx.headersParsed = true;
        
        // 移除已处理的请求头数据
        buffer.position(headerEnd + 4); // 跳过\r\n\r\n
    }
    
    // ==================================================
    // 请求处理
    // ==================================================
    private void processRequest(ConnectionContext ctx) {
        workerExecutor.submit(() -> {
            try {
                // 解析查询参数
                parseQueryParams(ctx);
                
                // 调用抽象方法处理请求
                Response response = serve(ctx.uri, ctx.method, ctx.headers, ctx.params, new Properties());
                
                if (response == null) {
                    sendError(ctx, HTTP_INTERNALERROR, "Internal Server Error");
                    return;
                }
                
                // 设置响应
                prepareResponse(ctx, response);
                
                // 注册写事件
                SelectionKey key = ctx.channel.keyFor(selector);
                if (key != null && key.isValid()) {
                    key.interestOps(SelectionKey.OP_WRITE);
                    selector.wakeup();
                }
            } catch (Exception e) {
                Log.e("NIOStreamServer", "处理请求错误", e);
                sendError(ctx, HTTP_INTERNALERROR, "Internal Server Error");
            }
        });
    }
    
    private void parseQueryParams(ConnectionContext ctx) {
        if (ctx.uri == null) {
            return;
        }
        
        int qmi = ctx.uri.indexOf('?');
        if (qmi >= 0) {
            String query = ctx.uri.substring(qmi + 1);
            ctx.uri = decodePercent(ctx.uri.substring(0, qmi));
            
            StringTokenizer st = new StringTokenizer(query, "&");
            while (st.hasMoreTokens()) {
                String pair = st.nextToken();
                int sep = pair.indexOf('=');
                if (sep >= 0) {
                    String key = decodePercent(pair.substring(0, sep)).trim();
                    String value = decodePercent(pair.substring(sep + 1));
                    ctx.params.put(key, value);
                } else {
                    ctx.params.put(decodePercent(pair).trim(), "");
                }
            }
        } else {
            ctx.uri = decodePercent(ctx.uri);
        }
    }
    
    private void prepareResponse(ConnectionContext ctx, Response response) {
        ctx.writeLock.lock();
        try {
            // 准备响应头
            StringBuilder headers = new StringBuilder(512);
            
            // 状态行
            headers.append(response.status != null ? response.status : HTTP_OK);
            
            // Content-Type
            if (response.mimeType != null) {
                headers.append("Content-Type: ").append(response.mimeType).append("\r\n");
            }
            
            // Content-Length 或 Transfer-Encoding
            if (response.data != null) {
                try {
                    ctx.dataSource = response.data;
                    ctx.fileSize = response.data.length();
                    ctx.contentType = response.mimeType;
                    
                    // 检查Range请求
                    String rangeHeader = ctx.headers.getProperty("range");
                    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                        String range = rangeHeader.substring(6);
                        int dash = range.indexOf('-');
                        if (dash > 0) {
                            try {
                                ctx.rangeStart = Long.parseLong(range.substring(0, dash));
                                String endStr = range.substring(dash + 1);
                                if (!endStr.isEmpty()) {
                                    ctx.rangeEnd = Long.parseLong(endStr);
                                } else {
                                    ctx.rangeEnd = ctx.fileSize - 1;
                                }
                            } catch (NumberFormatException e) {
                                // 忽略格式错误
                            }
                        }
                    }
                    
                    if (ctx.rangeStart > 0 || ctx.rangeEnd >= 0) {
                        // 部分内容
                        if (ctx.rangeStart >= ctx.fileSize) {
                            // 范围无效
                            headers.setLength(0);
                            headers.append(HTTP_RANGE_NOT_SATISFIABLE);
                            headers.append("Content-Range: bytes */").append(ctx.fileSize).append("\r\n");
                            ctx.dataSource = null;
                        } else {
                            if (ctx.rangeEnd < 0 || ctx.rangeEnd >= ctx.fileSize) {
                                ctx.rangeEnd = ctx.fileSize - 1;
                            }
                            
                            long contentLength = ctx.rangeEnd - ctx.rangeStart + 1;
                            headers.append("Content-Range: bytes ")
                                   .append(ctx.rangeStart).append("-")
                                   .append(ctx.rangeEnd).append("/")
                                   .append(ctx.fileSize).append("\r\n");
                            headers.append("Content-Length: ").append(contentLength).append("\r\n");
                            ctx.filePosition = ctx.rangeStart;
                            ctx.isWritingFile = true;
                        }
                    } else {
                        // 完整内容
                        headers.append("Content-Length: ").append(ctx.fileSize).append("\r\n");
                        ctx.filePosition = 0;
                        ctx.isWritingFile = true;
                    }
                } catch (IOException e) {
                    Log.e("NIOStreamServer", "获取数据源长度失败", e);
                    ctx.dataSource = null;
                }
            } else {
                headers.append("Content-Length: 0\r\n");
            }
            
            // 添加公共头
            headers.append(COMMON_HEADERS);
            
            // 添加自定义头
            if (response.header != null) {
                Enumeration<?> e = response.header.keys();
                while (e.hasMoreElements()) {
                    String key = (String) e.nextElement();
                    String value = response.header.getProperty(key);
                    headers.append(key).append(": ").append(value).append("\r\n");
                }
            }
            
            // Date头
            headers.append("Date: ").append(gmtFrmt.format(new Date())).append("\r\n");
            
            // Connection头
            if (ctx.keepAlive) {
                headers.append("Connection: keep-alive\r\n");
            } else {
                headers.append("Connection: close\r\n");
            }
            
            headers.append("\r\n");
            
            // 准备写缓冲区
            byte[] headerBytes = headers.toString().getBytes(StandardCharsets.UTF_8);
            ctx.writeBuffer = ByteBuffer.allocateDirect(Math.max(BUFFER_SIZE, headerBytes.length));
            ctx.writeBuffer.put(headerBytes);
            ctx.writeBuffer.flip();
            
            // 打开数据源（如果需要）
            if (ctx.dataSource != null && ctx.isWritingFile) {
                try {
                    ctx.dataSource.open();
                    if (ctx.rangeStart > 0) {
                        ctx.dataSource.moveTo(ctx.rangeStart);
                    }
                } catch (IOException e) {
                    Log.e("NIOStreamServer", "打开数据源失败", e);
                    ctx.dataSource = null;
                    ctx.isWritingFile = false;
                }
            }
        } finally {
            ctx.writeLock.unlock();
        }
    }
    
    // ==================================================
    // 写入处理
    // ==================================================
    private void handleWrite(SelectionKey key) throws IOException {
        ConnectionContext ctx = (ConnectionContext) key.attachment();
        if (ctx == null) {
            key.cancel();
            return;
        }
        
        ctx.writeLock.lock();
        try {
            SocketChannel channel = ctx.channel;
            
            // 首先写出缓冲区中的数据
            if (ctx.writeBuffer.hasRemaining()) {
                int written = channel.write(ctx.writeBuffer);
                if (written > 0) {
                    ctx.updateActivityTime();
                }
                
                // 如果缓冲区还有数据，继续等待写事件
                if (ctx.writeBuffer.hasRemaining()) {
                    return;
                }
            }
            
            // 如果还有文件数据要发送
            if (ctx.isWritingFile && ctx.dataSource != null) {
                // 分配新的缓冲区
                if (!ctx.writeBuffer.hasRemaining()) {
                    ctx.writeBuffer.clear();
                    
                    // 读取文件数据
                    byte[] tempBuffer = new byte[FILE_BUFFER_SIZE];
                    int bytesRead = ctx.dataSource.read(tempBuffer);
                    
                    if (bytesRead > 0) {
                        ctx.writeBuffer.put(tempBuffer, 0, bytesRead);
                        ctx.writeBuffer.flip();
                        ctx.bytesWritten += bytesRead;
                        
                        // 继续写出
                        int written = channel.write(ctx.writeBuffer);
                        if (written > 0) {
                            ctx.updateActivityTime();
                        }
                        
                        // 如果缓冲区还有数据，继续等待写事件
                        if (ctx.writeBuffer.hasRemaining()) {
                            return;
                        }
                    } else if (bytesRead == -1) {
                        // 文件读取完成
                        ctx.dataSource.close();
                        ctx.dataSource = null;
                        ctx.isWritingFile = false;
                    }
                }
            }
            
            // 所有数据已发送完成
            if (!ctx.isWritingFile && ctx.dataSource == null) {
                // 检查是否需要保持连接
                if (ctx.keepAlive) {
                    // 重置连接状态，准备处理下一个请求
                    ctx.resetForNextRequest();
                    key.interestOps(SelectionKey.OP_READ);
                } else {
                    // 关闭连接
                    closeConnection(ctx);
                }
            }
        } catch (IOException e) {
            Log.w("NIOStreamServer", "写入数据错误", e);
            closeConnection(ctx);
        } finally {
            ctx.writeLock.unlock();
        }
    }
    
    // ==================================================
    // 工具方法
    // ==================================================
    private void sendError(ConnectionContext ctx, String status, String message) {
        ctx.writeLock.lock();
        try {
            StringBuilder response = new StringBuilder();
            response.append(status).append("\r\n")
                    .append("Content-Type: ").append(MIME_PLAINTEXT).append("\r\n")
                    .append("Content-Length: ").append(message.length()).append("\r\n")
                    .append(COMMON_HEADERS)
                    .append("Connection: close\r\n")
                    .append("\r\n")
                    .append(message);
            
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            ctx.writeBuffer = ByteBuffer.allocateDirect(responseBytes.length);
            ctx.writeBuffer.put(responseBytes);
            ctx.writeBuffer.flip();
            ctx.keepAlive = false;
            
            // 注册写事件
            SelectionKey key = ctx.channel.keyFor(selector);
            if (key != null && key.isValid()) {
                key.interestOps(SelectionKey.OP_WRITE);
                selector.wakeup();
            }
        } finally {
            ctx.writeLock.unlock();
        }
    }
    
    private void closeConnection(ConnectionContext ctx) {
        if (ctx == null) {
            return;
        }
        
        try {
            SelectionKey key = ctx.channel.keyFor(selector);
            if (key != null) {
                key.cancel();
                connections.remove(key);
            }
            
            if (ctx.dataSource != null) {
                try {
                    ctx.dataSource.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
            
            ctx.channel.close();
            connectionCount.decrementAndGet();
            
            Log.d("NIOStreamServer", "连接关闭，剩余连接数: " + connectionCount.get());
        } catch (IOException e) {
            Log.w("NIOStreamServer", "关闭连接错误", e);
        }
    }
    
    private String decodePercent(String str) {
        if (str == null) {
            return "";
        }
        
        try {
            return Uri.decode(str);
        } catch (Exception e) {
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < str.length(); i++) {
                    char c = str.charAt(i);
                    switch (c) {
                        case '+':
                            sb.append(' ');
                            break;
                        case '%':
                            sb.append((char) Integer.parseInt(str.substring(i + 1, i + 3), 16));
                            i += 2;
                            break;
                        default:
                            sb.append(c);
                            break;
                    }
                }
                return sb.toString();
            } catch (Exception e2) {
                return str;
            }
        }
    }
    
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(30000); // 每30秒检查一次
                    
                    long now = System.currentTimeMillis();
                    List<ConnectionContext> toRemove = new ArrayList<>();
                    
                    for (ConnectionContext ctx : connections.values()) {
                        if (ctx.isIdle()) {
                            toRemove.add(ctx);
                        }
                    }
                    
                    for (ConnectionContext ctx : toRemove) {
                        Log.d("NIOStreamServer", "关闭空闲连接");
                        closeConnection(ctx);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "NIO-Cleanup-Thread");
        
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    // ==================================================
    // 响应类
    // ==================================================
    public static class Response {
        public String status;
        public String mimeType;
        public StreamSource data;
        public Properties header = new Properties();
        
        public Response() {
            this.status = HTTP_OK;
        }
        
        public Response(String status, String mimeType, StreamSource data) {
            this.status = status;
            this.mimeType = mimeType;
            this.data = data;
        }
        
        public void addHeader(String name, String value) {
            header.put(name, value);
        }
    }
    
    // ==================================================
    // 流数据源接口
    // ==================================================
    public interface StreamSource {
        void open() throws IOException;
        void close() throws IOException;
        int read(byte[] buffer) throws IOException;
        void moveTo(long position) throws IOException;
        long length() throws IOException;
    }
    
    // ==================================================
    // 获取服务器状态
    // ==================================================
    public int getConnectionCount() {
        return connectionCount.get();
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public int getPort() {
        return port;
    }
}