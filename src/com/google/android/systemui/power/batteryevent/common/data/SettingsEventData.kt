package com.google.android.systemui.power.batteryevent.common.data

data class SettingsEventData(
    val dockDefenderBypass: EventData<Int>,
    val chargingLimitSettings: EventData<Int>,
    val dndState: EventData<Boolean>,
    val airplaneState: EventData<Boolean>,
)
