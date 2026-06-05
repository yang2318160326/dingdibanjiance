/**
 * 数据导出视图模型层
 *
 * 本文件定义了数据导出相关的 ViewModel，负责将传感器数据
 * 导出为 CSV 或 JSON 格式的文件，并返回文件 URI 供用户分享或保存。
 */
package com.example.datacollector.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datacollector.domain.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 数据导出视图模型（ExportViewModel）
 *
 * 负责将指定设备的传感器数据记录导出为文件。
 * 支持以下导出格式：
 * - CSV（逗号分隔值）：适合用 Excel 等表格软件打开
 * - JSON（JavaScript 对象表示法）：适合程序化处理和数据分析
 *
 * 导出过程在协程中异步执行，完成后通过回调函数返回文件的 URI。
 *
 * @param dataRepository 数据仓库，负责读取数据记录并执行导出操作
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val dataRepository: DataRepository
) : ViewModel() {

    /**
     * 导出设备数据为文件
     *
     * 根据指定的格式将设备的传感器数据导出为文件。
     * 导出成功时回调返回文件的 content URI，失败时返回 null。
     *
     * @param macAddress 设备的蓝牙 MAC 地址，标识要导出数据的设备
     * @param format 导出格式，支持 "csv" 和 "json" 两种
     * @param onResult 导出结果回调函数，成功时传入文件 URI，失败时传入 null
     */
    fun export(macAddress: String, format: String, onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            try {
                // 根据格式调用对应的导出方法
                val uri = when (format) {
                    "csv" -> dataRepository.exportCsv(macAddress, null, null)
                    "json" -> dataRepository.exportJson(macAddress, null, null)
                    else -> null
                }
                // 通过回调返回导出结果
                onResult(uri)
            } catch (e: Exception) {
                // 导出异常时回调返回 null
                onResult(null)
            }
        }
    }
}
