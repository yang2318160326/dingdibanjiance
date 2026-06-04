package com.example.datacollector.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.domain.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val dataRepository: DataRepository
) : ViewModel() {

    fun export(macAddress: String, format: String, onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            try {
                val uri = when (format) {
                    "csv" -> dataRepository.exportCsv(macAddress, null, null)
                    "json" -> dataRepository.exportJson(macAddress, null, null)
                    else -> null
                }
                onResult(uri)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
}
