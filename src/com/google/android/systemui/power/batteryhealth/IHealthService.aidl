package com.google.android.systemui.power.batteryhealth;

import com.google.android.systemui.power.batteryhealth.HealthData;
import com.google.android.systemui.power.batteryhealth.IHealthListener;
import com.google.android.systemui.power.batteryhealth.IncompatibleChargerData;

interface IHealthService {
    HealthData getHealthData();
    oneway void registerHealthListener(IHealthListener listener);
    oneway void unregisterHealthListener(IHealthListener listener);
    IncompatibleChargerData getIncompatibleChargerData();
    HealthData getHealthDataWithAlgo(int healthAlgo);
    boolean setChargingPolicy(int policy);
    boolean isPulsarEnabled();
    void setPulsarEnabled(boolean enabled);
    void setRescheduled(boolean rescheduled);
    boolean isRescheduled();
}
