package com.google.android.systemui.power.batteryevent.common.data

data class FrameworkApiEventData(
    val batterySaverState: EventData<Boolean>,
    val extremeBatterySaverState: EventData<Boolean>,
)
