package com.example.datacollector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_configs")
data class ConfigEntity(
    @PrimaryKey
    val deviceId: String,
    val configJson: String,
    val savedAt: Long
)
