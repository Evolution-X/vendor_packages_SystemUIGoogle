package com.google.android.systemui.power.batteryevent.common.dagger

import android.app.Service
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.google.android.systemui.power.batteryevent.domain.BatteryEventService
import com.google.android.systemui.power.batteryevent.repository.BatteryTimePredictionRepository
import com.google.android.systemui.power.batteryevent.repository.FrameworkDataSource
import com.google.android.systemui.power.batteryevent.repository.HalDataSource
import com.google.android.systemui.power.batteryevent.repository.NoOpBatteryTimePredictionRepository
import com.google.android.systemui.power.batteryevent.repository.SettingsDataSource
import com.google.android.systemui.power.batteryevent.repository.SystemEventDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Module
interface EventFrameworkModule {
    @Binds
    @IntoMap
    @ClassKey(BatteryEventService::class)
    fun bindBatteryEventService(service: BatteryEventService): Service

    @Binds
    fun bindBatteryTimePredictionRepository(
        repository: NoOpBatteryTimePredictionRepository
    ): BatteryTimePredictionRepository

    companion object {
        @Provides @SysUISingleton fun provideHalDataSource(): HalDataSource = HalDataSource()

        @Provides
        @SysUISingleton
        fun provideSystemEventDataSource(
            halDataSource: HalDataSource,
            settingsDataSource: SettingsDataSource,
            frameworkDataSource: FrameworkDataSource,
            broadcastDispatcher: BroadcastDispatcher,
            @Background backgroundDispatcher: CoroutineDispatcher,
            @Application applicationScope: CoroutineScope,
        ): SystemEventDataSource =
            SystemEventDataSource(
                halDataSource,
                settingsDataSource,
                frameworkDataSource,
                broadcastDispatcher,
                backgroundDispatcher,
                applicationScope,
            )
    }
}
