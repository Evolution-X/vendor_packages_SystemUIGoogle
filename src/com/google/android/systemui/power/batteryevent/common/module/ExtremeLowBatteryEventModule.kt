package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import com.android.settingslib.fuelgauge.BatteryStatus
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class ExtremeLowBatteryEventModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.EXTREME_LOW_BATTERY

    override val intentActions = listOf(Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes = emptyList<EventDataType>()

    override fun validate(systemEventData: SystemEventData): Boolean {
        val level = systemEventData.batteryLevel
        val scale = systemEventData.batteryScale
        val plugged = systemEventData.plugged
        if (level.isChanged || plugged.isChanged || scale.isChanged) {
            lastValidation =
                BatteryStatus.getBatteryLevel(level.value, scale.value) <= 3 &&
                    !BatteryStatus.isPluggedIn(plugged.value)
        }
        return lastValidation
    }
}
