package com.sintech.wifi_direct.util;

import android.net.wifi.p2p.WifiP2pDevice;

public class DiscoveredService {
    public String name;
    public WifiP2pDevice device;
    public String remoteHost;
    public int port;

    public DiscoveredService(String name,String host, WifiP2pDevice p2pDevice, int port) {
        this.name = name;
        this.device = p2pDevice;
        this.remoteHost = host;
        this.port = port;
    }
}