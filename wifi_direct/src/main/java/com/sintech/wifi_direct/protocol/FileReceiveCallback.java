package com.sintech.wifi_direct.protocol;

/**
 * 文件接收回调接口
 */
public interface FileReceiveCallback {
    void onFileReceiveStarted(String fileId, String fileName, long fileSize);
    void onFileChunkReceived(String fileId, int chunkIndex, int chunkSize);
    void onFileReceived(String fileId, String fileName, byte[] fileData);
    void onFileTransferError(String error);
}