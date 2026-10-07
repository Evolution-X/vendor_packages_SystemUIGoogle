package com.google.android.systemui.power.batteryevent.common.module

import android.app.NotificationManager
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.SettingsDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class DndOnEventModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.DND_ON

    override val intentActions = listOf(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)

    override val eventDataTypes: List<EventDataType> = listOf(SettingsDataType.DND_STATE)

    override fun validate(systemEventData: SystemEventData): Boolean =
        systemEventData.settingsEventData.dndState.value
}
