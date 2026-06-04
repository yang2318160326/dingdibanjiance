package com.example.datacollector.domain.model

data class DeviceConfig(
    val samplingIntervalSec: Long,
    val sensorAddr: Int,
    val sensorStartReg: Int,
    val sensorRegCount: Int,
    val sensorDataType: Int,
    val modbusBaudrate: Int,
    val modbusParity: Int
)
