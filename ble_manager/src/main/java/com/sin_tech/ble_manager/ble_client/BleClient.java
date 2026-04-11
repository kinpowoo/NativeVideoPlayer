package com.sin_tech.ble_manager.ble_client;

import android.bluetooth.BluetoothGatt;
import com.sin_tech.ble_manager.BleManager;
import com.sin_tech.ble_manager.ble_tradition.protocol.BlueDirectProtocol;
import com.sin_tech.ble_manager.ble_tradition.protocol.BlueDirectProtocol.DecodedMessage;
import com.sin_tech.ble_manager.ble_tradition.protocol.BlueDirectProtocol.FileChunk;
import com.sin_tech.ble_manager.ble_tradition.protocol.BlueDirectProtocol.FileMeta;
import com.sin_tech.ble_manager.ble_tradition.protocol.ClientCallback;
import com.sin_tech.ble_manager.ble_tradition.protocol.FileReceiveCallback;
import com.sin_tech.ble_manager.models.BleDevice;
import com.sin_tech.ble_manager.protocols.BleGattCallback;
import com.sin_tech.ble_manager.protocols.BleNotifyCallback;
import com.sin_tech.ble_manager.protocols.BleWriteCallback;
import com.sin_tech.ble_manager.protocols.exception.BleException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WiFi Direct 客户端
 * 单Selector线程，支持心跳、文件传输
 */
public class BleClient {
    private final String SERVICE_UUID = "0000FFF0-0000-1000-8000-00805F9B34FB";
    private final String CHAR_READ_WRITE_UUID = "0000FFF1-0000-1000-8000-00805F9B34FB";
    // 核心NIO组件
    private BleDevice bleDevice;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 连接配置
    private String clientId;

    // 文件传输管理
    private final ConcurrentHashMap<String, FileReceiveSession> fileReceives =
            new ConcurrentHashMap<>();
    private final WeakReference<FileReceiveCallback> fileCallback;

    // 回调接口
    private final WeakReference<ClientCallback> callback;

    // 数据缓冲区
    private final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(BlueDirectProtocol.BUFFER_SIZE);

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

    public BleClient(String cacheDir,
                     WeakReference<ClientCallback> callback, WeakReference<FileReceiveCallback> fileCallback) {
        this.clientId = "CLIENT-" + UUID.randomUUID().toString().substring(0, 8);
        this.callback = callback;
        this.fileCallback = fileCallback;
        this.cacheDirPath = cacheDir;
//        downloadFile = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    }

    public void connectDevice(BleDevice device){
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Client already running");
        }
        this.bleDevice = device;
        this.clientId = "Client_"+device.getMac();
        BleManager.getInstance().connect(device, new BleGattCallback() {
            @Override
            public void onStartConnect() {

            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {

            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                if(callback != null && callback.get() != null) {
                    callback.get().onConnected(bleDevice.getMac());
                }
                connected.set(true);
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                if(callback != null && callback.get() != null) {
                    callback.get().onDisconnected(device.getMac(),"");
                }
            }
        });
        BleManager.getInstance().notify(bleDevice, SERVICE_UUID, CHAR_READ_WRITE_UUID, true,
                new BleNotifyCallback() {
                    @Override
                    public void onNotifySuccess() {

                    }

                    @Override
                    public void onNotifyFailure(BleException exception) {

                    }

                    @Override
                    public void onCharacteristicChanged(byte[] data) {
                        ByteBuffer buff = ByteBuffer.wrap(data);
                        processReceivedData(buff);
                    }
                });

        // 发送客户端信息
        sendString("CLIENT_INFO:" + clientId);
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
            DecodedMessage message = BlueDirectProtocol.decode(buffer);

            if (message == null) {
                buffer.reset();
                break;
            }

            if (message.type == BlueDirectProtocol.TYPE_ERROR) {
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
            case BlueDirectProtocol.TYPE_STRING:
                handleStringMessage(message.data);
                break;

            case BlueDirectProtocol.TYPE_FILE_META:
                handleFileMeta(message.data);
                break;

            case BlueDirectProtocol.TYPE_FILE_DATA:
                handleFileData(message.data);
                break;
            case BlueDirectProtocol.TYPE_FILE_ACK:
                handleFileAck(message.data);
                break;
            case BlueDirectProtocol.TYPE_FILE_ERROR:
                handleFileError(message.data);
                break;

            default:
                System.err.println("Unknown message type: " + message.type);
        }
    }

    /**
     * 处理字符串消息
     */
    private void handleStringMessage(byte[] data) {
        String message = BlueDirectProtocol.decodeString(data);

        if (callback != null && callback.get() != null) {
            callback.get().onMessageReceived(getClientId(),message);
        }
    }

    /**
     * 处理文件元数据
     */
    private void handleFileMeta(byte[] data) {
        try {
            FileMeta meta = BlueDirectProtocol.decodeFileMeta(data);

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
                        getClientId(),
                        meta.fileId,
                        meta.fileName,
                        meta.fileSize
                );
            }

        } catch (IOException e) {
            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileTransferError(getClientId(),
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
            FileChunk chunk = BlueDirectProtocol.decodeFileChunk(data);
            String fileId = chunk.fileId;

            FileReceiveSession session = fileReceives.get(fileId);
            if (session == null) {
                System.err.println("File session not found: " + fileId);
                return;
            }
            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileChunkReceived(
                        getClientId(),
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
                            fileCallback.get().onFileTransferError(getClientId(),
                                    "write data to local temp file failed"
                            );
                        }
                    }
                }
            });
        } catch (IOException e) {
            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileTransferError(getClientId(),
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
                fileCallback.get().onFileReceived(getClientId(),
                        session.fileId,
                        session.fileName,
                        filePath
                );
            }
            fileReceives.remove(session.fileId);
        } catch (IOException e) {
            if (fileCallback != null && fileCallback.get() != null) {
                fileCallback.get().onFileTransferError(getClientId(),
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
            callback.get().onFileAckReceived(getClientId(),ack);
        }
    }

    /**
     * 处理文件错误
     */
    private void handleFileError(byte[] data) {
        String error = new String(data, StandardCharsets.UTF_8);
        System.err.println("File transfer error: " + error);

        if (fileCallback != null && fileCallback.get() != null) {
            fileCallback.get().onFileTransferError(getClientId(),error);
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
        ByteBuffer data = BlueDirectProtocol.encodeString(message);
        sendData(data);
    }

    /**
     * 发送文件
     */
    public void sendFile(File file) throws IOException {
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
        sendData(meta);

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
        if(bleDevice != null) {
            byte[] arr = data.array();
            BleManager.getInstance().write(bleDevice, SERVICE_UUID, CHAR_READ_WRITE_UUID,
                    arr, true, new BleWriteCallback() {
                        @Override
                        public void onWriteSuccess(int current, int total, byte[] justWrite) {

                        }

                        @Override
                        public void onWriteFailure(BleException exception) {

                        }
                    });
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

            // 清理文件传输
            fileReceives.clear();

            if(bleDevice != null){
                BleManager.getInstance().disconnect(bleDevice);
                bleDevice = null;
            }

            dataBuffer.reset();

            if (callback != null && callback.get() != null) {
                callback.get().onDisconnected(getClientId(),reason);
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
        return connected.get();
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
                    int offset = chunkIndex * BlueDirectProtocol.MAX_CHUNK_SIZE;
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