package com.sin_tech.ble_manager.utils;

import static android.content.Context.BLUETOOTH_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public class BluetoothStatusManager {
    private static final String TAG = "BluetoothStatusManager";
    private final Context context;

    public BluetoothStatusManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public boolean isBluetoothSupported() {
        return BluetoothAdapter.getDefaultAdapter() != null;
    }
    
    public boolean isBluetoothEnabled() {
        BluetoothManager mBluetoothManager = (BluetoothManager)context.getSystemService(BLUETOOTH_SERVICE);
        if(mBluetoothManager.getAdapter() != null) {
            if (!mBluetoothManager.getAdapter().isEnabled()) {
                //如果蓝牙开关没有打开，请求开启蓝牙
                return false;
            } else {
                return true;
            }
        }else{
            return false;
        }
//
//        if (!checkPermissions()) {
//            return false;
//        }
//
//        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
//        return adapter != null && adapter.isEnabled();
    }
    
    @SuppressLint("MissingPermission")
    public void requestEnableBluetooth(Activity activity, int requestCode) {
        if (!isBluetoothSupported()) {
            Toast.makeText(activity, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableBtIntent, requestCode);
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}