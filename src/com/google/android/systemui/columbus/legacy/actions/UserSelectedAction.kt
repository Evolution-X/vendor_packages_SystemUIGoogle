package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.USER_SELECTABLE_ACTIONS
import com.google.android.systemui.columbus.legacy.ColumbusSettings
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import com.google.android.systemui.columbus.util.Listenable
import javax.inject.Inject
import javax.inject.Named

@SysUISingleton
class UserSelectedAction
@Inject
constructor(
    context: Context,
    columbusSettings: ColumbusSettings,
    @Named(USER_SELECTABLE_ACTIONS)
    private val userSelectableActions: Map<String, @JvmSuppressWildcards UserAction>,
    private val defaultUserAction: TakeScreenshot,
    private val keyguardStateController: KeyguardStateController,
    private val powerManager: PowerManager,
    wakefulnessLifecycle: WakefulnessLifecycle,
) : Action(context, null, null) {
    private var currentAction: UserAction =
        userSelectableActions.getOrDefault(columbusSettings.selectedAction(), defaultUserAction)

    override val tag: String
        get() = currentAction.tag

    private val settingsChangeListener =
        object : ColumbusSettings.ColumbusSettingsChangeListener {
            override fun onSelectedActionChange(action: String) {
                val userAction = userSelectableActions.getOrDefault(action, defaultUserAction)
                if (userAction == currentAction) return
                currentAction.updateFeedbackEffects(0, null)
                currentAction = userAction
                Log.i(TAG, "User Action selected: $userAction")
                updateAvailable()
            }
        }

    private val sublistener = Listenable.Listener {
        if (currentAction == it) {
            updateAvailable()
        }
    }

    private val keyguardMonitorCallback =
        object : KeyguardStateController.Callback {
            override fun onKeyguardShowingChanged() {
                updateAvailable()
            }
        }

    private val wakefulnessLifecycleObserver =
        object : WakefulnessLifecycle.Observer {
            override fun onStartedWakingUp() {
                updateAvailable()
            }

            override fun onFinishedGoingToSleep() {
                updateAvailable()
            }
        }

    init {
        Log.i(TAG, "User Action selected: $currentAction")
        columbusSettings.registerColumbusSettingsChangeListener(settingsChangeListener)
        userSelectableActions.values.forEach { it.registerListener(sublistener) }
        keyguardStateController.addCallback(keyguardMonitorCallback)
        wakefulnessLifecycle.addObserver(wakefulnessLifecycleObserver)
        updateAvailable()
    }

    override fun onGestureDetected(
        flags: Int,
        detectionProperties: GestureSensor.DetectionProperties?,
    ) {
        currentAction.onGestureDetected(flags, detectionProperties)
    }

    override fun executeOnTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        currentAction.executeOnTrigger(detectionProperties)
    }

    override fun updateFeedbackEffects(
        flags: Int,
        detectionProperties: GestureSensor.DetectionProperties?,
    ) {
        currentAction.updateFeedbackEffects(flags, detectionProperties)
    }

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        throw UnsupportedOperationException("UserSelectedAction#onTrigger called directly")
    }

    private fun updateAvailable() {
        val available =
            when {
                !currentAction.isAvailable -> false
                !currentAction.availableOnScreenOff() && !powerManager.isInteractive -> false
                else -> currentAction.availableOnLockscreen() || !keyguardStateController.isShowing
            }
        setAvailable(available)
    }

    override fun toString(): String = super.toString() + " [currentAction -> $currentAction]"

    private companion object {
        const val TAG = "Columbus/SelectedAction"
    }
}
