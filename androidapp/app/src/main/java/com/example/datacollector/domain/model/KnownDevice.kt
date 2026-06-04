package com.example.datacollector.domain.model

data class KnownDevice(
    val macAddress: String,
    val customName: String,
    val deviceId: Long,
    val firstSeen: Long,
    val lastConnected: Long,
    val recordCount: Int,
    val notes: String
)
