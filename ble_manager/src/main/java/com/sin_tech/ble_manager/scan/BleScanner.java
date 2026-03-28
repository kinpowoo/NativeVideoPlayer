package com.sin_tech.ble_manager.scan;


import android.annotation.SuppressLint;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;


import com.sin_tech.ble_manager.BleManager;
import com.sin_tech.ble_manager.models.BleScanState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleScanner {

    public static BleScanner getInstance() {
        return BleScannerHolder.sBleScanner;
    }

    private static class BleScannerHolder {
        private static final BleScanner sBleScanner = new BleScanner();
    }

    private BleScanState mBleScanState = BleScanState.STATE_IDLE;

    public void scan(UUID[] serviceUuids, String[] names, boolean fuzzy,
                     long timeOut, final BleScanCallback callback) {
        startLeScan(serviceUuids, names, fuzzy, timeOut, callback);
    }

    @SuppressLint("MissingPermission")
    private synchronized void startLeScan(UUID[] serviceUuids, String[] names,
                                          boolean fuzzy, long timeout, BleScanCallback imp) {

        if (mBleScanState != BleScanState.STATE_IDLE) {
            if (imp != null) {
                imp.onScanStarted(false);
            }
            return;
        }
        imp.prepare(names, fuzzy, timeout);

        List<ScanFilter> filters = new ArrayList<>();
        for(UUID s :serviceUuids){
            filters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(s.toString())).build());
        }
        BleManager.getInstance().getBluetoothScanner()
                .startScan(filters,new ScanSettings.Builder().build(),imp);
        mBleScanState = BleScanState.STATE_SCANNING;
        imp.notifyScanStarted(true);
    }

    @SuppressLint("MissingPermission")
    public synchronized void stopLeScan(BleScanCallback scanCallback) {
        if (BleManager.getInstance() == null)return;
        if (BleManager.getInstance().getBluetoothScanner() == null)return;
        try {
            BleManager.getInstance().getBluetoothScanner().stopScan(scanCallback);
            mBleScanState = BleScanState.STATE_IDLE;
            if(scanCallback != null) {
                scanCallback.notifyScanStopped();
            }
        }catch (IllegalStateException e){
            e.printStackTrace();
        }
    }

    public BleScanState getScanState() {
        return mBleScanState;
    }


}
