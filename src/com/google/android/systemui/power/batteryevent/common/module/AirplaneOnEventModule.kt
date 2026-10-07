package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.SettingsDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class AirplaneOnEventModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.AIRPLANE_ON

    override val intentActions = listOf(Intent.ACTION_AIRPLANE_MODE_CHANGED)

    override val eventDataTypes: List<EventDataType> = listOf(SettingsDataType.AIRPLANE_STATE)

    override fun validate(systemEventData: SystemEventData): Boolean =
        systemEventData.settingsEventData.airplaneState.value
}
