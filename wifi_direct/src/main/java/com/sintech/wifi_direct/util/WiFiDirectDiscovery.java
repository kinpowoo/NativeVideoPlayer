package com.sintech.wifi_direct.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;

import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WiFi Direct 设备发现和组网
 * 适用于Android环境
 */
public class WiFiDirectDiscovery {
    
    private static final String TAG = "WiFiDirectDiscovery";
    private static final String SERVICE_INSTANCE = "_sin_tech_video_play";
    private static final String SERVICE_TYPE = "_sin_tech_video_play._tcp.local.";
    private static final String FilterDeviceName = "SinTechDirectWiFi";
    
    private final WeakReference<Activity> context;
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final DiscoveryCallback callback;
    private Boolean isDiscovering = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String,String> dnsTxtRecord = new HashMap<>();
    
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
            }else{
                if (callback != null) {
                    callback.onConnectFailed("没有形成Group");
                }
            }
        }
    };
    
    public WiFiDirectDiscovery(WeakReference<Activity> context, DiscoveryCallback callback) {
        this.context = context;
        this.callback = callback;

        this.manager = (WifiP2pManager) context.get().getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(context.get(), context.get().getMainLooper(), null);
//        registerReceivers();
        createP2pGroup();
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
        if(context.get() != null) {
            context.get().registerReceiver(wifiP2pReceiver, filter);
        }
    }

    private void clearServiceRequests() {
        manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "已清除服务请求");
            }

            @Override
            public void onFailure(int reason) {
                Log.w(TAG, "清除服务请求失败: " + reason);
            }
        });
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
        dnsTxtRecord.clear();
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery started");
                if (callback != null) {
                    callback.onDiscoveryStarted();
                }
                // 延迟后开始服务发现
                android.os.Handler handler = new android.os.Handler();
                handler.postDelayed(() -> {
                    setupServiceDiscovery();
                }, 1000);
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
     * 设置服务发现监听器
     */
    @SuppressLint("MissingPermission")
    private void setupServiceDiscovery() {
        // 设置TXT记录监听器
        WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(
                    String fullDomainName, Map<String, String> txtRecordMap,
                    WifiP2pDevice srcDevice) {
                String mac = srcDevice.deviceAddress;
                String hostIp = txtRecordMap.getOrDefault("remote_host","");
                if(!TextUtils.isEmpty(hostIp)) {
                    dnsTxtRecord.put(mac,hostIp);
                }
                Log.d(TAG, "发现TXT记录: " + txtRecordMap);
                // TXT记录会在服务响应之前到达
            }
        };

        // 设置服务响应监听器
        WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(
                    String instanceName, String registrationType,
                    WifiP2pDevice srcDevice) {

                Log.d(TAG, "发现服务: " + instanceName +
                        " 类型: " + registrationType +
                        " 设备: " + srcDevice.deviceName);

                // 检查服务类型是否匹配
                if (!SERVICE_TYPE.equals(registrationType)) {
                    Log.d(TAG, "忽略不匹配的服务类型: " + registrationType);
                    return;
                }
                // 创建服务对象
                String host = dnsTxtRecord.getOrDefault(srcDevice.deviceAddress,"");
                DiscoveredService service = new DiscoveredService(
                        instanceName,
                        host,
                        srcDevice,
                        8888 // 默认端口，实际应该从TXT记录获取
                );
                if(callback != null){
                    callback.onServiceScan(service);
                }
                stopDiscovery();
            }
        };

        // 设置监听器
        manager.setDnsSdResponseListeners(channel, servListener, txtListener);

        // 创建服务请求
        WifiP2pDnsSdServiceRequest serviceRequest =
                WifiP2pDnsSdServiceRequest.newInstance();

        // 添加服务请求
        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "服务请求添加成功");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "添加服务请求失败: " + reason);
                    }
                });

        // 开始服务发现
        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "开始服务发现");
            }

            @Override
            public void onFailure(int reason) {
                String errorMsg = "服务发现失败: " + reason;
                Log.e(TAG, errorMsg);

            }
        });
    }

    /**
     * 停止发现设备
     */
    public void stopDiscovery() {
        if(!isDiscovering)return;
        isDiscovering = false;
        clearServiceRequests();
        dnsTxtRecord.clear();
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
    @SuppressLint("MissingPermission")
    public void connectToDevice(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC; // PBC方式
        // 明确设置不希望成为组所有者
        config.groupOwnerIntent = 0;

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
                    callback.onConnectFailed(""+reason);
                }
            }
        });
    }
    
    /**
     * 获取连接信息
     */
    public void requestConnectionInfo() {
        manager.requestConnectionInfo(channel, connectionInfoListener);
        // 这个IP通常是：
        // - 192.168.49.1 (Group Owner的IP)
        // - 192.168.49.xxx (客户端的IP)
        // 这是一个独立的P2P子网，与普通Wi-Fi网络不同
    }

    // 修改后的 createP2pGroup 方法
    private void createP2pGroup() {
        // 先移除现有组
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "已移除现有组");
                // 延迟创建新组
                mainHandler.postDelayed(() -> {
                    createGroup();
                }, 1000);
            }

            @Override
            public void onFailure(int reason) {
                // 即使移除失败也尝试创建新组
                Log.d(TAG, "移除现有组失败，尝试直接创建新组，原因: " + reason);
                createGroup();
            }
        });
    }
    
    /**
     * 创建组（作为组主）
     */
    @SuppressLint("MissingPermission")
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
                // 获取设备列表
                WifiP2pDeviceList peers = intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
