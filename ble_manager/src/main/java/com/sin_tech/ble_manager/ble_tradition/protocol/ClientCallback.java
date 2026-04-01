package com.sin_tech.ble_manager.ble_tradition.protocol;

/**
 * 客户端回调接口
 */
public interface ClientCallback {
    void onConnected(String clientId);
    void onDisconnected(String clientId,String reason);
    void onMessageReceived(String clientId,String message);
    void onHeartbeatReceived(String clientId);
    void onHeartbeatAckReceived(String clientId);
    void onFileAckReceived(String clientId,String ack);
}
