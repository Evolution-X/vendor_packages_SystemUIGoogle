package com.google.android.systemui.power.batteryevent.common.data

data class SystemEventData(
    val intentAction: String,
    val plugged: EventData<Int>,
    val batteryScale: EventData<Int>,
    val batteryLevel: EventData<Int>,
    val chargingStatus: EventData<Int>,
    val maxChargingCurrent: EventData<Int>,
    val maxChargingVoltage: EventData<Int>,
    val batteryStatus: EventData<Int>,
    val halEventData: HalEventData,
    val settingsEventData: SettingsEventData,
    val frameworkApiEventData: FrameworkApiEventData,
)
