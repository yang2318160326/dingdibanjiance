package com.example.datacollector.domain.repository

import android.net.Uri
import com.example.datacollector.domain.model.KnownDevice
import com.example.datacollector.domain.model.SensorRecord
import kotlinx.coroutines.flow.Flow

interface DataRepository {
    fun getAllDevices(): Flow<List<KnownDevice>>
    fun getRecordsByDevice(macAddress: String): Flow<List<SensorRecord>>
    suspend fun upsertDevice(device: KnownDevice)
    suspend fun deleteDevice(device: KnownDevice)
    suspend fun updateDeviceLastSeen(macAddress: String, time: Long, count: Int)
    suspend fun insertRecords(macAddress: String, records: List<SensorRecord>)
    suspend fun getRecordCount(macAddress: String): Int
    suspend fun clearRecords(macAddress: String)
    suspend fun exportCsv(macAddress: String?, timeFrom: Long?, timeTo: Long?): Uri
    suspend fun exportJson(macAddress: String?, timeFrom: Long?, timeTo: Long?): Uri
}
