package com.example.datacollector.domain.model

data class DeviceInfo(
    val deviceId: Long,
    val deviceName: String,
    val firmwareVersion: String,
    val recordCount: Long,
    val freeSpace: Long,
    val batteryLevel: Int,
    val uptime: Long
)
