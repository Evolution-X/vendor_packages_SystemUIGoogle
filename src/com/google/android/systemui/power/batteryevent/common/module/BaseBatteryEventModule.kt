package com.google.android.systemui.power.batteryevent.common.module

import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

abstract class BaseBatteryEventModule {
    var lastValidation: Boolean = false

    abstract val moduleType: BatteryEventType

    abstract val intentActions: List<String>

    abstract val eventDataTypes: List<EventDataType>

    abstract fun validate(systemEventData: SystemEventData): Boolean
}
