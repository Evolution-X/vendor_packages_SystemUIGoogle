package com.google.android.systemui.columbus

import android.content.Context
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.columbus.legacy.ColumbusServiceWrapper
import dagger.Lazy
import java.io.PrintWriter
import javax.inject.Inject

// Stock starts ColumbusServiceWrapper from GoogleServices, which does not exist here.
@SysUISingleton
class ColumbusStartable
@Inject
constructor(
    private val context: Context,
    private val columbusServiceWrapper: Lazy<ColumbusServiceWrapper>,
) : CoreStartable {
    private var started = false

    override fun start() {
        if (context.packageManager.hasSystemFeature(FEATURE_QUICK_TAP)) {
            columbusServiceWrapper.get()
            started = true
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        if (started) {
            columbusServiceWrapper.get().dump(pw, args)
        }
    }

    private companion object {
        const val FEATURE_QUICK_TAP = "com.google.android.feature.QUICK_TAP"
    }
}
