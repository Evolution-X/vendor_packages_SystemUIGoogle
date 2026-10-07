package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import android.os.BatteryManager
import com.android.settingslib.fuelgauge.BatteryStatus
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class FullChargedEventModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.FULL_CHARGED

    override val intentActions = listOf(Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes = emptyList<EventDataType>()

    override fun validate(systemEventData: SystemEventData): Boolean {
        val plugged = systemEventData.plugged
        val status = systemEventData.batteryStatus
        val scale = systemEventData.batteryScale
        val level = systemEventData.batteryLevel
        if (plugged.isChanged || status.isChanged || scale.isChanged || level.isChanged) {
            lastValidation =
                BatteryStatus.isPluggedIn(plugged.value) &&
                    (status.value == BatteryManager.BATTERY_STATUS_FULL ||
                        BatteryStatus.getBatteryLevel(level.value, scale.value) >= 100)
        }
        return lastValidation
    }
}
