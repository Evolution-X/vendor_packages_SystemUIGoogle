package com.google.android.systemui.columbus.legacy.feedback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import com.android.internal.app.AssistUtils
import com.android.systemui.assist.AssistManager
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import javax.inject.Inject

// Stock reports progress to AssistManagerGoogle, which is not ported. AOSP AssistManager has
// the same onInvocationProgress() hook (with swapped arguments) feeding its default UI.
@SysUISingleton
class AssistInvocationEffect @Inject constructor(private val assistManager: AssistManager) :
    FeedbackEffect {
    private var animation: Animator? = null
    private var progress = 0f

    private val animatorUpdateListener = ValueAnimator.AnimatorUpdateListener {
        progress = it.animatedValue as Float
        assistManager.onInvocationProgress(AssistUtils.INVOCATION_TYPE_PHYSICAL_GESTURE, progress)
    }

    private val animatorListener =
        object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                progress = 0f
            }

            override fun onAnimationEnd(animation: Animator) {
                progress = 0f
            }
        }

    override fun onGestureDetected(
        flags: Int,
        detectionProperties: GestureSensor.DetectionProperties?,
    ) {
        when (flags) {
            0 -> cancelAnimation()
            1 -> {
                cancelAnimation()
                animation =
                    ValueAnimator.ofFloat(progress, 1f).apply {
                        duration = 200
                        interpolator = DecelerateInterpolator()
                        addUpdateListener(animatorUpdateListener)
                        addListener(animatorListener)
                        start()
                    }
            }
        }
    }

    private fun cancelAnimation() {
        animation?.let { if (it.isRunning) it.cancel() }
        animation = null
    }
}
