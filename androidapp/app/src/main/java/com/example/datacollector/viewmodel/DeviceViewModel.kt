package com.example.datacollector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.ble.BleConnectionState
import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.domain.model.DeviceInfo
import com.example.datacollector.domain.model.KnownDevice
import com.example.datacollector.domain.repository.DataRepository
import com.example.datacollector.protocol.ResponseParser
import com.example.datacollector.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val bleManager: BleConnectionManager,
    private val dataRepository: DataRepository
) : ViewModel() {

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _deviceStatus = MutableStateFlow<ResponseParser.StatusResponse?>(null)
    val deviceStatus: StateFlow<ResponseParser.StatusResponse?> = _deviceStatus.asStateFlow()

    private val _currentConfig = MutableStateFlow<DeviceConfig?>(null)
    val currentConfig: StateFlow<DeviceConfig?> = _currentConfig.asStateFlow()

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState

    fun loadDeviceInfo(macAddress: String) {
        viewModelScope.launch {
            try {
                val ping = bleManager.sendPing()
                if (ping != null) {
                    val info = bleManager.sendGetInfo()
                    if (info != null) {
                        _deviceInfo.value = DeviceInfo(
                            deviceId = info.deviceId, deviceName = "Device",
                            firmwareVersion = info.firmwareVersion, recordCount = info.recordCount,
                            freeSpace = info.freeSpace, batteryLevel = info.batteryLevel, uptime = info.uptime
                        )
                        dataRepository.upsertDevice(KnownDevice(
                            macAddress = macAddress, customName = "Device", deviceId = info.deviceId,
                            firstSeen = DateTimeUtils.nowUnixMillis(), lastConnected = DateTimeUtils.nowUnixMillis(),
                            recordCount = info.recordCount.toInt(), notes = ""
                        ))
                    }
                }
                _deviceStatus.value = bleManager.sendGetStatus()
                _currentConfig.value = bleManager.sendGetConfig()
            } catch (e: Exception) { /* handle error */ }
        }
    }

    fun syncTime() { viewModelScope.launch { bleManager.sendSetTime(DateTimeUtils.nowUnixSeconds()) } }
    fun readConfig() { viewModelScope.launch { _currentConfig.value = bleManager.sendGetConfig() } }
    fun saveConfig(config: DeviceConfig) { viewModelScope.launch { bleManager.sendSetConfig(config); _currentConfig.value = config } }
    fun disconnect() { bleManager.disconnect() }
}
