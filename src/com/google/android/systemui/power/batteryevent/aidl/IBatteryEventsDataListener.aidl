package com.google.android.systemui.power.batteryevent.aidl;

import android.os.Bundle;
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType;

interface IBatteryEventsDataListener {
    void onBatteryEventDataChanged(in List<BatteryEventType> events, in Bundle batteryInfo);
}
