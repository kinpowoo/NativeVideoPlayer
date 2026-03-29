package com.sintech.wifi_direct.server;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.sintech.wifi_direct.protocol.FileTransferCallback;
import com.sintech.wifi_direct.protocol.ServerCallback;
import com.sintech.wifi_direct.protocol.WiFiDirectProtocol;
import com.sintech.wifi_direct.protocol.WiFiDirectProtocol.DecodedMessage;
import com.sintech.wifi_direct.protocol.WiFiDirectProtocol.FileMeta;
import com.sintech.wifi_direct.protocol.WiFiDirectProtocol.FileChunk;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WiFi Direct 服务端
 * 单Selector线程处理所有连接，支持心跳、文件传输
 */
public class WiFiDirectServer {
    
    // 核心NIO组件
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // 连接管理
    private final ConcurrentHashMap<SocketChannel, ClientSession> sessions = 
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientSession> sessionById = 
        new ConcurrentHashMap<>();
    
    // 心跳管理
    private final ScheduledExecutorService heartbeatScheduler = 
        Executors.newScheduledThreadPool(1);
    // 文件传输管理
    private final ConcurrentHashMap<String, FileTransfer> fileTransfers = 
        new ConcurrentHashMap<>();
    private final FileTransferCallback fileCallback;
    
    // 业务处理线程池
    private final ExecutorService workerPool = Executors.newCachedThreadPool(
        new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "Worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        }
    );
    
    // 回调接口
    private final ServerCallback callback;
    
    // 统计信息
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger totalMessages = new AtomicInteger(0);

    private final String cacheDirPath;
//    private File downloadFile;
    
    public WiFiDirectServer(int port, String cacheDir, ServerCallback callback,
                            FileTransferCallback fileCallback) throws IOException {
        this.callback = callback;
        this.fileCallback = fileCallback;
        this.cacheDirPath = cacheDir;
//        downloadFile = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        initialize(port);
    }
    
    /**
     * 初始化服务器
     */
    private void initialize(int port) throws IOException {
        // 创建Selector
        selector = Selector.open();
        
        // 创建ServerSocketChannel
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        // 获取ServerSocket并设置重用选项
        ServerSocket socket = serverChannel.socket();
        socket.setReuseAddress(true);
        socket.setReceiveBufferSize(64 * 1024); // 64KB接收缓冲区
        
        // 绑定端口
        serverChannel.bind(new InetSocketAddress(port));
        
        // 注册ACCEPT事件
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("WiFi Direct Server initialized on port " + port);
    }
    
