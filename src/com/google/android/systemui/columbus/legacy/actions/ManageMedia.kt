package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class ManageMedia
@Inject
constructor(
    context: Context,
    private val audioManager: AudioManager,
    private val uiEventLogger: UiEventLogger,
    @Main executor: Executor,
) : UserAction(context, executor) {
    override val tag = "Columbus/ManageMedia"

    init {
        setAvailable(true)
    }

    override fun availableOnLockscreen() = true

    override fun availableOnScreenOff() = true

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        val isMusicActive = audioManager.isMusicActive || audioManager.isMusicActiveRemotely
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        )
        uiEventLogger.log(
            if (isMusicActive) {
                ColumbusEvent.COLUMBUS_INVOKED_PAUSE_MEDIA
            } else {
                ColumbusEvent.COLUMBUS_INVOKED_PLAY_MEDIA
            }
        )
    }
}
