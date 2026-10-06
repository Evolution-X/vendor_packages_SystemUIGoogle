package com.google.android.systemui.columbus.legacy.gates

import android.view.KeyEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.CommandQueue
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.BLOCKING_SYSTEM_KEYS
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.TRANSIENT_GATE_DURATION
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Named

@SysUISingleton
class SystemKeyPress
@Inject
constructor(
    private val commandQueue: Lazy<CommandQueue>,
    @Named(TRANSIENT_GATE_DURATION) private val gateDuration: Long,
    @Named(BLOCKING_SYSTEM_KEYS) private val blockingKeys: Set<@JvmSuppressWildcards Int>,
) : TransientGate() {
    private val commandQueueCallbacks =
        object : CommandQueue.Callbacks {
            override fun handleSystemKey(arg1: KeyEvent) {
                if (blockingKeys.contains(arg1.keyCode)) {
                    blockForMillis(gateDuration)
                }
            }
        }

    override fun onActivate() {
        commandQueue.get().addCallback(commandQueueCallbacks)
    }

    override fun onDeactivate() {
        commandQueue.get().removeCallback(commandQueueCallbacks)
    }
}
