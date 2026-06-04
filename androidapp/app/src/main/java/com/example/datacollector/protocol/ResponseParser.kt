package com.example.datacollector.protocol

import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.domain.model.SensorRecord

object ResponseParser {

    data class PingResponse(
        val deviceId: Long,
        val firmwareVersion: String,
        val status: Int
    )

    data class InfoResponse(
        val deviceId: Long,
        val firmwareVersion: String,
        val recordCount: Long,
        val freeSpace: Long,
        val uptime: Long,
        val batteryLevel: Int
    )

    data class StatusResponse(
        val state: Int,
        val errorCode: Int,
        val nextReadIn: Long
    )

    fun parsePing(payload: ByteArray): PingResponse? {
        if (payload.size < 7) return null
        val deviceId = bytesToLong(payload, 0, 4)
        val fwMajor = payload[4].toInt() and 0xFF
        val fwMinor = payload[5].toInt() and 0xFF
        val status = payload[6].toInt() and 0xFF
        return PingResponse(deviceId, "V$fwMajor.$fwMinor", status)
    }

    fun parseInfo(payload: ByteArray): InfoResponse? {
        if (payload.size < 19) return null
        val deviceId = bytesToLong(payload, 0, 4)
        val fwMajor = payload[4].toInt() and 0xFF
        val fwMinor = payload[5].toInt() and 0xFF
        val recordCount = bytesToLong(payload, 6, 4)
        val freeSpace = bytesToLong(payload, 10, 4)
        val uptime = bytesToLong(payload, 14, 4)
        val battery = payload[18].toInt() and 0xFF
        return InfoResponse(deviceId, "V$fwMajor.$fwMinor", recordCount, freeSpace, uptime, battery)
    }

    fun parseConfig(payload: ByteArray): DeviceConfig? {
        if (payload.size < 15) return null
        val interval = bytesToLong(payload, 0, 4)
        val addr = payload[4].toInt() and 0xFF
        val startReg = ((payload[5].toInt() and 0xFF) shl 8) or (payload[6].toInt() and 0xFF)
        val regCount = ((payload[7].toInt() and 0xFF) shl 8) or (payload[8].toInt() and 0xFF)
        val dataType = payload[9].toInt() and 0xFF
        val baudrate = bytesToLong(payload, 10, 4).toInt()
        val parity = payload[14].toInt() and 0xFF
        return DeviceConfig(interval, addr, startReg, regCount, dataType, baudrate, parity)
    }

    fun parseStatus(payload: ByteArray): StatusResponse? {
        if (payload.size < 6) return null
        val state = payload[0].toInt() and 0xFF
        val errorCode = payload[1].toInt() and 0xFF
        val nextReadIn = bytesToLong(payload, 2, 4)
        return StatusResponse(state, errorCode, nextReadIn)
    }

    fun parseDataFrag(payload: ByteArray): Pair<Int, List<SensorRecord>>? {
        if (payload.size < 2) return null
        val chunkIndex = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val records = mutableListOf<SensorRecord>()
        var offset = 2
        while (offset + ProtocolConstants.RECORD_SIZE <= payload.size) {
            val record = parseRecord(payload, offset)
            if (record != null) records.add(record)
            offset += ProtocolConstants.RECORD_SIZE
        }
        return Pair(chunkIndex, records)
    }

    fun parseRecord(data: ByteArray, offset: Int): SensorRecord? {
        if (offset + ProtocolConstants.RECORD_SIZE > data.size) return null
        val timestamp = bytesToLong(data, offset, 4)
        val sensorAddr = data[offset + 4].toInt() and 0xFF
        val status = data[offset + 5].toInt() and 0xFF
        val regCount = ((data[offset + 6].toInt() and 0xFF) shl 8) or (data[offset + 7].toInt() and 0xFF)
        val regValues = mutableListOf<Int>()
        for (i in 0 until minOf(regCount, 8)) {
            val v = ((data[offset + 8 + i * 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 9 + i * 2].toInt() and 0xFF)
            regValues.add(v)
        }
        val seqNum = bytesToLong(data, offset + 24, 4)
        return SensorRecord(timestamp, sensorAddr, status, regValues, seqNum)
    }

    fun parseError(payload: ByteArray): Pair<Int, Byte>? {
        if (payload.size < 2) return null
        val errCode = payload[0].toInt() and 0xFF
        val origCmd = payload[1]
        return Pair(errCode, origCmd)
    }

    private fun bytesToLong(data: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        for (i in 0 until length) {
            result = (result shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return result
    }
}
