package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.telephony.TelephonyListenerManager
import com.google.android.systemui.columbus.legacy.gates.SilenceAlertsDisabled
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import com.google.android.systemui.columbus.util.Listenable
import dagger.Lazy
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class SilenceCall
@Inject
constructor(
    context: Context,
    private val silenceAlertsDisabled: SilenceAlertsDisabled,
    private val telecomManager: Lazy<TelecomManager?>,
    private val telephonyManager: Lazy<TelephonyManager>,
    private val telephonyListenerManager: Lazy<TelephonyListenerManager>,
    @Main executor: Executor,
) : Action(context, executor, null) {
    override val tag = "Columbus/SilenceCall"
    private var isPhoneRinging = false

    private val phoneStateListener = TelephonyCallback.CallStateListener { state ->
        isPhoneRinging = isPhoneRinging(state)
        updateAvailable()
    }

    private val gateListener = Listenable.Listener { updatePhoneStateListener() }

    init {
        silenceAlertsDisabled.registerListener(gateListener)
        updatePhoneStateListener()
    }

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        telecomManager.get()?.silenceRinger()
    }

    private fun updatePhoneStateListener() {
        if (silenceAlertsDisabled.isBlocking()) {
            telephonyListenerManager.get().removeCallStateListener(phoneStateListener)
        } else {
            telephonyListenerManager.get().addCallStateListener(phoneStateListener)
        }
        isPhoneRinging = isPhoneRinging(telephonyManager.get().callState)
        updateAvailable()
    }

    private fun updateAvailable() {
        setAvailable(!silenceAlertsDisabled.isBlocking() && isPhoneRinging)
    }

    private fun isPhoneRinging(state: Int) = state == TelephonyManager.CALL_STATE_RINGING

    override fun toString(): String = super.toString() + " [isPhoneRinging -> $isPhoneRinging]"
}
