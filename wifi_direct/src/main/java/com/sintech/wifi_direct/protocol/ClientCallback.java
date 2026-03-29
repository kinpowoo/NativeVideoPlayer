package com.sintech.wifi_direct.protocol;

/**
 * 客户端回调接口
 */
public interface ClientCallback {
    void onConnected();
    void onDisconnected(String reason);
    void onMessageReceived(String message);
    void onHeartbeatReceived();
    void onHeartbeatAckReceived();
    void onFileAckReceived(String ack);
}
