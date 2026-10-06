package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.recents.Recents
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class LaunchOverview
@Inject
constructor(
    context: Context,
    private val recents: Recents,
    private val uiEventLogger: UiEventLogger,
    @Main executor: Executor,
) : UserAction(context, executor) {
    override val tag = "Columbus/LaunchOverview"

    init {
        setAvailable(true)
    }

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        recents.toggleRecentApps()
        uiEventLogger.log(ColumbusEvent.COLUMBUS_INVOKED_OVERVIEW)
    }
}
