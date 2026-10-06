package com.google.android.systemui.columbus.legacy

import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.shared.system.InputMonitorCompat
import com.google.android.systemui.columbus.ColumbusStartable
import com.google.android.systemui.columbus.legacy.actions.Action
import com.google.android.systemui.columbus.legacy.actions.DismissTimer
import com.google.android.systemui.columbus.legacy.actions.LaunchApp
import com.google.android.systemui.columbus.legacy.actions.LaunchOpa
import com.google.android.systemui.columbus.legacy.actions.LaunchOverview
import com.google.android.systemui.columbus.legacy.actions.ManageMedia
import com.google.android.systemui.columbus.legacy.actions.OpenNotificationShade
import com.google.android.systemui.columbus.legacy.actions.SettingsAction
import com.google.android.systemui.columbus.legacy.actions.SilenceCall
import com.google.android.systemui.columbus.legacy.actions.SnoozeAlarm
import com.google.android.systemui.columbus.legacy.actions.TakeScreenshot
import com.google.android.systemui.columbus.legacy.actions.ToggleFlashlight
import com.google.android.systemui.columbus.legacy.actions.UnpinNotifications
import com.google.android.systemui.columbus.legacy.actions.UserAction
import com.google.android.systemui.columbus.legacy.actions.UserSelectedAction
import com.google.android.systemui.columbus.legacy.feedback.FeedbackEffect
import com.google.android.systemui.columbus.legacy.feedback.HapticClick
import com.google.android.systemui.columbus.legacy.feedback.UserActivity
import com.google.android.systemui.columbus.legacy.gates.CameraVisibility
import com.google.android.systemui.columbus.legacy.gates.ChargingState
import com.google.android.systemui.columbus.legacy.gates.FlagEnabled
import com.google.android.systemui.columbus.legacy.gates.Gate
import com.google.android.systemui.columbus.legacy.gates.KeyguardProximity
import com.google.android.systemui.columbus.legacy.gates.PowerSaveState
import com.google.android.systemui.columbus.legacy.gates.PowerState
import com.google.android.systemui.columbus.legacy.gates.ScreenTouch
import com.google.android.systemui.columbus.legacy.gates.SetupWizard
import com.google.android.systemui.columbus.legacy.gates.SystemKeyPress
import com.google.android.systemui.columbus.legacy.gates.TelephonyActivity
import com.google.android.systemui.columbus.legacy.gates.UsbState
import com.google.android.systemui.columbus.legacy.sensors.CHREGestureSensor
import com.google.android.systemui.columbus.legacy.sensors.CHREGestureSensorDelegator
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import com.google.android.systemui.columbus.legacy.sensors.GestureSensorImpl
import com.google.android.systemui.columbus.legacy.sensors.config.LowSensitivitySettingAdjustment
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Named

