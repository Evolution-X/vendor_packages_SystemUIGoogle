package com.google.android.systemui.columbus;

oneway interface IColumbusService {
    void registerGestureListener(IBinder token, IBinder listener);

    void registerServiceListener(IBinder token, IBinder listener);
}
