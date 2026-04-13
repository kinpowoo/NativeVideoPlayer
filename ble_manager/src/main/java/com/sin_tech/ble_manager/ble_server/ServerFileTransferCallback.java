package com.sin_tech.ble_manager.ble_server;

public interface ServerFileTransferCallback {
    void onFileTransferStarted(String clientId, String fileId, 
                              String fileName, long fileSize);
    void onFileChunkReceived(String clientId, String fileId, 
                            int chunkIndex, int chunkSize);
    void onFileTransferCompleted(String clientId, String fileId, 
                                String fileName, String filePath);
    void onFileTransferError(String clientId, String fileId, String error);
}