package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.headsup.OnHeadsUpChangedListener
import com.google.android.systemui.columbus.legacy.gates.SilenceAlertsDisabled
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import com.google.android.systemui.columbus.util.Listenable
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class UnpinNotifications
@Inject
constructor(
    context: Context,
    private val silenceAlertsDisabled: SilenceAlertsDisabled,
    private val headsUpManager: HeadsUpManager,
    @Main executor: Executor,
) : Action(context, executor, null) {
    override val tag = "Columbus/UnpinNotif"
    private var hasPinnedHeadsUp = false

    private val headsUpChangedListener =
        object : OnHeadsUpChangedListener {
            override fun onHeadsUpPinnedModeChanged(inPinnedMode: Boolean) {
                hasPinnedHeadsUp = inPinnedMode
                updateAvailable()
            }
        }

    private val gateListener = Listenable.Listener {
        if (silenceAlertsDisabled.isBlocking()) {
            headsUpManager.removeListener(headsUpChangedListener)
        } else {
            headsUpManager.addListener(headsUpChangedListener)
            hasPinnedHeadsUp = headsUpManager.hasPinnedHeadsUp()
        }
    }

    init {
        silenceAlertsDisabled.registerListener(gateListener)
        updateAvailable()
    }

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        headsUpManager.unpinAll(true, "legacy UnpinNotifications")
    }

    private fun updateAvailable() {
        setAvailable(!silenceAlertsDisabled.isBlocking() && hasPinnedHeadsUp)
    }
}
