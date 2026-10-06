package com.google.android.systemui.power.batteryhealth;

oneway interface IHealthListener {
    void onHealthIndexChanged(int healthIndex);
    void onPerformanceIndexChanged(int perfIndex);
    void onCapacityIndexChanged(int capacityIndex);
    void onHealthStatusChanged(int healthStatus);
}
