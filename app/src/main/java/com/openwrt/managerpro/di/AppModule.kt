package com.openwrt.managerpro.di

import android.content.Context
import com.openwrt.managerpro.data.oui.OuiDatabase
import com.openwrt.managerpro.data.repository.DeviceRepositoryImpl
import com.openwrt.managerpro.data.repository.FirmwareRepositoryImpl
import com.openwrt.managerpro.data.repository.RouterRepositoryImpl
import com.openwrt.managerpro.domain.DeviceRepository
import com.openwrt.managerpro.domain.FirmwareRepository
import com.openwrt.managerpro.domain.RouterRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideOuiDatabase(@ApplicationContext context: Context): OuiDatabase {
        val lines = runCatching {
            context.assets.open("oui.txt").bufferedReader().use { it.readLines() }
        }.getOrDefault(emptyList())
        return OuiDatabase(OuiDatabase.parse(lines))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRouterRepository(impl: RouterRepositoryImpl): RouterRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindFirmwareRepository(impl: FirmwareRepositoryImpl): FirmwareRepository
}
