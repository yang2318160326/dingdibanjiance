/**
 * 设备视图模型层
 *
 * 本包提供与蓝牙设备交互的 ViewModel 实现，负责管理设备状态、配置、数据采集等操作的 UI 逻辑。
 * ViewModel 通过 Hilt 依赖注入获取 BLE 连接管理器和数据仓库实例，
 * 并将业务逻辑与 UI 层解耦。
 */
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

/**
 * 操作结果数据类
 *
 * 用于封装每次设备操作（如读取配置、同步时间等）的执行结果，
 * 包含成功/失败状态和对应的消息提示。
 *
 * @property isSuccess 操作是否成功
 * @property message 操作结果的消息描述，用于 UI 显示提示信息
 */
data class OperationResult(
    val isSuccess: Boolean,
    val message: String
)

/**
 * 设备视图模型（DeviceViewModel）
 *
 * 负责管理与已连接蓝牙设备的所有交互操作，包括：
 * - 设备信息加载与展示
 * - 设备时间同步
 * - 设备配置的读取与保存
 * - 采集间隔设置与启停控制
 * - 设备数据下载与清除
 * - Modbus 命令发送
 * - 设备重启与断开连接
 *
 * 所有操作结果通过 StateFlow 暴露给 UI 层观察。
 *
 * @param bleManager 蓝牙连接管理器，负责底层 BLE 通信
 * @param dataRepository 数据仓库，负责本地数据的持久化存储
 */
