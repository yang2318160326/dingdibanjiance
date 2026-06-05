/**
 * BLE连接管理器模块
 *
 * 本模块负责管理Android设备与外部BLE（蓝牙低功耗）设备之间的完整通信流程，
 * 包括：设备扫描、连接建立、服务发现、数据帧收发、协议解析等。
 *
 * 主要职责：
 * - BLE设备扫描与发现
 * - GATT连接管理与状态维护
 * - 基于自定义协议的帧封装与解析
 * - 命令/响应模式的同步通信
 * - 数据分片传输与进度跟踪
 */
package com.example.datacollector.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.domain.model.SensorRecord
import com.example.datacollector.domain.model.TransferProgress
import com.example.datacollector.protocol.CommandBuilder
import com.example.datacollector.protocol.FrameParser
import com.example.datacollector.protocol.ProtocolConstants
import com.example.datacollector.protocol.ResponseParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE连接管理器
 *
 * 使用单例模式（@Singleton）通过Hilt依赖注入创建，负责与BLE设备的完整通信生命周期管理。
 * 内部使用Kotlin协程进行异步操作，通过StateFlow对外暴露连接状态、扫描结果等可观测状态。
 *
 * 通信流程：
 * 1. 扫描（startScan） -> 发现设备
 * 2. 连接（connect） -> 建立GATT连接
 * 3. 服务发现 -> 获取TX/RX特征值
 * 4. 命令收发（sendPing/sendGetInfo等） -> 数据交互
 * 5. 断开连接（disconnect） -> 释放资源
 *
 * @param context Android应用上下文，通过Hilt注入（@ApplicationContext）
 */
