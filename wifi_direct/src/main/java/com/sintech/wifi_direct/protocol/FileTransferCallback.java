package com.sintech.wifi_direct.protocol;

/**
 * 文件传输回调接口
 */
public interface FileTransferCallback {
    void onFileTransferStarted(String clientId, String fileId, 
                              String fileName, long fileSize);
    void onFileChunkReceived(String clientId, String fileId, 
                            int chunkIndex, int chunkSize);
    void onFileTransferCompleted(String clientId, String fileId, 
                                String fileName, String filePath);
    void onFileTransferError(String clientId, String fileId, String error);
}