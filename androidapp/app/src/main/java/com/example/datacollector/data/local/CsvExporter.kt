package com.example.datacollector.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.datacollector.data.local.dao.RecordDao
import com.example.datacollector.data.local.entity.RecordEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordDao: RecordDao
) {
    private val gson = Gson()

    suspend fun exportCsv(
        deviceMac: String?,
        deviceName: String?,
        deviceId: Long?,
        timeFrom: Long?,
        timeTo: Long?
    ): Uri {
        val records = getRecords(deviceMac, timeFrom, timeTo)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val macClean = deviceMac?.replace(":", "")?.take(8) ?: "all"

        val file = File(context.cacheDir, "data_${macClean}_$timestamp.csv")
        FileWriter(file).use { writer ->
            if (deviceMac != null) {
                writer.write("# 设备MAC: $deviceMac\n")
                if (deviceName != null) writer.write("# 设备名称: $deviceName\n")
                if (deviceId != null) writer.write("# 设备ID: $deviceId\n")
            }
            writer.write("# 导出时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            writer.write("# 记录总数: ${records.size}\n")
            writer.write("device_id,timestamp,datetime,sensor_address,status,status_text,reg_0,reg_1,reg_2,reg_3,reg_4,reg_5,reg_6,reg_7,sequence\n")

            for (record in records) {
                val dt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp * 1000))
                val statusText = when (record.status) {
                    0 -> "OK"; 1 -> "TIMEOUT"; 2 -> "CRC_ERR"; 3 -> "MODBUS_ERR"; else -> "UNKNOWN"
                }
                val regs = parseRegisterValues(record.registerValues)
                val regStr = (0 until 8).joinToString(",") { i -> regs.getOrElse(i) { 0 }.toString() }
                val macClean2 = record.deviceId.replace(":", "").take(8)
                writer.write("$macClean2,${record.timestamp},$dt,${record.sensorAddress},$record.status,$statusText,$regStr,${record.sequenceNum}\n")
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun exportJson(
        deviceMac: String?,
        deviceName: String?,
        deviceId: Long?,
        timeFrom: Long?,
        timeTo: Long?
    ): Uri {
        val records = getRecords(deviceMac, timeFrom, timeTo)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val macClean = deviceMac?.replace(":", "")?.take(8) ?: "all"

        val root = JsonObject()
        if (deviceMac != null) {
            val deviceInfo = JsonObject()
            deviceInfo.addProperty("mac_address", deviceMac)
            deviceInfo.addProperty("device_id", deviceId ?: 0)
            deviceInfo.addProperty("device_name", deviceName ?: "")
            root.add("device_info", deviceInfo)
        }

        val exportInfo = JsonObject()
        exportInfo.addProperty("export_time", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
        exportInfo.addProperty("app_version", "1.0.0")
        exportInfo.addProperty("record_count", records.size)
        root.add("export_info", exportInfo)

        val recordsArray = JsonArray()
        for (record in records) {
            val obj = JsonObject()
            obj.addProperty("timestamp", record.timestamp)
            obj.addProperty("datetime", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp * 1000)))
            obj.addProperty("sensor_address", record.sensorAddress)
            obj.addProperty("status", record.status)
            obj.addProperty("status_text", when (record.status) {
                0 -> "OK"; 1 -> "TIMEOUT"; 2 -> "CRC_ERR"; 3 -> "MODBUS_ERR"; else -> "UNKNOWN"
            })
            val regs = parseRegisterValues(record.registerValues)
            val regsArray = JsonArray()
            for (r in regs) regsArray.add(r)
            for (i in regs.size until 8) regsArray.add(0)
            obj.add("registers", regsArray)
            obj.addProperty("sequence", record.sequenceNum)
            recordsArray.add(obj)
        }
        root.add("records", recordsArray)

        val file = File(context.cacheDir, "data_${macClean}_$timestamp.json")
        FileWriter(file).use { writer ->
            gson.toJson(root, writer)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private suspend fun getRecords(deviceMac: String?, timeFrom: Long?, timeTo: Long?): List<RecordEntity> {
        return if (deviceMac != null && timeFrom != null && timeTo != null) {
            recordDao.getByDeviceAndTime(deviceMac, timeFrom, timeTo).let {
                // Collect from Flow
                var result: List<RecordEntity> = emptyList()
                it.collect { result = it }
                result
            }
        } else if (deviceMac != null) {
            var result: List<RecordEntity> = emptyList()
            recordDao.getByDevice(deviceMac).collect { result = it }
            result
        } else {
            var result: List<RecordEntity> = emptyList()
            recordDao.getAll().collect { result = it }
            result
        }
    }

    private fun parseRegisterValues(json: String): List<Int> {
        return try {
            val arr = gson.fromJson(json, Array<Int>::class.java)
            arr?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
