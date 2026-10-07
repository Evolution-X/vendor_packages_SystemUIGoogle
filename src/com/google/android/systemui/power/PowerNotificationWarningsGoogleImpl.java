package com.google.android.systemui.power;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.logging.UiEventLogger;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.animation.Expandable;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.power.EnhancedEstimates;
import com.android.systemui.power.PowerNotificationWarnings;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.util.settings.GlobalSettings;
import com.android.systemui.util.settings.SecureSettings;

import com.google.android.systemui.googlebattery.AdaptiveChargingManager;
import com.google.android.systemui.googlebattery.GoogleBatteryManager;
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType;
import com.google.android.systemui.power.batteryevent.aidl.SurfaceType;
import com.google.android.systemui.res.R;

import dagger.Lazy;

import kotlin.Unit;

import vendor.google.google_battery.IGoogleBattery;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Provider;

@SysUISingleton
public class PowerNotificationWarningsGoogleImpl extends PowerNotificationWarnings {

    private static final String TAG = "PowerNotificationWarningsGoogleImpl";

    private final Context mContext;
    private final Executor mExecutor;
    private final Handler mHandler;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final GlobalSettings mGlobalSettings;
    private final SecureSettings mSecureSettings;
    private final UserTracker mUserTracker;
    private final UiEventLogger mUiEventLogger;
    private final SevereLowBatteryNotification mSevereLowBatteryNotification;
    private final LowPowerWarningsController mLowPowerWarningsController;
    private final Provider<BatterySaverConfirmationDialog> mBatterySaverConfirmationDialogProvider;
    private final Lazy<BatteryController> mBatteryControllerLazy;
    private final ChargeLimitController mChargeLimitController;
    private final ChargeLimitDiscoveryNotification mChargeLimitDiscoveryNotification;
    private final AdaptiveChargingNotification mAdaptiveChargingNotification;
    private final PulsarController mPulsarController;
    private final BatteryInfoBroadcast mBatteryInfoBroadcast;
    private BatterySaverConfirmationDialog mBatterySaverConfirmationDialog;

