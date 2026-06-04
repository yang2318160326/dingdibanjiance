package com.example.datacollector.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.datacollector.domain.model.SensorRecord
import com.example.datacollector.domain.model.TransferProgress
import com.example.datacollector.util.DateTimeUtils
import com.example.datacollector.viewmodel.DataViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    macAddress: String,
    onChartClick: () -> Unit,
    onExportClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()

    LaunchedEffect(macAddress) { viewModel.loadRecords(macAddress) }

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

            if (transferProgress != null && transferProgress!!.totalRecords > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("下载进度: ${transferProgress!!.downloadedRecords}/${transferProgress!!.totalRecords}")
                        LinearProgressIndicator(progress = transferProgress!!.progress, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.downloadData(macAddress) }, modifier = Modifier.weight(1f),
                    enabled = transferProgress?.isComplete != false) { Text("下载数据") }
                OutlinedButton(onClick = { viewModel.clearData(macAddress) }, modifier = Modifier.weight(1f)) { Text("清空") }
                OutlinedButton(onClick = onExportClick, modifier = Modifier.weight(1f)) { Text("导出") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(records) { record ->
                    RecordItem(record)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
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
