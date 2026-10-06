package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import android.os.Handler
import android.view.WindowManager.ScreenshotSource
import com.android.internal.logging.UiEventLogger
import com.android.internal.util.ScreenshotHelper
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class TakeScreenshot
@Inject
constructor(
    context: Context,
    @Main private val handler: Handler,
    private val uiEventLogger: UiEventLogger,
    @Main executor: Executor,
) : UserAction(context, executor) {
    override val tag = "Columbus/TakeScreenshot"
    private val screenshotHelper = ScreenshotHelper(context)

    init {
        setAvailable(true)
    }

    override fun availableOnLockscreen() = true

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        screenshotHelper.takeScreenshot(ScreenshotSource.SCREENSHOT_VENDOR_GESTURE, handler, null)
        uiEventLogger.log(ColumbusEvent.COLUMBUS_INVOKED_SCREENSHOT)
    }
}
