package com.sin_tech.ble_manager.ble_tradition.protocol;

/**
 * 文件接收回调接口
 */
public interface FileReceiveCallback {
    void onFileReceiveStarted(String clientId,String fileId, String fileName, long fileSize);
    void onFileChunkReceived(String clientId,String fileId, int chunkIndex, int chunkSize);
    void onFileReceived(String clientId,String fileId, String fileName, String filePath);
    void onFileTransferError(String clientId,String error);
}