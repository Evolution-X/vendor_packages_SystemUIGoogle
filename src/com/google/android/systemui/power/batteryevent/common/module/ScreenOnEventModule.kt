package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class ScreenOnEventModule : BaseBatteryEventModule() {
    private var lastValidated = false

    override val moduleType = BatteryEventType.SCREEN_ON

    override val intentActions = listOf(Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF)

    override val eventDataTypes = emptyList<EventDataType>()

    override fun validate(systemEventData: SystemEventData): Boolean {
        lastValidated =
            when (systemEventData.intentAction) {
                Intent.ACTION_SCREEN_ON -> true
                Intent.ACTION_SCREEN_OFF -> false
                else -> lastValidated
            }
        return lastValidated
    }
}
