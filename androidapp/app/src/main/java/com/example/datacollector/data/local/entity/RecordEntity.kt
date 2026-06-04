package com.example.datacollector.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sensor_records",
    indices = [
        Index(value = ["deviceId", "timestamp"]),
        Index(value = ["deviceId"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val timestamp: Long,
    val sensorAddress: Int,
    val status: Int,
    val registerValues: String,
    val sequenceNum: Long,
    val downloadedAt: Long
)
