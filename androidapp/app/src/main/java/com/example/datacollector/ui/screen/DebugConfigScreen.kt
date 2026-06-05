package com.example.datacollector.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.datacollector.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConfigScreen(
    macAddress: String,
    onBack: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val currentConfig by viewModel.currentConfig.collectAsState()
    val deviceStatus by viewModel.deviceStatus.collectAsState()

    // 密码验证
    var isUnlocked by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    // 通信参数
    var baudrate by remember { mutableStateOf("9600") }
    var parity by remember { mutableStateOf("0") }

    // 传感器参数
    var slaveAddr by remember { mutableStateOf("1") }
    var startReg by remember { mutableStateOf("0") }
    var regCount by remember { mutableStateOf("4") }
    var dataType by remember { mutableStateOf("0") }

    // Modbus原始命令
    var modbusHex by remember { mutableStateOf("01 03 00 00 00 04") }
    var modbusResponse by remember { mutableStateOf("") }

    LaunchedEffect(currentConfig) {
        currentConfig?.let {
            baudrate = it.modbusBaudrate.toString()
            parity = it.modbusParity.toString()
            slaveAddr = it.sensorAddr.toString()
            startReg = it.sensorStartReg.toString()
            regCount = it.sensorRegCount.toString()
            dataType = it.sensorDataType.toString()
        }
    }

    // 如果未解锁，显示密码输入界面
    if (!isUnlocked) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("调试设置") },
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
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "调试设置需要密码",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "请输入调试密码以访问高级设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = false },
                    label = { Text("调试密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = passwordError,
                    supportingText = if (passwordError) {
                        { Text("密码错误，请重试") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (password == "2611") {
                            isUnlocked = true
                        } else {
                            passwordError = true
                            password = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("验证密码") }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("返回") }
            }
        }
    } else {
        // 密码验证通过，显示调试设置
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("调试设置") },
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
                // 读取所有配置按钮
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("设备状态读取", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("一键读取下位机和传感器所有状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.readDeviceStatus(); viewModel.readConfig() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("读取所有状态")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 通信参数
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("485通信参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        var baudExpanded by remember { mutableStateOf(false) }
                        val baudrates = listOf("1200", "2400", "4800", "9600", "19200", "38400", "115200")
                        ExposedDropdownMenuBox(expanded = baudExpanded, onExpandedChange = { baudExpanded = !baudExpanded }) {
                            OutlinedTextField(
                                value = baudrate,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("波特率") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(baudExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = baudExpanded, onDismissRequest = { baudExpanded = false }) {
                                baudrates.forEach { b ->
                                    DropdownMenuItem(text = { Text(b) }, onClick = { baudrate = b; baudExpanded = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        var parityExpanded by remember { mutableStateOf(false) }
                        val parities = listOf("无校验", "奇校验", "偶校验")
                        val parityValues = listOf("0", "1", "2")
                        ExposedDropdownMenuBox(expanded = parityExpanded, onExpandedChange = { parityExpanded = !parityExpanded }) {
                            OutlinedTextField(
                                value = parities.getOrElse(parity.toIntOrNull() ?: 0) { "无校验" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("校验位") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parityExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = parityExpanded, onDismissRequest = { parityExpanded = false }) {
                                parities.forEachIndexed { index, p ->
                                    DropdownMenuItem(text = { Text(p) }, onClick = { parity = parityValues[index]; parityExpanded = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.readConfig() }, modifier = Modifier.fillMaxWidth()) { Text("读取当前配置") }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 传感器参数
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("传感器参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = slaveAddr,
                            onValueChange = { slaveAddr = it.filter { c -> c.isDigit() } },
                            label = { Text("Modbus从站地址 (1-247)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = startReg,
                            onValueChange = { startReg = it.filter { c -> c.isDigit() } },
                            label = { Text("起始寄存器地址") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = regCount,
                            onValueChange = { regCount = it.filter { c -> c.isDigit() } },
                            label = { Text("读取寄存器数量") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var typeExpanded by remember { mutableStateOf(false) }
                        val dataTypes = listOf("UINT16", "INT16", "UINT32", "FLOAT32", "RAW")
                        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                            OutlinedTextField(
                                value = dataTypes.getOrElse(dataType.toIntOrNull() ?: 0) { "UINT16" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("数据类型") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                dataTypes.forEachIndexed { index, type ->
                                    DropdownMenuItem(text = { Text(type) }, onClick = { dataType = index.toString(); typeExpanded = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.saveConfig(
                                    com.example.datacollector.domain.model.DeviceConfig(
                                        samplingIntervalSec = currentConfig?.samplingIntervalSec ?: 60,
                                        sensorAddr = slaveAddr.toIntOrNull() ?: 1,
                                        sensorStartReg = startReg.toIntOrNull() ?: 0,
                                        sensorRegCount = regCount.toIntOrNull() ?: 4,
                                        sensorDataType = dataType.toIntOrNull() ?: 0,
                                        modbusBaudrate = baudrate.toIntOrNull() ?: 9600,
                                        modbusParity = parity.toIntOrNull() ?: 0
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("保存传感器配置") }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Modbus原始命令
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Modbus原始命令", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("手动发送485 Modbus数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = modbusHex,
                            onValueChange = { modbusHex = it },
                            label = { Text("HEX数据 (空格分隔)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("01 03 00 00 00 04") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.sendRawModbus(modbusHex) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("发送")
                            }
                            OutlinedButton(
                                onClick = { modbusResponse = "" },
                                modifier = Modifier.weight(1f)
                            ) { Text("清空") }
                        }

                        if (modbusResponse.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("响应:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(modbusResponse, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 设备控制
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("设备控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.readDeviceStatus() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("读取状态")
                            }
                            OutlinedButton(
                                onClick = { viewModel.rebootDevice() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("重启设备") }
                        }
                    }
                }
            }
        }
    }
}
