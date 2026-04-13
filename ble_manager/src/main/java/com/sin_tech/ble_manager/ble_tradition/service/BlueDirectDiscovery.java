package com.sin_tech.ble_manager.ble_tradition.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import com.sin_tech.ble_manager.models.BleDevice;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WiFi Direct 设备发现和组网
 * 适用于Android环境
 */
public class BlueDirectDiscovery extends ScanCallback {

    private static final String TAG = "BlueDirectDiscovery";
    // 1. 定义你想要过滤的目标服务UUID（例如：串口服务 SPP）
    ParcelUuid SERVICE_UUID  = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB");
    ParcelUuid CHAR_READ_WRITE_UUID  = ParcelUuid.fromString("0000FFF1-0000-1000-8000-00805F9B34FB");

    private final WeakReference<Activity> context;
    private final BluetoothAdapter mBluetoothAdapter;
    private final BlueDiscoveryCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BlueDirectDiscovery(WeakReference<Activity> context, BlueDiscoveryCallback callback) {
        this.context = context;
        this.callback = callback;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        registerReceivers();
    }
    
    /**
     * 注册广播接收器
     */
    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if(context.get() != null) {
            context.get().registerReceiver(blueScanReceiver, filter);
        }
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
        super.onBatchScanResults(results);
        for(ScanResult res : results){
            if(callback != null){
                BleDevice bleDevice = new BleDevice(res.getDevice(),res.getRssi());
                callback.onDeviceFound(bleDevice);
            }
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        super.onScanFailed(errorCode);
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        super.onScanResult(callbackType, result);
        if(callback != null){
            BleDevice bleDevice = new BleDevice(result.getDevice(),result.getRssi());
            callback.onDeviceFound(bleDevice);
        }
    }

    /**
     * 开始发现设备
     */
    @SuppressLint("MissingPermission")
    public void startDiscovery() {
        //先停止搜索
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build());
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(this);
        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters,
                new ScanSettings.Builder().build(),this);
        if (callback != null) {
            callback.onScanStart();
        }
    }


    /**
     * 停止发现设备
     */
    @SuppressLint("MissingPermission")
    public void stopDiscovery() {
//        if(mBluetoothAdapter != null){
//            mBluetoothAdapter.cancelDiscovery();
//        }
        if(mBluetoothAdapter != null){
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(this);
        }
        if(callback != null) {
            callback.onScanEnd();
        }
    }
    
    /**
     * 广播接收器
     */
    @SuppressLint("MissingPermission")
    private final BroadcastReceiver blueScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                ParcelUuid devUUID = intent.getParcelableExtra(BluetoothDevice.EXTRA_UUID);
//                String name = device != null ? device.getName() : null;
                // 关键步骤：获取设备的UUID列表
                ParcelUuid[] uuids = null;
                if (device != null) {
                    String deviceName = device.getName();
                    // 2. 获取设备 MAC 地址
                    String deviceAddress = device.getAddress();
                    // 3. 获取设备类型
                    int deviceType = device.getType();
                    String typeStr = switch (deviceType) {
                        case BluetoothDevice.DEVICE_TYPE_CLASSIC -> "经典蓝牙";
                        case BluetoothDevice.DEVICE_TYPE_LE -> "低功耗蓝牙";
                        case BluetoothDevice.DEVICE_TYPE_DUAL -> "双模设备";
                        default -> "未知类型";
                    };
                    uuids = device.getUuids();
                    // 检查UUID列表是否包含目标UUID
                    if (deviceName != null && deviceName.equals("sintechphil")) {
                        // 找到目标设备！进行后续处理（如添加到列表）
//                                Log.d("BluetoothFilter", "找到目标设备: " + device.getName() + " - " + device.getAddress());
                        if(callback != null){
                            BleDevice bleDevice = new BleDevice(device,0);
                            callback.onDeviceFound(bleDevice);
                        }
                        // 蓝牙搜索是非常消耗系统资源开销的过程，一旦发现了目标感兴趣的设备，可以考虑关闭扫描。
                        mBluetoothAdapter.cancelDiscovery();
                    }
                }
            }
        }
    };
    
    /**
     * 清理资源
     */
    public void cleanup() {
        stopDiscovery();
        if(context != null && context.get() != null){
            context.get().unregisterReceiver(blueScanReceiver);
        }
    }


    /**
     * 发现回调接口
     */
    public interface BlueDiscoveryCallback {
        void onDeviceFound(BleDevice dev);
        void onScanStart();
        void onScanEnd();
    }

}