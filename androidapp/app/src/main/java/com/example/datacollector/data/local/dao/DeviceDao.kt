package com.example.datacollector.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.datacollector.data.local.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM known_devices ORDER BY lastConnected DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query("UPDATE known_devices SET lastConnected = :time, recordCount = :count WHERE macAddress = :mac")
    suspend fun updateLastSeen(mac: String, time: Long, count: Int)
}
