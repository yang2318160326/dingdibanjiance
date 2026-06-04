package com.example.datacollector.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.datacollector.ui.theme.Blue40
import com.example.datacollector.ui.theme.Teal40
import com.example.datacollector.viewmodel.DataViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    macAddress: String,
    onBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()

    LaunchedEffect(macAddress) { viewModel.loadRecords(macAddress) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据图表") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("寄存器值趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val sortedRecords = records.sortedBy { it.timestamp }
                val maxValue = sortedRecords.maxOfOrNull { it.registerValues.getOrElse(0) { 0 } } ?: 1
                val minValue = sortedRecords.minOfOrNull { it.registerValues.getOrElse(0) { 0 } } ?: 0
                val range = (maxValue - minValue).coerceAtLeast(1)

                Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        val points = sortedRecords.mapIndexed { index, record ->
                            val x = index.toFloat() / (sortedRecords.size - 1).coerceAtLeast(1) * size.width
                            val y = size.height - ((record.registerValues.getOrElse(0) { 0 } - minValue).toFloat() / range * size.height)
                            Offset(x, y)
                        }

                        if (points.size >= 2) {
                            val path = Path()
                            path.moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) { path.lineTo(points[i].x, points[i].y) }
                            drawPath(path, Blue40, style = Stroke(width = 3f))
                            points.forEach { point -> drawCircle(Teal40, radius = 5f, center = point) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("共 ${sortedRecords.size} 条记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
