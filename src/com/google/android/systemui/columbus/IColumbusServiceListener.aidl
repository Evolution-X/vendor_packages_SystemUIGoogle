package com.google.android.systemui.columbus;

oneway interface IColumbusServiceListener {
    void setListener(IBinder token, IBinder listener);
}
