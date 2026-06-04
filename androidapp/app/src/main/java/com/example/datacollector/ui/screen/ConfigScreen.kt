package com.example.datacollector.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.datacollector.domain.model.DeviceConfig
import com.example.datacollector.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    macAddress: String,
    onBack: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val config by viewModel.currentConfig.collectAsState()

    var interval by remember { mutableStateOf("60") }
    var addr by remember { mutableStateOf("1") }
    var startReg by remember { mutableStateOf("0") }
    var regCount by remember { mutableStateOf("4") }
    var dataType by remember { mutableStateOf(0) }
    var baudrate by remember { mutableStateOf(9600) }
    var parity by remember { mutableStateOf(0) }

    LaunchedEffect(config) {
        config?.let {
            interval = it.samplingIntervalSec.toString()
            addr = it.sensorAddr.toString()
            startReg = it.sensorStartReg.toString()
            regCount = it.sensorRegCount.toString()
            dataType = it.sensorDataType
            baudrate = it.modbusBaudrate
            parity = it.modbusParity
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("参数配置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("采样设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = interval, onValueChange = { interval = it }, label = { Text("采样间隔 (秒)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("传感器参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Modbus从站地址") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = startReg, onValueChange = { startReg = it }, label = { Text("起始寄存器地址") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = regCount, onValueChange = { regCount = it }, label = { Text("读取寄存器数量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }
                    val dataTypes = listOf("UINT16", "INT16", "UINT32", "FLOAT32", "RAW")
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(value = dataTypes.getOrElse(dataType) { "UINT16" }, onValueChange = {}, readOnly = true,
                            label = { Text("数据类型") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            dataTypes.forEachIndexed { index, type ->
                                DropdownMenuItem(text = { Text(type) }, onClick = { dataType = index; expanded = false })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("通信参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    var baudExpanded by remember { mutableStateOf(false) }
                    val baudrates = listOf(1200, 2400, 4800, 9600, 19200, 38400, 115200)
                    ExposedDropdownMenuBox(expanded = baudExpanded, onExpandedChange = { baudExpanded = !baudExpanded }) {
                        OutlinedTextField(value = baudrate.toString(), onValueChange = {}, readOnly = true,
                            label = { Text("Modbus波特率") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(baudExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor())
                        ExposedDropdownMenu(expanded = baudExpanded, onDismissRequest = { baudExpanded = false }) {
                            baudrates.forEach { b ->
                                DropdownMenuItem(text = { Text(b.toString()) }, onClick = { baudrate = b; baudExpanded = false })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    var parityExpanded by remember { mutableStateOf(false) }
                    val parities = listOf("无", "奇", "偶")
                    ExposedDropdownMenuBox(expanded = parityExpanded, onExpandedChange = { parityExpanded = !parityExpanded }) {
                        OutlinedTextField(value = parities.getOrElse(parity) { "无" }, onValueChange = {}, readOnly = true,
                            label = { Text("校验位") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parityExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor())
                        ExposedDropdownMenu(expanded = parityExpanded, onDismissRequest = { parityExpanded = false }) {
                            parities.forEachIndexed { index, p ->
                                DropdownMenuItem(text = { Text(p) }, onClick = { parity = index; parityExpanded = false })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.readConfig() }, modifier = Modifier.weight(1f)) { Text("读取当前配置") }
                Button(onClick = {
                    val newConfig = DeviceConfig(
                        samplingIntervalSec = interval.toLongOrNull() ?: 60,
                        sensorAddr = addr.toIntOrNull() ?: 1,
                        sensorStartReg = startReg.toIntOrNull() ?: 0,
                        sensorRegCount = regCount.toIntOrNull() ?: 4,
                        sensorDataType = dataType,
                        modbusBaudrate = baudrate,
                        modbusParity = parity
                    )
                    viewModel.saveConfig(newConfig)
                }, modifier = Modifier.weight(1f)) { Text("保存到设备") }
            }
        }
    }
}
