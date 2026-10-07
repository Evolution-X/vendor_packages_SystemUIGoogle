package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import android.util.Log
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.HalDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class DwellDefendBatteryModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.DWELL_DEFEND_BATTERY

    override val intentActions = listOf(Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes: List<EventDataType> =
        listOf(HalDataType.GOOGLE_BATTERY_DWELL_DEFEND_STATUS)

    override fun validate(systemEventData: SystemEventData): Boolean {
        val defended = systemEventData.halEventData.dwellDefendEventData.value
        Log.d("DwellDefendBatteryModule", "validate: $lastValidation -> $defended")
        lastValidation = defended
        return defended
    }
}
