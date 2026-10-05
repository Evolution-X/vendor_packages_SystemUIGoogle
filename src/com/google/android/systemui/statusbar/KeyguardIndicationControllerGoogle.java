package com.google.android.systemui.statusbar;

import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.icu.text.DateFormat;
import android.os.BatteryManager;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.app.IBatteryStats;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.logging.KeyguardLogger;
import com.android.settingslib.fuelgauge.BatteryStatus;
import com.android.settingslib.fuelgauge.BatteryUtils;
import com.android.systemui.biometrics.AuthController;
import com.android.systemui.biometrics.FaceHelpMessageDeferralFactory;
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor;
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor;
import com.android.systemui.bouncer.domain.interactor.BouncerMessageInteractor;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.deviceentry.domain.interactor.BiometricMessageInteractor;
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBiometricSettingsInteractor;
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor;
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFingerprintAuthInteractor;
import com.android.systemui.dock.DockManager;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.util.IndicationHelper;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.KeyguardIndicationController;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.user.domain.interactor.UserLogoutInteractor;
import com.android.systemui.util.DeviceConfigProxy;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.settings.SecureSettings;
import com.android.systemui.util.wakelock.WakeLock;

import com.google.android.systemui.googlebattery.AdaptiveChargingManager;
import com.google.android.systemui.power.PowerUtils;
import com.google.android.systemui.res.R;

import dagger.Lazy;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

@SysUISingleton
public class KeyguardIndicationControllerGoogle extends KeyguardIndicationController {

    private static final String TAG = "KeyguardIndicationGoogle";
    private static final String ACTION_ADAPTIVE_CHARGING_DEADLINE_SET =
            "com.google.android.systemui.adaptivecharging.ADAPTIVE_CHARGING_DEADLINE_SET";
    private static final int CHARGE_LIMIT_LEVEL = 80;

    private final Context mContext;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final TunerService mTunerService;
    private final DeviceConfigProxy mDeviceConfig;
    private final SecureSettings mSecureSettings;
    private final UserTracker mUserTracker;
    protected AdaptiveChargingManager mAdaptiveChargingManager;

    private boolean mInited;
    private int mBatteryLevel;
    private boolean mAdaptiveChargingActive;
    private boolean mAdaptiveChargingEnabledInSettings;
    private long mEstimatedChargeCompletion;
    private KeyguardUpdateMonitorCallback mUpdateMonitorCallback;

