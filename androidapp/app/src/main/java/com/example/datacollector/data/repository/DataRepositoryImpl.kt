package com.example.datacollector.data.repository

import android.net.Uri
import com.example.datacollector.data.local.CsvExporter
import com.example.datacollector.data.local.dao.DeviceDao
import com.example.datacollector.data.local.dao.RecordDao
import com.example.datacollector.data.local.entity.DeviceEntity
import com.example.datacollector.data.local.entity.RecordEntity
import com.example.datacollector.domain.model.KnownDevice
import com.example.datacollector.domain.model.SensorRecord
import com.example.datacollector.domain.repository.DataRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    private val recordDao: RecordDao,
    private val csvExporter: CsvExporter,
    private val gson: Gson
) : DataRepository {

    override fun getAllDevices(): Flow<List<KnownDevice>> {
        return deviceDao.getAllDevices().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecordsByDevice(macAddress: String): Flow<List<SensorRecord>> {
        return recordDao.getByDevice(macAddress).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertDevice(device: KnownDevice) {
        deviceDao.upsert(device.toEntity())
    }

    override suspend fun deleteDevice(device: KnownDevice) {
        deviceDao.delete(device.toEntity())
    }

    override suspend fun updateDeviceLastSeen(macAddress: String, time: Long, count: Int) {
        deviceDao.updateLastSeen(macAddress, time, count)
    }

    override suspend fun insertRecords(macAddress: String, records: List<SensorRecord>) {
        val now = System.currentTimeMillis()
        val entities = records.map { r ->
            RecordEntity(
                deviceId = macAddress,
                timestamp = r.timestamp,
                sensorAddress = r.sensorAddress,
                status = r.status,
                registerValues = gson.toJson(r.registerValues),
                sequenceNum = r.sequenceNum,
                downloadedAt = now
            )
        }
        recordDao.insertAll(entities)
    }

    override suspend fun getRecordCount(macAddress: String): Int {
        return recordDao.getCount(macAddress)
    }

    override suspend fun clearRecords(macAddress: String) {
        recordDao.clearByDevice(macAddress)
    }

    override suspend fun exportCsv(macAddress: String?, timeFrom: Long?, timeTo: Long?): Uri {
        return csvExporter.exportCsv(macAddress, null, null, timeFrom, timeTo)
    }

    override suspend fun exportJson(macAddress: String?, timeFrom: Long?, timeTo: Long?): Uri {
        return csvExporter.exportJson(macAddress, null, null, timeFrom, timeTo)
    }

    private fun DeviceEntity.toDomain() = KnownDevice(
        macAddress = macAddress,
        customName = customName,
        deviceId = deviceId,
        firstSeen = firstSeen,
        lastConnected = lastConnected,
        recordCount = recordCount,
        notes = notes
    )

    private fun KnownDevice.toEntity() = DeviceEntity(
        macAddress = macAddress,
        customName = customName,
        deviceId = deviceId,
        firstSeen = firstSeen,
        lastConnected = lastConnected,
        recordCount = recordCount,
        notes = notes
    )

    private fun RecordEntity.toDomain() = SensorRecord(
        timestamp = timestamp,
        sensorAddress = sensorAddress,
        status = status,
        registerValues = try {
            gson.fromJson(registerValues, Array<Int>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() },
        sequenceNum = sequenceNum
    )
}
