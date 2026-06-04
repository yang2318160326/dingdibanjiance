package com.example.datacollector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.datacollector.data.local.dao.ConfigDao
import com.example.datacollector.data.local.dao.DeviceDao
import com.example.datacollector.data.local.dao.RecordDao
import com.example.datacollector.data.local.entity.ConfigEntity
import com.example.datacollector.data.local.entity.DeviceEntity
import com.example.datacollector.data.local.entity.RecordEntity

@Database(
    entities = [RecordEntity::class, ConfigEntity::class, DeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun configDao(): ConfigDao
    abstract fun deviceDao(): DeviceDao
}
