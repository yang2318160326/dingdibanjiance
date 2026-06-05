package com.example.datacollector.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.datacollector.util.DateTimeUtils
import com.example.datacollector.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyConfigScreen(
    macAddress: String,
    onBack: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val deviceStatus by viewModel.deviceStatus.collectAsState()
    var deviceId by remember { mutableStateOf("0") }
    var intervalHour by remember { mutableStateOf("00") }
    var intervalMin by remember { mutableStateOf("01") }
    var showEraseDialog by remember { mutableStateOf(false) }
    var eraseConfirmText by remember { mutableStateOf("") }

    LaunchedEffect(deviceInfo) {
        deviceInfo?.let { deviceId = it.deviceId.toString() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日常设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
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
            // 分机号设置
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("分机号设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("设置下位机编号 (0-99)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = deviceId,
                            onValueChange = { if (it.length <= 2) deviceId = it.filter { c -> c.isDigit() } },
                            label = { Text("分机号") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = {
                            val id = deviceId.toIntOrNull() ?: 0
                            if (id in 0..99) viewModel.setDeviceId(id)
                        }) { Text("设置") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 采集间隔
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("采集间隔", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("设置数据采集的时间间隔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = intervalHour,
                            onValueChange = { if (it.length <= 2) intervalHour = it.filter { c -> c.isDigit() } },
                            label = { Text("小时") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp)
                        )
                        Text(" : ", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp))
                        OutlinedTextField(
                            value = intervalMin,
                            onValueChange = { if (it.length <= 2) intervalMin = it.filter { c -> c.isDigit() } },
                            label = { Text("分钟") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = {
                            val h = intervalHour.toIntOrNull() ?: 0
                            val m = intervalMin.toIntOrNull() ?: 0
                            viewModel.setSamplingInterval(h, m)
                        }) { Text("设置") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 启动/停止采集
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("采集控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val isCollecting = deviceStatus?.state == 1
                    Text(
                        if (isCollecting) "当前状态: 采集中" else "当前状态: 空闲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCollecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.startCollecting() },
                            enabled = !isCollecting
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("启动采集")
                        }
                        OutlinedButton(
                            onClick = { viewModel.stopCollecting() },
                            enabled = isCollecting,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("停止采集")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 时间同步
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("时间同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("将手机时间同步到下位机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("手机时间: ${DateTimeUtils.formatTimestampMs(System.currentTimeMillis())}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.syncTime() }) { Text("同步时间") }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 清除数据
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("清除数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("清空下位机所有存储的数据记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showEraseDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清除设备数据")
                    }
                }
            }
        }
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false; eraseConfirmText = "" },
            title = { Text("⚠️ 确认清除数据", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("警告：此操作不可恢复！", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• 将永久删除设备上的所有数据记录", style = MaterialTheme.typography.bodySmall)
                            Text("• 删除后无法恢复", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("请确保：", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• 所有数据已下载到手机", style = MaterialTheme.typography.bodySmall)
                            Text("• 数据已备份到本地", style = MaterialTheme.typography.bodySmall)
                            Text("• 确认不再需要这些数据", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("请输入 CONFIRM 确认操作:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = eraseConfirmText,
                        onValueChange = { eraseConfirmText = it },
                        label = { Text("输入 CONFIRM") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.eraseDeviceData(); showEraseDialog = false; eraseConfirmText = "" },
                    enabled = eraseConfirmText == "CONFIRM",
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认清除") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEraseDialog = false; eraseConfirmText = "" }) { Text("取消") }
            }
        )
    }
}
