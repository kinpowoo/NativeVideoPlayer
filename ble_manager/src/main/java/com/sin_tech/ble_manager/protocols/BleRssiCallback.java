package com.sin_tech.ble_manager.protocols;


import com.sin_tech.ble_manager.protocols.exception.BleException;

public abstract class BleRssiCallback extends BleBaseCallback{

    public abstract void onRssiFailure(BleException exception);

    public abstract void onRssiSuccess(int rssi);

}