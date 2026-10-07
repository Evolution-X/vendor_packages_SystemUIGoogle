package com.google.android.systemui.power;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.settingslib.fuelgauge.BatteryStatus;
import com.android.settingslib.fuelgauge.Estimate;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.power.EnhancedEstimates;
import com.android.systemui.settings.UserTracker;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Executor;

/** Pushes phone and Bluetooth battery state to the Settings Intelligence battery widget. */
public final class BatteryInfoBroadcast {

    private static final String TAG = "BatteryInfoBroadcast";

    private static final String SETTINGS_INTELLIGENCE_PACKAGE =
            "com.google.android.settings.intelligence";
    private static final String ACTION_BATTERY_STATUS_CHANGED = "PNW.batteryStatusChanged";
    private static final String ACTION_BLUETOOTH_STATUS_CHANGED = "PNW.bluetoothStatusChanged";
    private static final String KEY_BATTERY_WIDGET_ENABLED = "battery_widget_enabled";
    private static final String KEY_TIME_TO_FULL_MILLIS = "time_to_full_millis";
    private static final String KEY_REMAINING_TIME_MILLIS = "remaining_time_millis";

    private final Context mContext;
    private final BroadcastSender mBroadcastSender;
    private final EnhancedEstimates mEnhancedEstimates;
    private final Executor mExecutor;
    private final UserTracker mUserTracker;
    private final PowerManager mPowerManager;
    private final BatteryManager mBatteryManager;
    private final SharedPreferences mSharedPreferences;

    private boolean mIsPowerSaveMode;
    private int mBatteryLevel = -1;
    private int mBatteryPlugged = 0;
    private int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private int mBatteryChargingStatus = BatteryManager.CHARGING_POLICY_DEFAULT;
    private long mRemainingTimeMillis = -1;
    private long mTimeToFullMillis = -1;
    private boolean mWidgetEnabled = true;

