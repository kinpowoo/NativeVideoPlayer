package com.sin_tech.ble_manager.ble_server;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * WiFi Direct 通信协议
 * 协议格式: [魔数(4字节)][版本(1字节)][类型(1字节)][长度(4字节)][数据体]
 * 大端字节序
 */
public class WiFiDirectProtocol {
    
    // 协议魔数
    public static final int MAGIC_NUMBER = 0x57494644; // "WIFD"
    
    // 协议版本
    public static final byte VERSION_1 = 0x01;
    
    // 消息类型
    public static final byte TYPE_HEARTBEAT = 0x01;      // 心跳包
    public static final byte TYPE_HEARTBEAT_ACK = 0x02;  // 心跳确认
    public static final byte TYPE_STRING = 0x03;         // 字符串消息
    public static final byte TYPE_FILE_META = 0x04;      // 文件元数据
    public static final byte TYPE_FILE_DATA = 0x05;      // 文件数据
    public static final byte TYPE_FILE_ACK = 0x06;       // 文件确认
    public static final byte TYPE_FILE_ERROR = 0x07;     // 文件错误
    public static final byte TYPE_CMD_DISCOVERY = 0x08;  // 设备发现
    public static final byte TYPE_CMD_CONNECT = 0x09;    // 连接命令
    public static final byte TYPE_CMD_DISCONNECT = 0x0A; // 断开命令
    public static final byte TYPE_ERROR = 0x7F;          // 错误消息
    
    // 心跳配置
    public static final int HEARTBEAT_INTERVAL = 3000;    // 3秒发送一次
    public static final int HEARTBEAT_TIMEOUT = 10000;    // 10秒无响应超时
    
    // 传输配置
    public static final int MAX_CHUNK_SIZE = 32 * 1024;   // 32KB分片
    public static final int BUFFER_SIZE = 256 * 1024;     // 256KB缓冲区
    
    /**
     * 编码消息
     */
    public static ByteBuffer encode(byte type, byte[] data) {
        int dataLength = (data != null) ? data.length : 0;
        int totalLength = 10 + dataLength; // 头部10字节
        
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // 写入协议头
        buffer.putInt(MAGIC_NUMBER);  // 4字节魔数
        buffer.put(VERSION_1);        // 1字节版本
        buffer.put(type);             // 1字节类型
        buffer.putInt(dataLength);    // 4字节数据长度
        
        // 写入数据
        if (data != null && dataLength > 0) {
            buffer.put(data);
        }
        
        buffer.flip();
        return buffer;
    }
    
    /**
     * 编码心跳包
     */
    public static ByteBuffer encodeHeartbeat() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        buffer.putInt(MAGIC_NUMBER);
        buffer.put(VERSION_1);
        buffer.put(TYPE_HEARTBEAT);
        buffer.putInt(0);
        
        buffer.flip();
        return buffer;
    }
    
    /**
     * 编码心跳确认
     */
    public static ByteBuffer encodeHeartbeatAck() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        buffer.putInt(MAGIC_NUMBER);
        buffer.put(VERSION_1);
        buffer.put(TYPE_HEARTBEAT_ACK);
        buffer.putInt(0);
        
        buffer.flip();
        return buffer;
    }
    
    /**
     * 编码字符串消息
     */
    public static ByteBuffer encodeString(String message) {
        try {
            byte[] data = message.getBytes("UTF-8");
            return encode(TYPE_STRING, data);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 编码文件元数据
     */
    public static ByteBuffer encodeFileMeta(String fileId, String fileName, 
                                           long fileSize, int totalChunks) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.writeUTF(fileId);
            dos.writeUTF(fileName);
            dos.writeLong(fileSize);
            dos.writeInt(totalChunks);
            dos.writeLong(System.currentTimeMillis()); // 时间戳
            
            dos.flush();
            return encode(TYPE_FILE_META, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode file meta", e);
        }
    }
    
    /**
     * 编码文件数据块
     */
    public static ByteBuffer encodeFileData(String fileId, int chunkIndex, 
                                          byte[] chunkData, boolean isLast) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.writeUTF(fileId);
            dos.writeInt(chunkIndex);
            dos.writeInt(chunkData.length);
            dos.write(chunkData);
            dos.writeBoolean(isLast);
            
            dos.flush();
            return encode(TYPE_FILE_DATA, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode file data", e);
        }
    }
    
    /**
     * 解码消息
     */
    public static DecodedMessage decode(ByteBuffer buffer) {
        if (buffer.remaining() < 10) {
            return null; // 头部不完整
        }
        
        buffer.mark();
        
        int magic = buffer.getInt();
        if (magic != MAGIC_NUMBER) {
            buffer.reset();
            return new DecodedMessage(TYPE_ERROR, 0, 
                "Invalid magic number".getBytes());
        }
        
        byte version = buffer.get();
        if (version != VERSION_1) {
            buffer.reset();
            return new DecodedMessage(TYPE_ERROR, 0, 
                "Unsupported version".getBytes());
        }
        
        byte type = buffer.get();
        int length = buffer.getInt();
        
        if (buffer.remaining() < length) {
            buffer.reset(); // 数据不完整
            return null;
        }
        
        byte[] data = new byte[length];
        if (length > 0) {
            buffer.get(data);
        }
        
        return new DecodedMessage(type, length, data);
    }
    
    /**
     * 解码字符串消息
     */
    public static String decodeString(byte[] data) {
        try {
            return new String(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(data);
        }
    }
    
    /**
     * 解码文件元数据
     */
    public static FileMeta decodeFileMeta(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        
        String fileId = dis.readUTF();
        String fileName = dis.readUTF();
        long fileSize = dis.readLong();
        int totalChunks = dis.readInt();
        long timestamp = dis.readLong();
        
        return new FileMeta(fileId, fileName, fileSize, totalChunks, timestamp);
    }
    
    /**
     * 解码文件数据块
     */
    public static FileChunk decodeFileChunk(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        
        String fileId = dis.readUTF();
        int chunkIndex = dis.readInt();
        int chunkSize = dis.readInt();
        byte[] chunkData = new byte[chunkSize];
        dis.readFully(chunkData);
        boolean isLast = dis.readBoolean();
        
        return new FileChunk(fileId, chunkIndex, chunkData, isLast);
    }
    
    /**
     * 生成文件ID
     */
    public static String generateFileId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 数据类定义
     */
    public static class DecodedMessage {
        public final byte type;
        public final int length;
        public final byte[] data;
        
        public DecodedMessage(byte type, int length, byte[] data) {
            this.type = type;
            this.length = length;
            this.data = data;
        }
    }
    
    public static class FileMeta {
        public final String fileId;
        public final String fileName;
        public final long fileSize;
        public final int totalChunks;
        public final long timestamp;
        
        public FileMeta(String fileId, String fileName, long fileSize, 
                       int totalChunks, long timestamp) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.totalChunks = totalChunks;
            this.timestamp = timestamp;
        }
    }
    
    public static class FileChunk {
        public final String fileId;
        public final int chunkIndex;
        public final byte[] chunkData;
        public final boolean isLast;
        
        public FileChunk(String fileId, int chunkIndex, byte[] chunkData, 
                        boolean isLast) {
            this.fileId = fileId;
            this.chunkIndex = chunkIndex;
            this.chunkData = chunkData;
            this.isLast = isLast;
        }
    }
}