package com.example.datacollector.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.datacollector.ble.BleConnectionState
import com.example.datacollector.ui.theme.StatusOk
import com.example.datacollector.util.DateTimeUtils
import com.example.datacollector.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    macAddress: String,
    onDailyConfigClick: () -> Unit,
    onDebugConfigClick: () -> Unit,
    onDataClick: () -> Unit,
    onDisconnect: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val deviceStatus by viewModel.deviceStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val currentConfig by viewModel.currentConfig.collectAsState()

    LaunchedEffect(macAddress) { viewModel.loadDeviceInfo(macAddress) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备信息") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.disconnect(); onDisconnect() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (connectionState is BleConnectionState.Connected) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = StatusOk, modifier = Modifier.padding(end = 8.dp))
                        Text("已连接", color = StatusOk, modifier = Modifier.padding(end = 16.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 设备基本信息
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("设备信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    deviceInfo?.let { info ->
                        InfoRow("固件地址", macAddress)
                        InfoRow("设备ID", "0x${info.deviceId.toString(16).uppercase()}")
                        InfoRow("固件版本", info.firmwareVersion)
                        InfoRow("分机号", info.deviceId.toString())
                    } ?: Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 当前时间
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("手机时间: ${DateTimeUtils.formatTimestampMs(System.currentTimeMillis())}")
                    deviceStatus?.let {
                        // 显示设备时间（如果有）
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.syncTime() }) { Text("同步时间到设备") }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 存储状态
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("存储状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    deviceInfo?.let { info ->
                        val total = info.recordCount + info.freeSpace
                        val usedPercent = if (total > 0) info.recordCount.toFloat() / total else 0f
                        InfoRow("已记录", "${info.recordCount} 条")
                        InfoRow("剩余空间", "${info.freeSpace} 条")
                        InfoRow("总容量", "$total 条")
                        LinearProgressIndicator(
                            progress = { usedPercent },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        Text("使用率: ${(usedPercent * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 设备状态
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("设备状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    deviceStatus?.let { status ->
                        val stateText = when (status.state) {
                            0 -> "空闲"; 1 -> "采集中"; 2 -> "BLE传输中"; 3 -> "睡眠"; else -> "未知"
                        }
                        val stateColor = when (status.state) {
                            1 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        InfoRow("运行状态", stateText)
                        Text("状态颜色", color = stateColor, modifier = Modifier.height(0.dp)) // 隐藏
                        InfoRow("错误码", if (status.errorCode == 0) "正常" else "错误: ${status.errorCode}")
                        InfoRow("下次采集", "${status.nextReadIn}秒后")
                    } ?: Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 传感器状态
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("传感器状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    currentConfig?.let { config ->
                        InfoRow("从站地址", config.sensorAddr.toString())
                        InfoRow("起始寄存器", "0x${config.sensorStartReg.toString(16).uppercase()}")
                        InfoRow("寄存器数量", config.sensorRegCount.toString())
                        val dataTypeText = when (config.sensorDataType) {
                            0 -> "UINT16"; 1 -> "INT16"; 2 -> "UINT32"; 3 -> "FLOAT32"; 4 -> "RAW"; else -> "未知"
                        }
                        InfoRow("数据类型", dataTypeText)
                        InfoRow("波特率", config.modbusBaudrate.toString())
                        val parityText = when (config.modbusParity) {
                            0 -> "无校验"; 1 -> "奇校验"; 2 -> "偶校验"; else -> "未知"
                        }
                        InfoRow("校验位", parityText)
                    } ?: Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(8.dp))

                    // 传感器位移数值（模拟显示）
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("当前传感器位移数值", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "-- mm",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("(连接设备后实时显示)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 两个设置入口
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDailyConfigClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("日常设置")
                }
                OutlinedButton(
                    onClick = onDebugConfigClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("调试设置")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onDataClick, modifier = Modifier.fillMaxWidth()) { Text("下载数据") }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.disconnect(); onDisconnect() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("断开连接") }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
