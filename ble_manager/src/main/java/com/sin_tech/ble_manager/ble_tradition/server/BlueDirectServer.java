package com.sin_tech.ble_manager.ble_tradition.server;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;

import com.sin_tech.ble_manager.ble_tradition.client.BlueDirectClient;
import com.sin_tech.ble_manager.ble_tradition.protocol.BlueDirectProtocol;
import com.sin_tech.ble_manager.ble_tradition.protocol.ClientCallback;
import com.sin_tech.ble_manager.ble_tradition.protocol.FileReceiveCallback;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WiFi Direct 服务端
 * 单Selector线程处理所有连接，支持心跳、文件传输
 */
public class BlueDirectServer{
    private final String tag = "sintechphil";
    private final String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    // 核心NIO组件
    private BluetoothServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // 连接管理
    private final ConcurrentHashMap<BlueDirectClient, ClientSession> sessions =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientSession> sessionById = 
        new ConcurrentHashMap<>();

    // 心跳管理
    private final ScheduledExecutorService heartbeatScheduler =
        Executors.newScheduledThreadPool(1);
    // 文件传输管理
    private final ConcurrentHashMap<String, FileTransfer> fileTransfers = 
        new ConcurrentHashMap<>();
    private final FileReceiveCallback fileCallback;
    
    // 回调接口
    private final ClientCallback callback;
    
    // 统计信息
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    private final String cacheDirPath;
    
    public BlueDirectServer(String cacheDir,ClientCallback callback,
                            FileReceiveCallback fileCallback) throws IOException {
        this.callback = callback;
        this.fileCallback = fileCallback;
        this.cacheDirPath = cacheDir;
        initialize();
    }
    
