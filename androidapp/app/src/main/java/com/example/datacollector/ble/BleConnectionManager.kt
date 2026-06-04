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

@Singleton
class BleConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private var seqCounter = 0
    private var pendingCommand: CompletableDeferred<FrameParser.Frame>? = null
    private val receiveBuffer = ByteArrayOutputStream()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleScanResult>>(emptyList())
    val scanResults: StateFlow<List<BleScanResult>> = _scanResults.asStateFlow()

    private val _currentDevice = MutableStateFlow<ConnectedDevice?>(null)
    val currentDevice: StateFlow<ConnectedDevice?> = _currentDevice.asStateFlow()

    private val _transferProgress = MutableStateFlow(TransferProgress(0, 0, 0, 0, false))
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    var onDataFragment: ((Int, List<SensorRecord>) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: "Unknown"
            val mac = result.device.address
            val rssi = result.rssi
            val current = _scanResults.value.toMutableList()
            if (current.none { it.macAddress == mac }) {
                current.add(BleScanResult(name, mac, rssi))
                _scanResults.value = current
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.Error("扫描失败: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        _scanResults.value = emptyList()
        _connectionState.value = BleConnectionState.Scanning
        scanner?.startScan(scanCallback)
        handler.postDelayed({ stopScan() }, 30_000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        stopScan()
        _connectionState.value = BleConnectionState.Connecting

        val device = bluetoothAdapter?.getRemoteDevice(macAddress)
        if (device == null) {
            _connectionState.value = BleConnectionState.Error("设备未找到: $macAddress")
            return
        }

        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

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

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.post {
                        _connectionState.value = BleConnectionState.Disconnected
                        _currentDevice.value = null
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post {
                    _connectionState.value = BleConnectionState.Error("服务发现失败")
                }
                return
            }

            var service = gatt.getService(ProtocolConstants.UUID_SERVICE_HM10)
            if (service != null) {
                txCharacteristic = service.getCharacteristic(ProtocolConstants.UUID_CHAR_HM10_TX)
                rxCharacteristic = service.getCharacteristic(ProtocolConstants.UUID_CHAR_HM10_RX)
            }

            if (txCharacteristic == null) {
                service = gatt.getService(ProtocolConstants.UUID_SERVICE_NUS)
                if (service != null) {
                    txCharacteristic = service.getCharacteristic(ProtocolConstants.UUID_CHAR_NUS_TX)
                    rxCharacteristic = service.getCharacteristic(ProtocolConstants.UUID_CHAR_NUS_RX)
                }
            }

            if (txCharacteristic == null || rxCharacteristic == null) {
                handler.post {
                    _connectionState.value = BleConnectionState.Error("未找到BLE服务")
                }
                return
            }

            gatt.setCharacteristicNotification(rxCharacteristic, true)
            val descriptor = rxCharacteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)

            gatt.requestMtu(247)

            handler.post {
                val name = gatt.device.name ?: "Unknown"
                val mac = gatt.device.address
                _connectionState.value = BleConnectionState.Connected(name, mac)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // MTU negotiation complete
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleDataReceived(characteristic.value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Write complete
        }
    }

    private fun handleDataReceived(data: ByteArray) {
        receiveBuffer.write(data)
        val buffer = receiveBuffer.toByteArray()

        if (buffer.size >= ProtocolConstants.MIN_FRAME_SIZE) {
            val sofIndex = buffer.indexOf(ProtocolConstants.SOF)
            if (sofIndex > 0) {
                receiveBuffer.reset()
                receiveBuffer.write(buffer, sofIndex, buffer.size - sofIndex)
                return handleDataReceived(byteArrayOf())
            }

            val frame = FrameParser.parse(buffer)
            if (frame != null) {
                receiveBuffer.reset()
                processFrame(frame)
            } else if (buffer.size > ProtocolConstants.MAX_PAYLOAD_SIZE + ProtocolConstants.MIN_FRAME_SIZE) {
                receiveBuffer.reset()
            }
        }
    }

    private fun processFrame(frame: FrameParser.Frame) {
        when (frame.cmd) {
            ProtocolConstants.CMD_DATA_FRAG -> {
                val result = ResponseParser.parseDataFrag(frame.payload)
                if (result != null) {
                    val (chunkIdx, records) = result
                    onDataFragment?.invoke(chunkIdx, records)

                    val current = _transferProgress.value
                    _transferProgress.value = current.copy(
                        downloadedRecords = current.downloadedRecords + records.size,
                        currentChunk = chunkIdx
                    )

                    scope.launch {
                        sendAck(chunkIdx, ProtocolConstants.ACK_OK)
                    }
                }
            }
            ProtocolConstants.CMD_ERROR -> {
                val error = ResponseParser.parseError(frame.payload)
                pendingCommand?.completeExceptionally(
                    Exception("设备错误: code=${error?.first}, cmd=${error?.second}")
                )
                pendingCommand = null
            }
            else -> {
                pendingCommand?.complete(frame)
                pendingCommand = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendFrame(data: ByteArray) {
        val char = txCharacteristic ?: return
        val g = gatt ?: return

        val mtu = 247
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + mtu, data.size)
            val chunk = data.copyOfRange(offset, end)
            char.value = chunk
            g.writeCharacteristic(char)
            offset = end
            delay(ProtocolConstants.FRAME_INTERVAL_MS)
        }
    }

    suspend fun sendPing(): ResponseParser.PingResponse? {
        val frame = sendAndWait(CommandBuilder.ping(nextSeq())) ?: return null
        return ResponseParser.parsePing(frame.payload)
    }

    suspend fun sendGetInfo(): ResponseParser.InfoResponse? {
        val frame = sendAndWait(CommandBuilder.getInfo(nextSeq())) ?: return null
        return ResponseParser.parseInfo(frame.payload)
    }

    suspend fun sendSetTime(unixTimestamp: Long): Boolean {
        val frame = sendAndWait(CommandBuilder.setTime(nextSeq(), unixTimestamp)) ?: return false
        return frame.payload.isNotEmpty() && frame.payload[0].toInt() == ProtocolConstants.STATUS_OK
    }

    suspend fun sendGetConfig(): DeviceConfig? {
        val frame = sendAndWait(CommandBuilder.getConfig(nextSeq())) ?: return null
        return ResponseParser.parseConfig(frame.payload)
    }

    suspend fun sendSetConfig(config: DeviceConfig): Boolean {
        val frame = sendAndWait(CommandBuilder.setConfig(nextSeq(), config)) ?: return false
        return frame.payload.isNotEmpty() && frame.payload[0].toInt() == ProtocolConstants.STATUS_OK
    }

    suspend fun sendGetData(startIndex: Int, count: Int) {
        _transferProgress.value = TransferProgress(
            totalRecords = count,
            downloadedRecords = 0,
            totalChunks = (count + 6) / 7,
            currentChunk = 0,
            isComplete = false
        )
        sendFrame(CommandBuilder.getData(nextSeq(), startIndex, count))
    }

    suspend fun sendEraseData(): Boolean {
        val frame = sendAndWait(CommandBuilder.eraseData(nextSeq())) ?: return false
        return frame.payload.isNotEmpty() && frame.payload[0].toInt() == ProtocolConstants.STATUS_OK
    }

    suspend fun sendGetStatus(): ResponseParser.StatusResponse? {
        val frame = sendAndWait(CommandBuilder.getStatus(nextSeq())) ?: return null
        return ResponseParser.parseStatus(frame.payload)
    }

    suspend fun sendAck(chunkIndex: Int, status: Byte) {
        sendFrame(CommandBuilder.dataAck(nextSeq(), chunkIndex, status))
    }

    private suspend fun sendAndWait(data: ByteArray, timeoutMs: Long = ProtocolConstants.TIMEOUT_MS): FrameParser.Frame? {
        pendingCommand = CompletableDeferred()
        sendFrame(data)
        return try {
            withTimeoutOrNull(timeoutMs) {
                pendingCommand?.await()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun nextSeq(): Int {
        return seqCounter++ and 0xFF
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
