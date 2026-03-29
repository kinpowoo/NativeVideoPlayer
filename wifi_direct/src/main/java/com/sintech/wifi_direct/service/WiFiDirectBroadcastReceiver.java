package com.sintech.wifi_direct.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

/**
 * WiFi Direct 广播接收器
 * 监听系统事件，保持服务存活
 */
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
    
    private static final String TAG = "WiFiDirectReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Broadcast received: " + action);
        
        if (action == null) {
            return;
        }
        
        switch (action) {
            // 系统启动完成
            case Intent.ACTION_BOOT_COMPLETED:
                handleBootCompleted(context);
                break;
                
            // 网络状态变化
            case ConnectivityManager.CONNECTIVITY_ACTION:
                handleConnectivityChange(context);
                break;
                
            // WiFi状态变化
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                handleWifiStateChange(context, intent);
                break;
                
            // 屏幕状态变化
            case Intent.ACTION_SCREEN_ON:
                handleScreenOn(context);
                break;
                
            case Intent.ACTION_SCREEN_OFF:
                handleScreenOff(context);
                break;
                
            // 用户解锁
            case Intent.ACTION_USER_PRESENT:
                handleUserPresent(context);
                break;
                
            // WiFi P2P相关
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                handleP2pStateChanged(context, intent);
                break;
                
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                handleP2pConnectionChanged(context, intent);
                break;
        }
    }
    
    /**
     * 处理系统启动完成
     */
    private void handleBootCompleted(Context context) {
        Log.d(TAG, "Boot completed, starting WiFi Direct service");
        
        // 延迟启动服务，避免系统负载过高
        new android.os.Handler().postDelayed(() -> {
//            WiFiDirectForegroundService.startService(context);
        }, 30000); // 30秒后启动
    }
    
    /**
     * 处理网络连接变化
     */
    private void handleConnectivityChange(Context context) {
        ConnectivityManager cm = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
            
            if (isConnected) {
                Log.d(TAG, "Network connected, ensuring WiFi Direct service is running");
//                WiFiDirectForegroundService.startService(context);
            } else {
                Log.w(TAG, "Network disconnected");
            }
        }
    }
    
    /**
     * 处理WiFi状态变化
     */
    private void handleWifiStateChange(Context context, Intent intent) {
        int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 
                                          WifiManager.WIFI_STATE_UNKNOWN);
        
        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                Log.d(TAG, "WiFi enabled");
//                WiFiDirectForegroundService.startService(context);
                break;
                
            case WifiManager.WIFI_STATE_DISABLED:
                Log.d(TAG, "WiFi disabled");
                // WiFi关闭时不需要停止服务，因为可能会自动重连
                break;
        }
    }
    
    /**
     * 处理屏幕打开
     */
    private void handleScreenOn(Context context) {
        Log.d(TAG, "Screen turned on");
        // 屏幕亮起时确保服务运行
        WiFiDirectForegroundService.startService(context);
    }
    
    /**
     * 处理屏幕关闭
     */
    private void handleScreenOff(Context context) {
        Log.d(TAG, "Screen turned off");
        // 屏幕关闭时保持服务运行
    }
    
    /**
     * 处理用户解锁
     */
    private void handleUserPresent(Context context) {
        Log.d(TAG, "User present");
//        WiFiDirectForegroundService.startService(context);
    }
    
    /**
     * 处理WiFi P2P状态变化
     */
    private void handleP2pStateChanged(Context context, Intent intent) {
        int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
        boolean enabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
        
        if (enabled) {
            Log.d(TAG, "WiFi P2P enabled");
//            WiFiDirectForegroundService.startService(context);
        } else {
            Log.w(TAG, "WiFi P2P disabled");
        }
    }
    
    /**
     * 处理WiFi P2P连接变化
     */
    private void handleP2pConnectionChanged(Context context, Intent intent) {
        // WiFi P2P连接状态变化
        Log.d(TAG, "WiFi P2P connection changed");
    }
}