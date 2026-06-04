package com.example.datacollector.di

import android.content.Context
import androidx.room.Room
import com.example.datacollector.ble.BleConnectionManager
import com.example.datacollector.data.local.AppDatabase
import com.example.datacollector.data.local.dao.ConfigDao
import com.example.datacollector.data.local.dao.DeviceDao
import com.example.datacollector.data.local.dao.RecordDao
import com.example.datacollector.data.repository.DataRepositoryImpl
import com.example.datacollector.domain.repository.DataRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "data_collector.db").build()
    }

    @Provides
    fun provideRecordDao(db: AppDatabase): RecordDao = db.recordDao()

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun provideConfigDao(db: AppDatabase): ConfigDao = db.configDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideBleConnectionManager(@ApplicationContext context: Context): BleConnectionManager {
        return BleConnectionManager(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDataRepository(impl: DataRepositoryImpl): DataRepository
}
