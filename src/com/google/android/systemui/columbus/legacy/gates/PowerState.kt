package com.google.android.systemui.columbus.legacy.gates

import android.os.PowerManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.WakefulnessLifecycle
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class PowerState
@Inject
constructor(
    private val powerManager: Lazy<PowerManager>,
    private val wakefulnessLifecycle: Lazy<WakefulnessLifecycle>,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : Gate() {
    private val wakefulnessLifecycleObserver =
        object : WakefulnessLifecycle.Observer {
            override fun onFinishedGoingToSleep() {
                updateBlocking()
            }

            override fun onStartedWakingUp() {
                updateBlocking()
            }
        }

    override fun onActivate() {
        wakefulnessLifecycle.get().addObserver(wakefulnessLifecycleObserver)
        updateBlocking()
    }

    override fun onDeactivate() {
        wakefulnessLifecycle.get().removeObserver(wakefulnessLifecycleObserver)
    }

    private fun updateBlocking() {
        coroutineScope.launch(mainDispatcher) {
            setBlocking(withContext(bgDispatcher) { !powerManager.get().isInteractive })
        }
    }
}