@Singleton
class BleConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ======================== 协程与线程管理 ========================

    /** 协程作用域，使用SupervisorJob防止子协程失败影响父协程，绑定到IO调度器 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 主线程Handler，用于将回调结果切换回UI线程更新状态 */
    private val handler = Handler(Looper.getMainLooper())

    // ======================== 蓝牙核心组件 ========================

    /** 系统蓝牙管理器，用于获取蓝牙适配器和已连接设备列表 */
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    /** 蓝牙适配器，代表设备的蓝牙硬件，可能为null（设备不支持蓝牙时） */
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    /** BLE扫描器，用于扫描周围的BLE设备 */
    private var scanner: BluetoothLeScanner? = null

    /** GATT连接对象，代表与目标BLE设备的连接会话 */
    private var gatt: BluetoothGatt? = null

    // ======================== GATT特征值 ========================

    /** 发送特征值（TX），通过此特征值向设备发送数据 */
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    /** 接收特征值（RX），通过此特征值接收设备发来的数据 */
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    // ======================== 协议层状态 ========================

    /** 命令序列号计数器，用于区分不同的命令请求，每次递增 */
    private var seqCounter = 0

    /** 当前等待响应的命令，使用CompletableDeferred实现挂起等待机制 */
    private var pendingCommand: CompletableDeferred<FrameParser.Frame>? = null

    /** 接收缓冲区，用于拼接多次BLE回调中收到的分片数据，最终组装成完整帧 */
    private val receiveBuffer = ByteArrayOutputStream()

    // ======================== 可观测状态（StateFlow） ========================

    /**
     * BLE连接状态
     * 对外暴露为只读的StateFlow，内部通过MutableStateFlow进行修改。
     * UI层可以collect此Flow来响应连接状态变化。
     */
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    /** 扫描结果列表，每当发现新设备时更新 */
    private val _scanResults = MutableStateFlow<List<BleScanResult>>(emptyList())
    val scanResults: StateFlow<List<BleScanResult>> = _scanResults.asStateFlow()

    /** 当前已连接设备的信息，断开时置为null */
    private val _currentDevice = MutableStateFlow<ConnectedDevice?>(null)
    val currentDevice: StateFlow<ConnectedDevice?> = _currentDevice.asStateFlow()

    /** 数据传输进度，包含总记录数、已下载数、总分片数、当前分片索引、是否完成 */
    private val _transferProgress = MutableStateFlow<TransferProgress>(TransferProgress(0, 0, 0, 0, false))
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    /**
     * 数据分片回调
     * 当收到数据帧（CMD_DATA_FRAG）时触发，参数为分片索引和传感器记录列表。
     * 由外部设置，用于将解析后的传感器数据传递给上层业务逻辑。
     */
    var onDataFragment: ((Int, List<SensorRecord>) -> Unit)? = null

    // ======================== BLE扫描回调 ========================

    /**
     * BLE扫描回调对象
     *
     * 当扫描到BLE设备时系统会回调此对象的方法：
     * - onScanResult：发现单个设备时调用，将结果去重后添加到扫描结果列表
     * - onScanFailed：扫描失败时调用，更新连接状态为错误状态
     */
    private val scanCallback = object : ScanCallback() {
        /**
         * 扫描到设备时的回调
         *
         * @param callbackType 回调类型，通常为CALLBACK_TYPE_ALL_MATCHES
         * @param result 扫描结果，包含设备对象、广播数据、信号强度等信息
         */
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // 优先获取设备名称，依次尝试：设备广播名、扫描记录中的名称、默认"Unknown"
            val name = result.device.name ?: result.scanRecord?.deviceName ?: "Unknown"
            val mac = result.device.address
            val rssi = result.rssi
            val current = _scanResults.value.toMutableList()
            // 去重：如果该MAC地址的设备尚未在列表中，则添加
            if (current.none { it.macAddress == mac }) {
                current.add(BleScanResult(name, mac, rssi))
                _scanResults.value = current
            }
        }

        /**
         * 扫描失败时的回调
         *
         * @param errorCode 错误码，常见值：SCAN_FAILED_ALREADY_STARTED(1)等
         */
        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.Error("扫描失败: $errorCode")
        }
    }

    // ======================== 扫描控制方法 ========================

    /**
     * 开始BLE设备扫描
     *
     * 执行以下操作：
     * 1. 获取BLE扫描器实例
     * 2. 清空之前的扫描结果
     * 3. 将状态切换为"扫描中"
     * 4. 启动扫描
     * 5. 设置30秒超时自动停止扫描
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        _scanResults.value = emptyList()
        _connectionState.value = BleConnectionState.Scanning
        scanner?.startScan(scanCallback)
        // 30秒后自动停止扫描，防止长时间扫描耗电
        handler.postDelayed({ stopScan() }, 30_000)
    }

    /**
     * 停止BLE设备扫描
     *
     * 如果当前状态为"扫描中"，则将状态切换为"已断开"。
     * 即使扫描已超时自动停止，调用此方法也是安全的。
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        // 仅当当前处于扫描状态时才切换状态，避免覆盖其他状态
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    // ======================== 连接管理方法 ========================

    /**
     * 连接到指定MAC地址的BLE设备
     *
     * 连接流程：
     * 1. 停止正在进行的扫描
     * 2. 将状态切换为"连接中"
     * 3. 通过MAC地址获取远程设备对象
     * 4. 发起GATT连接（自动连接=false，传输方式=LE）
     *
     * @param macAddress 目标设备的MAC地址，格式如"AA:BB:CC:DD:EE:FF"
     */
    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        stopScan()
        _connectionState.value = BleConnectionState.Connecting

        // 通过MAC地址获取蓝牙设备对象
        val device = bluetoothAdapter?.getRemoteDevice(macAddress)
        if (device == null) {
            _connectionState.value = BleConnectionState.Error("设备未找到: $macAddress")
            return
        }

        // 发起GATT连接，autoConnect=false表示直接连接（快速但可能不稳定）
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /**
     * 断开与当前BLE设备的连接并释放所有相关资源
     *
     * 执行顺序：
     * 1. 断开GATT连接
     * 2. 关闭GATT客户端（释放系统资源）
     * 3. 清空GATT对象和特征值引用
     * 4. 重置连接状态和当前设备信息
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        txCharacteristic = null
        rxCharacteristic = null
        _connectionState.value = BleConnectionState.Disconnected
        _currentDevice.value = null
    }

    // ======================== GATT回调处理 ========================

    /**
     * GATT回调对象
     *
     * 处理BLE连接生命周期中的各种事件：
     * - 连接状态变化（onConnectionStateChange）
     * - 服务发现完成（onServicesDiscovered）
     * - MTU协商完成（onMtuChanged）
     * - 特征值数据变化通知（onCharacteristicChanged）
     * - 特征值写入完成（onCharacteristicWrite）
     */
    private val gattCallback = object : BluetoothGattCallback() {

        /**
         * 连接状态变化回调
         *
         * @param gatt GATT客户端实例
         * @param status 操作状态，GATT_SUCCESS(0)表示成功
         * @param newState 新的连接状态：STATE_CONNECTED或STATE_DISCONNECTED
         */
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // 连接成功后，立即开始发现设备上的服务
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // 断开连接时，切回主线程更新UI状态
                    handler.post {
                        _connectionState.value = BleConnectionState.Disconnected
                        _currentDevice.value = null
                    }
                }
            }
        }

        /**
         * 服务发现完成回调
         *
         * 服务发现完成后，需要：
         * 1. 查找BLE服务（先尝试HM10服务，再尝试NUS服务）
         * 2. 获取TX/RX特征值
         * 3. 启用RX特征值的通知功能
         * 4. 请求MTU协商以支持更大的数据包
         * 5. 更新连接状态
         *
         * @param gatt GATT客户端实例
         * @param status 操作状态，GATT_SUCCESS表示服务发现成功
         */
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post {
                    _connectionState.value = BleConnectionState.Error("服务发现失败")
                }
                return
            }

            // 首先尝试HM10蓝牙模块的服务UUID
            var service = gatt.getService(ProtocolConstants.UUID_SERVICE_HM10)
            if (service != null) {
                txCharacteristic = service.getCharacteristic(ProtocolConstants.UUID_CHAR_HM10_TX)
                rxCharacteristic = service.getCharacteristic(ProtocolConstants.UUID_CHAR_HM10_RX)
            }

            // 如果HM10服务未找到，尝试Nordic UART Service（NUS）
            if (txCharacteristic == null) {
                service = gatt.getService(ProtocolConstants.UUID_SERVICE_NUS)
                if (service != null) {
                    txCharacteristic = service.getCharacteristic(ProtocolConstants.UUID_CHAR_NUS_TX)
                    rxCharacteristic = service.getCharacteristic(ProtocolConstants.UUID_CHAR_NUS_RX)
                }
            }

            // 如果两种服务都没找到，报告错误
            if (txCharacteristic == null || rxCharacteristic == null) {
                handler.post {
                    _connectionState.value = BleConnectionState.Error("未找到BLE服务")
                }
                return
            }

            // 启用RX特征值的本地通知（手机端接收设备数据的开关）
            gatt.setCharacteristicNotification(rxCharacteristic, true)

            // 写入Client Characteristic Configuration Descriptor（CCCD）
            // 这是BLE协议标准，写入ENABLE_NOTIFICATION_VALUE才能收到设备端的数据通知
            val descriptor = rxCharacteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)

            // 请求MTU（Maximum Transmission Unit）协商，247字节是常见BLE模块支持的最大值
            // 更大的MTU可以减少数据分包，提高传输效率
            gatt.requestMtu(247)

            // 切回主线程，设置连接状态为已连接
            handler.post {
                val name = gatt.device.name ?: "Unknown"
                val mac = gatt.device.address
                _connectionState.value = BleConnectionState.Connected(name, mac)
            }
        }

        /**
         * MTU协商完成回调
         *
         * @param gatt GATT客户端实例
         * @param mtu 协商后的MTU大小
         * @param status 操作状态
         */
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // MTU协商完成，当前实现中未做特殊处理
        }

        /**
         * 特征值数据变化通知回调
         *
         * 当设备通过RX特征值发送数据时，Android系统会回调此方法。
         * 这是接收设备端数据的主要入口。
         *
         * @param gatt GATT客户端实例
         * @param characteristic 发生变化的特征值（这里是RX特征值）
         */
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleDataReceived(characteristic.value)
        }

        /**
         * 特征值写入完成回调
         *
         * @param gatt GATT客户端实例
         * @param characteristic 写入完成的特征值
         * @param status 操作状态
         */
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // 写入完成，当前实现中未做特殊处理
        }
    }

    // ======================== 数据帧处理 ========================

    /**
     * 处理从BLE设备接收到的原始数据
     *
     * BLE数据以分片方式到达，需要在缓冲区中拼接后进行帧解析。
     * 处理逻辑：
     * 1. 将新数据写入缓冲区
     * 2. 如果缓冲区数据量达到最小帧大小，尝试解析
     * 3. 如果帧头前有多余数据，丢弃并重新解析
     * 4. 解析成功则处理该帧，缓冲区清空
     * 5. 如果缓冲区溢出（超过最大负载+最小帧大小），则清空缓冲区防止内存泄漏
     *
     * @param data 从BLE特征值回调中获取的原始字节数组
     */
    private fun handleDataReceived(data: ByteArray) {
        receiveBuffer.write(data)
        val buffer = receiveBuffer.toByteArray()

        if (buffer.size >= ProtocolConstants.MIN_FRAME_SIZE) {
            // 检查帧头（SOF = Start Of Frame）位置
            val sofIndex = buffer.indexOf(ProtocolConstants.SOF)
            if (sofIndex > 0) {
                // 如果帧头前有多余的无效数据，丢弃这些数据，保留从帧头开始的内容
                receiveBuffer.reset()
                receiveBuffer.write(buffer, sofIndex, buffer.size - sofIndex)
                return handleDataReceived(byteArrayOf())
            }

            // 尝试解析完整帧
            val frame = FrameParser.parse(buffer)
            if (frame != null) {
                // 帧解析成功，清空缓冲区并处理帧数据
                receiveBuffer.reset()
                processFrame(frame)
            } else if (buffer.size > ProtocolConstants.MAX_PAYLOAD_SIZE + ProtocolConstants.MIN_FRAME_SIZE) {
                // 缓冲区数据量超过安全阈值仍无法解析，可能是异常数据，清空缓冲区
                receiveBuffer.reset()
            }
        }
    }

    /**
     * 处理解析完成的协议帧
     *
     * 根据帧中的命令码（cmd）进行分发处理：
     * - CMD_DATA_FRAG：数据分片帧，解析传感器记录并通过回调返回，同时发送ACK确认
     * - CMD_ERROR：错误响应帧，将错误信息传递给等待中的命令
     * - 其他命令：作为普通响应帧，完成等待中的命令
     *
     * @param frame 解析完成的协议帧
     */
    private fun processFrame(frame: FrameParser.Frame) {
        when (frame.cmd) {
            ProtocolConstants.CMD_DATA_FRAG -> {
                // 数据分片帧：解析负载获取分片索引和传感器记录列表
                val result = ResponseParser.parseDataFrag(frame.payload)
                if (result != null) {
                    val (chunkIdx, records) = result
                    // 触发回调，将数据传递给上层业务处理
                    onDataFragment?.invoke(chunkIdx, records)

                    // 更新传输进度：累加已下载记录数，更新当前分片索引
                    val current = _transferProgress.value
                    _transferProgress.value = current.copy(
                        downloadedRecords = current.downloadedRecords + records.size,
                        currentChunk = chunkIdx
                    )

                    // 异步发送ACK确认帧，告知设备该分片已成功接收
                    scope.launch {
                        sendAck(chunkIdx, ProtocolConstants.ACK_OK)
                    }
                }
            }
            ProtocolConstants.CMD_ERROR -> {
                // 错误响应帧：解析错误码和相关命令
                val error = ResponseParser.parseError(frame.payload)
                // 将异常传递给等待中的命令调用者
                pendingCommand?.completeExceptionally(
                    Exception("设备错误: code=${error?.first}, cmd=${error?.second}")
                )
                pendingCommand = null
            }
            else -> {
                // 其他响应帧：完成等待中的命令
                pendingCommand?.complete(frame)
                pendingCommand = null
            }
        }
    }

    // ======================== 数据发送方法 ========================

    /**
     * 通过TX特征值向设备发送原始数据帧
     *
     * 由于BLE的MTU限制，大数据需要分片发送。每次发送不超过MTU大小的数据块，
     * 每个分片之间添加固定间隔延迟，确保设备有足够时间处理。
     *
     * @param data 需要发送的完整帧数据字节数组
     */
    @SuppressLint("MissingPermission")
    private suspend fun sendFrame(data: ByteArray) {
        val char = txCharacteristic ?: return
        val g = gatt ?: return

        val mtu = 247  // 最大传输单元大小
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + mtu, data.size)
            val chunk = data.copyOfRange(offset, end)
            char.value = chunk
            g.writeCharacteristic(char)
            offset = end
            // 分片间添加延迟，确保设备端能正确接收和处理
            delay(ProtocolConstants.FRAME_INTERVAL_MS)
        }
    }

    // ======================== 业务命令方法 ========================

    /**
     * 发送Ping命令，测试与设备的通信是否正常
     *
     * @return 设备的Ping响应，包含时间戳等信息；通信失败返回null
     */
    suspend fun sendPing(): ResponseParser.PingResponse? {
        val frame = sendAndWait(CommandBuilder.ping(nextSeq())) ?: return null
        return ResponseParser.parsePing(frame.payload)
    }

    /**
     * 发送获取设备信息命令
     *
     * @return 设备信息响应，包含设备名称、固件版本等；失败返回null
     */
    suspend fun sendGetInfo(): ResponseParser.InfoResponse? {
        val frame = sendAndWait(CommandBuilder.getInfo(nextSeq())) ?: return null
        return ResponseParser.parseInfo(frame.payload)
    }

    /**
     * 发送设置设备时间命令
     *
     * @param unixTimestamp Unix时间戳（秒），用于同步设备时钟
     * @return 设置是否成功
     */
    suspend fun sendSetTime(unixTimestamp: Long): Boolean {
        val frame = sendAndWait(CommandBuilder.setTime(nextSeq(), unixTimestamp)) ?: return false
        // 检查响应负载是否非空且状态码为OK
        return frame.payload.isNotEmpty() && frame.payload[0].toInt() == ProtocolConstants.STATUS_OK
    }

    /**
     * 发送获取设备配置命令
     *
     * @return 设备配置对象；失败返回null
     */
    suspend fun sendGetConfig(): DeviceConfig? {
        val frame = sendAndWait(CommandBuilder.getConfig(nextSeq())) ?: return null
        return ResponseParser.parseConfig(frame.payload)
    }

    /**
     * 发送设置设备配置命令
     *
     * @param config 要写入设备的配置对象
     * @return 设置是否成功
     */
    suspend fun sendSetConfig(config: DeviceConfig): Boolean {
        val frame = sendAndWait(CommandBuilder.setConfig(nextSeq(), config)) ?: return false
        return frame.payload.isNotEmpty() && frame.payload[0].toInt() == ProtocolConstants.STATUS_OK
    }

    /**
     * 发送获取传感器数据命令（非阻塞，不等待响应）
     *
     * 此命令会触发设备端批量发送数据分片，数据通过onDataFragment回调返回。
     * 初始化传输进度信息，用于UI展示下载进度。
     *
     * @param startIndex 起始记录索引
     * @param count 要获取的记录总数
     */
    suspend fun sendGetData(startIndex: Int, count: Int) {
        // 初始化传输进度：计算总分片数（每分片7条记录，向上取整）
        _transferProgress.value = TransferProgress(
            totalRecords = count,
            downloadedRecords = 0,
            totalChunks = (count + 6) / 7,  // 向上取整：(count + 7 - 1) / 7
            currentChunk = 0,
            isComplete = false
        )
        // 直接发送帧，不等待响应（设备会异步推送数据分片）
        sendFrame(CommandBuilder.getData(nextSeq(), startIndex, count))
    }

    /**
     * 发送擦除设备数据命令
     *
     * @return 擦除是否成功
     */
    suspend fun sendEraseData(): Boolean {
        val frame = sendAndWait(CommandBuilder.eraseData(nextSeq())) ?: return false
        return frame.payload.isNotEmpty() && frame.payload[0].toInt() == ProtocolConstants.STATUS_OK
    }

    /**
     * 发送获取设备状态命令
     *
     * @return 设备状态响应，包含电池电量、存储使用情况等；失败返回null
     */
    suspend fun sendGetStatus(): ResponseParser.StatusResponse? {
        val frame = sendAndWait(CommandBuilder.getStatus(nextSeq())) ?: return null
        return ResponseParser.parseStatus(frame.payload)
    }

    /**
     * 发送数据接收确认帧（ACK）
     *
     * 在接收到数据分片后调用，告知设备该分片已被成功接收，
     * 设备收到ACK后会继续发送下一个分片。
     *
     * @param chunkIndex 已接收的分片索引
     * @param status 确认状态，通常为ACK_OK
     */
    suspend fun sendAck(chunkIndex: Int, status: Byte) {
        sendFrame(CommandBuilder.dataAck(nextSeq(), chunkIndex, status))
    }

    /**
     * 发送设置设备ID命令
     *
     * @param deviceId 设备编号（Long型）
     * @param name 设备名称
     * @return 设置是否成功
     */
    suspend fun sendSetDeviceId(deviceId: Long, name: String): Boolean {
        val frame = sendAndWait(CommandBuilder.setDeviceId(nextSeq(), deviceId, name)) ?: return false
        return frame.payload.isNotEmpty() && frame.payload[0].toInt() == ProtocolConstants.STATUS_OK
    }

    /**
     * 发送重启设备命令
     *
     * @return 重启命令是否成功下发
     */
    suspend fun sendReboot(): Boolean {
        val frame = sendAndWait(CommandBuilder.reboot(nextSeq())) ?: return false
        return frame.payload.isNotEmpty() && frame.payload[0].toInt() == ProtocolConstants.STATUS_OK
    }

    // ======================== 通信底层方法 ========================

    /**
     * 发送命令并等待设备响应（同步模式）
     *
     * 使用CompletableDeferred实现挂起等待：发送帧后，当前协程挂起，
     * 直到设备响应帧到达（通过processFrame完成）或超时。
     *
     * @param data 要发送的帧数据
     * @param timeoutMs 等待超时时间（毫秒），默认使用协议常量中定义的超时值
     * @return 设备响应帧；超时或异常返回null
     */
    private suspend fun sendAndWait(data: ByteArray, timeoutMs: Long = ProtocolConstants.TIMEOUT_MS): FrameParser.Frame? {
        pendingCommand = CompletableDeferred()
        sendFrame(data)
        return try {
            // 使用withTimeoutOrNull设置超时，避免无限等待
            withTimeoutOrNull(timeoutMs) {
                pendingCommand?.await()
            }
        } catch (e: Exception) {
            // 捕获协程取消等异常，返回null
            null
        }
    }

    /**
     * 获取下一个命令序列号
     *
     * 序列号范围为0-255（单字节），使用位与运算 (& 0xFF) 实现溢出回绕。
     * 序列号用于区分不同的命令-响应对，确保响应与正确的命令匹配。
     *
     * @return 当前序列号，然后自动递增
     */
    private fun nextSeq(): Int {
        return seqCounter++ and 0xFF
    }

    /**
     * 销毁管理器，释放所有资源
     *
     * 执行顺序：
     * 1. 断开蓝牙连接
     * 2. 取消协程作用域中的所有协程
     * 通常在应用退出或不再需要BLE通信时调用。
     */
    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
