package com.sin_tech.ble_manager.ble_version.ble_tradition

import java.util.UUID

object BleConstant {

    val UUID_SERVER: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val UUID_CHAR_READ: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val UUID_CHAR_WRITE: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34ff")
}