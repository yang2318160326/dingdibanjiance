/**
 * 设备扫描视图模型层
 *
 * 本文件定义了设备扫描相关的 ViewModel，负责管理蓝牙设备的扫描、
 * 连接状态展示以及已知设备列表的维护。
 */
package com.example.datacollector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.ble.BleConnectionState
import com.example.datacollector.ble.BleScanResult
import com.example.datacollector.domain.model.KnownDevice
import com.example.datacollector.domain.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设备扫描视图模型（ScanViewModel）
 *
 * 负责蓝牙设备扫描和连接管理的 UI 逻辑，包括：
 * - 启动/停止/暂停蓝牙扫描
 * - 展示扫描结果列表
 * - 管理与目标设备的连接/断开
 * - 维护已知设备列表（历史连接过的设备）
 *
 * 通过 Hilt 依赖注入获取 BLE 连接管理器和数据仓库。
 *
 * @param bleManager 蓝牙连接管理器，负责底层 BLE 扫描和连接操作
 * @param dataRepository 数据仓库，负责已知设备列表的持久化存储
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleManager: BleConnectionManager,
    private val dataRepository: DataRepository
) : ViewModel() {

    /**
     * 蓝牙连接状态流
     *
     * 监听 BLE 管理器的连接状态变化，默认值为"已断开"。
     * 使用 WhileSubscribed 策略：在有订阅者时活跃 5 秒后停止，
     * 避免在没有 UI 观察时浪费资源。
     */
    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleConnectionState.Disconnected)

    /**
     * 蓝牙扫描结果列表流
     *
     * 实时展示当前扫描到的蓝牙设备列表。
     * 每次发现新设备或设备信息更新时自动刷新。
     */
    val scanResults: StateFlow<List<BleScanResult>> = bleManager.scanResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 已知设备列表流
     *
     * 从数据库中读取所有历史连接过的设备信息。
     * 用于在扫描结果中显示设备别名和历史记录。
     */
    val knownDevices: StateFlow<List<KnownDevice>> = dataRepository.getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 启动蓝牙扫描
     *
     * 开始扫描周围的蓝牙 BLE 设备，扫描结果通过 scanResults 流实时更新。
     */
    fun startScan() { bleManager.startScan() }

    /**
     * 停止蓝牙扫描
     *
     * 停止当前的蓝牙扫描操作，释放扫描资源。
     */
    fun stopScan() { bleManager.stopScan() }

    /**
     * 暂停蓝牙扫描
     *
     * 暂停当前扫描，效果与 stopScan 相同。
     * 保留独立方法以便后续实现差异化的暂停/停止逻辑。
     */
    fun pauseScan() { bleManager.stopScan() }

    /**
     * 连接到指定蓝牙设备
     *
     * 通过 MAC 地址发起 BLE 连接。连接操作在协程中异步执行，
     * 连接状态通过 connectionState 流实时更新。
     *
     * @param macAddress 目标设备的蓝牙 MAC 地址
     */
    fun connect(macAddress: String) { viewModelScope.launch { bleManager.connect(macAddress) } }

    /**
     * 断开当前蓝牙连接
     *
     * 主动断开与当前设备的 BLE 连接，释放蓝牙资源。
     */
    fun disconnect() { bleManager.disconnect() }
}
