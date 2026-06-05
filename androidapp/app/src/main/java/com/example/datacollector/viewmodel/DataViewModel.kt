/**
 * 数据管理视图模型层
 *
 * 本文件定义了数据管理相关的 ViewModel，负责传感器数据记录的
 * 加载、下载和清除操作，以及传输进度的跟踪。
 */
package com.example.datacollector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.domain.model.SensorRecord
import com.example.datacollector.domain.model.TransferProgress
import com.example.datacollector.domain.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 数据管理视图模型（DataViewModel）
 *
 * 负责管理传感器数据记录的完整生命周期，包括：
 * - 从本地数据库加载指定设备的传感器记录
 * - 从蓝牙设备下载新的采集数据并存储到本地
 * - 清除指定设备的所有本地数据记录
 * - 跟踪数据下载/传输的实时进度
 *
 * @param bleManager 蓝牙连接管理器，用于与设备通信获取数据
 * @param dataRepository 数据仓库，用于本地数据的读写操作
 */
@HiltViewModel
class DataViewModel @Inject constructor(
    private val bleManager: BleConnectionManager,
    private val dataRepository: DataRepository
) : ViewModel() {

    /** 传感器数据记录列表，按时间排序 */
    private val _records = MutableStateFlow<List<SensorRecord>>(emptyList())
    val records: StateFlow<List<SensorRecord>> = _records.asStateFlow()

    /** 数据记录总数 */
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    /** 数据传输进度信息，包含已下载数、总分块数等 */
    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    /**
     * 初始化块
     *
     * 订阅 BLE 管理器的数据传输进度流，
     * 当有传输活动时（totalRecords > 0）自动更新本地进度状态。
     * 仅在 totalRecords 大于 0 时更新，避免初始值覆盖。
     */
    init {
        viewModelScope.launch {
            bleManager.transferProgress.collect { progress ->
                if (progress.totalRecords > 0) _transferProgress.value = progress
            }
        }
    }

    /**
     * 加载指定设备的传感器数据记录
     *
     * 从本地数据库查询该设备的所有记录，并持续监听数据变化。
     * 当数据库中的数据发生变更时（如新数据下载完成），
     * 会自动更新记录列表和总数。
     *
     * @param macAddress 设备的蓝牙 MAC 地址，作为数据库查询的设备标识
     */
    fun loadRecords(macAddress: String) {
        viewModelScope.launch {
            // collect 持续监听数据变化，确保 UI 始终反映最新数据
            dataRepository.getRecordsByDevice(macAddress).collect { records ->
                _records.value = records
                _totalCount.value = records.size
            }
        }
    }

    /**
     * 从设备下载数据
     *
     * 执行以下步骤：
     * 1. 获取设备信息以确定总记录数
     * 2. 查询本地已有记录数，计算差值
     * 3. 如果数据已完整则跳过下载
     * 4. 注册数据回调函数，每批数据到达后写入数据库
     * 5. 发送数据读取指令，开始分块传输
     *
     * @param macAddress 设备的蓝牙 MAC 地址
     */
    fun downloadData(macAddress: String) {
        viewModelScope.launch {
            // 获取设备信息，失败则直接返回
            val info = bleManager.sendGetInfo() ?: return@launch
            // 查询本地已有记录数
            val existingCount = dataRepository.getRecordCount(macAddress)
            // 计算需要下载的剩余记录数
            val remaining = (info.recordCount - existingCount).toInt()

            // 数据已是最新，无需下载
            if (remaining <= 0) {
                // 标记传输为已完成
                _transferProgress.value = TransferProgress(0, 0, 0, 0, true)
                return@launch
            }

            // 注册数据分片回调：每收到一批数据就写入数据库
            bleManager.onDataFragment = { _, records ->
                viewModelScope.launch { dataRepository.insertRecords(macAddress, records) }
            }

            // 发送数据读取指令，从已有记录数之后的位置开始读取
            bleManager.sendGetData(existingCount, remaining)
            // 初始化传输进度（总分块数向上取整，每块约 7 条记录）
            _transferProgress.value = TransferProgress(remaining, 0, (remaining + 6) / 7, 0, false)
        }
    }

    /**
     * 清除指定设备的所有本地数据记录
     *
     * 删除数据库中该设备的所有传感器记录，
     * 并重置内存中的记录列表和计数。
     * 注意：此操作不会清除设备上存储的数据，仅清除本地数据库。
     *
     * @param macAddress 设备的蓝牙 MAC 地址
     */
    fun clearData(macAddress: String) {
        viewModelScope.launch {
            dataRepository.clearRecords(macAddress)
            _records.value = emptyList()
            _totalCount.value = 0
        }
    }
}
