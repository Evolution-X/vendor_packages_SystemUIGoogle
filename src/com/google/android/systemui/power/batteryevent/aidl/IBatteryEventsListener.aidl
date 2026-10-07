package com.google.android.systemui.power.batteryevent.aidl;

import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType;

interface IBatteryEventsListener {
    void onBatteryEventChanged(in List<BatteryEventType> events, int batteryLevel, int pluggedType);
}
