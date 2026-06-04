package com.example.datacollector.ui.screen

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    LaunchedEffect(Unit) {
        if (!BlePermissionHelper.hasPermissions(context)) {
            BlePermissionHelper.requestPermissions(context as Activity)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("数据采集系统") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (connectionState) {
                is BleConnectionState.Scanning -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("正在扫描...", style = MaterialTheme.typography.bodyMedium)
                }
                is BleConnectionState.Connected -> {
                    Text("已连接: ${(connectionState as BleConnectionState.Connected).deviceName}",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                is BleConnectionState.Error -> {
                    Text("错误: ${(connectionState as BleConnectionState.Error).message}",
                        color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (scanResults.isNotEmpty()) {
                Text("可用设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(scanResults) { device ->
                        ScanDeviceCard(device = device, onClick = {
                            viewModel.connect(device.macAddress)
                            onDeviceClick(device.macAddress)
                        })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            if (knownDevices.isNotEmpty()) {
                Text("已知设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(knownDevices) { device ->
                        KnownDeviceCard(device = device, onClick = {
                            viewModel.connect(device.macAddress)
                            onDeviceClick(device.macAddress)
                        })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.startScan() },
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionState !is BleConnectionState.Scanning
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (connectionState is BleConnectionState.Scanning) "扫描中..." else "开始扫描")
            }
        }
    }
}

@Composable
fun ScanDeviceCard(device: BleScanResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Bold)
                Text(device.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("RSSI: ${device.rssi}dBm", style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onClick) { Text("连接") }
        }
    }
}

@Composable
fun KnownDeviceCard(device: KnownDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.customName, fontWeight = FontWeight.Bold)
                Text(device.macAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("记录数: ${device.recordCount}", style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onClick) { Text("连接") }
        }
    }
}
