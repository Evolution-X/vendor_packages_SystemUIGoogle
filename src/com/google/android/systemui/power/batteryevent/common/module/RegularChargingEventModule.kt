package com.google.android.systemui.power.batteryevent.common.module

import android.content.Context
import android.content.Intent
import com.android.settingslib.fuelgauge.BatteryStatus
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class RegularChargingEventModule(private val context: Context) : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.REGULAR_CHARGING

    override val intentActions = listOf(Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes = emptyList<EventDataType>()

    override fun validate(systemEventData: SystemEventData): Boolean {
        val plugged = systemEventData.plugged
        val maxCurrent = systemEventData.maxChargingCurrent
        val maxVoltage = systemEventData.maxChargingVoltage
        if (plugged.isChanged || maxCurrent.isChanged || maxVoltage.isChanged) {
            lastValidation =
                BatteryStatus.isPluggedIn(plugged.value) &&
                    BatteryStatus.calculateChargingSpeed(
                        context,
                        maxCurrent.value,
                        maxVoltage.value,
                    ) == BatteryStatus.CHARGING_REGULAR
        }
        return lastValidation
    }
}
