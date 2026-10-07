package com.google.android.systemui.power.batteryevent.domain

import com.android.settingslib.fuelgauge.BatteryStatus
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.BatteryEventSubscriber
import com.google.android.systemui.power.batteryevent.common.BatteryEvents
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData
import com.google.android.systemui.power.batteryevent.common.module.BatteryEventModuleProvider
import com.google.android.systemui.power.batteryevent.repository.SystemEventDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SysUISingleton
class BatteryEventStateController
@Inject
constructor(
    private val batteryEventModuleProvider: BatteryEventModuleProvider,
    systemEventDataSource: SystemEventDataSource,
) {
    private val mutableBatteryEventsFlow = MutableStateFlow<BatteryEvents?>(null)
    val batteryEventsFlow: StateFlow<BatteryEvents?> = mutableBatteryEventsFlow.asStateFlow()

    init {
        val subscribers =
            batteryEventModuleProvider.eventModuleList.map {
                BatteryEventSubscriber(it.moduleType, it.intentActions, it.eventDataTypes)
            }
        systemEventDataSource.subscribe(subscribers, ::onEventSourceUpdate)
    }

    private fun onEventSourceUpdate(
        systemEventData: SystemEventData,
        eventTypes: List<BatteryEventType>,
    ) {
        val batteryLevel =
            BatteryStatus.getBatteryLevel(
                systemEventData.batteryLevel.value,
                systemEventData.batteryScale.value,
            )
        val validatedEventTypes =
            batteryEventModuleProvider.eventModuleList
                .filter { eventTypes.contains(it.moduleType) && it.validate(systemEventData) }
                .map { it.moduleType }
                .toSet()
        mutableBatteryEventsFlow.value =
            BatteryEvents(validatedEventTypes, batteryLevel, systemEventData.plugged.value)
    }
}
