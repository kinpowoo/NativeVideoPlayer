package com.sin_tech.ble_manager.ble_tradition

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent

object BluetoothUtil {

    fun getBlueToothStatus(context: Context): Boolean{
        val bluetoothAdapter = (context.getSystemService(
            Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }
}