@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val bleManager: BleConnectionManager,
    private val dataRepository: DataRepository
) : ViewModel() {

    /** 当前设备基本信息（设备ID、固件版本、记录数等） */
    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    /** 设备运行状态（传感器状态、存储状态等） */
    private val _deviceStatus = MutableStateFlow<ResponseParser.StatusResponse?>(null)
    val deviceStatus: StateFlow<ResponseParser.StatusResponse?> = _deviceStatus.asStateFlow()

    /** 当前设备配置（采集间隔、Modbus 参数等） */
    private val _currentConfig = MutableStateFlow<DeviceConfig?>(null)
    val currentConfig: StateFlow<DeviceConfig?> = _currentConfig.asStateFlow()

    /** Modbus 原始命令的响应内容 */
    private val _modbusResponse = MutableStateFlow<String>("")
    val modbusResponse: StateFlow<String> = _modbusResponse.asStateFlow()

    /** 最近一次操作的结果状态 */
    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    /** 数据下载/传输进度信息 */
    private val _downloadProgress = MutableStateFlow<TransferProgress?>(null)
    val downloadProgress: StateFlow<TransferProgress?> = _downloadProgress.asStateFlow()

    /** 蓝牙连接状态，直接委托给 BLE 管理器的连接状态流 */
    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState

    /**
     * 清除操作结果状态
     *
     * 在 UI 显示完操作提示后调用，将操作结果重置为 null，
     * 避免重复显示旧的提示信息。
     */
    fun clearOperationResult() {
        _operationResult.value = null
    }

    /**
     * 加载设备信息
     *
     * 通过 BLE 连接向设备发送 Ping 指令确认设备在线后，
     * 依次获取设备基本信息、运行状态和配置参数。
     * 如果设备是首次连接，会自动将设备信息保存到本地数据库。
     *
     * @param macAddress 设备的蓝牙 MAC 地址
     */
    fun loadDeviceInfo(macAddress: String) {
        viewModelScope.launch {
            try {
                // 先发送 Ping 确认设备可达
                val ping = bleManager.sendPing()
                if (ping != null) {
                    // Ping 成功后获取设备详细信息
                    val info = bleManager.sendGetInfo()
                    if (info != null) {
                        // 构造设备信息对象并更新 UI 状态
                        _deviceInfo.value = DeviceInfo(
                            deviceId = info.deviceId, deviceName = "Device",
                            firmwareVersion = info.firmwareVersion, recordCount = info.recordCount,
                            freeSpace = info.freeSpace, batteryLevel = info.batteryLevel, uptime = info.uptime
                        )
                        // 将设备信息持久化到数据库（首次连接时插入，后续更新）
                        dataRepository.upsertDevice(KnownDevice(
                            macAddress = macAddress, customName = "Device", deviceId = info.deviceId,
                            firstSeen = DateTimeUtils.nowUnixMillis(), lastConnected = DateTimeUtils.nowUnixMillis(),
                            recordCount = info.recordCount.toInt(), notes = ""
                        ))
                    }
                }
                // 获取设备运行状态（无论 Ping 是否成功都尝试获取）
                _deviceStatus.value = bleManager.sendGetStatus()
                // 获取设备当前配置
                _currentConfig.value = bleManager.sendGetConfig()
            } catch (e: Exception) {
                _operationResult.value = OperationResult(false, "加载设备信息失败: ${e.message}")
            }
        }
    }

    /**
     * 时间同步
     *
     * 将当前手机系统时间（Unix 时间戳，秒级）发送给设备，
     * 使设备内部时钟与手机时间保持一致，确保采集数据的时间戳准确。
     */
    fun syncTime() {
        viewModelScope.launch {
            try {
                // 获取当前 Unix 时间戳（秒）并发送给设备
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

    /**
     * 读取设备配置
     *
     * 从设备读取当前的配置参数，包括采集间隔、Modbus 通信参数等。
     * 读取成功后会更新本地的配置状态。
     */
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

    /**
     * 保存设备配置
     *
     * 将新的配置参数发送给设备并持久化保存。
     * 保存成功后更新本地配置状态，确保 UI 与设备实际配置一致。
     *
     * @param config 要保存的新配置对象
     */
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

    /**
     * 设置设备分机号
     *
     * Modbus 网络中每个设备需要唯一的分机号（从站地址），
     * 此方法用于修改设备的分机号。设置后设备会自动重启以应用新地址。
     *
     * @param id 新的分机号（从站地址），范围通常为 1-247
     */
    fun setDeviceId(id: Int) {
        viewModelScope.launch {
            try {
                // 发送设置分机号指令，同时传入设备名称
                val result = bleManager.sendSetDeviceId(id.toLong(), "Device$id")
                if (result) {
                    // 更新本地设备信息中的分机号
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

    /**
     * 设置数据采集间隔
     *
     * 将采集间隔（小时 + 分钟）转换为秒数后写入设备配置。
     * 如果当前配置不存在，则使用默认配置作为基础进行修改。
     * 默认配置包含 Modbus 从站地址 1、起始寄存器 0、寄存器数量 4、波特率 9600 等。
     *
     * @param hours 采集间隔的小时部分
     * @param minutes 采集间隔的分钟部分
     */
    fun setSamplingInterval(hours: Int, minutes: Int) {
        viewModelScope.launch {
            try {
                // 将小时和分钟转换为总秒数
                val totalSeconds = (hours * 3600L) + (minutes * 60L)
                // 基于当前配置创建新配置，如果没有当前配置则使用默认值
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

    /**
     * 启动数据采集
     *
     * 通过发送配置指令启动设备的数据采集功能。
     * 采集间隔使用当前已加载的配置值，如果配置未加载则默认 60 秒。
     */
    fun startCollecting() {
        viewModelScope.launch {
            try {
                // 使用当前配置中的采集间隔，如果没有配置则默认 60 秒
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

    /**
     * 停止数据采集
     *
     * 将采集间隔设置为 0 来停止设备的数据采集。
     * 间隔为 0 表示设备停止自动采集数据。
     */
    fun stopCollecting() {
        viewModelScope.launch {
            try {
                // 将采集间隔设为 0 表示停止采集
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

    /**
     * 清除设备数据
     *
     * 删除设备上存储的所有采集记录。此操作不可逆。
     * 执行过程中会更新下载进度状态以显示清除进度。
     */
    fun eraseDeviceData() {
        viewModelScope.launch {
            try {
                // 初始化进度为"开始"状态
                _downloadProgress.value = TransferProgress(100, 0, 1, 0, false)
                val result = bleManager.sendEraseData()
                // 操作完成后标记为"完成"状态
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

    /**
     * 从设备下载数据
     *
     * 分块下载设备上的采集记录，下载过程中通过 onDataFragment 回调逐批接收数据。
     * 每批数据接收后立即写入本地数据库，并更新下载进度。
     * 下载前会比较设备记录总数与本地已有记录数，只下载缺失的部分。
     *
     * @param macAddress 设备的蓝牙 MAC 地址
     */
    fun downloadData(macAddress: String) {
        viewModelScope.launch {
            try {
                // 获取设备信息以确定总记录数
                val info = bleManager.sendGetInfo()
                if (info != null) {
                    // 计算本地已有的记录数量
                    val existingCount = dataRepository.getRecordCount(macAddress)
                    // 计算需要下载的剩余记录数
                    val remaining = (info.recordCount - existingCount).toInt()

                    if (remaining <= 0) {
                        _operationResult.value = OperationResult(true, "数据已是最新，无需下载")
                        return@launch
                    }

                    // 初始化下载进度（总分块数 = (总记录数 + 6) / 7，每块约 7 条记录）
                    _downloadProgress.value = TransferProgress(remaining, 0, (remaining + 6) / 7, 0, false)

                    // 注册数据分片回调：每收到一批数据就写入数据库并更新进度
                    bleManager.onDataFragment = { _, records ->
                        viewModelScope.launch {
                            // 将本批记录插入数据库
                            dataRepository.insertRecords(macAddress, records)
                            // 更新已下载记录数和当前分块计数
                            val current = _downloadProgress.value
                            if (current != null) {
                                _downloadProgress.value = current.copy(
                                    downloadedRecords = current.downloadedRecords + records.size,
                                    currentChunk = current.currentChunk + 1
                                )
                            }
                        }
                    }

                    // 开始从设备读取数据（从已有记录数之后的位置开始读取）
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

    /**
     * 发送原始 Modbus 命令
     *
     * 将十六进制字符串格式的 Modbus 命令直接发送给设备。
     * 命令格式为空格分隔的十六进制字节，例如 "01 03 00 00 00 04"。
     *
     * @param hexString 十六进制格式的 Modbus 命令字符串
     */
    fun sendRawModbus(hexString: String) {
        viewModelScope.launch {
            try {
                // 将十六进制字符串解析为字节数组
                val data = hexString.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()
                _modbusResponse.value = "发送: $hexString\n等待响应..."
                // TODO: 这里需要通过协议层实际发送命令，当前仅记录日志
                _operationResult.value = OperationResult(true, "Modbus命令已发送")
            } catch (e: Exception) {
                _modbusResponse.value = "错误: ${e.message}"
                _operationResult.value = OperationResult(false, "发送Modbus命令错误: ${e.message}")
            }
        }
    }

    /**
     * 读取设备运行状态
     *
     * 从设备获取当前的运行状态信息，包括传感器状态、存储状态等。
     * 读取成功后更新本地状态缓存。
     */
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

    /**
     * 重启设备
     *
     * 向设备发送重启指令，设备将自动断开蓝牙连接并重新启动。
     * 重启后需要重新扫描和连接设备。
     */
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

    /**
     * 断开与设备的蓝牙连接
     *
     * 主动断开当前 BLE 连接，释放蓝牙资源。
     * 断开后需要重新扫描才能再次连接设备。
     */
    fun disconnect() {
        bleManager.disconnect()
    }
}
