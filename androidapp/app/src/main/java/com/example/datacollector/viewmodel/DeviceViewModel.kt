package com.example.datacollector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.ble.BleConnectionState
import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.domain.model.DeviceInfo
import com.example.datacollector.domain.model.KnownDevice
import com.example.datacollector.domain.repository.DataRepository
import com.example.datacollector.protocol.CommandBuilder
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

    private val _modbusResponse = MutableStateFlow<String>("")
    val modbusResponse: StateFlow<String> = _modbusResponse.asStateFlow()

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

    // 时间同步
    fun syncTime() {
        viewModelScope.launch { bleManager.sendSetTime(DateTimeUtils.nowUnixSeconds()) }
    }

    // 读取配置
    fun readConfig() {
        viewModelScope.launch { _currentConfig.value = bleManager.sendGetConfig() }
    }

    // 保存配置
    fun saveConfig(config: DeviceConfig) {
        viewModelScope.launch {
            bleManager.sendSetConfig(config)
            _currentConfig.value = config
        }
    }

    // 设置分机号
    fun setDeviceId(id: Int) {
        viewModelScope.launch {
            bleManager.sendSetDeviceId(id.toLong(), "Device$id")
            // 重新加载设备信息
            val info = bleManager.sendGetInfo()
            if (info != null) {
                _deviceInfo.value = _deviceInfo.value?.copy(deviceId = info.deviceId)
            }
        }
    }

    // 设置采集间隔 (小时:分钟)
    fun setSamplingInterval(hours: Int, minutes: Int) {
        viewModelScope.launch {
            val totalSeconds = (hours * 3600L) + (minutes * 60L)
            val config = _currentConfig.value?.copy(samplingIntervalSec = totalSeconds)
                ?: DeviceConfig(
                    samplingIntervalSec = totalSeconds,
                    sensorAddr = 1,
                    sensorStartReg = 0,
                    sensorRegCount = 4,
                    sensorDataType = 0,
                    modbusBaudrate = 9600,
                    modbusParity = 0
                )
            bleManager.sendSetConfig(config)
            _currentConfig.value = config
        }
    }

    // 启动采集
    fun startCollecting() {
        viewModelScope.launch {
            // 发送启动采集命令 (通过SET_CONFIG设置采集状态)
            val config = _currentConfig.value?.copy(samplingIntervalSec = _currentConfig.value?.samplingIntervalSec ?: 60)
            if (config != null) {
                bleManager.sendSetConfig(config)
            }
        }
    }

    // 停止采集
    fun stopCollecting() {
        viewModelScope.launch {
            // 发送停止采集命令 (通过SET_CONFIG设置采集间隔为0)
            val config = _currentConfig.value?.copy(samplingIntervalSec = 0)
            if (config != null) {
                bleManager.sendSetConfig(config)
                _currentConfig.value = config
            }
        }
    }

    // 清除设备数据
    fun eraseDeviceData() {
        viewModelScope.launch {
            bleManager.sendEraseData()
        }
    }

    // 发送原始Modbus命令
    fun sendRawModbus(hexString: String) {
        viewModelScope.launch {
            try {
                val data = hexString.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()
                // 通过BLE发送原始Modbus数据
                // 这里需要通过协议层发送，暂时记录响应
                _modbusResponse.value = "发送: $hexString"
            } catch (e: Exception) {
                _modbusResponse.value = "错误: ${e.message}"
            }
        }
    }

    // 读取设备状态
    fun readDeviceStatus() {
        viewModelScope.launch {
            _deviceStatus.value = bleManager.sendGetStatus()
        }
    }

    // 重启设备
    fun rebootDevice() {
        viewModelScope.launch {
            bleManager.sendReboot()
        }
    }

    // 断开连接
    fun disconnect() {
        bleManager.disconnect()
    }
}