    /**
     * 启动服务器
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server already running");
        }
        
        // 启动事件循环线程
        new Thread(this::eventLoop, "Server-EventLoop").start();
        
        // 启动心跳检测
        startHeartbeatMonitor();
        
        System.out.println("WiFi Direct Server started");
    }
    
    /**
     * 核心事件循环
     */
    private void eventLoop() {
        try {
            while (running.get()) {
                // 等待事件，超时1秒
                int readyChannels = selector.select(1000);
                
                if (readyChannels > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                    
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        
                        try {
                            if (key.isValid()) {
                                if (key.isAcceptable()) {
                                    handleAccept(key);
                                } else if (key.isReadable()) {
                                    handleRead(key);
                                } else if (key.isWritable()) {
                                    handleWrite(key);
                                }
                            }
                        } catch (CancelledKeyException e) {
                            // 连接被取消
                        } catch (IOException e) {
                            // IO异常，关闭连接
                            closeChannel(key);
                        } catch (Exception e) {
                            closeChannel(key);
                        }
                    }
                }
                
                // 处理超时会话
                cleanupTimeoutSessions();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }


    /**
     * 关闭通道
     */
    private void closeChannel(SelectionKey key) {
        if (key != null && key.channel() instanceof SocketChannel) {
            SocketChannel clientChannel = (SocketChannel) key.channel();
            closeClient(clientChannel, "Error processing channel");
        }
    }
    
    /**
     * 处理新的客户端连接
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = null;

        try {
            clientChannel = serverSocketChannel.accept();

            if (clientChannel == null) {
                return; // 没有客户端连接
            }
            // 配置客户端通道
            configureClientChannel(clientChannel);
            // 配置非阻塞模式
            clientChannel.configureBlocking(false);
            
            // 注册读事件
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            
            // 创建客户端会话
            String clientId = generateClientId(clientChannel);
            ClientSession session = new ClientSession(clientId, clientChannel, clientKey);
            
            sessions.put(clientChannel, session);
            sessionById.put(clientId, session);
            
            totalConnections.incrementAndGet();
            
            // 回调连接建立
            if (callback != null) {
                workerPool.submit(() -> callback.onClientConnected(clientId));
            }
            
            System.out.println("Client connected: " + clientId + 
                             " (" + sessions.size() + " clients connected)");
        }catch (IOException e) {
            if (clientChannel != null && clientChannel.isOpen()) {
                clientChannel.close();
            }
        } catch (Exception e) {
//            Log.error("Unexpected error accepting connection: " + e.getMessage(), e);
        }
    }


    /**
     * 配置客户端通道
     */
    private void configureClientChannel(SocketChannel channel) throws IOException {
        channel.configureBlocking(false);

        // 设置TCP选项
        Socket socket = channel.socket();
        socket.setTcpNoDelay(true); // 禁用Nagle算法
        socket.setKeepAlive(true);  // 启用TCP KeepAlive
        socket.setSoTimeout(30000); // 30秒超时

        // 设置缓冲区大小
        socket.setReceiveBufferSize(32 * 1024); // 32KB接收缓冲区
        socket.setSendBufferSize(32 * 1024);    // 32KB发送缓冲区

        // 设置SO_LINGER为0，快速关闭连接
        socket.setSoLinger(false, 0);
    }
    
    /**
     * 处理读事件
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession session = sessions.get(clientChannel);
        
        if (session == null) {
            closeClient(clientChannel, "Session not found");
            return;
        }
        
        // 读取数据
        ByteBuffer buffer = session.getReadBuffer();
        buffer.clear();
        
        int bytesRead = clientChannel.read(buffer);
        
        if (bytesRead == -1) {
            // 连接关闭
            closeClient(clientChannel, "Connection closed by client");
            return;
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            session.updateLastActivity();
            
            // 处理接收到的数据
            processReceivedData(session, buffer);
        }
    }
    
    /**
     * 处理接收到的数据
     */
    private void processReceivedData(ClientSession session, ByteBuffer data) {
        session.appendToBuffer(data);
        
        ByteBuffer buffer = session.getBufferAsByteBuffer();
        
        while (buffer.remaining() >= 10) {  // 至少需要头部长度
            buffer.mark();
            DecodedMessage message = WiFiDirectProtocol.decode(buffer);
            
            if (message == null) {
                // 数据不完整，重置并等待更多数据
                buffer.reset();
                break;
            }
            
            if (message.type == WiFiDirectProtocol.TYPE_ERROR) {
                // 协议错误
                System.err.println("Protocol error: " + 
                    new String(message.data, StandardCharsets.UTF_8));
                session.clearBuffer();  // 清空缓冲区
                break;
            }
            
            // 处理完整消息
            handleMessage(session, message);
            
            // 从缓冲区移除已处理的数据
            session.removeFromBuffer(10 + message.length);
            
            totalMessages.incrementAndGet();
        }
    }
    
    /**
     * 处理消息
     */
    private void handleMessage(ClientSession session, DecodedMessage message) {
        switch (message.type) {
            case WiFiDirectProtocol.TYPE_HEARTBEAT:
                handleHeartbeat(session);
                break;
                
            case WiFiDirectProtocol.TYPE_HEARTBEAT_ACK:
                handleHeartbeatAck(session);
                break;
                
            case WiFiDirectProtocol.TYPE_STRING:
                handleStringMessage(session, message.data);
                break;
                
            case WiFiDirectProtocol.TYPE_FILE_META:
                handleFileMeta(session, message.data);
                break;
                
            case WiFiDirectProtocol.TYPE_FILE_DATA:
                handleFileData(session, message.data);
                break;
            case WiFiDirectProtocol.TYPE_FILE_ACK:
                handleFileAck(session, message.data);
                break;
                
            default:
                System.err.println("Unknown message type: " + message.type);
        }
    }
    
    /**
     * 处理心跳
     */
    private void handleHeartbeat(ClientSession session) {
        session.updateLastHeartbeat();
        
        // 发送心跳确认
        ByteBuffer ack = WiFiDirectProtocol.encodeHeartbeatAck();
        enqueueWrite(session, ack);
        
        if (callback != null) {
            workerPool.submit(() -> callback.onHeartbeatReceived(session.clientId));
        }
    }
    
    /**
     * 处理心跳确认
     */
    private void handleHeartbeatAck(ClientSession session) {
        session.updateLastHeartbeat();
        
        if (callback != null) {
            workerPool.submit(() -> callback.onHeartbeatAckReceived(session.clientId));
        }
    }
    
    /**
     * 处理字符串消息
     */
    private void handleStringMessage(ClientSession session, byte[] data) {
        String message = WiFiDirectProtocol.decodeString(data);
        
        if (callback != null) {
            workerPool.submit(() -> callback.onMessageReceived(session.clientId, message));
        }
    }
    
    /**
     * 处理文件元数据
     */
    private void handleFileMeta(ClientSession session, byte[] data) {
        try {
            FileMeta meta = WiFiDirectProtocol.decodeFileMeta(data);
            
            FileTransfer transfer = new FileTransfer(
                    meta.fileId,
                    session.clientId,
                    meta.fileName,
                    meta.fileSize,
                    meta.totalChunks,
                    cacheDirPath
            );
            
            fileTransfers.put(meta.fileId, transfer);
            
            // 发送文件接收确认
            sendFileAck(session, meta.fileId, 0, "START");
            
            if (fileCallback != null) {
                workerPool.submit(() -> 
                    fileCallback.onFileTransferStarted(
                        session.clientId,
                        meta.fileId,
                        meta.fileName,
                        meta.fileSize
                    )
                );
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            sendFileError(session, "Invalid file metadata");
        }
    }
    
    /**
     * 处理文件数据
     */
    private void handleFileData(ClientSession session, byte[] data) {
        try {
            FileChunk chunk = WiFiDirectProtocol.decodeFileChunk(data);
            String fileId = chunk.fileId;
            final FileTransfer transfer = fileTransfers.get(fileId);
            if (transfer == null) {
                sendFileError(session, "File transfer not found: " + fileId);
                return;
            }

            // 保存数据块
            workerPool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        transfer.addChunk(chunk.chunkIndex, chunk.chunkData);
                    } catch (IOException e) {
                        sendFileError(session, "write file chunk data to local temp file failed");
                    }
                }
            });
            // 发送确认
            sendFileAck(session, fileId, chunk.chunkIndex + 1, "CHUNK");
            
