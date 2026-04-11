package com.sin_tech.ble_manager.ble_server;

import androidx.annotation.NonNull;

/**
 * 服务器回调接口
 */
public interface ServerCallback {
    void onClientConnected(@NonNull String clientId);
    void onClientDisconnected(@NonNull String clientId, String reason);
    void onMessageReceived(@NonNull String clientId, String message);
    void onHeartbeatReceived(@NonNull String clientId);
    void onHeartbeatAckReceived(@NonNull String clientId);
    void onFileAckReceived(@NonNull String clientId, String ack);
}

