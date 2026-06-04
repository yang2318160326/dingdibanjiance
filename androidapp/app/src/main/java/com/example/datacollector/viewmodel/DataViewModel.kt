package com.example.datacollector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.domain.model.SensorRecord
import com.example.datacollector.domain.model.TransferProgress
import com.example.datacollector.domain.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    private val bleManager: BleConnectionManager,
    private val dataRepository: DataRepository
) : ViewModel() {

    private val _records = MutableStateFlow<List<SensorRecord>>(emptyList())
    val records: StateFlow<List<SensorRecord>> = _records.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    init {
        viewModelScope.launch {
            bleManager.transferProgress.collect { progress ->
                if (progress.totalRecords > 0) _transferProgress.value = progress
            }
        }
    }

    fun loadRecords(macAddress: String) {
        viewModelScope.launch {
            dataRepository.getRecordsByDevice(macAddress).collect { records ->
                _records.value = records
                _totalCount.value = records.size
            }
        }
    }

    fun downloadData(macAddress: String) {
        viewModelScope.launch {
            val info = bleManager.sendGetInfo() ?: return@launch
            val existingCount = dataRepository.getRecordCount(macAddress)
            val remaining = (info.recordCount - existingCount).toInt()

            if (remaining <= 0) {
                _transferProgress.value = TransferProgress(0, 0, 0, 0, true)
                return@launch
            }

            bleManager.onDataFragment = { _, records ->
                viewModelScope.launch { dataRepository.insertRecords(macAddress, records) }
            }

            bleManager.sendGetData(existingCount, remaining)
            _transferProgress.value = TransferProgress(remaining, 0, (remaining + 6) / 7, 0, false)
        }
    }

    fun clearData(macAddress: String) {
        viewModelScope.launch {
            dataRepository.clearRecords(macAddress)
            _records.value = emptyList()
            _totalCount.value = 0
        }
    }
}
