package com.google.android.systemui.columbus.legacy

import android.os.PowerManager
import android.util.Log
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.concurrency.DelayableExecutor
import com.android.systemui.util.time.SystemClock
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.COLUMBUS_ACTIONS
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.COLUMBUS_EFFECTS
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.COLUMBUS_GATES
import com.google.android.systemui.columbus.legacy.actions.Action
import com.google.android.systemui.columbus.legacy.feedback.FeedbackEffect
import com.google.android.systemui.columbus.legacy.gates.Gate
import com.google.android.systemui.columbus.legacy.sensors.GestureController
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import com.google.android.systemui.columbus.util.Listenable
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Named

@SysUISingleton
class ColumbusService
@Inject
constructor(
    @Named(COLUMBUS_ACTIONS) private val actions: List<@JvmSuppressWildcards Action>,
    @Named(COLUMBUS_EFFECTS) private val effects: Set<@JvmSuppressWildcards FeedbackEffect>,
    @Named(COLUMBUS_GATES) private val gates: Set<@JvmSuppressWildcards Gate>,
    private val gestureController: GestureController,
    powerManager: PowerManager,
    @Main private val delayableExecutor: DelayableExecutor,
    private val systemClock: SystemClock,
) : Dumpable {
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
    private var lastActiveAction: Action? = null
    private var lastStateChangeTimestamp = 0L
    private var removeStartListeningRunnable: Runnable? = null
    private var removeStopListeningRunnable: Runnable? = null

    private val actionListener = Listenable.Listener { updateSensorListener() }
    private val gateListener = Listenable.Listener { updateSensorListener() }

    private val gestureListener = GestureSensor.Listener { flags, detectionProperties ->
        if (flags != 0) {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT)
        }
        updateActiveAction()?.let { action ->
            action.onGestureDetected(flags, detectionProperties)
            effects.forEach { it.onGestureDetected(flags, detectionProperties) }
        }
    }

    private val startListeningRunnable = Runnable {
        if (gestureController.startListening()) {
            lastStateChangeTimestamp = systemClock.elapsedRealtime()
        }
    }

    private val stopListeningRunnable = Runnable {
        if (gestureController.stopListening()) {
            lastStateChangeTimestamp = systemClock.elapsedRealtime()
            effects.forEach { it.onGestureDetected(0, null) }
            updateActiveAction()?.onGestureDetected(0, null)
        }
    }

    init {
        actions.forEach { it.registerListener(actionListener) }
        gestureController.gestureListener = gestureListener
        updateSensorListener()
    }

    private fun updateActiveAction(): Action? {
        val action = actions.firstOrNull { it.isAvailable }
        lastActiveAction?.let {
            if (action != it) {
                Log.i(TAG, "Switching action from $it to $action")
                it.onGestureDetected(0, null)
            }
        }
        lastActiveAction = action
        return action
    }

    private fun updateSensorListener() {
        val action = updateActiveAction()
        if (action == null) {
            Log.i(TAG, "No available actions")
            gates.forEach { it.unregisterListener(gateListener) }
            stopListening()
            return
        }
        gates.forEach { it.registerListener(gateListener) }
        val blockingGate = gates.firstOrNull { it.isBlocking() }
        if (blockingGate != null) {
            Log.i(TAG, "Gated by $blockingGate")
            stopListening()
            return
        }
        Log.i(TAG, "Unblocked; current action: $action")
        startListening()
    }

    private fun startListening() {
        removeStopListeningRunnable?.run()
        removeStopListeningRunnable = null
        removeStartListeningRunnable?.run()
        removeStartListeningRunnable =
            delayableExecutor.executeDelayed(startListeningRunnable, stateChangeDelay())
    }

    private fun stopListening() {
        removeStartListeningRunnable?.run()
        removeStartListeningRunnable = null
        removeStopListeningRunnable?.run()
        removeStopListeningRunnable =
            delayableExecutor.executeDelayed(stopListeningRunnable, stateChangeDelay())
    }

    private fun stateChangeDelay(): Long =
        maxOf(
            0,
            MIN_STATE_CHANGE_INTERVAL - (systemClock.elapsedRealtime() - lastStateChangeTimestamp),
        )

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("ColumbusService state:")
        pw.println("  Gates:")
        gates.forEach {
            pw.print("    ")
            pw.print(
                when {
                    !it.isActive() -> "- "
                    it.isBlocking() -> "X "
                    else -> "O "
                }
            )
            pw.println(it.toString())
        }
        pw.println("  Actions:")
        actions.forEach {
            pw.print("    ")
            pw.print(if (it.isAvailable) "O " else "X ")
            pw.println(it.toString())
        }
        pw.println("  Active: $lastActiveAction")
        pw.println("  Feedback Effects:")
        effects.forEach {
            pw.print("    ")
            pw.println(it.toString())
        }
        gestureController.dump(pw, args)
    }

    private companion object {
        const val TAG = "Columbus/Service"
        const val WAKE_LOCK_TIMEOUT = 2000L
        const val MIN_STATE_CHANGE_INTERVAL = 1000L
    }
}
