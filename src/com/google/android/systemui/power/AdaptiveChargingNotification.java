package com.google.android.systemui.power;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.DeviceConfig;

import androidx.core.app.NotificationCompat;

import com.android.internal.logging.UiEventLogger;
import com.android.settingslib.fuelgauge.BatteryStatus;

import com.google.android.systemui.googlebattery.AdaptiveChargingManager;
import com.google.android.systemui.res.R;

import java.util.concurrent.TimeUnit;

public class AdaptiveChargingNotification {
    public final AdaptiveChargingManager mAdaptiveChargingManager;
    public final Context mContext;
    public final NotificationManager mNotificationManager;
    public final UiEventLogger mUiEventLogger;
    public final Handler mHandler = new Handler(Looper.getMainLooper());
    boolean mWasActive = false;
    boolean mAdaptiveChargingQueryInBackground = true;

    public AdaptiveChargingNotification(
            Context context,
            AdaptiveChargingManager adaptiveChargingManager,
            UiEventLogger uiEventLogger) {
        mContext = context;
        mNotificationManager = context.getSystemService(NotificationManager.class);
        mUiEventLogger = uiEventLogger;
        mAdaptiveChargingManager = adaptiveChargingManager;
    }

    public final void cancelNotification() {
        if (mWasActive) {
            mNotificationManager.cancelAsUser(
                    "adaptive_charging", R.string.adaptive_charging_notify_title, UserHandle.ALL);
            mWasActive = false;
        }
    }

    public final void checkAdaptiveChargingStatus(boolean forceUpdate) {
        if (DeviceConfig.getBoolean("adaptive_charging", "adaptive_charging_notification", false)) {
            AdaptiveChargingManager.AdaptiveChargingStatusReceiver receiver =
                    new AdaptiveChargingManager.AdaptiveChargingStatusReceiver() {
                        @Override
                        public void onReceiveStatus(String stage, int deadline) {
                            mHandler.post(() -> showNotification(stage, deadline, forceUpdate));
                        }

                        @Override
                        public void onDestroyInterface() {}
                    };
            if (!mAdaptiveChargingQueryInBackground) {
                mAdaptiveChargingManager.queryStatus(receiver);
                return;
            }
            AsyncTask.execute(() -> mAdaptiveChargingManager.queryStatus(receiver));
        }
    }

    private void showNotification(String stage, int deadline, boolean forceUpdate) {
        if ((!"Active".equals(stage) && !"Enabled".equals(stage)) || deadline <= 0) {
            cancelNotification();
            return;
        }
        if (!mWasActive || forceUpdate) {
            String timeToFull =
                    mAdaptiveChargingManager.formatTimeToFull(
                            TimeUnit.SECONDS.toMillis(deadline + 29) + System.currentTimeMillis());
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(mContext, "BAT")
                            .setShowWhen(false)
                            .setSilent(true)
                            .setSmallIcon(R.drawable.ic_battery_charging)
                            .setContentTitle(
                                    mContext.getString(R.string.adaptive_charging_notify_title))
                            .setContentText(
                                    mContext.getString(
                                            R.string.adaptive_charging_notify_des, timeToFull))
                            .addAction(
                                    0,
                                    mContext.getString(
                                            R.string.adaptive_charging_notify_turn_off_once),
                                    PowerUtils.createPendingIntent(
                                            mContext, "PNW.acChargeNormally", null))
                            .setDeleteIntent(
                                    PowerUtils.createPendingIntent(
                                            mContext,
                                            "systemui.power.action.dismissAdaptiveChargingWarning",
                                            null));
            PowerUtils.overrideNotificationAppName(mContext, builder);
            mNotificationManager.notifyAsUser(
                    "adaptive_charging",
                    R.string.adaptive_charging_notify_title,
                    builder.build(),
                    UserHandle.ALL);
            mUiEventLogger.log(BatteryMetricEvent.ADAPTIVE_CHARGING_NOTIFICATION);
            mWasActive = true;
        }
    }

    public void resolveBatteryChangedIntent(Intent intent) {
        boolean plugged = intent.getIntExtra("plugged", 0) != 0;
        int status = intent.getIntExtra("status", 1);
        int batteryLevel = BatteryStatus.getBatteryLevel(intent);
        boolean fullyCharged = (status == 5 || batteryLevel >= 100);

        if (!plugged || fullyCharged) {
            cancelNotification();
        } else {
            checkAdaptiveChargingStatus(false);
        }
    }
}
