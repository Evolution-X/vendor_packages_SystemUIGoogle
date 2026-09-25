/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.systemui.theme

import android.app.ActivityManager
import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.UserManager
import android.util.Log
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.SystemPropertiesHelper
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.theme.ThemeOverlayApplier
import com.android.systemui.theme.ThemeOverlayController
import com.android.systemui.user.utils.UserScopedService
import com.android.systemui.util.kotlin.JavaAdapter
import com.android.systemui.util.settings.SecureSettings
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class ThemeOverlayControllerGoogle
@Inject
constructor(
    private val context: Context,
    broadcastDispatcher: BroadcastDispatcher,
    @Background bgHandler: Handler,
    @Main mainExecutor: Executor,
    @Background bgExecutor: Executor,
    themeOverlayApplier: ThemeOverlayApplier,
    secureSettings: SecureSettings,
    wallpaperManager: WallpaperManager,
    userManager: UserManager,
    deviceProvisionedController: DeviceProvisionedController,
    private val userTracker: UserTracker,
    dumpManager: DumpManager,
    featureFlags: FeatureFlags,
    @Main resources: Resources,
    wakefulnessLifecycle: WakefulnessLifecycle,
    javaAdapter: JavaAdapter,
    keyguardTransitionInteractor: KeyguardTransitionInteractor,
    uiModeManager: UiModeManager,
    uiModeManagerProvider: UserScopedService<UiModeManager>,
    activityManager: ActivityManager,
    systemPropertiesHelper: SystemPropertiesHelper,
    configurationController: ConfigurationController,
) :
    ThemeOverlayController(
        context,
        broadcastDispatcher,
        bgHandler,
        mainExecutor,
        bgExecutor,
        themeOverlayApplier,
        secureSettings,
        wallpaperManager,
        userManager,
        deviceProvisionedController,
        userTracker,
        dumpManager,
        featureFlags,
        resources,
        wakefulnessLifecycle,
        javaAdapter,
        keyguardTransitionInteractor,
        uiModeManager,
        uiModeManagerProvider,
        activityManager,
        systemPropertiesHelper,
    ) {
    private val configurationChangedListener =
        object : ConfigurationController.ConfigurationListener {
            override fun onThemeChanged() {
                if (userTracker.userId != 0) {
                    return
                }
                try {
                    val bootColors = getBootColors()
                    for ((i, color) in bootColors.withIndex()) {
                        mSystemPropertiesHelper.set(
                            "persist.bootanim.color${i + 1}",
                            color.toString(),
                        )
                        Log.d(
                            TAG,
                            "Writing boot animation colors ${i + 1}: ${Integer.toHexString(color)}",
                        )
                    }
                } catch (unused: RuntimeException) {
                    Log.w(
                        TAG,
                        "Cannot set sysprop. Look for 'init' and 'dmesg' logs for more info.",
                    )
                }
            }
        }

    init {
        configurationController.addCallback(configurationChangedListener)
        val bootColors = getBootColors()
        for ((i, color) in bootColors.withIndex()) {
            Log.d(TAG, "Boot animation colors ${i + 1}: $color")
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        super.dump(pw, args)
        pw.println("ThemeOverlayControllerGoogle: yes")
    }

    fun getBootColors(): IntArray {
        return intArrayOf(
            context.getColor(com.android.internal.R.color.system_brand_a_dark),
            context.getColor(com.android.internal.R.color.system_brand_c_dark),
            context.getColor(com.android.internal.R.color.system_brand_d_dark),
            context.getColor(com.android.internal.R.color.system_brand_b_dark),
        )
    }

    companion object {
        private const val TAG = "ThemeOverlayController"
    }
}