    /**
     * 初始化服务器
     */
    @SuppressLint("MissingPermission")
    private void initialize(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter != null) {
            try {
                serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(tag,
                        UUID.fromString(MY_UUID));
            } catch (Exception e) {
               throw new RuntimeException("create server socket error");
            }
        }
    }

    /**
     * 启动服务器
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server already running");
        }
        
        // 启动事件循环线程
        new Thread(this::acceptLoop, "Server-AcceptLoop").start();
        
        // 启动心跳检测
        startHeartbeatMonitor();
        
        System.out.println("WiFi Direct Server started");
    }

    /**
     * 核心事件循环
     */
    private void acceptLoop() {
        try {
            while (running.get()) {
                BluetoothSocket socket = serverSocket.accept();
                handleAccept(socket);
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
     * 处理新的客户端连接
     */
    private void handleAccept(BluetoothSocket socket) throws IOException {
        try {
            if (socket == null) {
                return; // 没有客户端连接
            }
            // 创建客户端会话
            BlueDirectClient client = new BlueDirectClient(cacheDirPath,
                    new WeakReference<>(callback),new WeakReference<>(fileCallback));
            client.initSocket(socket);
            String clientId = generateClientId(socket);
            ClientSession session = new ClientSession(clientId,client);
            
            sessions.put(client, session);
            sessionById.put(clientId, session);
            
            totalConnections.incrementAndGet();
            
            System.out.println("Client connected: " + clientId + 
                             " (" + sessions.size() + " clients connected)");
        } catch (Exception e) {
            if (socket != null && socket.isConnected()) {
                socket.close();
            }
//            Log.error("Unexpected error accepting connection: " + e.getMessage(), e);
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
        ByteBuffer data = BlueDirectProtocol.encodeString(message);
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
        ByteBuffer data = BlueDirectProtocol.encodeString(message);
        broadcast(data);
    }
    
    /**
     * 发送文件到客户端
     */
    public void sendFile(String clientId, File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found: " + file.getPath());
        }
        
        String fileId = BlueDirectProtocol.generateFileId();
        long fileSize = file.length();
        int totalChunks = (int) Math.ceil((double) fileSize / BlueDirectProtocol.MAX_CHUNK_SIZE);
        
        // 发送文件元数据
        ByteBuffer meta = BlueDirectProtocol.encodeFileMeta(
            fileId, file.getName(), fileSize, totalChunks
        );
        sendToClient(clientId, meta);

        // 分片发送文件数据
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] chunkBuffer = new byte[BlueDirectProtocol.MAX_CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesRead;

            while ((bytesRead = raf.read(chunkBuffer)) != -1) {
                byte[] chunkData = Arrays.copyOf(chunkBuffer, bytesRead);
                boolean isLast = (raf.getFilePointer() >= fileSize);

                ByteBuffer chunk = BlueDirectProtocol.encodeFileData(
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
    }
    
    /**
     * 启动心跳监控
     */
    private void startHeartbeatMonitor() {
        heartbeatScheduler.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            
            for (ClientSession session : sessions.values()) {
                // 检查心跳超时
                if (now - session.getLastHeartbeatTime() > BlueDirectProtocol.HEARTBEAT_TIMEOUT) {
                    closeClient(session.socket, "Heartbeat timeout");
                    continue;
                }
                
                // 发送心跳
                if (now - session.getLastHeartbeatSent() > BlueDirectProtocol.HEARTBEAT_INTERVAL) {
                    ByteBuffer heartbeat = BlueDirectProtocol.encodeHeartbeat();
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
        
        List<BlueDirectClient> timeoutSessions = new ArrayList<>();
        for (Map.Entry<BlueDirectClient, ClientSession> entry : sessions.entrySet()) {
            if (now - entry.getValue().getLastActivityTime() > timeout) {
                timeoutSessions.add(entry.getKey());
            }
        }
        for (BlueDirectClient channel : timeoutSessions) {
            closeClient(channel, "Inactivity timeout");
        }
    }
    
    /**
     * 关闭客户端连接
     */
    private void closeClient(BlueDirectClient channel, String reason) {
        ClientSession session = sessions.remove(channel);
        
        if (session != null) {
            sessionById.remove(session.clientId);

            // 清理相关文件传输
            cleanupFileTransfers(session.clientId);
            
            System.out.println("Client disconnected: " + session.clientId + 
                             " - Reason: " + reason);
            
            closeChannelQuietly(channel);
        }else {
            // 没有会话，直接关闭通道
            closeChannelQuietly(channel);
        }
    }

    /**
     * 安静地关闭通道
     */
    private void closeChannelQuietly(BlueDirectClient channel) {
        if (channel != null) {
            channel.disconnect();
        }
    }

    /**
     * 清理文件传输
     */
    private void cleanupFileTransfers(String clientId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fileTransfers.entrySet().removeIf(entry -> entry.getValue().clientId.equals(clientId));
        }
    }
    
    /**
     * 断开指定客户端
     */
    public void disconnectClient(String clientId, String reason) {
        ClientSession session = sessionById.get(clientId);
        if (session != null) {
            closeClient(session.socket, reason);
        }
    }
    
    /**
     * 生成客户端ID
     */
    private String generateClientId(BluetoothSocket socket) {
        try {
            String address = socket.getRemoteDevice().getAddress();
            return "CLIENT-" + UUID.randomUUID().toString().substring(0, 8) + 
                   "-" + address;
        } catch (NullPointerException e) {
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
            for (BlueDirectClient channel : sessions.keySet()) {
                channel.disconnect("server shutdown");
            }
            
            sessions.clear();
            sessionById.clear();
            fileTransfers.clear();
            
            // 关闭调度器
            heartbeatScheduler.shutdown();

            // 关闭ServerSocketChannel
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                    serverSocket = null;
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
            0,
            fileTransfers.size()
        );
    }
    
    /**
     * 客户端会话类
     */
    private static class ClientSession {
        final String clientId;
        final BlueDirectClient socket;
        final ByteBuffer readBuffer = ByteBuffer.allocateDirect(BlueDirectProtocol.BUFFER_SIZE);
        final LinkedList<ByteBuffer> writeQueue = new LinkedList<>();
        final ReentrantLock writeLock = new ReentrantLock();
        final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
        
        volatile long lastActivityTime = System.currentTimeMillis();
        volatile long lastHeartbeatTime = System.currentTimeMillis();
        volatile long lastHeartbeatSent = 0;
        
        ClientSession(String clientId, BlueDirectClient channel) {
            this.clientId = clientId;
            this.socket = channel;
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
            OutputStream outputStream = null;

            try {
                while (!writeQueue.isEmpty()) {
                    ByteBuffer buffer = writeQueue.peek();
                    if (buffer != null) {
                        socket.sendData(buffer);
                        if (buffer.hasRemaining()) {
                            return false; // 没有写完
                        }
                        writeQueue.poll(); // 已写完，移除
                    }
                }
                return true; // 所有数据已写完
            } finally {
                if(outputStream != null){
                    outputStream.close();
                }
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
            if(!writeFile.exists()) {
                try {
//                      writeFile.mkdir();
                    writeFile.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.totalChunks = totalChunks;
        }
        
        void addChunk(int chunkIndex, byte[] chunkData) throws IOException{
            if(writeFile != null){
                RandomAccessFile raf = null;
                try{
                    raf = new RandomAccessFile(writeFile,"rw");
                    int offset = chunkIndex*BlueDirectProtocol.MAX_CHUNK_SIZE;
                    raf.seek(offset);
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