@Module
abstract class ColumbusModule {
    @Binds
    @IntoMap
    @ClassKey(ColumbusStartable::class)
    abstract fun bindColumbusStartable(startable: ColumbusStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(ColumbusTargetRequestService::class)
    abstract fun bindColumbusTargetRequestService(service: ColumbusTargetRequestService): Service

    companion object {
        const val TRANSIENT_GATE_DURATION = "columbus_transient_gate_duration"
        const val BLOCKING_SYSTEM_KEYS = "columbus_blocking_system_keys"
        const val QUICK_TAP_INPUT_MONITOR = "columbus_input_monitor"
        const val FULLSCREEN_ACTIONS = "columbus_fullscreen_actions"
        const val SETUP_WIZARD_EXCEPTIONS = "columbus_setup_wizard_exceptions"
        const val USER_SELECTABLE_ACTIONS = "columbus_user_selectable_actions"
        const val COLUMBUS_ACTIONS = "columbus_actions"
        const val COLUMBUS_EFFECTS = "columbus_effects"
        const val COLUMBUS_GATES = "columbus_gates"
        const val COLUMBUS_SOFT_GATES = "columbus_soft_gates"

        const val LONG_PRESS_ADDED_DURATION = 500L
        const val ACTION_UP_DURATION = 250L

        private const val TAG = "Columbus/Module"

        @Provides @Named(TRANSIENT_GATE_DURATION) fun provideTransientGateDuration(): Long = 500L

        @Provides
        @Named(BLOCKING_SYSTEM_KEYS)
        fun provideBlockingSystemKeys(): Set<@JvmSuppressWildcards Int> =
            setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER)

        @Provides
        @Named(QUICK_TAP_INPUT_MONITOR)
        fun provideInputMonitor(): InputMonitorCompat =
            InputMonitorCompat("Quick Tap", Display.DEFAULT_DISPLAY)

        @Provides
        @Named(FULLSCREEN_ACTIONS)
        fun provideFullscreenActions(
            dismissTimer: DismissTimer,
            snoozeAlarm: SnoozeAlarm,
            silenceCall: SilenceCall,
            settingsAction: SettingsAction,
        ): List<@JvmSuppressWildcards Action> =
            listOf(dismissTimer, snoozeAlarm, silenceCall, settingsAction)

        @Provides
        @Named(SETUP_WIZARD_EXCEPTIONS)
        fun provideSetupWizardExceptions(
            settingsAction: SettingsAction
        ): Set<@JvmSuppressWildcards Action> = setOf(settingsAction)

        @Provides
        @Named(USER_SELECTABLE_ACTIONS)
        fun provideUserSelectableActions(
            launchOpa: LaunchOpa,
            manageMedia: ManageMedia,
            takeScreenshot: TakeScreenshot,
            launchOverview: LaunchOverview,
            openNotificationShade: OpenNotificationShade,
            launchApp: LaunchApp,
            toggleFlashlight: ToggleFlashlight,
        ): Map<String, @JvmSuppressWildcards UserAction> =
            mapOf(
                "assistant" to launchOpa,
                "media" to manageMedia,
                "screenshot" to takeScreenshot,
                "overview" to launchOverview,
                "notifications" to openNotificationShade,
                "launch" to launchApp,
                "flashlight" to toggleFlashlight,
            )

        @Provides
        @Named(COLUMBUS_ACTIONS)
        fun provideColumbusActions(
            @Named(FULLSCREEN_ACTIONS) fullscreenActions: List<@JvmSuppressWildcards Action>,
            unpinNotifications: UnpinNotifications,
            userSelectedAction: UserSelectedAction,
        ): List<@JvmSuppressWildcards Action> =
            fullscreenActions + unpinNotifications + userSelectedAction

        @Provides
        @Named(COLUMBUS_EFFECTS)
        fun provideColumbusEffects(
            hapticClick: HapticClick,
            userActivity: UserActivity,
        ): Set<@JvmSuppressWildcards FeedbackEffect> = setOf(hapticClick, userActivity)

        @Provides
        @Named(COLUMBUS_GATES)
        fun provideColumbusGates(
            flagEnabled: FlagEnabled,
            keyguardProximity: KeyguardProximity,
            setupWizard: SetupWizard,
            telephonyActivity: TelephonyActivity,
            cameraVisibility: CameraVisibility,
            powerSaveState: PowerSaveState,
            powerState: PowerState,
        ): Set<@JvmSuppressWildcards Gate> =
            setOf(
                flagEnabled,
                keyguardProximity,
                setupWizard,
                telephonyActivity,
                cameraVisibility,
                powerSaveState,
                powerState,
            )

        @Provides
        @Named(COLUMBUS_SOFT_GATES)
        fun provideColumbusSoftGates(
            chargingState: ChargingState,
            usbState: UsbState,
            systemKeyPress: SystemKeyPress,
            screenTouch: ScreenTouch,
        ): Set<@JvmSuppressWildcards Gate> =
            setOf(chargingState, usbState, systemKeyPress, screenTouch)

        @Provides
        fun provideGestureAdjustments(
            lowSensitivitySettingAdjustment: LowSensitivitySettingAdjustment
        ): List<@JvmSuppressWildcards LowSensitivitySettingAdjustment> =
            listOf(lowSensitivitySettingAdjustment)

        @Provides
        @SysUISingleton
        fun provideGestureSensor(
            context: Context,
            columbusSettings: ColumbusSettings,
            featureFlags: FeatureFlags,
            chreGestureSensor: Lazy<CHREGestureSensor>,
            chreGestureSensorDelegator: Lazy<CHREGestureSensorDelegator>,
            gestureSensorImpl: Lazy<GestureSensorImpl>,
        ): GestureSensor =
            when {
                columbusSettings.useApSensor() ||
                    !context.packageManager.hasSystemFeature(
                        PackageManager.FEATURE_CONTEXT_HUB
                    ) -> {
                    Log.i(TAG, "Creating AP sensor")
                    gestureSensorImpl.get()
                }
                featureFlags.isEnabled(Flags.QUICK_TAP_IN_PCC) -> {
                    Log.i(TAG, "Creating CHRE sensor delegator")
                    chreGestureSensorDelegator.get()
                }
                else -> {
                    Log.i(TAG, "Creating CHRE sensor")
                    chreGestureSensor.get()
                }
            }
    }
}