//                if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
//                        != PackageManager.PERMISSION_GRANTED) ||
//                        (ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
//                                != PackageManager.PERMISSION_GRANTED)) {
//                    return;
//                }
                if(peers != null && peers.getDeviceList() != null){
                    Collection<WifiP2pDevice> devices = peers.getDeviceList();
//                    if(devices != null && !devices.isEmpty()){
//                        for (WifiP2pDevice d : devices){
//                            String deviceName = d.deviceName;
//                            String deviceAddr = d.deviceAddress;
//                            String type = d.primaryDeviceType;
//                            if(deviceName.equals(WiFiDirectDiscovery.FilterDeviceName)){
//                                discoveredDevices.add(d);
//                                if (callback != null) {
//                                    List<WifiP2pDevice> matchDevice = new ArrayList<>();
//                                    matchDevice.add(d);
//                                    callback.onPeersDiscovered(matchDevice);
//                                }
//                            }
//                        }
//                    }
                }

//                requestPeers();
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                WifiP2pInfo p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                WifiP2pGroup group = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                NetworkInfo networkInfo = intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_NETWORK_INFO);

                if (networkInfo != null && networkInfo.isConnected() && p2pInfo != null) {
                    // 3. P2P连接已建立
                    if (callback != null) {
                        callback.onConnectionChanged(p2pInfo, group);
                    }
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
//        try {
//            if(context.get() != null) {
//                context.get().unregisterReceiver(wifiP2pReceiver);
//            }
//        } catch (IllegalArgumentException e) {
//            // 接收器未注册
//        }

        removeGroup();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            if (channel != null) {
                channel.close();
            }
        }
    }


    /**
     * 获取本机在P2P网络中的IP地址
     */
    private String getLocalP2pIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // 查找P2P网络接口
                if (networkInterface.getName().contains("p2p") ||
                        networkInterface.getDisplayName().contains("p2p")) {

                    Log.d(TAG, "发现P2P网络接口: " + networkInterface.getName());

                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (!address.isLoopbackAddress() &&
                                address instanceof Inet4Address) {  // 只关心IPv4
                            Log.d(TAG, "本机P2P IP: " + address.getHostAddress());
                            return address.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "获取网络接口失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 分析所有网络接口
     */
    private static void analyzeNetworkInterfaces() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            Log.d(TAG, "=== 所有网络接口 ===");
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Log.d(TAG, "接口: " + networkInterface.getName() +
                        " - " + networkInterface.getDisplayName());

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String type = address instanceof Inet4Address ? "IPv4" : "IPv6";
                    Log.d(TAG, "  " + type + ": " + address.getHostAddress());
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "分析网络接口失败: " + e.getMessage());
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
        void onConnectFailed(String reason);
        void onConnectionEstablished(InetAddress groupOwnerAddress, boolean isGroupOwner);
        void onConnectionChanged(WifiP2pInfo p2pInfo, WifiP2pGroup group);
        void onServiceScan(DiscoveredService service);
        void onThisDeviceChanged(WifiP2pDevice device);
        void onGroupCreated();
        void onGroupCreateFailed(int reason);
        void onGroupRemoved();
    }


}