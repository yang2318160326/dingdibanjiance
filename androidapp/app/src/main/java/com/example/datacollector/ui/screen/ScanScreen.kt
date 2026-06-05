package com.example.datacollector.ui.screen

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.datacollector.ble.BleConnectionState
import com.example.datacollector.ble.BleScanResult
import com.example.datacollector.domain.model.KnownDevice
import com.example.datacollector.util.BlePermissionHelper
import com.example.datacollector.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val knownDevices by viewModel.knownDevices.collectAsState()
    var isScanOverlayExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (!BlePermissionHelper.hasPermissions(context)) {
            BlePermissionHelper.requestPermissions(context as Activity)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("数据采集系统") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 底层：引导内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "数据采集系统",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "BLE工业数据采集与管理",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FeatureItem(Icons.Default.Bluetooth, "扫描设备", "自动搜索附近的BLE数据采集器")
                        FeatureItem(Icons.Default.CheckCircle, "连接管理", "一键连接，支持多设备切换")
                        FeatureItem(Icons.Default.Sensors, "数据采集", "实时采集传感器数据")
                        FeatureItem(Icons.Default.Settings, "参数配置", "远程配置采集参数")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "点击下方按钮开始扫描设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 浮层：扫描状态和结果（可最小化）
            AnimatedVisibility(
                visible = connectionState is BleConnectionState.Scanning || scanResults.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 标题栏：可最小化
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "扫描结果 (${scanResults.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (connectionState is BleConnectionState.Scanning) {
                                // 暂停按钮
                                IconButton(onClick = { viewModel.pauseScan() }) {
                                    Icon(Icons.Default.Stop, contentDescription = "暂停扫描", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            // 最小化按钮
                            IconButton(onClick = { isScanOverlayExpanded = !isScanOverlayExpanded }) {
                                Icon(Icons.Default.Settings, contentDescription = "最小化")
                            }
                        }

                        // 扫描进度
                        if (connectionState is BleConnectionState.Scanning) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "正在扫描... 发现 ${scanResults.size} 个设备",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 展开的内容
                        AnimatedVisibility(visible = isScanOverlayExpanded) {
                            Column {
                                // 已连接状态
                                if (connectionState is BleConnectionState.Connected) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "已连接: ${(connectionState as BleConnectionState.Connected).deviceName}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // 扫描结果列表
                                if (scanResults.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 300.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(scanResults) { device ->
                                            ScanDeviceCard(device = device, onClick = {
                                                viewModel.connect(device.macAddress)
                                                onDeviceClick(device.macAddress)
                                            })
                                        }
                                    }
                                }

                                // 已知设备
                                if (knownDevices.isNotEmpty() && scanResults.isEmpty() && connectionState !is BleConnectionState.Scanning) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("已知设备", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 200.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(knownDevices) { device ->
                                            KnownDeviceCard(device = device, onClick = {
                                                viewModel.connect(device.macAddress)
                                                onDeviceClick(device.macAddress)
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部扫描/停止按钮
            Button(
                onClick = {
                    if (connectionState is BleConnectionState.Scanning) {
                        viewModel.stopScan()
                    } else {
                        viewModel.startScan()
                        isScanOverlayExpanded = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                colors = if (connectionState is BleConnectionState.Scanning)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    if (connectionState is BleConnectionState.Scanning) Icons.Default.Stop else Icons.Default.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (connectionState is BleConnectionState.Scanning) "停止扫描" else "开始扫描",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun FeatureItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ScanDeviceCard(device: BleScanResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(device.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("RSSI: ${device.rssi}dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onClick) { Text("连接") }
        }
    }
}

@Composable
fun KnownDeviceCard(device: KnownDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.customName, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(device.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("记录数: ${device.recordCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onClick) { Text("连接") }
        }
    }
}
