package com.example.datacollector.data.remote

import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.protocol.ResponseParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDataSource @Inject constructor(
    private val bleManager: BleConnectionManager
) {
    suspend fun ping(): ResponseParser.PingResponse? = bleManager.sendPing()
    suspend fun getInfo(): ResponseParser.InfoResponse? = bleManager.sendGetInfo()
    suspend fun setTime(unixTimestamp: Long): Boolean = bleManager.sendSetTime(unixTimestamp)
    suspend fun getConfig(): DeviceConfig? = bleManager.sendGetConfig()
    suspend fun setConfig(config: DeviceConfig): Boolean = bleManager.sendSetConfig(config)
    suspend fun getData(startIndex: Int, count: Int) = bleManager.sendGetData(startIndex, count)
    suspend fun eraseData(): Boolean = bleManager.sendEraseData()
    suspend fun getStatus(): ResponseParser.StatusResponse? = bleManager.sendGetStatus()
    suspend fun sendAck(chunkIndex: Int, status: Byte) = bleManager.sendAck(chunkIndex, status)
}
