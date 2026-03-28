package com.sin_tech.ble_manager.utils;


import androidx.annotation.NonNull;

import com.sin_tech.ble_manager.bluetooth.BleBluetooth;

import java.util.LinkedHashMap;

public class BleLruHashMap extends LinkedHashMap<String,BleBluetooth> {

    private final int MAX_SIZE;

    public BleLruHashMap(int saveSize) {
        super((int) Math.ceil(saveSize / 0.75) + 1, 0.75f, true);
        MAX_SIZE = saveSize;
    }

    @Override
    protected boolean removeEldestEntry(Entry<String,BleBluetooth> eldest) {
        if (size() > MAX_SIZE && eldest.getValue() != null) {
            (eldest.getValue()).destroy();
        }
        return size() > MAX_SIZE;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Entry<String,BleBluetooth> entry : entrySet()) {
            sb.append(String.format("%s:%s ", entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }

}
