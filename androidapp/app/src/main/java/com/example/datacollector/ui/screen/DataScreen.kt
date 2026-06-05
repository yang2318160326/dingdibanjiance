package com.example.datacollector.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.datacollector.domain.model.SensorRecord
import com.example.datacollector.domain.model.TransferProgress
import com.example.datacollector.util.DateTimeUtils
import com.example.datacollector.viewmodel.DataViewModel
import com.example.datacollector.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    macAddress: String,
    onChartClick: () -> Unit,
    onExportClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel(),
    deviceViewModel: DeviceViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val downloadProgress by deviceViewModel.downloadProgress.collectAsState()
    val operationResult by deviceViewModel.operationResult.collectAsState()

    var showResultDialog by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(macAddress) { viewModel.loadRecords(macAddress) }

    LaunchedEffect(operationResult) {
        operationResult?.let { result ->
            resultTitle = if (result.isSuccess) "操作成功" else "操作失败"
            resultMessage = result.message
            isSuccess = result.isSuccess
            showResultDialog = true
            deviceViewModel.clearOperationResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据管理") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("当前设备: $macAddress", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("已下载: $totalCount 条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // 下载进度
            if (downloadProgress != null && downloadProgress!!.totalRecords > 0 && !downloadProgress!!.isComplete) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在下载...", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "进度: ${downloadProgress!!.downloadedRecords}/${downloadProgress!!.totalRecords} 条",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress!!.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "分片: ${downloadProgress!!.currentChunk}/${downloadProgress!!.totalChunks}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 下载完成提示
            if (downloadProgress != null && downloadProgress!!.isComplete) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("下载完成！", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { deviceViewModel.downloadData(macAddress) },
                    modifier = Modifier.weight(1f),
                    enabled = downloadProgress?.isComplete != false || downloadProgress == null
                ) { Text("下载数据") }
                OutlinedButton(onClick = { showClearDialog = true }, modifier = Modifier.weight(1f)) { Text("清空") }
                OutlinedButton(onClick = onExportClick, modifier = Modifier.weight(1f)) { Text("导出") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(records) { record ->
                        RecordItem(record)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    // 操作结果弹窗
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            icon = {
                Icon(
                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text(resultTitle, color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) },
            text = { Text(resultMessage) },
            confirmButton = {
                Button(onClick = { showResultDialog = false }) { Text("确定") }
            }
        )
    }

    // 清空数据确认弹窗
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清空本地数据") },
            text = {
                Column {
                    Text("此操作将删除手机上已下载的所有数据记录")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("设备上的原始数据不会受影响", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearData(macAddress); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认清空") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun RecordItem(record: SensorRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DateTimeUtils.formatTimestamp(record.timestamp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(if (record.status == 0) "OK" else "ERR:${record.status}",
                    color = if (record.status == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("Addr:${record.sensorAddress}  Seq:${record.sequenceNum}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(record.registerValues.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
