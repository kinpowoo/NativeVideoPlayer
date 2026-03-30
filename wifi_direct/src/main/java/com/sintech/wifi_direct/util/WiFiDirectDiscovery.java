package com.sintech.wifi_direct.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * WiFi Direct 设备发现和组网
 * 适用于Android环境
 */
public class WiFiDirectDiscovery {
    
    private static final String TAG = "WiFiDirectDiscovery";
    
    private final Context context;
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final DiscoveryCallback callback;
    private Boolean isDiscovering = false;
    
    private final WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers) {
            List<WifiP2pDevice> deviceList = new ArrayList<>(peers.getDeviceList());
            if (callback != null) {
                callback.onPeersDiscovered(deviceList);
            }
        }
    };
    
    private final WifiP2pManager.ConnectionInfoListener connectionInfoListener =
        new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            if (info.groupFormed) {
                InetAddress groupOwnerAddress = info.groupOwnerAddress;
                boolean isGroupOwner = info.isGroupOwner;
                
                if (callback != null) {
                    callback.onConnectionEstablished(groupOwnerAddress, isGroupOwner);
                }
            }
        }
    };
    
    public WiFiDirectDiscovery(Context context, DiscoveryCallback callback) {
        this.context = context;
        this.callback = callback;
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(context, context.getMainLooper(), null);
        registerReceivers();
    }
    
    /**
     * 注册广播接收器
     */
    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        
        context.registerReceiver(wifiP2pReceiver, filter);
    }
    
    /**
     * 开始发现设备
     */
    @SuppressLint("MissingPermission")
    public void startDiscovery() {
        if(isDiscovering)return;
        isDiscovering = true;

        // 1. 先停止之前的发现
        manager.stopPeerDiscovery(channel, null);
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery started");
                if (callback != null) {
                    callback.onDiscoveryStarted();
                }
            }
            
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Discovery failed: " + reason);
                if (callback != null) {
                    callback.onDiscoveryFailed(reason);
                }
            }
        });
    }
    
    /**
     * 停止发现设备
     */
    public void stopDiscovery() {
        if(!isDiscovering)return;
        isDiscovering = false;
        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery stopped");
            }
            
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Stop discovery failed: " + reason);
            }
        });
    }
    
    /**
     * 请求对等设备列表
     */
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES})
    public void requestPeers() {
        manager.requestPeers(channel, peerListListener);
    }
    
    /**
     * 连接到设备
     */
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES})
    public void connectToDevice(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC; // PBC方式
        
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Connect request sent");
                if (callback != null) {
                    callback.onConnectRequested(device);
                }
            }
            
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Connect failed: " + reason);
                if (callback != null) {
                    callback.onConnectFailed(reason);
                }
            }
        });
    }
    
    /**
     * 获取连接信息
     */
    public void requestConnectionInfo() {
        manager.requestConnectionInfo(channel, connectionInfoListener);
    }
    
    /**
     * 创建组（作为组主）
     */
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES})
    public void createGroup() {
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Group created");
                if (callback != null) {
                    callback.onGroupCreated();
                }
            }
            
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Create group failed: " + reason);
                if (callback != null) {
                    callback.onGroupCreateFailed(reason);
                }
            }
        });
    }
    
    /**
     * 移除组
     */
    public void removeGroup() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Group removed");
                if (callback != null) {
                    callback.onGroupRemoved();
                }
            }
            
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Remove group failed: " + reason);
            }
        });
    }
    
    /**
     * 广播接收器
     */
    private final BroadcastReceiver wifiP2pReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                boolean enabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
                
                if (callback != null) {
                    callback.onWiFiP2pStateChanged(enabled);
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) ||
                        (ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                                != PackageManager.PERMISSION_GRANTED)) {
                    return;
                }
                requestPeers();
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                WifiP2pInfo p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                WifiP2pGroup group = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                
                if (callback != null) {
                    callback.onConnectionChanged(p2pInfo, group);
                }
                
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                
                if (callback != null) {
                    callback.onThisDeviceChanged(device);
                }
            }
        }
    };
    
    /**
     * 清理资源
     */
    public void cleanup() {
        try {
            context.unregisterReceiver(wifiP2pReceiver);
        } catch (IllegalArgumentException e) {
            // 接收器未注册
        }

        removeGroup();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            if (channel != null) {
                channel.close();
            }
        }

    }
    
    /**
     * 发现回调接口
     */
    public interface DiscoveryCallback {
        void onWiFiP2pStateChanged(boolean enabled);
        void onDiscoveryStarted();
        void onDiscoveryFailed(int reason);
        void onPeersDiscovered(List<WifiP2pDevice> peers);
        void onConnectRequested(WifiP2pDevice device);
        void onConnectFailed(int reason);
        void onConnectionEstablished(InetAddress groupOwnerAddress, boolean isGroupOwner);
        void onConnectionChanged(WifiP2pInfo p2pInfo, WifiP2pGroup group);
        void onThisDeviceChanged(WifiP2pDevice device);
        void onGroupCreated();
        void onGroupCreateFailed(int reason);
        void onGroupRemoved();
    }
}