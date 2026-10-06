package com.google.android.systemui.columbus.legacy.gates

import android.os.Looper
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shared.system.InputChannelCompat
import com.android.systemui.shared.system.InputMonitorCompat
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.ACTION_UP_DURATION
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.LONG_PRESS_ADDED_DURATION
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.QUICK_TAP_INPUT_MONITOR
import com.google.android.systemui.columbus.util.Listenable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import kotlinx.coroutines.launch

@SysUISingleton
class ScreenTouch
@Inject
constructor(
    private val powerState: PowerState,
    @Named(QUICK_TAP_INPUT_MONITOR) private val inputMonitorProvider: Provider<InputMonitorCompat>,
) : TransientGate() {
    private var inputMonitor: InputMonitorCompat? = null
    private var inputEventReceiver: InputChannelCompat.InputEventReceiver? = null

    private val gateListener = Listenable.Listener {
        if (powerState.isBlocking()) {
            stopListeningForTouch()
        } else {
            startListeningForTouch()
        }
    }

    private val inputEventListener = InputChannelCompat.InputEventListener { event ->
        if (event !is MotionEvent) return@InputEventListener
        val blockDuration =
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN ->
                    ViewConfiguration.getLongPressTimeout() + LONG_PRESS_ADDED_DURATION
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> ACTION_UP_DURATION
                else -> return@InputEventListener
            }
        blockForMillis(blockDuration)
    }

    override fun onActivate() {
        powerState.registerListener(gateListener)
        if (!powerState.isBlocking()) {
            startListeningForTouch()
        }
        setBlocking(false)
    }

    override fun onDeactivate() {
        powerState.unregisterListener(gateListener)
        stopListeningForTouch()
    }

    private fun startListeningForTouch() {
        coroutineScope.launch {
            if (inputEventReceiver == null) {
                inputMonitor = inputMonitorProvider.get()
                inputEventReceiver =
                    inputMonitor?.getInputReceiver(
                        Looper.getMainLooper(),
                        Choreographer.getInstance(),
                        inputEventListener,
                    )
            }
        }
    }

    private fun stopListeningForTouch() {
        coroutineScope.launch {
            inputEventReceiver?.dispose()
            inputEventReceiver = null
            inputMonitor?.dispose()
            inputMonitor = null
        }
    }
}