    private final BroadcastReceiver mBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || intent.getAction() == null) {
                        return;
                    }
                    String action = intent.getAction();
                    Log.d(TAG, "onReceive: " + action);
                    mBatteryInfoBroadcast.dispatchIntent(intent);
                    switch (action) {
                        case Intent.ACTION_BATTERY_CHANGED:
                            if (mAdaptiveChargingNotification != null) {
                                mAdaptiveChargingNotification.resolveBatteryChangedIntent(intent);
                            }
                            if (mChargeLimitDiscoveryNotification != null) {
                                mChargeLimitDiscoveryNotification.dispatchIntent(intent);
                            }
                            break;
                        case Intent.ACTION_BOOT_COMPLETED:
                        case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                            if (mChargeLimitController != null) {
                                mChargeLimitController.onBootCompleted(intent);
                            }
                            break;
                        case Intent.ACTION_POWER_CONNECTED:
                        case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED:
                        case "com.android.settingslib.fuelgauge.ACTION_SAVER_STATE_MANUAL_UPDATE":
                            if (mLowPowerWarningsController != null) {
                                mExecutor.execute(
                                        () -> mLowPowerWarningsController.dispatchIntent(intent));
                            }
                            break;
                        case "PNW.dismissSevereLowBatteryWarning":
                            handleDismissSevereLowBatteryWarning(intent);
                            break;
                        case "PNW.startSaverConfirmation":
                        case "FLIPENDO.startSaverConfirmation":
                            handleStartSaverConfirmation();
                            break;
                        case "systemui.power.action.START_FLIPENDO":
                            handleStartFlipendo(intent);
                            break;
                        case "PNW.dismissedWarning":
                            dismissLowBatteryWarning();
                            break;
                        case "com.google.android.systemui.adaptivecharging.ADAPTIVE_CHARGING_DEADLINE_SET":
                            if (mAdaptiveChargingNotification != null) {
                                mAdaptiveChargingNotification.checkAdaptiveChargingStatus(true);
                            }
                            break;
                        case "PNW.acChargeNormally":
                            if (mAdaptiveChargingNotification != null) {
                                mAdaptiveChargingNotification.mUiEventLogger.log(
                                        BatteryMetricEvent.ADAPTIVE_CHARGING_NOTIFICATION_BYPASS);
                                IGoogleBattery battery =
                                        GoogleBatteryManager.initHalInterface(null);
                                if (battery != null) {
                                    try {
                                        battery.setChargingDeadline(-3);
                                    } catch (ServiceSpecificException
                                            | RemoteException
                                            | IllegalArgumentException e) {
                                        Log.e(
                                                "AdaptiveChargingManager",
                                                "setChargingDeadline failed: ",
                                                e);
                                    }
                                    GoogleBatteryManager.destroyHalInterface(battery, null);
                                }
                                mAdaptiveChargingNotification.cancelNotification();
                                Intent acIntent =
                                        new Intent(
                                                "com.google.android.systemui.adaptivecharging.ADAPTIVE_CHARGING_DEADLINE_SET");
                                acIntent.setPackage(mContext.getPackageName());
                                acIntent.setFlags(
                                        Intent.FLAG_RECEIVER_REGISTERED_ONLY
                                                | Intent.FLAG_RECEIVER_FOREGROUND);
                                mContext.sendBroadcastAsUser(acIntent, UserHandle.ALL);
                            }
                            break;
                        case "systemui.power.action.dismissAdaptiveChargingWarning":
                            if (mAdaptiveChargingNotification != null) {
                                mAdaptiveChargingNotification.mUiEventLogger.log(
                                        BatteryMetricEvent.DELETE_ADAPTIVE_CHARGING_NOTIFICATION);
                            }
                            break;
                        case "systemui.power.action.clickChargeLimitNotification":
                        case "systemui.power.action.dismissChargeLimitNotification":
                        case "systemui.power.action.enableChargeLimitFeature":
                            if (mChargeLimitDiscoveryNotification != null) {
                                mChargeLimitDiscoveryNotification.dispatchIntent(intent);
                            }
                            break;
                    }
                    mPulsarController.dispatchIntent(intent);
                }
            };

    @Inject
    public PowerNotificationWarningsGoogleImpl(
            Context context,
            ActivityStarter activityStarter,
            BroadcastSender broadcastSender,
            Lazy<BatteryController> batteryControllerLazy,
            DialogTransitionAnimator dialogTransitionAnimator,
            UiEventLogger uiEventLogger,
            UserTracker userTracker,
            EnhancedEstimates enhancedEstimates,
            BatteryEventClient batteryEventClient,
            SystemUIDialog.Factory systemUIDialogFactory,
            BroadcastDispatcher broadcastDispatcher,
            GlobalSettings globalSettings,
            SecureSettings secureSettings,
            @Background Executor backgroundExecutor,
            @Main Handler mainHandler,
            SevereLowBatteryNotification severeLowBatteryNotification,
            Provider<BatterySaverConfirmationDialog> batterySaverConfirmationDialogProvider,
            ChargeLimitController chargeLimitController,
            ChargeLimitDiscoveryNotification chargeLimitDiscoveryNotification,
            BatterySaverAutoDisableController batterySaverAutoDisableController,
            PulsarController pulsarController) {
        super(
                context,
                activityStarter,
                broadcastSender,
                batteryControllerLazy,
                dialogTransitionAnimator,
                uiEventLogger,
                userTracker,
                systemUIDialogFactory);
        mContext = context;
        mBroadcastDispatcher = broadcastDispatcher;
        mBatteryControllerLazy = batteryControllerLazy;
        mGlobalSettings = globalSettings;
        mSecureSettings = secureSettings;
        mUserTracker = userTracker;
        mExecutor = backgroundExecutor;
        mHandler = mainHandler;
        mUiEventLogger = uiEventLogger;
        mSevereLowBatteryNotification = severeLowBatteryNotification;
        mBatterySaverConfirmationDialogProvider = batterySaverConfirmationDialogProvider;
        mChargeLimitController = chargeLimitController;
        mPulsarController = pulsarController;
        mBatteryInfoBroadcast =
                new BatteryInfoBroadcast(
                        context,
                        broadcastSender,
                        enhancedEstimates,
                        backgroundExecutor,
                        userTracker);
        boolean adaptiveChargingEnabled =
                context.getResources().getBoolean(R.bool.config_adaptive_charging_warning_enabled);
        boolean isEuSku = TextUtils.equals(SystemProperties.get("ro.boot.warranty.sku"), "EMA");
        if (!isEuSku) {
            Log.d(TAG, "Skip LotX intent registration for non-EU devices.");
        }
        boolean chargeLimitDiscoveryEnabled =
                isEuSku
                        && context.getResources()
                                .getBoolean(R.bool.config_charge_limit_discovery_enabled);
        mChargeLimitDiscoveryNotification =
                chargeLimitDiscoveryEnabled ? chargeLimitDiscoveryNotification : null;
        mAdaptiveChargingNotification =
                adaptiveChargingEnabled
                        ? new AdaptiveChargingNotification(
                                context, new AdaptiveChargingManager(context), uiEventLogger)
                        : null;
        mLowPowerWarningsController =
                new LowPowerWarningsController(
                        context,
                        backgroundExecutor,
                        globalSettings,
                        uiEventLogger,
                        severeLowBatteryNotification);
        batteryEventClient.registerBatteryEventCallback(
                SurfaceType.NOTIFICATION,
                TAG,
                Arrays.asList(
                        BatteryEventType.LOW_BATTERY,
                        BatteryEventType.SEVERE_LOW_BATTERY,
                        BatteryEventType.EXTREME_LOW_BATTERY,
                        BatteryEventType.WIRED_INCOMPATIBLE_CHARGING,
                        BatteryEventType.TEMP_DEFEND_BATTERY,
                        BatteryEventType.DWELL_DEFEND_BATTERY,
                        BatteryEventType.DOCK_DEFEND_BATTERY),
                (events, batteryLevel, pluggedType) -> {
                    Log.d(TAG, "[onBatteryEventUpdate] " + events);
                    mLowPowerWarningsController.onBatteryEventUpdate(batteryLevel, events);
                    return Unit.INSTANCE;
                });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction("com.android.settingslib.fuelgauge.ACTION_SAVER_STATE_MANUAL_UPDATE");
        filter.addAction("PNW.dismissSevereLowBatteryWarning");
        filter.addAction("PNW.startSaverConfirmation");
        filter.addAction("FLIPENDO.startSaverConfirmation");
        filter.addAction("systemui.power.action.START_FLIPENDO");
        filter.addAction("PNW.dismissedWarning");
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ALIAS_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        filter.addAction(PulsarController.ACTION_CLICK_PULSAR_ENABLED_NOTIFICATION);
        filter.addAction(PulsarController.ACTION_DISMISS_PULSAR_ENABLED_NOTIFICATION);
        filter.addAction(PulsarController.ACTION_CLICK_PULSAR_REMINDER_NOTIFICATION);
        filter.addAction(PulsarController.ACTION_DISMISS_PULSAR_REMINDER_NOTIFICATION);
        if (adaptiveChargingEnabled) {
            filter.addAction(
                    "com.google.android.systemui.adaptivecharging.ADAPTIVE_CHARGING_DEADLINE_SET");
            filter.addAction("PNW.acChargeNormally");
            filter.addAction("systemui.power.action.dismissAdaptiveChargingWarning");
        }
        if (chargeLimitDiscoveryEnabled) {
            filter.addAction("systemui.power.action.clickChargeLimitNotification");
            filter.addAction("systemui.power.action.dismissChargeLimitNotification");
            filter.addAction("systemui.power.action.enableChargeLimitFeature");
        }
        mBroadcastDispatcher.registerReceiver(mBroadcastReceiver, filter);

        Intent batteryChanged =
                context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryChanged != null) {
            mBatteryInfoBroadcast.dispatchIntent(batteryChanged);
            if (mAdaptiveChargingNotification != null) {
                mAdaptiveChargingNotification.resolveBatteryChangedIntent(batteryChanged);
            }
            if (mChargeLimitDiscoveryNotification != null) {
                mChargeLimitDiscoveryNotification.dispatchIntent(batteryChanged);
            }
        }

        batterySaverAutoDisableController.start();
    }

    private void handleStartSaverConfirmation() {
        if (mLowPowerWarningsController != null) {
            mLowPowerWarningsController.cancelNotification();
        }
        if (!mContext.getResources().getBoolean(R.bool.config_extra_battery_saver_confirmation)) {
            return;
        }
        if (mBatterySaverConfirmationDialog == null) {
            mBatterySaverConfirmationDialog = mBatterySaverConfirmationDialogProvider.get();
        }
        WeakReference<Expandable> ref =
                mBatteryControllerLazy.get().getLastPowerSaverStartExpandable();
        Expandable expandable = (ref != null) ? ref.get() : null;
        mBatteryControllerLazy.get().clearLastPowerSaverStartExpandable();
        mBatterySaverConfirmationDialog.show(expandable);
    }

    private void handleStartFlipendo(Intent intent) {
        mExecutor.execute(
                () -> {
                    try {
                        mContext.getContentResolver()
                                .call(
                                        "com.google.android.flipendo.api",
                                        "force_enable_flipendo_method",
                                        null,
                                        null);
                    } catch (Exception e) {
                        Log.e(TAG, "enableFlipendo() failed", e);
                    }
                    PowerManager pm = mContext.getSystemService(PowerManager.class);
                    if (pm != null && !pm.isPowerSaveMode()) {
                        pm.setPowerSaveModeEnabled(true);
                    }

                    String extra = intent.getStringExtra("extra_severe_low_battery_notification");
                    if (mSevereLowBatteryNotification != null) {
                        if ("low_battery_notification_turn_on_ebs".equals(extra)) {
                            mSevereLowBatteryNotification.logEvent(
                                    BatteryMetricEvent
                                            .SEVERE_LOW_BATTERY_NOTIFICATION_TURN_ON_EBS_CLICK_TURN_ON);
                        } else if ("low_battery_notification_switch_to_ebs".equals(extra)) {
                            mSevereLowBatteryNotification.logEvent(
                                    BatteryMetricEvent
                                            .SEVERE_LOW_BATTERY_NOTIFICATION_SWITCH_TO_EBS_CLICK_SWITCH);
                        }
                    }
                });
    }

    private void handleDismissSevereLowBatteryWarning(Intent intent) {
        if (mLowPowerWarningsController != null) {
            mLowPowerWarningsController.cancelNotification();
        }
        String extra = intent.getStringExtra("extra_severe_low_battery_notification");
        if (mSevereLowBatteryNotification != null) {
            if ("low_battery_notification_turn_on_ebs".equals(extra)) {
                mSevereLowBatteryNotification.logEvent(
                        BatteryMetricEvent.SEVERE_LOW_BATTERY_NOTIFICATION_TURN_ON_EBS_DISMISS);
            } else if ("low_battery_notification_switch_to_ebs".equals(extra)) {
                mSevereLowBatteryNotification.logEvent(
                        BatteryMetricEvent.SEVERE_LOW_BATTERY_NOTIFICATION_SWITCH_TO_EBS_DISMISS);
            }
        }
    }

    @Override
    public void showLowBatteryWarning(boolean playSound) {
        // Handled by LowPowerWarningsController
    }

    @Override
    protected void showWarningNotification() {
        // Handled by LowPowerWarningsController
    }

    @Override
    public void updateLowBatteryWarning() {
        // Handled by LowPowerWarningsController
    }

    @Override
    public void dismissLowBatteryWarning() {
        if (mLowPowerWarningsController != null) {
            mLowPowerWarningsController.cancelNotification();
        }
    }

    @Override
    public void dump(PrintWriter pw) {
        super.dump(pw);
        mBatteryInfoBroadcast.dump(pw);
        if (mLowPowerWarningsController != null) {
            pw.println("\tdump LowPowerWarningsController states");
            pw.println("\t\tprevBatteryLevel: " + mLowPowerWarningsController.prevBatteryLevel);
            pw.println(
                    "\t\tprevBatteryEventType: "
                            + mLowPowerWarningsController.prevBatteryEventTypes);
            pw.println(
                    "\t\tisBatterySaverReminderDisabled: "
                            + (mGlobalSettings.getInt("low_power_mode_reminder_enabled", 1) == 0));
            pw.println(
                    "\t\tisScheduledByPercentage: "
                            + mLowPowerWarningsController.isScheduledByPercentage());
            pw.println(
                    "\t\tlowBatteryNotificationCancelled: "
                            + mLowPowerWarningsController.lowBatteryNotificationCancelled);
            pw.println(
                    "\t\tsevereLowBatteryNotificationCancelled: "
                            + mLowPowerWarningsController.severeLowBatteryNotificationCancelled);
        }
    }

    @Override
    public void userSwitched() {
        if (mLowPowerWarningsController != null
                && mLowPowerWarningsController.prevBatteryLevel != null) {
            mLowPowerWarningsController.onBatteryEventUpdate(
                    mLowPowerWarningsController.prevBatteryLevel,
                    mLowPowerWarningsController.prevBatteryEventTypes);
        }
        if (mChargeLimitController != null) {
            int userId = mUserTracker.getUserId();
            Log.d("ChargeLimitController", "onUserSwitched - current user= " + userId);
            boolean isChargeLimitEnabled =
                    PowerUtils.isChargeLimitEnabledForUser(mSecureSettings, userId);
            Log.d("ChargeLimitController", "current charge limit= " + isChargeLimitEnabled);
            mChargeLimitController.setChargingPolicy(isChargeLimitEnabled ? 2 : 1);
        }
    }
}
