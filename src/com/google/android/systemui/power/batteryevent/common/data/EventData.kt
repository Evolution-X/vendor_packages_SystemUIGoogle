package com.google.android.systemui.power.batteryevent.common.data

data class EventData<T : Any>(val value: T) {
    var isChanged: Boolean = true
}
