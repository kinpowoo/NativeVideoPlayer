package com.sintech.wifi_direct.client;

import android.content.Context;
import android.net.InetAddresses;
import android.os.Environment;

import com.sintech.wifi_direct.protocol.ClientCallback;
import com.sintech.wifi_direct.protocol.FileReceiveCallback;
import com.sintech.wifi_direct.protocol.WiFiDirectProtocol;
import com.sintech.wifi_direct.protocol.WiFiDirectProtocol.DecodedMessage;
import com.sintech.wifi_direct.protocol.WiFiDirectProtocol.FileMeta;
import com.sintech.wifi_direct.protocol.WiFiDirectProtocol.FileChunk;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WiFi Direct 客户端
 * 单Selector线程，支持心跳、文件传输
 */
public class WiFiDirectClient {

    // 核心NIO组件
    private Selector selector;
    private SocketChannel socketChannel;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 连接配置
    private final String serverHost;
    private final int serverPort;
    private String clientId;

    // 心跳管理
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(1);
    private volatile long lastHeartbeatTime = System.currentTimeMillis();

    // 文件传输管理
    private final ConcurrentHashMap<String, FileReceiveSession> fileReceives =
            new ConcurrentHashMap<>();
    private final WeakReference<FileReceiveCallback> fileCallback;

    // 写队列
    private final LinkedList<ByteBuffer> writeQueue = new LinkedList<>();
    private final ReentrantLock writeLock = new ReentrantLock();

    // 回调接口
    private final WeakReference<ClientCallback> callback;

    // 数据缓冲区
    private final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(WiFiDirectProtocol.BUFFER_SIZE);

    // 统计信息
    private final AtomicInteger totalMessagesSent = new AtomicInteger(0);
    private final AtomicInteger totalMessagesReceived = new AtomicInteger(0);

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

    private final String cacheDirPath;

