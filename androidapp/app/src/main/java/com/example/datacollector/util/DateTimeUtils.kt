package com.example.datacollector.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun formatTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp * 1000))
    }

    fun formatTimestampMs(timestampMs: Long): String {
        return dateFormat.format(Date(timestampMs))
    }

    fun nowUnixSeconds(): Long {
        return System.currentTimeMillis() / 1000
    }

    fun nowUnixMillis(): Long {
        return System.currentTimeMillis()
    }
}
