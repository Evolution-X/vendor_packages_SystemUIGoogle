package com.google.android.systemui.power.batteryevent.common

import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType

data class BatteryEventSubscriber(
    val batteryEventType: BatteryEventType,
    val actions: List<String>,
    val eventDataType: List<EventDataType>,
)
