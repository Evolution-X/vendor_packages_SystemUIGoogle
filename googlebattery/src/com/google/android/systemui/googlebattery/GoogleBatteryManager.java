package com.google.android.systemui.googlebattery;

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import vendor.google.google_battery.IGoogleBattery;

import java.util.NoSuchElementException;

public abstract class GoogleBatteryManager {
    private static final String TAG = "GoogleBatteryManager";
    private static final boolean DEBUG = Log.isLoggable(TAG, 3);

    public static IGoogleBattery initHalInterface(IBinder.DeathRecipient deathRecipient) {
        if (DEBUG) {
            Log.d(TAG, "initHalInterface");
        }
        try {
            IBinder iBinderAllowBlocking =
                    Binder.allowBlocking(
                            ServiceManager.waitForDeclaredService(
                                    "vendor.google.google_battery.IGoogleBattery/default"));
            if (iBinderAllowBlocking == null) {
                return null;
            }
            IGoogleBattery iGoogleBatteryAsInterface =
                    IGoogleBattery.Stub.asInterface(iBinderAllowBlocking);
            if (iGoogleBatteryAsInterface == null || deathRecipient == null) {
                return iGoogleBatteryAsInterface;
            }
            iBinderAllowBlocking.linkToDeath(deathRecipient, 0);
            return iGoogleBatteryAsInterface;
        } catch (RemoteException | SecurityException | NoSuchElementException e) {
            Log.e(TAG, "failed to get Google Battery HAL: ", e);
            return null;
        }
    }

    public static void destroyHalInterface(
            IGoogleBattery iGoogleBattery, IBinder.DeathRecipient deathRecipient) {
        if (DEBUG) {
            Log.d(TAG, "destroyHalInterface");
        }
        if (deathRecipient == null || iGoogleBattery == null) {
            return;
        }
        iGoogleBattery.asBinder().unlinkToDeath(deathRecipient, 0);
    }
}
