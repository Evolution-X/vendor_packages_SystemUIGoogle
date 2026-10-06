package com.google.android.systemui.columbus.legacy.actions

import android.app.IActivityManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.google.android.systemui.columbus.legacy.gates.SilenceAlertsDisabled
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class SnoozeAlarm
@Inject
constructor(
    context: Context,
    silenceAlertsDisabled: SilenceAlertsDisabled,
    activityManager: IActivityManager,
    @Main executor: Executor,
) : DeskClockAction(context, silenceAlertsDisabled, activityManager, executor) {
    override val tag: String
        get() = "Columbus/SnoozeAlarm"

    override val alertAction: String
        get() = "com.google.android.deskclock.action.ALARM_ALERT"

    override val doneAction: String
        get() = "com.google.android.deskclock.action.ALARM_DONE"

    override fun createDismissIntent() = Intent(AlarmClock.ACTION_SNOOZE_ALARM)
}
