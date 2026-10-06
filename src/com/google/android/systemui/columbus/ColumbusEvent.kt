package com.google.android.systemui.columbus

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

enum class ColumbusEvent(private val id: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "Quick Tap detected a double tap") COLUMBUS_DOUBLE_TAP_DETECTED(628),
    @UiEvent(doc = "Quick Tap invoked the assistant") COLUMBUS_INVOKED_ASSISTANT(629),
    @UiEvent(doc = "Quick Tap took a screenshot") COLUMBUS_INVOKED_SCREENSHOT(630),
    @UiEvent(doc = "Quick Tap played media") COLUMBUS_INVOKED_PLAY_MEDIA(631),
    @UiEvent(doc = "Quick Tap paused media") COLUMBUS_INVOKED_PAUSE_MEDIA(639),
    @UiEvent(doc = "Quick Tap opened overview") COLUMBUS_INVOKED_OVERVIEW(632),
    @UiEvent(doc = "Quick Tap opened the notification shade")
    COLUMBUS_INVOKED_NOTIFICATION_SHADE_OPEN(633),
    @UiEvent(doc = "Quick Tap closed the notification shade")
    COLUMBUS_INVOKED_NOTIFICATION_SHADE_CLOSE(634),
    @UiEvent(doc = "Quick Tap launched an app") COLUMBUS_INVOKED_LAUNCH_APP(815),
    @UiEvent(doc = "Quick Tap launched a shortcut") COLUMBUS_INVOKED_LAUNCH_SHORTCUT(816),
    @UiEvent(doc = "Quick Tap launched an app over the keyguard")
    COLUMBUS_INVOKED_LAUNCH_APP_SECURE(898),
    @UiEvent(doc = "Quick Tap toggled the flashlight") COLUMBUS_INVOKED_FLASHLIGHT_TOGGLE(932),
    @UiEvent(doc = "Quick Tap was triggered on the settings page")
    COLUMBUS_INVOKED_ON_SETTINGS(817),
    @UiEvent(doc = "Quick Tap is listening in low power mode") COLUMBUS_MODE_LOW_POWER_ACTIVE(635),
    @UiEvent(doc = "Quick Tap is listening in high power mode")
    COLUMBUS_MODE_HIGH_POWER_ACTIVE(636),
    @UiEvent(doc = "Quick Tap stopped listening") COLUMBUS_MODE_INACTIVE(637),
    @UiEvent(doc = "Quick Tap target request dialog was shown") COLUMBUS_RETARGET_DIALOG_SHOWN(899),
    @UiEvent(doc = "Quick Tap target request was approved") COLUMBUS_RETARGET_APPROVED(900),
    @UiEvent(doc = "Quick Tap target request was not approved") COLUMBUS_RETARGET_NOT_APPROVED(901),
    @UiEvent(doc = "Follow-on Quick Tap target request was approved")
    COLUMBUS_RETARGET_FOLLOW_ON_APPROVED(902),
    @UiEvent(doc = "Follow-on Quick Tap target request was not approved")
    COLUMBUS_RETARGET_FOLLOW_ON_NOT_APPROVED(903);

    override fun getId() = id
}