    private final BroadcastReceiver mBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_ADAPTIVE_CHARGING_DEADLINE_SET.equals(intent.getAction())) {
                        triggerAdaptiveChargingStatusUpdate();
                    }
                }
            };

    protected final AdaptiveChargingManager.AdaptiveChargingStatusReceiver
            mAdaptiveChargingStatusReceiver =
                    new AdaptiveChargingManager.AdaptiveChargingStatusReceiver() {
                        @Override
                        public void onReceiveStatus(String stage, int deadlineSecs) {
                            boolean wasActive = mAdaptiveChargingActive;
                            mAdaptiveChargingActive =
                                    AdaptiveChargingManager.isActive(stage, deadlineSecs);
                            long previousCompletion = mEstimatedChargeCompletion;
                            mEstimatedChargeCompletion =
                                    TimeUnit.SECONDS.toMillis(deadlineSecs + 29)
                                            + System.currentTimeMillis();
                            long delta = Math.abs(mEstimatedChargeCompletion - previousCompletion);
                            if (wasActive != mAdaptiveChargingActive
                                    || (mAdaptiveChargingActive && delta > 30000)) {
                                updateDeviceEntryIndication(true);
                            }
                        }

                        @Override
                        public void onDestroyInterface() {}
                    };

    @Inject
    public KeyguardIndicationControllerGoogle(
            Context context,
            @Main Looper mainLooper,
            WakeLock.Builder wakeLockBuilder,
            KeyguardStateController keyguardStateController,
            StatusBarStateController statusBarStateController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            DockManager dockManager,
            BroadcastDispatcher broadcastDispatcher,
            DevicePolicyManager devicePolicyManager,
            IBatteryStats iBatteryStats,
            UserManager userManager,
            @Main DelayableExecutor executor,
            @Background DelayableExecutor bgExecutor,
            FalsingManager falsingManager,
            AuthController authController,
            LockPatternUtils lockPatternUtils,
            ScreenLifecycle screenLifecycle,
            KeyguardBypassController keyguardBypassController,
            AccessibilityManager accessibilityManager,
            FaceHelpMessageDeferralFactory faceHelpMessageDeferral,
            KeyguardLogger keyguardLogger,
            AlternateBouncerInteractor alternateBouncerInteractor,
            BouncerInteractor bouncerInteractor,
            AlarmManager alarmManager,
            UserTracker userTracker,
            BouncerMessageInteractor bouncerMessageInteractor,
            IndicationHelper indicationHelper,
            DeviceEntryBiometricSettingsInteractor deviceEntryBiometricSettingsInteractor,
            KeyguardInteractor keyguardInteractor,
            BiometricMessageInteractor biometricMessageInteractor,
            DeviceEntryFingerprintAuthInteractor deviceEntryFingerprintAuthInteractor,
            DeviceEntryFaceAuthInteractor deviceEntryFaceAuthInteractor,
            UserLogoutInteractor userLogoutInteractor,
            Lazy<SecureLockDeviceInteractor> secureLockDeviceInteractor,
            TunerService tunerService,
            DeviceConfigProxy deviceConfigProxy,
            SecureSettings secureSettings) {
        super(
                context,
                mainLooper,
                wakeLockBuilder,
                keyguardStateController,
                statusBarStateController,
                keyguardUpdateMonitor,
                dockManager,
                broadcastDispatcher,
                devicePolicyManager,
                iBatteryStats,
                userManager,
                executor,
                bgExecutor,
                falsingManager,
                authController,
                lockPatternUtils,
                screenLifecycle,
                keyguardBypassController,
                accessibilityManager,
                faceHelpMessageDeferral,
                keyguardLogger,
                alternateBouncerInteractor,
                bouncerInteractor,
                alarmManager,
                userTracker,
                bouncerMessageInteractor,
                indicationHelper,
                deviceEntryBiometricSettingsInteractor,
                keyguardInteractor,
                biometricMessageInteractor,
                deviceEntryFingerprintAuthInteractor,
                deviceEntryFaceAuthInteractor,
                userLogoutInteractor,
                secureLockDeviceInteractor);
        mContext = context;
        mBroadcastDispatcher = broadcastDispatcher;
        mTunerService = tunerService;
        mDeviceConfig = deviceConfigProxy;
        mSecureSettings = secureSettings;
        mUserTracker = userTracker;
        mAdaptiveChargingManager = new AdaptiveChargingManager(context);
    }

    @Override
    public void init() {
        super.init();
        if (mInited) {
            return;
        }
        mInited = true;
        mTunerService.addTunable(
                (key, newValue) -> refreshAdaptiveChargingEnabled(), "adaptive_charging_enabled");
        mDeviceConfig.addOnPropertiesChangedListener(
                "adaptive_charging",
                mExecutor,
                properties -> {
                    if (properties.getKeyset().contains("adaptive_charging_enabled")) {
                        triggerAdaptiveChargingStatusUpdate();
                    }
                });
        triggerAdaptiveChargingStatusUpdate();
        mBroadcastDispatcher.registerReceiver(
                mBroadcastReceiver,
                new IntentFilter(ACTION_ADAPTIVE_CHARGING_DEADLINE_SET),
                null,
                UserHandle.ALL);
    }

    @Override
    protected KeyguardUpdateMonitorCallback getKeyguardCallback() {
        if (mUpdateMonitorCallback == null) {
            mUpdateMonitorCallback = new GoogleKeyguardCallback();
        }
        return mUpdateMonitorCallback;
    }

    @Override
    protected boolean isPowerPluggedIn(BatteryStatus status, boolean isChargingOrFull) {
        if (status.isPluggedIn() && isChargingOrFull) {
            return true;
        }
        int userId = mUserTracker.getUserId();
        boolean isChargeLimitEnabled =
                PowerUtils.isChargeLimitEnabledForUser(mSecureSettings, userId);
        Log.d(TAG, "isChargeLimitEnabled= " + isChargeLimitEnabled + ", user= " + userId);
        if (status.level >= CHARGE_LIMIT_LEVEL && isChargeLimitEnabled) {
            Log.d(TAG, "Charge limit enabled, charging policy = " + status.chargingStatus);
            return status.chargingStatus == BatteryManager.CHARGING_POLICY_ADAPTIVE_LONGLIFE;
        }
        return false;
    }

    @Override
    protected boolean isBatteryDefender(BatteryStatus status) {
        return status.isBatteryDefender() && !isChargeLimitEnabled();
    }

    @Override
    protected String computePowerIndication() {
        if (mPowerPluggedIn && mAdaptiveChargingEnabledInSettings && mAdaptiveChargingActive) {
            return mContext.getString(
                    R.string.adaptive_charging_time_estimate,
                    formatPercentage(),
                    mAdaptiveChargingManager.formatTimeToFull(mEstimatedChargeCompletion));
        }
        return super.computePowerIndication();
    }

    @Override
    protected String computePowerChargingStringIndication() {
        if (mChargingStatus == BatteryManager.CHARGING_POLICY_ADAPTIVE_LONGLIFE
                && isChargeLimitEnabled()) {
            String percentage = formatPercentage();
            if (mChargingTimeRemaining > 0 && mBatteryLevel < CHARGE_LIMIT_LEVEL) {
                if (BatteryUtils.isChargingStringV2Enabled()) {
                    return mContext.getString(
                            R.string.keyguard_indication_charging_time_charge_limit,
                            getChargingTimeFormatted(mChargingTimeRemaining),
                            percentage);
                }
                return mContext.getString(
                        R.string.keyguard_indication_charging_time_charge_limit_v1,
                        Formatter.formatShortElapsedTimeRoundingUpToMinutes(
                                mContext, mChargingTimeRemaining),
                        percentage);
            }
            if (mBatteryLevel >= CHARGE_LIMIT_LEVEL) {
                return mContext.getString(
                        R.string.keyguard_indication_charging_time_reach_charge_limit, percentage);
            }
        }
        return super.computePowerChargingStringIndication();
    }

    private boolean isChargeLimitEnabled() {
        return PowerUtils.isChargeLimitEnabledForUser(mSecureSettings, mUserTracker.getUserId());
    }

    private String formatPercentage() {
        return NumberFormat.getPercentInstance().format(mBatteryLevel / 100f);
    }

    private String getChargingTimeFormatted(long chargingTimeRemaining) {
        long chargeCompletion = System.currentTimeMillis() + chargingTimeRemaining;
        if (chargingTimeRemaining >= 900000) {
            // Round up to the next 15 minutes.
            chargeCompletion = 900000 * ((Math.abs(chargeCompletion) + 900000 - 1) / 900000);
        }
        return DateFormat.getInstanceForSkeleton(
                        android.text.format.DateFormat.getTimeFormatString(mContext))
                .format(Date.from(Instant.ofEpochMilli(chargeCompletion)));
    }

    private void refreshAdaptiveChargingEnabled() {
        mAdaptiveChargingEnabledInSettings =
                mAdaptiveChargingManager.isAvailable() && mAdaptiveChargingManager.isEnabled();
    }

    private void triggerAdaptiveChargingStatusUpdate() {
        refreshAdaptiveChargingEnabled();
        if (!mAdaptiveChargingEnabledInSettings) {
            mAdaptiveChargingActive = false;
            return;
        }
        mAdaptiveChargingManager.queryStatus(mAdaptiveChargingStatusReceiver);
    }

    protected class GoogleKeyguardCallback extends BaseKeyguardCallback {
        @Override
        public void onRefreshBatteryInfo(BatteryStatus status) {
            mBatteryLevel = status.level;
            super.onRefreshBatteryInfo(status);
            if (mPowerPluggedIn) {
                triggerAdaptiveChargingStatusUpdate();
            } else {
                mAdaptiveChargingActive = false;
            }
        }
    }
}
