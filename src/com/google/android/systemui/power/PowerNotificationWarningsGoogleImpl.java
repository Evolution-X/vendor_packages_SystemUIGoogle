package com.google.android.systemui.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import com.android.internal.logging.UiEventLogger;
import com.android.settingslib.fuelgauge.BatteryStatus;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.animation.Expandable;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.power.BatteryStateSnapshot;
import com.android.systemui.power.PowerNotificationWarnings;
import com.android.systemui.res.R;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.util.settings.GlobalSettings;

import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType;
import com.google.android.systemui.power.batteryevent.common.module.BaseBatteryEventModule;
import com.google.android.systemui.power.batteryevent.common.module.ExtremeLowBatteryEventModule;
import com.google.android.systemui.power.batteryevent.common.module.LowBatteryEventModule;
import com.google.android.systemui.power.batteryevent.common.module.SevereLowBatteryEventModule;

import dagger.Lazy;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
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
    private final UiEventLogger mUiEventLogger;
    private final SevereLowBatteryNotification mSevereLowBatteryNotification;
    private final LowPowerWarningsController mLowPowerWarningsController;
    private final Provider<BatterySaverConfirmationDialog> mBatterySaverConfirmationDialogProvider;
    private final Lazy<BatteryController> mBatteryControllerLazy;
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
                    switch (action) {
                        case Intent.ACTION_BATTERY_CHANGED:
                            handleBatteryChanged(intent);
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
                    }
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
            SystemUIDialog.Factory systemUIDialogFactory,
            BroadcastDispatcher broadcastDispatcher,
            GlobalSettings globalSettings,
            @Background Executor backgroundExecutor,
            @Main Handler mainHandler,
            SevereLowBatteryNotification severeLowBatteryNotification,
            Provider<BatterySaverConfirmationDialog> batterySaverConfirmationDialogProvider) {
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
        mExecutor = backgroundExecutor;
        mHandler = mainHandler;
        mUiEventLogger = uiEventLogger;
        mSevereLowBatteryNotification = severeLowBatteryNotification;
        mBatterySaverConfirmationDialogProvider = batterySaverConfirmationDialogProvider;
        mLowPowerWarningsController =
                new LowPowerWarningsController(
                        context,
                        backgroundExecutor,
                        globalSettings,
                        uiEventLogger,
                        severeLowBatteryNotification);

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
        mBroadcastDispatcher.registerReceiver(mBroadcastReceiver, filter);
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

    private void handleBatteryChanged(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int batteryLevel = BatteryStatus.getBatteryLevel(level, scale);
        updateBatteryEvent(batteryLevel, plugged);
    }

    private void updateBatteryEvent(int batteryLevel, int plugged) {
        List<BatteryEventType> events = evaluateBatteryEvents(batteryLevel, plugged);
        if (mLowPowerWarningsController != null) {
            mLowPowerWarningsController.onBatteryEventUpdate(batteryLevel, events);
        }
    }

    private final List<BaseBatteryEventModule> mBatteryEventModules =
            List.of(
                    new ExtremeLowBatteryEventModule(),
                    new SevereLowBatteryEventModule(),
                    new LowBatteryEventModule());

    private List<BatteryEventType> evaluateBatteryEvents(int batteryLevel, int plugged) {
        for (BaseBatteryEventModule module : mBatteryEventModules) {
            if (module.validate(batteryLevel, plugged)) {
                return Collections.singletonList(module.getModuleType());
            }
        }
        return Collections.emptyList();
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
    public void updateSnapshot(BatteryStateSnapshot snapshot) {
        super.updateSnapshot(snapshot);
        int plugged = snapshot.getPlugged() ? BatteryManager.BATTERY_PLUGGED_AC : 0;
        updateBatteryEvent(snapshot.getBatteryLevel(), plugged);
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
    }
}
