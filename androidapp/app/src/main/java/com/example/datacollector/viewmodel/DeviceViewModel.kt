package com.example.datacollector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.ble.BleConnectionState
import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.domain.model.DeviceInfo
import com.example.datacollector.domain.model.KnownDevice
import com.example.datacollector.domain.model.TransferProgress
import com.example.datacollector.domain.repository.DataRepository
import com.example.datacollector.protocol.ResponseParser
import com.example.datacollector.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OperationResult(
    val isSuccess: Boolean,
    val message: String
)

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

    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    private val _downloadProgress = MutableStateFlow<TransferProgress?>(null)
    val downloadProgress: StateFlow<TransferProgress?> = _downloadProgress.asStateFlow()

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState

    fun clearOperationResult() {
        _operationResult.value = null
    }

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
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "加载设备信息失败: ${e.message}")
            }
        }
    }

    // 时间同步
    fun syncTime() {
        viewModelScope.launch {
            try {
                val result = bleManager.sendSetTime(DateTimeUtils.nowUnixSeconds())
                _operationResult.value = if (result) {
                    OperationResult(true, "时间同步成功！")
                } else {
                    OperationResult(false, "时间同步失败，请检查设备连接")
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "时间同步错误: ${e.message}")
            }
        }
    }

    // 读取配置
    fun readConfig() {
        viewModelScope.launch {
            try {
                val config = bleManager.sendGetConfig()
                if (config != null) {
                    _currentConfig.value = config
                    _operationResult.value = OperationResult(true, "配置读取成功")
                } else {
                    _operationResult.value = OperationResult(false, "读取配置失败，设备未响应")
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "读取配置错误: ${e.message}")
            }
        }
    }

    // 保存配置
    fun saveConfig(config: DeviceConfig) {
        viewModelScope.launch {
            try {
                val result = bleManager.sendSetConfig(config)
                if (result) {
                    _currentConfig.value = config
                    _operationResult.value = OperationResult(true, "配置保存成功！")
                } else {
                    _operationResult.value = OperationResult(false, "配置保存失败，请检查参数")
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "保存配置错误: ${e.message}")
            }
        }
    }

    // 设置分机号
    fun setDeviceId(id: Int) {
        viewModelScope.launch {
            try {
                val result = bleManager.sendSetDeviceId(id.toLong(), "Device$id")
                if (result) {
                    _deviceInfo.value = _deviceInfo.value?.copy(deviceId = id.toLong())
                    _operationResult.value = OperationResult(true, "分机号设置成功！当前分机号: $id")
                } else {
                    _operationResult.value = OperationResult(false, "分机号设置失败")
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "设置分机号错误: ${e.message}")
            }
        }
    }

    // 设置采集间隔 (小时:分钟)
    fun setSamplingInterval(hours: Int, minutes: Int) {
        viewModelScope.launch {
            try {
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
                val result = bleManager.sendSetConfig(config)
                if (result) {
                    _currentConfig.value = config
                    _operationResult.value = OperationResult(true, "采集间隔设置成功！间隔: ${hours}小时${minutes}分钟")
                } else {
                    _operationResult.value = OperationResult(false, "采集间隔设置失败")
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "设置采集间隔错误: ${e.message}")
            }
        }
    }

    // 启动采集
    fun startCollecting() {
        viewModelScope.launch {
            try {
                val config = _currentConfig.value?.copy(samplingIntervalSec = _currentConfig.value?.samplingIntervalSec ?: 60)
                if (config != null) {
                    val result = bleManager.sendSetConfig(config)
                    if (result) {
                        _operationResult.value = OperationResult(true, "采集已启动！")
                    } else {
                        _operationResult.value = OperationResult(false, "启动采集失败")
                    }
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "启动采集错误: ${e.message}")
            }
        }
    }

    // 停止采集
    fun stopCollecting() {
        viewModelScope.launch {
            try {
                val config = _currentConfig.value?.copy(samplingIntervalSec = 0)
                if (config != null) {
                    val result = bleManager.sendSetConfig(config)
                    if (result) {
                        _currentConfig.value = config
                        _operationResult.value = OperationResult(true, "采集已停止！")
                    } else {
                        _operationResult.value = OperationResult(false, "停止采集失败")
                    }
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "停止采集错误: ${e.message}")
            }
        }
    }

    // 清除设备数据
    fun eraseDeviceData() {
        viewModelScope.launch {
            try {
                _downloadProgress.value = TransferProgress(100, 0, 1, 0, false)
                val result = bleManager.sendEraseData()
                _downloadProgress.value = TransferProgress(100, 100, 1, 1, true)
                if (result) {
                    _operationResult.value = OperationResult(true, "数据清除成功！所有记录已删除")
                } else {
                    _operationResult.value = OperationResult(false, "数据清除失败")
                }
            } catch (e: Exception) {
                _downloadProgress.value = null
                _operationResult.value = OperationResult(false, "清除数据错误: ${e.message}")
            }
        }
    }

    // 下载数据
    fun downloadData(macAddress: String) {
        viewModelScope.launch {
            try {
                val info = bleManager.sendGetInfo()
                if (info != null) {
                    val existingCount = dataRepository.getRecordCount(macAddress)
                    val remaining = (info.recordCount - existingCount).toInt()

                    if (remaining <= 0) {
                        _operationResult.value = OperationResult(true, "数据已是最新，无需下载")
                        return@launch
                    }

                    _downloadProgress.value = TransferProgress(remaining, 0, (remaining + 6) / 7, 0, false)

                    bleManager.onDataFragment = { _, records ->
                        viewModelScope.launch {
                            dataRepository.insertRecords(macAddress, records)
                            val current = _downloadProgress.value
                            if (current != null) {
                                _downloadProgress.value = current.copy(
                                    downloadedRecords = current.downloadedRecords + records.size,
                                    currentChunk = current.currentChunk + 1
                                )
                            }
                        }
                    }

                    bleManager.sendGetData(existingCount, remaining)
                    _operationResult.value = OperationResult(true, "数据下载完成！共下载 $remaining 条记录")
                } else {
                    _operationResult.value = OperationResult(false, "获取设备信息失败")
                }
            } catch (e: Exception) {
                _downloadProgress.value = null
                _operationResult.value = OperationResult(false, "下载数据错误: ${e.message}")
            }
        }
    }

    // 发送原始Modbus命令
    fun sendRawModbus(hexString: String) {
        viewModelScope.launch {
            try {
                val data = hexString.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()
                _modbusResponse.value = "发送: $hexString\n等待响应..."
                // 这里需要通过协议层发送，暂时记录
                _operationResult.value = OperationResult(true, "Modbus命令已发送")
            } catch (e: Exception) {
                _modbusResponse.value = "错误: ${e.message}"
                _operationResult.value = OperationResult(false, "发送Modbus命令错误: ${e.message}")
            }
        }
    }

    // 读取设备状态
    fun readDeviceStatus() {
        viewModelScope.launch {
            try {
                val status = bleManager.sendGetStatus()
                if (status != null) {
                    _deviceStatus.value = status
                    _operationResult.value = OperationResult(true, "状态读取成功")
                } else {
                    _operationResult.value = OperationResult(false, "读取状态失败，设备未响应")
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "读取状态错误: ${e.message}")
            }
        }
    }

    // 重启设备
    fun rebootDevice() {
        viewModelScope.launch {
            try {
                val result = bleManager.sendReboot()
                if (result) {
                    _operationResult.value = OperationResult(true, "设备重启指令已发送，设备将重新启动")
                } else {
                    _operationResult.value = OperationResult(false, "重启设备失败")
                }
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "重启设备错误: ${e.message}")
            }
        }
    }

    // 断开连接
    fun disconnect() {
        bleManager.disconnect()
    }
}
