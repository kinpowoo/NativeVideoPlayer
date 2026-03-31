package com.sintech.wifi_direct.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class P2pServiceManager {
    private static final String TAG = "P2pServiceManager";
    
    // 服务相关常量
    private static final String SERVICE_INSTANCE = "_sin_tech_video_play";
    private static final String SERVICE_TYPE = "_sin_tech_video_play._tcp";
    private static final int DEFAULT_PORT = 8888;

    private final WifiP2pManager mWifiP2pManager;
    private WifiP2pManager.Channel mChannel;
    private final int mPort = DEFAULT_PORT;
    private final String packageName = "com.jhkj.videoplayer";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 监听接口
    public interface ServiceStatusListener {
        void onServiceStarted(int port);
        void onServiceStopped();
        void onError(String error);
    }
    
    private final ServiceStatusListener mListener;

    private WifiP2pDnsSdServiceInfo serviceInfo;
    
    public P2pServiceManager(Context context, ServiceStatusListener listener) {
        Context mContext = context.getApplicationContext();
        mListener = listener;
        mWifiP2pManager = (WifiP2pManager) mContext.getSystemService(Context.WIFI_P2P_SERVICE);
        
        if (mWifiP2pManager == null) {
            if (mListener != null) {
                mListener.onError("设备不支持Wi-Fi Direct");
            }
            return;
        }
        // 创建Channel
        mChannel = mWifiP2pManager.initialize(mContext, mContext.getMainLooper(), null);

        // 准备服务信息
        Map<String, String> record = new HashMap<>();
        record.put("port", String.valueOf(mPort));
        record.put("device_name", "SinTechDirectWiFi");
        record.put("app_name", mContext.getPackageName());
        String localIp = findLocalInterfaceIp();
        if(!TextUtils.isEmpty(localIp)) {
            record.put("remote_host", localIp);
        }

        record.put("listenport", String.valueOf(mPort));
        record.put("buddyname", "SinTechDirectWiFi");
        record.put("available", "visible");

        // 创建DNS-SD服务信息
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE,
                SERVICE_TYPE,
                record
        );

    }
    
    /**
     * 启动P2P服务
     */
    public void startService() {
        // 2. 创建P2P组
        createP2pGroup();
    }

    // 修改后的 createP2pGroup 方法
    private void createP2pGroup() {
        // 先移除现有组
        mWifiP2pManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "已移除现有组");
                // 延迟创建新组
                mainHandler.postDelayed(() -> {
                    createNewGroup();
                }, 1000);
            }

            @Override
            public void onFailure(int reason) {
                // 即使移除失败也尝试创建新组
                Log.d(TAG, "移除现有组失败，尝试直接创建新组，原因: " + reason);
                createNewGroup();
            }
        });
    }

    /**
     * 创建P2P组
     */
    @SuppressLint("MissingPermission")
    private void createNewGroup() {
        WifiP2pConfig config = new WifiP2pConfig();
        config.groupOwnerIntent = 15; // 0-15，值越大越可能成为组所有者
//        config.networkName = "MyP2PGroup";
//        config.passphrase = "12345678";
        mWifiP2pManager.createGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "P2P组创建成功");
                // 组创建成功后注册服务
                registerService();
            }
            
            @Override
            public void onFailure(int reason) {
                String errorMsg = "P2P组创建失败: ";
                switch (reason) {
                    case WifiP2pManager.P2P_UNSUPPORTED:
                        errorMsg += "设备不支持P2P";
                        break;
                    case WifiP2pManager.BUSY:
                        errorMsg += "系统繁忙";
                        break;
                    case WifiP2pManager.ERROR:
                    default:
                        errorMsg += "未知错误 (code: " + reason + ")";
                        break;
                }
                notifyError(errorMsg);
            }
        });
    }


    /**
     * 注册P2P服务
     */
    @SuppressLint("MissingPermission")
    private void registerService() {
        // 注册服务
        mWifiP2pManager.addLocalService(mChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "P2P服务注册成功，端口: " + mPort);
                mListener.onServiceStarted(mPort);
            }
            
            @Override
            public void onFailure(int reason) {
                String errorMsg = "P2P服务注册失败: ";
                switch (reason) {
                    case WifiP2pManager.P2P_UNSUPPORTED:
                        errorMsg += "设备不支持P2P";
                        break;
                    case WifiP2pManager.BUSY:
                        errorMsg += "系统繁忙";
                        break;
                    default:
                        errorMsg += "错误码: " + reason;
                        break;
                }
                notifyError(errorMsg);
            }
        });
    }

    /**
     * 查找P2P接口的IP地址
     */
    private String findLocalInterfaceIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                String ifaceName = networkInterface.getName().toLowerCase();

                // 查找P2P接口
                if ((ifaceName.contains("wlan")) &&
                        networkInterface.isUp() && !networkInterface.isLoopback()) {

                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof Inet4Address) {
                            String ip = address.getHostAddress();
                            // 检查是否是P2P网络IP
                            if (ip != null && ip.startsWith("192.168.")) {
                                return ip;
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * 停止P2P服务
     */
    public void stopService() {
        if (mWifiP2pManager == null || mChannel == null) {
            return;
        }

        // 移除本地服务
        mWifiP2pManager.removeLocalService(mChannel, serviceInfo, null);

        // 停止P2P组
        mWifiP2pManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "P2P组已移除");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "移除P2P组失败: " + reason);
            }
        });

        if (mListener != null) {
            mListener.onServiceStopped();
        }
    }

    private void notifyError(String error) {
        Log.e(TAG, error);
        if (mListener != null) {
            mListener.onError(error);
        }
    }
    
    /**
     * 获取当前端口
     */
    public int getPort() {
        return mPort;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        stopService();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            if (mChannel != null) {
                mChannel.close();
            }
        }
    }
}