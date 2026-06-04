package com.example.datacollector.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
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
    onConfigClick: () -> Unit,
    onDataClick: () -> Unit,
    onDisconnect: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val deviceStatus by viewModel.deviceStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("设备信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    deviceInfo?.let { info ->
                        InfoRow("设备名称", info.deviceName)
                        InfoRow("设备ID", "0x${info.deviceId.toString(16).uppercase()}")
                        InfoRow("固件版本", info.firmwareVersion)
                        InfoRow("MAC地址", macAddress)
                    } ?: Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("存储状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    deviceInfo?.let { info ->
                        val total = info.recordCount + info.freeSpace
                        val usedPercent = if (total > 0) info.recordCount.toFloat() / total else 0f
                        Text("已记录: ${info.recordCount} 条")
                        Text("总容量: $total 条")
                        LinearProgressIndicator(progress = usedPercent, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        Text("${(usedPercent * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("采集状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    deviceStatus?.let { status ->
                        val stateText = when (status.state) { 0 -> "空闲"; 1 -> "采集中"; 2 -> "BLE传输中"; 3 -> "睡眠"; else -> "未知" }
                        InfoRow("状态", stateText)
                        InfoRow("错误码", if (status.errorCode == 0) "正常" else "错误: ${status.errorCode}")
                        InfoRow("下次采集", "${status.nextReadIn}秒后")
                    } ?: Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("时间同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("当前手机时间: ${DateTimeUtils.formatTimestampMs(System.currentTimeMillis())}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.syncTime() }) { Text("同步时间") }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onConfigClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("参数配置")
                }
                Button(onClick = onDataClick, modifier = Modifier.weight(1f)) { Text("下载数据") }
            }

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
