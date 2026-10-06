package com.google.android.systemui.columbus.legacy.gates

import com.android.app.tracing.coroutines.runBlockingTraced as runBlocking
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.google.android.systemui.columbus.util.Listenable
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.launch

@SysUISingleton
class KeyguardProximity
@Inject
constructor(
    private val keyguardGate: KeyguardVisibility,
    private val proximity: Proximity,
    private val keyguardStateController: Lazy<KeyguardStateController>,
) : Gate() {
    private var isListening = false
    private val keyguardListener = Listenable.Listener { updateProximityListener() }
    private val proximityListener = Listenable.Listener { updateBlocking() }

    override fun onActivate() {
        keyguardGate.registerListener(keyguardListener)
        updateProximityListener()
    }

    override fun onDeactivate() {
        keyguardGate.unregisterListener(keyguardListener)
        updateProximityListener()
    }

    private fun updateProximityListener() {
        coroutineScope.launch {
            val keyguardState = keyguardStateController.get()
            if (
                isActive() &&
                    keyguardState.isShowing &&
                    !keyguardState.isOccluded
            ) {
                if (!isListening) {
                    proximity.registerListener(proximityListener)
                    isListening = true
                }
            } else if (isListening) {
                proximity.unregisterListener(proximityListener)
                isListening = false
            }
            updateBlocking()
        }
    }

    private fun updateBlocking() {
        coroutineScope.launch { setBlocking(isListening && proximity.isBlocking()) }
    }

    override fun toString(): String =
        super.toString() +
            runBlocking(context = mainDispatcher) {
                " [isListening -> $isListening; proximityBlocked -> ${proximity.isBlocking()}]"
            }
}
