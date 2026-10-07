package com.google.android.systemui.power.batteryevent.common.data

data class HalEventData(
    val dockDefendStatus: EventData<Int>,
    val tempDefendEventData: EventData<Boolean>,
    val dwellDefendEventData: EventData<Boolean>,
)
