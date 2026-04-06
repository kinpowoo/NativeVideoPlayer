package com.jhkj.videoplayer.third_file_framework.smb_client;

import java.util.List;

public interface OnDeviceFoundListener {
    void onStart(DeviceFinder deviceFinder);
    void onFinished(DeviceFinder deviceFinder, List<DeviceItem> deviceItems);
    void onFailed(DeviceFinder deviceFinder, int errorCode);
}