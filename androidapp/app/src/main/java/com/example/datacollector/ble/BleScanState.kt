package com.example.datacollector.ble

data class BleScanResult(
    val name: String,
    val macAddress: String,
    val rssi: Int
)

data class ConnectedDevice(
    val name: String,
    val macAddress: String,
    val deviceId: Long,
    val firmwareVersion: String
)

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    object Connecting : BleConnectionState()
    data class Connected(val deviceName: String, val macAddress: String) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}
