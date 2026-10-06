package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.shade.ShadeController
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import dagger.Lazy
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class OpenNotificationShade
@Inject
constructor(
    context: Context,
    private val notificationShadeWindowController: Lazy<NotificationShadeWindowController>,
    private val shadeController: ShadeController,
    private val uiEventLogger: UiEventLogger,
    @Main executor: Executor,
) : UserAction(context, executor) {
    override val tag = "Columbus/OpenNotif"

    init {
        setAvailable(true)
    }

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        if (notificationShadeWindowController.get().panelExpanded) {
            shadeController.animateCollapseShade(CommandQueue.FLAG_EXCLUDE_NONE)
            uiEventLogger.log(ColumbusEvent.COLUMBUS_INVOKED_NOTIFICATION_SHADE_CLOSE)
        } else {
            shadeController.animateExpandShade()
            uiEventLogger.log(ColumbusEvent.COLUMBUS_INVOKED_NOTIFICATION_SHADE_OPEN)
        }
    }
}
