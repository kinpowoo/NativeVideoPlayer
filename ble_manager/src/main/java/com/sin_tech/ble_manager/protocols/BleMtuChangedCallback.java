package com.sin_tech.ble_manager.protocols;


import com.sin_tech.ble_manager.protocols.exception.BleException;

public abstract class BleMtuChangedCallback extends BleBaseCallback {

    public abstract void onSetMTUFailure(BleException exception);

    public abstract void onMtuChanged(int mtu);

}