            if (fileCallback != null) {
                workerPool.submit(() -> 
                    fileCallback.onFileChunkReceived(
                        session.clientId,
                        fileId,
                        chunk.chunkIndex,
                        chunk.chunkData.length
                    )
                );
            }
            
            // 检查是否完成
//            if (chunk.isLast && transfer.isComplete()) {
            if (chunk.isLast) {
                completeFileTransfer(session, transfer);
            }
        } catch (IOException e) {
            e.printStackTrace();
            sendFileError(session, "Invalid file chunk data");
        }
    }
    
    /**
     * 完成文件传输
     */
    private void completeFileTransfer(ClientSession session, FileTransfer transfer) {
        try {
            String filePath = transfer.getFilePath();
            
            if (fileCallback != null) {
                workerPool.submit(() -> 
                    fileCallback.onFileTransferCompleted(
                        session.clientId,
                        transfer.fileId,
                        transfer.fileName, filePath
                    )
                );
            }
            
            // 发送完成确认
            sendFileAck(session, transfer.fileId, transfer.totalChunks, "COMPLETE");
            
            // 清理
            fileTransfers.remove(transfer.fileId);
        } catch (IOException e) {
            e.printStackTrace();
            sendFileError(session, "Failed to assemble file: " + e.getMessage());
        }
    }
    
    /**
     * 发送文件确认
     */
    private void sendFileAck(ClientSession session, String fileId, 
                           int receivedChunks, String status) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.writeUTF(fileId);
            dos.writeInt(receivedChunks);
            dos.writeUTF(status);
            dos.writeLong(System.currentTimeMillis());
            
            dos.flush();
            
            ByteBuffer ack = WiFiDirectProtocol.encode(
                WiFiDirectProtocol.TYPE_FILE_ACK, 
                baos.toByteArray()
            );
            
            enqueueWrite(session, ack);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 发送文件错误
     */
    private void sendFileError(ClientSession session, String error) {
        ByteBuffer errorMsg = WiFiDirectProtocol.encode(
            WiFiDirectProtocol.TYPE_FILE_ERROR,
            error.getBytes(StandardCharsets.UTF_8)
        );
        
        enqueueWrite(session, errorMsg);
    }
    
    /**
     * 处理文件确认
     */
    private void handleFileAck(ClientSession session, byte[] data) {
        // 文件传输确认处理
        if (callback != null) {
            String ack = new String(data, StandardCharsets.UTF_8);
            workerPool.submit(() -> 
                callback.onFileAckReceived(session.clientId, ack)
            );
        }
    }
    
    /**
     * 处理写事件
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession session = sessions.get(clientChannel);
        if (session == null) {
            closeClient(clientChannel, "Session not found");
            return;
        }

        try {
            boolean allWritten = session.writePendingData();

            if (allWritten) {
                // 所有数据已写完，切换回读模式
                key.interestOps(SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            closeClient(clientChannel, "Write error: " + e.getMessage());
        } catch (Exception e) {
            closeClient(clientChannel, "Write error");
        }
    }
    
    /**
     * 发送数据到客户端
     */
    public void sendToClient(String clientId, ByteBuffer data) {
        ClientSession session = sessionById.get(clientId);
        if (session != null) {
            enqueueWrite(session, data);
        }
    }
    
    /**
     * 发送字符串到客户端
     */
    public void sendString(String clientId, String message) {
        ByteBuffer data = WiFiDirectProtocol.encodeString(message);
        sendToClient(clientId, data);
    }
    
    /**
     * 广播消息到所有客户端
     */
    public void broadcast(ByteBuffer data) {
        for (ClientSession session : sessions.values()) {
            enqueueWrite(session, data.duplicate());
        }
    }
    
    /**
     * 广播字符串
     */
    public void broadcastString(String message) {
        ByteBuffer data = WiFiDirectProtocol.encodeString(message);
        broadcast(data);
    }
    
    /**
     * 发送文件到客户端
     */
    public void sendFile(String clientId, File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found: " + file.getPath());
        }
        
        String fileId = WiFiDirectProtocol.generateFileId();
        long fileSize = file.length();
        int totalChunks = (int) Math.ceil((double) fileSize / WiFiDirectProtocol.MAX_CHUNK_SIZE);
        
        // 发送文件元数据
        ByteBuffer meta = WiFiDirectProtocol.encodeFileMeta(
            fileId, file.getName(), fileSize, totalChunks
        );
        sendToClient(clientId, meta);

        // 分片发送文件数据
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] chunkBuffer = new byte[WiFiDirectProtocol.MAX_CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesRead;

            while ((bytesRead = raf.read(chunkBuffer)) != -1) {
                byte[] chunkData = Arrays.copyOf(chunkBuffer, bytesRead);
                boolean isLast = (raf.getFilePointer() >= fileSize);

                ByteBuffer chunk = WiFiDirectProtocol.encodeFileData(
                    fileId, chunkIndex, chunkData, isLast
                );

                sendToClient(clientId, chunk);
                chunkIndex++;

                // 控制发送速率
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 入队写数据
     */
    private void enqueueWrite(ClientSession session, ByteBuffer data) {
        session.enqueueWrite(data);
        
        SelectionKey key = session.selectionKey;
        if (key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
        
        // 唤醒Selector
        selector.wakeup();
    }
    
    /**
     * 启动心跳监控
     */
    private void startHeartbeatMonitor() {
        heartbeatScheduler.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            
            for (ClientSession session : sessions.values()) {
                // 检查心跳超时
                if (now - session.getLastHeartbeatTime() > WiFiDirectProtocol.HEARTBEAT_TIMEOUT) {
                    closeClient(session.channel, "Heartbeat timeout");
                    continue;
                }
                
                // 发送心跳
                if (now - session.getLastHeartbeatSent() > WiFiDirectProtocol.HEARTBEAT_INTERVAL) {
                    ByteBuffer heartbeat = WiFiDirectProtocol.encodeHeartbeat();
                    enqueueWrite(session, heartbeat);
                    session.updateLastHeartbeatSent();
                }
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 清理超时会话
     */
    private void cleanupTimeoutSessions() {
        long now = System.currentTimeMillis();
        long timeout = 300000; // 5分钟无活动超时
        
        List<SocketChannel> timeoutSessions = new ArrayList<>();
        
        for (Map.Entry<SocketChannel, ClientSession> entry : sessions.entrySet()) {
            if (now - entry.getValue().getLastActivityTime() > timeout) {
                timeoutSessions.add(entry.getKey());
            }
        }
        
        for (SocketChannel channel : timeoutSessions) {
            closeClient(channel, "Inactivity timeout");
        }
    }
    
    /**
     * 关闭客户端连接
     */
    private void closeClient(SocketChannel channel, String reason) {
        ClientSession session = sessions.remove(channel);
        
        if (session != null) {
            sessionById.remove(session.clientId);

            // 取消SelectionKey
            if (session.selectionKey != null) {
                session.selectionKey.cancel();
            }
            // 清理相关文件传输
            cleanupFileTransfers(session.clientId);
            
            // 回调连接断开
            if (callback != null) {
                workerPool.submit(() -> callback.onClientDisconnected(session.clientId, reason));
            }
            
            System.out.println("Client disconnected: " + session.clientId + 
                             " - Reason: " + reason);
            
            closeChannelQuietly(channel);
            
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                key.cancel();
            }
        }else {
            // 没有会话，直接关闭通道
            closeChannelQuietly(channel);
        }
    }

    /**
     * 安静地关闭通道
     */
    private void closeChannelQuietly(SocketChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * 清理文件传输
     */
    private void cleanupFileTransfers(String clientId) {
        fileTransfers.entrySet().removeIf(entry -> entry.getValue().clientId.equals(clientId));
    }
    
    /**
     * 断开指定客户端
     */
    public void disconnectClient(String clientId, String reason) {
        ClientSession session = sessionById.get(clientId);
        if (session != null) {
            closeClient(session.channel, reason);
        }
    }
    
    /**
     * 生成客户端ID
     */
    private String generateClientId(SocketChannel channel) {
        try {
            String address = channel.getRemoteAddress().toString();
            return "CLIENT-" + UUID.randomUUID().toString().substring(0, 8) + 
                   "-" + address.hashCode();
        } catch (IOException e) {
            return "CLIENT-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
    
    /**
     * 关闭服务器
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            System.out.println("Shutting down server...");
            
            // 关闭所有客户端连接
            for (SocketChannel channel : sessions.keySet()) {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            sessions.clear();
            sessionById.clear();
            fileTransfers.clear();
            
            // 关闭调度器
            heartbeatScheduler.shutdown();
            
            // 关闭线程池
            workerPool.shutdown();
            
            // 关闭Selector
            if (selector != null) {
                try {
                    selector.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            // 关闭ServerSocketChannel
            if (serverChannel != null) {
                try {
                    serverChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            System.out.println("Server shutdown complete");
        }
    }
    
    /**
     * 获取连接统计
     */
    public ServerStats getStats() {
        return new ServerStats(
            totalConnections.get(),
            sessions.size(),
            totalMessages.get(),
            fileTransfers.size()
        );
    }
    
    /**
     * 客户端会话类
     */
    private static class ClientSession {
        final String clientId;
        final SocketChannel channel;
        final SelectionKey selectionKey;
        final ByteBuffer readBuffer = ByteBuffer.allocateDirect(WiFiDirectProtocol.BUFFER_SIZE);
        final LinkedList<ByteBuffer> writeQueue = new LinkedList<>();
        final ReentrantLock writeLock = new ReentrantLock();
        final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
        
        volatile long lastActivityTime = System.currentTimeMillis();
        volatile long lastHeartbeatTime = System.currentTimeMillis();
        volatile long lastHeartbeatSent = 0;
        
        ClientSession(String clientId, SocketChannel channel, SelectionKey selectionKey) {
            this.clientId = clientId;
            this.channel = channel;
            this.selectionKey = selectionKey;
        }
        
        ByteBuffer getReadBuffer() {
            return readBuffer;
        }
        
        void appendToBuffer(ByteBuffer data) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            dataBuffer.write(bytes, 0, bytes.length);
        }
        
        ByteBuffer getBufferAsByteBuffer() {
            byte[] data = dataBuffer.toByteArray();
            return ByteBuffer.wrap(data);
        }
        
        void removeFromBuffer(int length) {
            byte[] current = dataBuffer.toByteArray();
            if (length >= current.length) {
                dataBuffer.reset();
            } else {
                dataBuffer.reset();
                dataBuffer.write(current, length, current.length - length);
            }
        }
        
        void clearBuffer() {
            dataBuffer.reset();
        }
        
        void enqueueWrite(ByteBuffer data) {
            writeLock.lock();
            try {
                writeQueue.add(data);
            } finally {
                writeLock.unlock();
            }
        }
        
        boolean writePendingData() throws IOException {
            writeLock.lock();
            try {
                while (!writeQueue.isEmpty()) {
                    ByteBuffer buffer = writeQueue.peek();
                    channel.write(buffer);
                    
                    if (buffer.hasRemaining()) {
                        return false; // 没有写完
                    }
                    
                    writeQueue.poll(); // 已写完，移除
                }
                return true; // 所有数据已写完
            } finally {
                writeLock.unlock();
            }
        }
        
        void updateLastActivity() {
            lastActivityTime = System.currentTimeMillis();
        }
        
        void updateLastHeartbeat() {
            lastHeartbeatTime = System.currentTimeMillis();
        }
        
        void updateLastHeartbeatSent() {
            lastHeartbeatSent = System.currentTimeMillis();
        }
        
        long getLastActivityTime() {
            return lastActivityTime;
        }
        
        long getLastHeartbeatTime() {
            return lastHeartbeatTime;
        }
        
        long getLastHeartbeatSent() {
            return lastHeartbeatSent;
        }
    }
    
    /**
     * 文件传输类
     */
    private static class FileTransfer {
        final String fileId;
        final String clientId;
        final String fileName;
        final long fileSize;
        final int totalChunks;
//        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        final String parentDir;
        private File writeFile;
        final AtomicInteger receivedChunks = new AtomicInteger(0);
        final long startTime = System.currentTimeMillis();
        
        FileTransfer(String fileId, String clientId, String fileName, 
                    long fileSize, int totalChunks,String parentDirPath) {
            this.fileId = fileId;
            this.clientId = clientId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.parentDir = parentDirPath;
            this.writeFile = new File(parentDirPath,fileName);
            this.totalChunks = totalChunks;
        }
        
        void addChunk(int chunkIndex, byte[] chunkData) throws IOException{
            if(writeFile != null){
                if(!writeFile.exists()) {
                    try {
                        if (writeFile != null) {
//                            writeFile.mkdir();
                            writeFile.createNewFile();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                RandomAccessFile raf = null;
                try{
                    raf = new RandomAccessFile(writeFile,"rw");
                    int offset = chunkIndex*WiFiDirectProtocol.MAX_CHUNK_SIZE;
                    long pointer = (long)offset * chunkIndex;
                    if(pointer < raf.length()){
                        raf.seek(pointer);
                    }
                    raf.write(chunkData,0,chunkData.length);
                    receivedChunks.incrementAndGet();
                }catch (IOException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }finally {
                    if(raf != null){
                        raf.close();
                    }
                }
            }
        }
        
        boolean isComplete() {
            return receivedChunks.get() >= totalChunks;
        }
        
        String getFilePath() throws IOException {
            if (!isComplete()) {
                throw new IOException("File not complete");
            }
            return writeFile.getAbsolutePath();
//            ByteArrayOutputStream baos = new ByteArrayOutputStream((int) fileSize);
//            for (int i = 0; i < totalChunks; i++) {
//                byte[] chunk = chunks.get(i);
//                if (chunk != null) {
//                    baos.write(chunk);
//                }
//            }
//            return baos.toByteArray();
        }
    }
    
    /**
     * 服务器统计信息
     */
    public static class ServerStats {
        public final int totalConnections;
        public final int currentConnections;
        public final int totalMessages;
        public final int activeFileTransfers;
        
        ServerStats(int totalConnections, int currentConnections, 
                   int totalMessages, int activeFileTransfers) {
            this.totalConnections = totalConnections;
            this.currentConnections = currentConnections;
            this.totalMessages = totalMessages;
            this.activeFileTransfers = activeFileTransfers;
        }
    }
}