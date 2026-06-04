package com.example.datacollector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_devices")
data class DeviceEntity(
    @PrimaryKey
    val macAddress: String,
    val customName: String,
    val deviceId: Long,
    val firstSeen: Long,
    val lastConnected: Long,
    val recordCount: Int,
    val notes: String
)
