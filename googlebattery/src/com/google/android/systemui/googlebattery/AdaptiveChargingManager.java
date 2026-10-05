package com.google.android.systemui.googlebattery;

import android.content.Context;
import android.os.IBinder;
import android.os.LocaleList;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;

import vendor.google.google_battery.ChargingStage;
import vendor.google.google_battery.IGoogleBattery;

import java.util.Locale;

public class AdaptiveChargingManager {
    private static final String TAG = "AdaptiveChargingManager";
    private static final boolean DEBUG = Log.isLoggable(TAG, 3);
    Context mContext;

    public interface AdaptiveChargingStatusReceiver {
        void onDestroyInterface();

        void onReceiveStatus(String stage, int deadlineSecs);
    }

    public AdaptiveChargingManager(Context context) {
        this.mContext = context;
    }

    boolean hasAdaptiveChargingFeature() {
        return this.mContext
                .getPackageManager()
                .hasSystemFeature("com.google.android.feature.ADAPTIVE_CHARGING");
    }

    public boolean isAvailable() {
        return hasAdaptiveChargingFeature()
                && DeviceConfig.getBoolean("adaptive_charging", "adaptive_charging_enabled", true);
    }

    public boolean isEnabled() {
        return Settings.Secure.getInt(
                        this.mContext.getContentResolver(), "adaptive_charging_enabled", 1)
                == 1;
    }

    public void setEnabled(boolean enabled) {
        Settings.Secure.putInt(
                this.mContext.getContentResolver(), "adaptive_charging_enabled", enabled ? 1 : 0);
    }

    public static boolean isStageActive(String stage) {
        return "Active".equals(stage);
    }

    public static boolean isStageEnabled(String stage) {
        return "Enabled".equals(stage);
    }

    public static boolean isStageActiveOrEnabled(String stage) {
        return isStageActive(stage) || isStageEnabled(stage);
    }

    public static boolean isActive(String stage, int deadlineSecs) {
        return isStageActiveOrEnabled(stage) && deadlineSecs > 0;
    }

    private Locale getLocale() {
        LocaleList locales = this.mContext.getResources().getConfiguration().getLocales();
        return (locales == null || locales.isEmpty()) ? Locale.getDefault() : locales.get(0);
    }

    public String formatTimeToFull(long time) {
        return DateFormat.format(
                        DateFormat.getBestDateTimePattern(
                                getLocale(),
                                DateFormat.is24HourFormat(this.mContext) ? "Hm" : "hma"),
                        time)
                .toString();
    }

    public boolean setAdaptiveChargingDeadline(int deadlineSecs) {
        IGoogleBattery googleBattery = GoogleBatteryManager.initHalInterface(null);
        if (googleBattery == null) {
            return false;
        }
        boolean success = false;
        try {
            googleBattery.setChargingDeadline(deadlineSecs);
            success = true;
        } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
            Log.e(TAG, "setChargingDeadline failed: ", e);
        }
        GoogleBatteryManager.destroyHalInterface(googleBattery, null);
        return success;
    }

    public boolean setDefaultChargingPolicy() {
        IGoogleBattery googleBattery = GoogleBatteryManager.initHalInterface(null);
        if (googleBattery == null) {
            return false;
        }
        boolean success = false;
        try {
            googleBattery.setChargingPolicy(1);
            success = true;
        } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
            Log.e(TAG, "setChargingPolicy failed: ", e);
        }
        GoogleBatteryManager.destroyHalInterface(googleBattery, null);
        return success;
    }

    private void queryStatusReceived(
            AdaptiveChargingStatusReceiver receiver, String stage, int deadlineSecs) {
        if (DEBUG) {
            Log.d(
                    TAG,
                    "getChargingStageDeadlineCallback stage: \""
                            + stage
                            + "\", seconds: "
                            + deadlineSecs);
        }
        receiver.onReceiveStatus(stage, deadlineSecs);
    }

    public void queryStatus(final AdaptiveChargingStatusReceiver receiver) {
        IBinder.DeathRecipient deathRecipient =
                new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        if (AdaptiveChargingManager.DEBUG) {
                            Log.d(TAG, "serviceDied");
                        }
                        receiver.onDestroyInterface();
                    }
                };
        IGoogleBattery googleBattery = GoogleBatteryManager.initHalInterface(deathRecipient);
        if (googleBattery == null) {
            receiver.onDestroyInterface();
            return;
        }
        try {
            ChargingStage chargingStageAndDeadline = googleBattery.getChargingStageAndDeadline();
            queryStatusReceived(
                    receiver,
                    chargingStageAndDeadline.stage,
                    chargingStageAndDeadline.deadlineSecs);
        } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
            Log.e(TAG, "Failed to get Adaptive Charging status: ", e);
        }
        GoogleBatteryManager.destroyHalInterface(googleBattery, deathRecipient);
        receiver.onDestroyInterface();
    }
}
