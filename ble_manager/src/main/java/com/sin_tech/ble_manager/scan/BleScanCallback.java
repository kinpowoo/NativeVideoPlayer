package com.sin_tech.ble_manager.scan;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Handler;
import android.os.Looper;


import com.sin_tech.ble_manager.models.BleDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class BleScanCallback extends ScanCallback {

    private String[] mDeviceNames;
    private boolean mFuzzy;
    private long mScanTimeout;
    private final List<BleDevice> mBleDeviceList = new ArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mHandling;

    private void handleResult(final BleDevice bleDevice) {
        boolean isExisted = false;
        for(BleDevice bd :mBleDeviceList){
            if(bd.getDevice().getAddress().equals(bleDevice.getDevice().getAddress())) {
                isExisted = true;
                break;
            }
        }
        if(!isExisted) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    onLeScan(bleDevice);
                }
            });
            checkDevice(bleDevice);
        }
    }

    public void prepare(String[] names, boolean fuzzy,long timeOut) {
        mDeviceNames = names;
        mFuzzy = fuzzy;
        mScanTimeout = timeOut;
        mHandling = true;
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        super.onScanResult(callbackType, result);
        scannedDevice(result);
    }

    public void scannedDevice(ScanResult result){
        BluetoothDevice device = result.getDevice();
        if (device == null) return;
        if (!mHandling) return;
        BleDevice ble = new BleDevice(device, result.getRssi());
        handleResult(ble);
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
        super.onBatchScanResults(results);
        for(ScanResult sr:results){
            scannedDevice(sr);
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        super.onScanFailed(errorCode);
        notifyScanStopped();
    }


    private void checkDevice(BleDevice bleDevice) {
        if (mDeviceNames != null && mDeviceNames.length > 0) {
            AtomicBoolean equal = new AtomicBoolean(false);
            for (String name : mDeviceNames) {
                String remoteName = bleDevice.getName();
                if (remoteName == null){
                    remoteName = "";
                }
                if (mFuzzy ? remoteName.contains(name) : remoteName.equals(name)) {
                    equal.set(true);
                }
            }
            if (!equal.get()) {
                return;
            }
        }
        correctDeviceAndNextStep(bleDevice);
    }


    private void correctDeviceAndNextStep(final BleDevice bleDevice) {
        AtomicBoolean hasFound = new AtomicBoolean(false);
        for (BleDevice result : mBleDeviceList) {
            if (result.getDevice().getAddress().equals(bleDevice.getDevice().getAddress())) {
                hasFound.set(true);
                break;
            }
        }
        if (!hasFound.get()) {
            mBleDeviceList.add(bleDevice);
        }
    }

    public final void notifyScanStarted(final boolean success) {
        mBleDeviceList.clear();
        removeHandlerMsg();

        if (success && mScanTimeout > 0) {
            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    BleScanner.getInstance().stopLeScan(BleScanCallback.this);
                }
            }, mScanTimeout);
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                onScanStarted(success);
            }
        });
    }

    public final void notifyScanStopped() {
        mHandling = false;
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                onScanFinished(mBleDeviceList);
            }
        });
    }

    public final void removeHandlerMsg() {
        mMainHandler.removeCallbacksAndMessages(null);
    }

    public abstract void onScanStarted(boolean success);

    public abstract void onLeScan(BleDevice bleDevice);

    public abstract void onScanFinished(List<BleDevice> bleDeviceList);
}
