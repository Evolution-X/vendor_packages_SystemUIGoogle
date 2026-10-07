package com.google.android.systemui.power.batteryevent.common

import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType

data class BatteryEvents(
    val eventTypes: Set<BatteryEventType>,
    val batteryLevel: Int,
    val pluggedType: Int,
)
