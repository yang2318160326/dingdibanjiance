/**
 * BLE扫描与连接状态数据模型模块
 *
 * 本模块定义了BLE通信过程中使用的数据模型和状态枚举，包括：
 * - BleScanResult：BLE扫描结果数据类，表示扫描发现的设备信息
 * - ConnectedDevice：已连接设备的数据类，包含设备的基本信息
 * - BleConnectionState：连接状态的密封类，用代数数据类型（ADT）精确建模所有可能的连接状态
 *
 * 这些模型被BleConnectionManager使用，通过StateFlow对外暴露给UI层进行状态展示。
 */
package com.example.datacollector.ble

/**
 * BLE扫描结果数据类
 *
 * 表示一次BLE设备扫描中发现的单个设备的信息。
 * 当扫描回调onScanResult触发时，会创建此对象并添加到扫描结果列表中。
 *
 * @param name 设备广播名称，从广播包中解析得到。如果设备未广播名称，则显示为"Unknown"
 * @param macAddress 设备的MAC地址，格式如"AA:BB:CC:DD:EE:FF"，作为设备的唯一标识符
 * @param rssi 接收信号强度指示（Received Signal Strength Indicator），单位为dBm
 *             典型范围：-30dBm（极强）到 -90dBm（极弱），值越大信号越强
 */
data class BleScanResult(
    val name: String,
    val macAddress: String,
    val rssi: Int
)

/**
 * 已连接设备数据类
 *
 * 表示已成功建立BLE连接的设备的详细信息。
 * 在GATT连接成功并完成服务发现后，由BleConnectionManager创建并设置到currentDevice状态中。
 *
 * @param name 设备名称，从GATT对象获取
 * @param macAddress 设备的MAC地址
 * @param deviceId 设备编号（Long型），由设备端固件分配，用于设备管理系统的唯一标识
 * @param firmwareVersion 设备固件版本号，格式通常为"x.y.z"，用于功能兼容性判断和OTA升级
 */
data class ConnectedDevice(
    val name: String,
    val macAddress: String,
    val deviceId: Long,
    val firmwareVersion: String
)

/**
 * BLE连接状态密封类
 *
 * 使用Kotlin密封类（sealed class）建模连接的完整生命周期状态机。
 * 密封类的优势：编译器可以对when表达式进行穷举检查，确保所有状态都被处理。
 *
 * 状态流转：
 * Disconnected -> Scanning -> Connecting -> Connected
 *      ^             |           |           |
 *      |             v           v           |
 *      +----<-- Error ----------+----<-------+
 *
 * 所有子类都是不可变的，状态变化通过StateFlow发射新的状态对象实现。
 */
sealed class BleConnectionState {

    /**
     * 已断开状态（初始状态）
     *
     * 表示当前没有与任何BLE设备建立连接。
     * 这是连接状态机的起始和终止状态。
     */
    object Disconnected : BleConnectionState()

    /**
     * 扫描中状态
     *
     * 表示BLE扫描器正在扫描周围的BLE设备。
     * 此状态下scanResults StateFlow会持续更新扫描到的设备列表。
     * 扫描会自动在30秒后超时停止，也可手动调用stopScan停止。
     */
    object Scanning : BleConnectionState()

    /**
     * 连接中状态
     *
     * 表示已发起GATT连接请求，正在等待连接建立完成。
     * 此状态是短暂的过渡状态，连接成功后会转为Connected，
     * 连接失败则会转为Error或Disconnected。
     */
    object Connecting : BleConnectionState()

    /**
     * 已连接状态
     *
     * 表示与BLE设备的连接已建立，服务发现已完成，TX/RX特征值已就绪，
     * 可以开始进行命令收发和数据传输。
     *
     * @param deviceName 设备广播名称
     * @param macAddress 设备的MAC地址
     */
    data class Connected(val deviceName: String, val macAddress: String) : BleConnectionState()

    /**
     * 错误状态
     *
     * 表示在扫描或连接过程中发生了不可恢复的错误。
     * 包含错误描述信息，可用于UI层向用户展示错误原因。
     *
     * 常见错误场景：
     * - "扫描失败: x" — BLE扫描启动失败
     * - "设备未找到: xx:xx:xx:xx:xx:xx" — 指定MAC地址的设备不存在
     * - "服务发现失败" — GATT服务发现过程出错
     * - "未找到BLE服务" — 设备不支持HM10或NUS服务
     *
     * @param message 错误描述信息，包含错误原因和相关上下文
     */
    data class Error(val message: String) : BleConnectionState()
}