    final ContentObserver mWidgetEnableObserver =
            new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange) {
                    boolean enabled =
                            Settings.Secure.getIntForUser(
                                            mContext.getContentResolver(),
                                            KEY_BATTERY_WIDGET_ENABLED,
                                            1,
                                            mUserTracker.getUserId())
                                    == 1;
                    if (enabled && !mWidgetEnabled) {
                        mWidgetEnabled = true;
                        dispatchIntent(new Intent(ACTION_BATTERY_STATUS_CHANGED));
                    }
                    mWidgetEnabled = enabled;
                    Log.d(TAG, "mWidgetEnableObserver: " + mWidgetEnabled);
                }
            };

    final ContentObserver mTimeToFullObserver =
            new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange) {
                    if (!selfChange) {
                        sendBatteryChangeIntent(
                                createIntentForSI(ACTION_BATTERY_STATUS_CHANGED), false, true);
                    }
                }
            };

    final ContentObserver mDeviceNameObserver =
            new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange) {
                    Log.d(TAG, "mDeviceNameObserver: " + selfChange);
                    sendBroadcast(createIntentForSI(ACTION_BATTERY_STATUS_CHANGED));
                }
            };

    final ContentObserver mRemainingTimeObserver =
            new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange) {
                    Log.d(TAG, "mRemainingTimeObserver: " + selfChange);
                    sendBatteryChangeIntent(new Intent(ACTION_BATTERY_STATUS_CHANGED), true, false);
                }
            };

    public BatteryInfoBroadcast(
            Context context,
            BroadcastSender broadcastSender,
            EnhancedEstimates enhancedEstimates,
            Executor executor,
            UserTracker userTracker) {
        mContext = context;
        mPowerManager = context.getSystemService(PowerManager.class);
        mBatteryManager = context.getSystemService(BatteryManager.class);
        mIsPowerSaveMode = mPowerManager.isPowerSaveMode();
        mBroadcastSender = broadcastSender;
        mEnhancedEstimates = enhancedEstimates;
        mExecutor = executor;
        mUserTracker = userTracker;
        mWidgetEnableObserver.onChange(true);
        mSharedPreferences =
                context.getApplicationContext()
                        .getSharedPreferences("battery_info_shared_prefs", Context.MODE_PRIVATE);
        registerObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_NAME),
                mDeviceNameObserver,
                "device name");
        registerObserver(
                new Uri.Builder()
                        .scheme("content")
                        .authority("com.google.android.apps.turbo.estimated_time_remaining")
                        .appendPath("time_remaining")
                        .build(),
                mRemainingTimeObserver,
                "remaining time");
        registerObserver(
                Settings.Secure.getUriFor(KEY_BATTERY_WIDGET_ENABLED),
                mWidgetEnableObserver,
                "enabled widget");
        registerObserver(
                Settings.Global.getUriFor(KEY_TIME_TO_FULL_MILLIS),
                mTimeToFullObserver,
                "time to full");
    }

    private static Intent createIntentForSI(String action) {
        return new Intent(action).setPackage(SETTINGS_INTELLIGENCE_PACKAGE);
    }

    public void dispatchIntent(Intent intent) {
        mExecutor.execute(() -> handleIntent(intent));
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (!mWidgetEnabled
                && !Intent.ACTION_POWER_CONNECTED.equals(action)
                && !Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            return;
        }
        switch (action) {
            case Intent.ACTION_POWER_DISCONNECTED:
                sendPluggedInStateIntent(false);
                recordDateTime("last_phone_disconnected_time");
                break;
            case Intent.ACTION_BATTERY_CHANGED:
                int batteryLevel = BatteryStatus.getBatteryLevel(intent);
                int status =
                        intent.getIntExtra(
                                BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                int chargingStatus =
                        intent.getIntExtra(
                                BatteryManager.EXTRA_CHARGING_STATUS,
                                BatteryManager.CHARGING_POLICY_DEFAULT);
                if (mBatteryLevel != batteryLevel
                        || mBatteryStatus != status
                        || mBatteryPlugged != plugged
                        || mBatteryChargingStatus != chargingStatus) {
                    mBatteryLevel = batteryLevel;
                    mBatteryStatus = status;
                    mBatteryPlugged = plugged;
                    mBatteryChargingStatus = chargingStatus;
                    sendBatteryChangeIntent(intent, false, false);
                }
                break;
            case BluetoothAdapter.ACTION_STATE_CHANGED:
            case BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED:
            case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
            case BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED:
            case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
            case BluetoothDevice.ACTION_ALIAS_CHANGED:
            case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                Intent bluetoothIntent = createIntentForSI(ACTION_BLUETOOTH_STATUS_CHANGED);
                bluetoothIntent.putExtra(action, intent);
                sendBroadcast(bluetoothIntent);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
            case ACTION_BATTERY_STATUS_CHANGED:
                sendBatteryChangeIntent(new Intent(ACTION_BATTERY_STATUS_CHANGED), false, false);
                break;
            case Intent.ACTION_POWER_CONNECTED:
                sendPluggedInStateIntent(true);
                recordDateTime("last_phone_connected_time");
                break;
            case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED:
                boolean isPowerSaveMode = mPowerManager.isPowerSaveMode();
                if (mIsPowerSaveMode != isPowerSaveMode) {
                    mIsPowerSaveMode = isPowerSaveMode;
                    sendBatteryChangeIntent(intent, false, false);
                }
                break;
        }
    }

    private void sendBatteryChangeIntent(
            Intent intent, boolean ignoreSameRemainingTime, boolean ignoreSameTimeToFull) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "sendBatteryIntent() with invalid intent");
            return;
        }
        String action = intent.getAction();
        Intent siIntent =
                createIntentForSI(ACTION_BATTERY_STATUS_CHANGED)
                        .putExtra("battery_save", mIsPowerSaveMode);
        if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
            siIntent.putExtra("battery_changed_intent", intent);
        }
        long timeToFull = -1;
        if (BatteryStatus.isPluggedIn(mBatteryPlugged)) {
            try {
                timeToFull = mBatteryManager.computeChargeTimeRemaining();
            } catch (Exception e) {
                Log.w(TAG, "computeChargeTimeRemaining failed.", e);
                timeToFull = -1;
            }
            Log.d(TAG, "computeChargeTimeRemaining=" + timeToFull);
            if (ignoreSameTimeToFull && mTimeToFullMillis == timeToFull) {
                Log.w(TAG, "sendBroadcastIntent() ignore from the same timeToFull");
                return;
            }
            if (mTimeToFullMillis != timeToFull) {
                mTimeToFullMillis = timeToFull != 0 ? timeToFull : -1;
                Settings.Global.putLong(
                        mContext.getContentResolver(), KEY_TIME_TO_FULL_MILLIS, mTimeToFullMillis);
            }
            siIntent.putExtra("time_to_full", mTimeToFullMillis);
        } else if (mEnhancedEstimates != null) {
            Estimate estimate = mEnhancedEstimates.getEstimate();
            long remainingTime = estimate.getEstimateMillis();
            if (ignoreSameRemainingTime && mRemainingTimeMillis == remainingTime) {
                Log.w(TAG, "sendBatteryIntent() ignore from the same remaining time");
                return;
            }
            mRemainingTimeMillis = remainingTime;
            siIntent.putExtra("remaining_time", remainingTime);
            Settings.Global.putLong(
                    mContext.getContentResolver(), KEY_REMAINING_TIME_MILLIS, mRemainingTimeMillis);
            if (remainingTime > 0) {
                Estimate.storeCachedEstimate(mContext, estimate);
            }
        }
        Log.d(
                TAG,
                String.format(
                        "sendBroadcast: %s, saverMode: %b, remainingTime: %d, timeToFull: %d",
                        action, mIsPowerSaveMode, mRemainingTimeMillis, timeToFull));
        sendBroadcast(siIntent);
    }

    private void sendBroadcast(Intent intent) {
        if (mBroadcastSender == null || intent == null) {
            return;
        }
        mBroadcastSender.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendPluggedInStateIntent(boolean pluggedIn) {
        sendBroadcast(
                new Intent(
                                pluggedIn
                                        ? "com.android.settings.battery.action.ACTION_BATTERY_PLUGGING"
                                        : "com.android.settings.battery.action.ACTION_BATTERY_UNPLUGGING")
                        .setComponent(
                                new ComponentName(
                                        "com.android.settings",
                                        "com.android.settings.fuelgauge.batteryusage.BatteryUsageBroadcastReceiver")));
        if (pluggedIn) {
            return;
        }
        Intent batteryChanged =
                Optional.ofNullable(
                                mContext.registerReceiver(
                                        null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
                        .orElse(new Intent());
        int status =
                batteryChanged.getIntExtra(
                        BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int batteryLevel = BatteryStatus.getBatteryLevel(batteryChanged);
        if (status == BatteryManager.BATTERY_STATUS_FULL || batteryLevel >= 100) {
            recordDateTime("last_data_reset_time");
        }
    }

    private void recordDateTime(String key) {
        if (mSharedPreferences != null) {
            mSharedPreferences
                    .edit()
                    .putString(key, DumpUtils.toReadableDateTime(System.currentTimeMillis()))
                    .apply();
        }
    }

    private void registerObserver(Uri uri, ContentObserver observer, String name) {
        try {
            mContext.getContentResolver()
                    .registerContentObserver(uri, false, observer, UserHandle.USER_ALL);
        } catch (Exception e) {
            Log.e(TAG, "failed to register observer for " + name, e);
        }
    }

    public void dump(PrintWriter pw) {
        pw.println("\tdump BatteryInfoBroadcast states:");
        writeString(pw, "LastConnectedTime: ", "last_phone_connected_time");
        writeString(pw, "LastDisconnectedTime: ", "last_phone_disconnected_time");
        writeString(pw, "LastDataResetTime: ", "last_data_reset_time");
    }

    private void writeString(PrintWriter pw, String label, String key) {
        if (mSharedPreferences != null) {
            pw.println("\t\t" + label + mSharedPreferences.getString(key, ""));
        }
    }
}
