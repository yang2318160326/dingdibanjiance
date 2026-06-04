package com.example.datacollector.domain.repository

import com.example.datacollector.ble.BleConnectionState
import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.domain.model.TransferProgress
import com.example.datacollector.protocol.ResponseParser
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    val connectionState: Flow<BleConnectionState>
    val transferProgress: Flow<TransferProgress>

    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(macAddress: String)
    suspend fun disconnect()
    suspend fun sendPing(): ResponseParser.PingResponse?
    suspend fun sendGetInfo(): ResponseParser.InfoResponse?
    suspend fun sendSetTime(unixTimestamp: Long): Boolean
    suspend fun sendGetConfig(): DeviceConfig?
    suspend fun sendSetConfig(config: DeviceConfig): Boolean
    suspend fun sendGetData(startIndex: Int, count: Int)
    suspend fun sendEraseData(): Boolean
    suspend fun sendGetStatus(): ResponseParser.StatusResponse?
    suspend fun sendAck(chunkIndex: Int, status: Byte)
}
