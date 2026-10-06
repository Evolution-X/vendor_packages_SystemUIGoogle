package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shade.ShadeController
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class SettingsAction
@Inject
constructor(
    context: Context,
    private val shadeController: ShadeController,
    private val uiEventLogger: UiEventLogger,
    @Background executor: Executor,
) : ServiceAction(context, executor) {
    override val tag: String
        get() = "Columbus/SettingsAction"

    override val supportedCallerPackages: Set<String>
        get() = setOf("com.android.settings")

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        uiEventLogger.log(ColumbusEvent.COLUMBUS_INVOKED_ON_SETTINGS)
        shadeController.cancelExpansionAndCollapseShade()
        super.onTrigger(detectionProperties)
    }
}
