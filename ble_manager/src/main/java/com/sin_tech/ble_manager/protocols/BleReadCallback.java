package com.sin_tech.ble_manager.protocols;


import com.sin_tech.ble_manager.protocols.exception.BleException;

public abstract class BleReadCallback extends BleBaseCallback {

    public abstract void onReadSuccess(byte[] data);

    public abstract void onReadFailure(BleException exception);

}
