package com.example.datacollector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.datacollector.data.local.entity.RecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM sensor_records WHERE deviceId = :macAddress ORDER BY timestamp DESC")
    fun getByDevice(macAddress: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM sensor_records WHERE deviceId = :macAddress AND timestamp BETWEEN :timeFrom AND :timeTo ORDER BY timestamp DESC")
    fun getByDeviceAndTime(macAddress: String, timeFrom: Long, timeTo: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM sensor_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<RecordEntity>>

    @Query("SELECT COUNT(*) FROM sensor_records WHERE deviceId = :macAddress")
    suspend fun getCount(macAddress: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RecordEntity>)

    @Query("DELETE FROM sensor_records WHERE deviceId = :macAddress")
    suspend fun clearByDevice(macAddress: String)

    @Query("DELETE FROM sensor_records")
    suspend fun clearAll()
}
