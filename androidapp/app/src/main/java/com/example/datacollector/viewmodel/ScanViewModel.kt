package com.example.datacollector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.ble.BleConnectionState
import com.example.datacollector.ble.BleScanResult
import com.example.datacollector.domain.model.KnownDevice
import com.example.datacollector.domain.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleManager: BleConnectionManager,
    private val dataRepository: DataRepository
) : ViewModel() {

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleConnectionState.Disconnected)

    val scanResults: StateFlow<List<BleScanResult>> = bleManager.scanResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knownDevices: StateFlow<List<KnownDevice>> = dataRepository.getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startScan() { bleManager.startScan() }
    fun stopScan() { bleManager.stopScan() }
    fun connect(macAddress: String) { viewModelScope.launch { bleManager.connect(macAddress) } }
    fun disconnect() { bleManager.disconnect() }
}
