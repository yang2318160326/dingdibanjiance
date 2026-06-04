package com.example.datacollector.domain.model

data class TransferProgress(
    val totalRecords: Int,
    val downloadedRecords: Int,
    val totalChunks: Int,
    val currentChunk: Int,
    val isComplete: Boolean,
    val error: String? = null
) {
    val progress: Float
        get() = if (totalRecords > 0) downloadedRecords.toFloat() / totalRecords else 0f
}
