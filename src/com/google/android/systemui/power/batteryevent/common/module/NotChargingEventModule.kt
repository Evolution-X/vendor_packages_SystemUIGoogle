package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import com.android.settingslib.fuelgauge.BatteryStatus
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class NotChargingEventModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.NOT_CHARGING

    override val intentActions = listOf(Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes = emptyList<EventDataType>()

    override fun validate(systemEventData: SystemEventData): Boolean =
        !BatteryStatus.isPluggedIn(systemEventData.plugged.value)
}
