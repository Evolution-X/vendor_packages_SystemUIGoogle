package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import com.google.android.systemui.columbus.legacy.feedback.FeedbackEffect
import java.util.concurrent.Executor

abstract class UserAction(
    context: Context,
    executor: Executor?,
    feedbackEffects: Set<FeedbackEffect>? = null,
) : Action(context, executor, feedbackEffects) {
    open fun availableOnLockscreen(): Boolean = false

    open fun availableOnScreenOff(): Boolean = false
}
