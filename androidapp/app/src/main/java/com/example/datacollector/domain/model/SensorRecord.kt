package com.example.datacollector.domain.model

data class SensorRecord(
    val timestamp: Long,
    val sensorAddress: Int,
    val status: Int,
    val registerValues: List<Int>,
    val sequenceNum: Long
)
