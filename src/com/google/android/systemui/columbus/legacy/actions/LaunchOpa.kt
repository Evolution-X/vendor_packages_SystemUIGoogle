package com.google.android.systemui.columbus.legacy.actions

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.internal.app.AssistUtils
import com.android.internal.logging.UiEventLogger
import com.android.systemui.assist.AssistManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.shade.ShadeController
import com.android.systemui.tuner.TunerService
import com.google.android.systemui.assist.OpaEnabledListener
import com.google.android.systemui.assist.OpaEnabledReceiver
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.ColumbusContentObserver
import com.google.android.systemui.columbus.legacy.feedback.AssistInvocationEffect
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import dagger.Lazy
import java.util.concurrent.Executor
import javax.inject.Inject

// Stock calls into AssistManagerGoogle, which is not ported. AOSP AssistManager provides the
// same startAssist() entry point, and OpaEnabledReceiver supplies the Opa availability state.
@SysUISingleton
class LaunchOpa
@Inject
constructor(
    context: Context,
    private val shadeController: ShadeController,
    assistInvocationEffect: AssistInvocationEffect,
    private val assistManager: AssistManager,
    private val opaEnabledReceiver: OpaEnabledReceiver,
    private val keyguardManager: Lazy<KeyguardManager>,
    tunerService: TunerService,
    contentObserverFactory: ColumbusContentObserver.Factory,
    private val uiEventLogger: UiEventLogger,
    @Main executor: Executor,
) : UserAction(context, executor, setOf(assistInvocationEffect)) {
    override val tag = "Columbus/LaunchOpa"

    private var isGestureEnabled = readGestureEnabled()
    private var enableForAnyAssistant =
        Settings.Secure.getInt(context.contentResolver, ASSIST_GESTURE_ANY_ASSISTANT, 0) == 1
    private var isOpaEnabled = false

    private val opaEnabledListener = OpaEnabledListener { _, eligible, agsaAssistant, opaEnabled ->
        val supported = agsaAssistant || enableForAnyAssistant
        Log.i(tag, "eligible: $eligible, supported: $supported, opa: $opaEnabled")
        isOpaEnabled = eligible && supported && opaEnabled
        updateAvailable()
    }

    private val tunable = TunerService.Tunable { key, newValue ->
        if (ASSIST_GESTURE_ANY_ASSISTANT == key) {
            enableForAnyAssistant = "1" == newValue
            opaEnabledReceiver.dispatchOpaEnabledState()
        }
    }

    init {
        contentObserverFactory
            .create(Settings.Secure.getUriFor(Settings.Secure.ASSIST_GESTURE_ENABLED)) {
                isGestureEnabled = readGestureEnabled()
                updateAvailable()
            }
            .activate()
        tunerService.addTunable(tunable, ASSIST_GESTURE_ANY_ASSISTANT)
        opaEnabledReceiver.addOpaEnabledListener(opaEnabledListener)
        updateAvailable()
    }

    override fun availableOnLockscreen() = true

    override fun availableOnScreenOff() = true

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        uiEventLogger.log(ColumbusEvent.COLUMBUS_INVOKED_ASSISTANT)
        shadeController.cancelExpansionAndCollapseShade()
        val bundle =
            Bundle().apply {
                putInt(
                    TRIGGERED_BY,
                    if (keyguardManager.get().isKeyguardLocked) {
                        TRIGGERED_BY_QUICK_TAP_LOCKED
                    } else {
                        TRIGGERED_BY_QUICK_TAP_UNLOCKED
                    },
                )
                putLong(LATENCY_ID, detectionProperties?.actionId ?: 0)
                putInt(
                    AssistUtils.INVOCATION_TYPE_KEY,
                    AssistUtils.INVOCATION_TYPE_PHYSICAL_GESTURE,
                )
            }
        assistManager.startAssist(bundle)
    }

    private fun readGestureEnabled() =
        Settings.Secure.getIntForUser(
            context.contentResolver,
            Settings.Secure.ASSIST_GESTURE_ENABLED,
            1,
            UserHandle.USER_CURRENT,
        ) != 0

    private fun updateAvailable() {
        setAvailable(isGestureEnabled && isOpaEnabled)
    }

    override fun toString(): String =
        super.toString() + " [isGestureEnabled -> $isGestureEnabled; isOpaEnabled -> $isOpaEnabled]"

    private companion object {
        const val ASSIST_GESTURE_ANY_ASSISTANT = "assist_gesture_any_assistant"
        const val TRIGGERED_BY = "triggered_by"
        const val TRIGGERED_BY_QUICK_TAP_UNLOCKED = 119
        const val TRIGGERED_BY_QUICK_TAP_LOCKED = 120
        const val LATENCY_ID = "latency_id"
    }
}
