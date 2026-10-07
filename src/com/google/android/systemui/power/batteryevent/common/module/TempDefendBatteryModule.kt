package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import android.util.Log
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.HalDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class TempDefendBatteryModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.TEMP_DEFEND_BATTERY

    override val intentActions = listOf(Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes: List<EventDataType> =
        listOf(HalDataType.GOOGLE_BATTERY_TEMP_DEFEND_STATUS)

    override fun validate(systemEventData: SystemEventData): Boolean {
        val defended = systemEventData.halEventData.tempDefendEventData.value
        Log.d("TempDefendBatteryModule", "validate: $lastValidation -> $defended")
        lastValidation = defended
        return defended
    }
}
