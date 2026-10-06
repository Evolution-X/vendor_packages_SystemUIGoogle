package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import android.os.Handler
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.policy.FlashlightController
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SysUISingleton
class ToggleFlashlight
@Inject
constructor(
    context: Context,
    private val flashlightController: FlashlightController,
    @Main private val handler: Handler,
    private val uiEventLogger: UiEventLogger,
    @Main executor: Executor,
) : UserAction(context, executor) {
    override val tag = "ToggleFlashlight"

    private val turnOffFlashlight = Runnable { flashlightController.setFlashlight(false) }

    private val flashlightListener =
        object : FlashlightController.FlashlightListener {
            override fun onFlashlightChanged(enabled: Boolean) {
                if (!enabled) {
                    handler.removeCallbacks(turnOffFlashlight)
                }
                updateAvailable()
            }

            override fun onFlashlightError() {
                handler.removeCallbacks(turnOffFlashlight)
                updateAvailable()
            }

            override fun onFlashlightAvailabilityChanged(available: Boolean) {
                if (!available) {
                    handler.removeCallbacks(turnOffFlashlight)
                }
                updateAvailable()
            }
        }

    init {
        flashlightController.addCallback(flashlightListener)
        updateAvailable()
    }

    override fun availableOnLockscreen() = true

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        handler.removeCallbacks(turnOffFlashlight)
        val enabled = flashlightController.isEnabled
        flashlightController.setFlashlight(!enabled)
        if (!enabled) {
            handler.postDelayed(turnOffFlashlight, FLASHLIGHT_TIMEOUT)
        }
        uiEventLogger.log(ColumbusEvent.COLUMBUS_INVOKED_FLASHLIGHT_TOGGLE)
    }

    private fun updateAvailable() {
        setAvailable(flashlightController.hasFlashlight() && flashlightController.isAvailable)
    }

    private companion object {
        val FLASHLIGHT_TIMEOUT = TimeUnit.MINUTES.toMillis(5)
    }
}
