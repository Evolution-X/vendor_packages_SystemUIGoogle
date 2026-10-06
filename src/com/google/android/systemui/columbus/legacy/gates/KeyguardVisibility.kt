package com.google.android.systemui.columbus.legacy.gates

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.policy.KeyguardStateController
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
class KeyguardVisibility
@Inject
constructor(private val keyguardStateController: Lazy<KeyguardStateController>) : Gate() {
    private val keyguardMonitorCallback =
        object : KeyguardStateController.Callback {
            override fun onKeyguardShowingChanged() {
                setBlocking(keyguardStateController.get().isShowing)
            }
        }

    override fun onActivate() {
        keyguardStateController.get().addCallback(keyguardMonitorCallback)
        setBlocking(keyguardStateController.get().isShowing)
    }

    override fun onDeactivate() {
        keyguardStateController.get().removeCallback(keyguardMonitorCallback)
    }
}
