package com.google.android.systemui.power.batteryevent.aidl;

import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType;
import com.google.android.systemui.power.batteryevent.aidl.IBatteryEventsDataListener;
import com.google.android.systemui.power.batteryevent.aidl.IBatteryEventsListener;
import com.google.android.systemui.power.batteryevent.aidl.SurfaceType;

oneway interface IBatteryEventService {
    void registerBatteryEventsCallback(
            IBatteryEventsListener listener, in List<BatteryEventType> events, in SurfaceType surfaceType);
    void unregisterBatteryEventsCallback(IBatteryEventsListener listener);
    void registerBatteryEventsUpdate(
            String packageName, String className, in List<BatteryEventType> events, int userId);
    void unregisterBatteryEventsUpdate(String packageName, String className, int userId);
    // ???: transaction 5 takes no arguments and is a no-op in stock; its name was stripped.
    void reserved();
    void registerBatteryEventsDataCallback(
            IBatteryEventsDataListener listener, in List<BatteryEventType> events, in int[] timeToFullSocs);
    void unregisterBatteryEventsDataCallback(IBatteryEventsDataListener listener);
}
