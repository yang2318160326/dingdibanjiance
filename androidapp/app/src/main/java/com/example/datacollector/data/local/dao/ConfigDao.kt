package com.example.datacollector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.datacollector.data.local.entity.ConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM device_configs WHERE deviceId = :macAddress")
    fun getByDevice(macAddress: String): Flow<ConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ConfigEntity)

    @Query("DELETE FROM device_configs WHERE deviceId = :macAddress")
    suspend fun delete(macAddress: String)
}