    public WiFiDirectClient(InetSocketAddress addresses, String cacheDir,
                            WeakReference<ClientCallback> callback, WeakReference<FileReceiveCallback> fileCallback) {
        this.serverHost = addresses.getHostString();
        this.serverPort = addresses.getPort();
        this.callback = callback;
        this.fileCallback = fileCallback;
        this.cacheDirPath = cacheDir;
//        downloadFile = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        this.clientId = "CLIENT-" + UUID.randomUUID().toString().substring(0, 8);
    }
    public WiFiDirectClient(String serverHost, int serverPort, String cacheDir,
                            WeakReference<ClientCallback> callback, WeakReference<FileReceiveCallback> fileCallback) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.callback = callback;
        this.fileCallback = fileCallback;
        this.cacheDirPath = cacheDir;
//        downloadFile = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        this.clientId = "CLIENT-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 连接到服务器
     */
    public void connect() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Client already running");
        }

        // 创建Selector
        selector = Selector.open();

        // 创建SocketChannel
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        // 注册CONNECT事件
        socketChannel.register(selector, SelectionKey.OP_CONNECT);

        // 开始连接
        socketChannel.connect(new InetSocketAddress(serverHost, serverPort));

        System.out.println("Connecting to server " + serverHost + ":" + serverPort);

        // 启动事件循环线程
        new Thread(this::eventLoop, "Client-EventLoop").start();

        // 启动心跳服务
        startHeartbeatService();
    }

    /**
     * 核心事件循环
     */
    private void eventLoop() {
        try {
            while (running.get()) {
                int readyChannels = selector.select(1000);

                if (readyChannels > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();

                        try {
                            if (key.isValid()) {
                                if (key.isConnectable()) {
                                    handleConnect(key);
                                } else if (key.isReadable()) {
                                    handleRead(key);
                                } else if (key.isWritable()) {
                                    handleWrite(key);
                                }
                            }
                        } catch (CancelledKeyException e) {
                            // 连接被取消
                            disconnect("Connection cancelled");
                        } catch (IOException e) {
                            disconnect("IO Exception: " + e.getMessage());
                            key.cancel();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                // 检查心跳超时
                checkHeartbeatTimeout();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            disconnect("Event loop stopped");
        }
    }

    /**
     * 处理连接事件
     */
    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        if (channel.finishConnect()) {
            connected.set(true);

            // 连接成功，注册读事件
            key.interestOps(SelectionKey.OP_READ);

            System.out.println("Connected to server");

            if (callback != null && callback.get() != null) {
                callback.get().onConnected();
            }

            // 发送客户端信息
            sendString("CLIENT_INFO:" + clientId);
        } else {
            disconnect("Connection failed");
        }
    }

    /**
     * 处理读事件
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        readBuffer.clear();
        int bytesRead = channel.read(readBuffer);

        if (bytesRead == -1) {
            disconnect("Connection closed by server");
            return;
        }

        if (bytesRead > 0) {
            readBuffer.flip();
            processReceivedData(readBuffer);
        }
    }

    /**
     * 处理接收到的数据
     */
    private void processReceivedData(ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        dataBuffer.write(bytes, 0, bytes.length);

        ByteBuffer buffer = ByteBuffer.wrap(dataBuffer.toByteArray());

        while (buffer.remaining() >= 10) {
            buffer.mark();
            DecodedMessage message = WiFiDirectProtocol.decode(buffer);

            if (message == null) {
                buffer.reset();
                break;
            }

            if (message.type == WiFiDirectProtocol.TYPE_ERROR) {
                System.err.println("Protocol error: " +
                        new String(message.data, StandardCharsets.UTF_8));
                dataBuffer.reset();
                break;
            }

            // 处理消息
            handleMessage(message);

            // 移除已处理的数据
            removeFromBuffer(10 + message.length);

            totalMessagesReceived.incrementAndGet();
        }
    }

    /**
     * 处理消息
     */
    private void handleMessage(DecodedMessage message) {
        switch (message.type) {
            case WiFiDirectProtocol.TYPE_HEARTBEAT:
                handleHeartbeat();
                break;

            case WiFiDirectProtocol.TYPE_HEARTBEAT_ACK:
                handleHeartbeatAck();
                break;

            case WiFiDirectProtocol.TYPE_STRING:
                handleStringMessage(message.data);
                break;

            case WiFiDirectProtocol.TYPE_FILE_META:
                handleFileMeta(message.data);
                break;

            case WiFiDirectProtocol.TYPE_FILE_DATA:
                handleFileData(message.data);
                break;

            case WiFiDirectProtocol.TYPE_FILE_ACK:
                handleFileAck(message.data);
                break;

            case WiFiDirectProtocol.TYPE_FILE_ERROR:
                handleFileError(message.data);
                break;

            default:
                System.err.println("Unknown message type: " + message.type);
        }
    }

    /**
     * 处理心跳
     */
    private void handleHeartbeat() {
        lastHeartbeatTime = System.currentTimeMillis();

        // 发送心跳确认
        ByteBuffer ack = WiFiDirectProtocol.encodeHeartbeatAck();
        enqueueWrite(ack);

        if (callback != null && callback.get() != null) {
            callback.get().onHeartbeatReceived();
        }
    }

    /**
     * 处理心跳确认
     */
    private void handleHeartbeatAck() {
        lastHeartbeatTime = System.currentTimeMillis();

        if (callback != null && callback.get() != null) {
            callback.get().onHeartbeatAckReceived();
        }
    }

    /**
     * 处理字符串消息
     */
    private void handleStringMessage(byte[] data) {
        String message = WiFiDirectProtocol.decodeString(data);

        if (callback != null && callback.get() != null) {
            callback.get().onMessageReceived(message);
        }
    }

    /**
     * 处理文件元数据
     */
    private void handleFileMeta(byte[] data) {
        try {
            FileMeta meta = WiFiDirectProtocol.decodeFileMeta(data);

            FileReceiveSession session = new FileReceiveSession(
                    meta.fileId,
                    meta.fileName,
                    meta.fileSize,
                    meta.totalChunks,
                    cacheDirPath
            );

            fileReceives.put(meta.fileId, session);

            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileReceiveStarted(
                        meta.fileId,
                        meta.fileName,
                        meta.fileSize
                );
            }

        } catch (IOException e) {
            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileTransferError(
                        e.getLocalizedMessage()
                );
            }
        }
    }

    /**
     * 处理文件数据
     */
    private void handleFileData(byte[] data) {
        try {
            FileChunk chunk = WiFiDirectProtocol.decodeFileChunk(data);
            String fileId = chunk.fileId;

            FileReceiveSession session = fileReceives.get(fileId);
            if (session == null) {
                System.err.println("File session not found: " + fileId);
                return;
            }
            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileChunkReceived(
                        fileId,
                        chunk.chunkIndex,
                        chunk.chunkData.length
                );
            }

            workerPool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        session.addChunk(chunk.chunkIndex, chunk.chunkData);

                        if (chunk.isLast && session.isComplete()) {
                            completeFileReceive(session);
                        }
                    } catch (IOException e) {
                        if (fileCallback != null && fileCallback.get() != null) {
                            fileCallback.get().onFileTransferError(
                                    "write data to local temp file failed"
                            );
                        }
                    }
                }
            });
        } catch (IOException e) {
            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileTransferError(
                        e.getLocalizedMessage()
                );
            }
        }
    }

    /**
     * 完成文件接收
     */
    private void completeFileReceive(FileReceiveSession session) {
        try {
            String filePath = session.getFilePath();

            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileReceived(
                        session.fileId,
                        session.fileName,
                        filePath
                );
            }
            fileReceives.remove(session.fileId);
        } catch (IOException e) {
            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileTransferError(
                        e.getLocalizedMessage()
                );
            }
            fileReceives.remove(session.fileId);
        }
    }

    /**
     * 处理文件确认
     */
    private void handleFileAck(byte[] data) {
        String ack = new String(data, StandardCharsets.UTF_8);

        if (callback != null && callback.get() != null) {
            callback.get().onFileAckReceived(ack);
        }
    }

    /**
     * 处理文件错误
     */
    private void handleFileError(byte[] data) {
        String error = new String(data, StandardCharsets.UTF_8);
        System.err.println("File transfer error: " + error);

        if (fileCallback != null && fileCallback.get() != null) {
            fileCallback.get().onFileTransferError(error);
        }
    }

    /**
     * 处理写事件
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        boolean allWritten = false;

        writeLock.lock();
        try {
            while (!writeQueue.isEmpty()) {
                ByteBuffer buffer = writeQueue.peek();
                channel.write(buffer);

                if (buffer != null && buffer.hasRemaining()) {
                    break; // 没有写完
                }

                writeQueue.poll(); // 已写完，移除
            }

            allWritten = writeQueue.isEmpty();

        } finally {
            writeLock.unlock();
        }

        if (allWritten) {
            // 所有数据已写完，切换回读模式
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * 发送数据
     */
    public void sendData(ByteBuffer data) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected");
        }

        enqueueWrite(data);
        totalMessagesSent.incrementAndGet();
    }

    /**
     * 发送字符串
     */
    public void sendString(String message) {
        ByteBuffer data = WiFiDirectProtocol.encodeString(message);
        sendData(data);
    }

    /**
     * 发送文件
     */
    public void sendFile(File file) throws IOException {
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
        sendData(meta);

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

                sendData(chunk);
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
    private void enqueueWrite(ByteBuffer data) {
        writeLock.lock();
        try {
            writeQueue.add(data);
        } finally {
            writeLock.unlock();
        }

        SelectionKey key = socketChannel.keyFor(selector);
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }

        // 唤醒Selector
        selector.wakeup();
    }

    /**
     * 启动心跳服务
     */
    private void startHeartbeatService() {
        heartbeatScheduler.scheduleWithFixedDelay(() -> {
            if (!isConnected()) {
                return;
            }

            // 发送心跳
            if (System.currentTimeMillis() - lastHeartbeatTime > WiFiDirectProtocol.HEARTBEAT_INTERVAL) {
                ByteBuffer heartbeat = WiFiDirectProtocol.encodeHeartbeat();
                enqueueWrite(heartbeat);
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查心跳超时
     */
    private void checkHeartbeatTimeout() {
        if (isConnected() &&
                System.currentTimeMillis() - lastHeartbeatTime > WiFiDirectProtocol.HEARTBEAT_TIMEOUT) {
            disconnect("Heartbeat timeout");
        }
    }

    /**
     * 从缓冲区移除数据
     */
    private void removeFromBuffer(int length) {
        byte[] current = dataBuffer.toByteArray();
        if (length >= current.length) {
            dataBuffer.reset();
        } else {
            dataBuffer.reset();
            dataBuffer.write(current, length, current.length - length);
        }
    }

    /**
     * 断开连接
     */
    public void disconnect(String reason) {
        if (running.compareAndSet(true, false)) {
            connected.set(false);

            System.out.println("Disconnecting: " + reason);

            // 关闭调度器
            heartbeatScheduler.shutdown();

            // 清理文件传输
            fileReceives.clear();

            // 关闭连接
            if (socketChannel != null && socketChannel.isOpen()) {
                try {
                    socketChannel.close();
                    socketChannel = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // 关闭Selector
            if (selector != null) {
                try {
                    selector.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    selector = null;
                }
            }

            // 清理写队列
            writeLock.lock();
            try {
                writeQueue.clear();
            } finally {
                writeLock.unlock();
            }

            dataBuffer.reset();

            if (callback != null && callback.get() != null) {
                callback.get().onDisconnected(reason);
            }
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        disconnect("Client initiated");
    }

    /**
     * 检查是否连接
     */
    public boolean isConnected() {
        return connected.get() && socketChannel != null;
    }

    /**
     * 获取客户端ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * 设置客户端ID
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 获取统计信息
     */
    public ClientStats getStats() {
        return new ClientStats(
                totalMessagesSent.get(),
                totalMessagesReceived.get(),
                fileReceives.size(),
                isConnected()
        );
    }

    /**
     * 文件接收会话
     */
    private static class FileReceiveSession {
        final String fileId;
        final String fileName;
        final long fileSize;
        final int totalChunks;
        //        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        final AtomicInteger receivedChunks = new AtomicInteger(0);
        final long startTime = System.currentTimeMillis();
        private File writeFile;

        FileReceiveSession(String fileId, String fileName, long fileSize, int totalChunks,
                           String parentPath) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.totalChunks = totalChunks;
            this.writeFile = new File(parentPath, fileName);
            if (!writeFile.exists()) {
                try {
                    writeFile.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        void addChunk(int chunkIndex, byte[] chunkData) throws IOException {
            if (writeFile != null) {
                try (RandomAccessFile raf = new RandomAccessFile(writeFile, "rw")) {
                    int offset = chunkIndex * WiFiDirectProtocol.MAX_CHUNK_SIZE;
                    raf.seek(offset);
                    raf.write(chunkData, 0, chunkData.length);
                    receivedChunks.incrementAndGet();
                } catch (IOException e) {
                    Thread.currentThread().interrupt();
                }
            }
//            if (chunks.putIfAbsent(chunkIndex, chunkData) == null) {
//                receivedChunks.incrementAndGet();
//            }
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
     * 客户端统计信息
     */
    public static class ClientStats {
        public final int messagesSent;
        public final int messagesReceived;
        public final int activeFileReceives;
        public final boolean isConnected;

        ClientStats(int messagesSent, int messagesReceived,
                    int activeFileReceives, boolean isConnected) {
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.activeFileReceives = activeFileReceives;
            this.isConnected = isConnected;
        }
    }
}