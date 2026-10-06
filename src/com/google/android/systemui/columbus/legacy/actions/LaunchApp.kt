package com.google.android.systemui.columbus.legacy.actions

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.IActivityManager
import android.app.SynchronousUserSwitchObserver
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.RemoteException
import android.os.UserHandle
import android.os.UserManager
import android.provider.DeviceConfig
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import com.android.app.tracing.traceSection
import com.android.internal.logging.UiEventLogger
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.ColumbusSettings
import com.google.android.systemui.columbus.legacy.QuickTapAllowList
import com.google.android.systemui.columbus.legacy.gates.KeyguardVisibility
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import com.google.android.systemui.columbus.util.Listenable
import com.google.android.systemui.res.R
import dagger.Lazy
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class LaunchApp
@Inject
constructor(
    context: Context,
    private val launcherApps: LauncherApps,
    private val activityStarter: ActivityStarter,
    private val statusBarKeyguardViewManager: StatusBarKeyguardViewManager,
    activityManager: IActivityManager,
    private val userManager: UserManager,
    columbusSettings: ColumbusSettings,
    private val keyguardVisibility: KeyguardVisibility,
    private val keyguardStateController: Lazy<KeyguardStateController>,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    @Main private val mainExecutor: Executor,
    @Background private val bgExecutor: Executor,
    private val uiEventLogger: UiEventLogger,
    private val userTracker: UserTracker,
) : UserAction(context, mainExecutor) {
    override val tag = "Columbus/LaunchApp"

    private val allowList = QuickTapAllowList(context)
    private val denyPackageList = mutableSetOf<String>()
    private val availableApps = mutableMapOf<ComponentName, Intent>()
    private val availableShortcuts = mutableMapOf<String, MutableMap<String, ShortcutInfo>>()
    private var currentApp: ComponentName? = null
    private var currentShortcut = ""

    private val settingsListener =
        object : ColumbusSettings.ColumbusSettingsChangeListener {
            override fun onSelectedAppChange(app: String) {
                currentApp = ComponentName.unflattenFromString(app)
                updateAvailable()
            }

            override fun onSelectedAppShortcutChange(shortcut: String) {
                currentShortcut = shortcut
                updateAvailable()
            }
        }

    private val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateAvailableAppsAndShortcutsAsync()
            }
        }

    private val gateListener = Listenable.Listener {
        if (!keyguardVisibility.isBlocking()) {
            updateAvailableAppsAndShortcutsAsync()
        }
    }

    private val keyguardUpdateMonitorCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onKeyguardBouncerFullyShowingChanged(bouncerIsFullyShowing: Boolean) {
                if (bouncerIsFullyShowing) {
                    keyguardUpdateMonitor.removeCallback(this)
                    mainExecutor.execute {
                        statusBarKeyguardViewManager.setKeyguardMessage(
                            context.getString(R.string.columbus_bouncer_message),
                            ColorStateList.valueOf(Color.WHITE),
                            null,
                        )
                    }
                }
            }
        }

    private val onDismissKeyguardAction = ActivityStarter.OnDismissAction {
        if (usingShortcut()) {
            availableShortcuts[currentApp?.packageName]?.get(currentShortcut)?.let {
                uiEventLogger.log(
                    ColumbusEvent.COLUMBUS_INVOKED_LAUNCH_SHORTCUT,
                    0,
                    currentApp?.packageName,
                )
                launcherApps.startShortcut(it, null, null)
            }
        } else {
            availableApps[currentApp]?.let {
                uiEventLogger.log(
                    ColumbusEvent.COLUMBUS_INVOKED_LAUNCH_APP,
                    0,
                    currentApp?.packageName,
                )
                context.startActivityAsUser(it, userTracker.userHandle)
            }
        }
        false
    }

    init {
        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_SYSTEMUI, bgExecutor) {
            if (it.keyset.contains(SECURE_DENY_LIST)) {
                updateDenyList(it.getString(SECURE_DENY_LIST, null))
            }
        }
        updateDenyList(
            DeviceConfig.getString(DeviceConfig.NAMESPACE_SYSTEMUI, SECURE_DENY_LIST, null)
        )
        try {
            activityManager.registerUserSwitchObserver(
                object : SynchronousUserSwitchObserver() {
                    override fun onUserSwitching(newUserId: Int) {
                        updateAvailableAppsAndShortcutsAsync()
                    }
                },
                tag,
            )
        } catch (e: RemoteException) {
            Log.e(tag, "Failed to register user switch observer", e)
        }
        val packageFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
        context.registerReceiver(broadcastReceiver, packageFilter)
        context.registerReceiver(broadcastReceiver, IntentFilter(Intent.ACTION_BOOT_COMPLETED))
        updateAvailableAppsAndShortcutsAsync()
        columbusSettings.registerColumbusSettingsChangeListener(settingsListener)
        currentApp = ComponentName.unflattenFromString(columbusSettings.selectedApp())
        currentShortcut = columbusSettings.selectedAppShortcut()
        keyguardVisibility.registerListener(gateListener)
        updateAvailable()
    }

    override fun availableOnLockscreen() = true

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        val keyguardState = keyguardStateController.get()
        if (keyguardVisibility.isBlocking() && !keyguardState.isUnlocked) {
            val secureIntent = getSecureLaunchIntent()
            if (secureIntent != null && launchSecure(secureIntent)) return
        }
        if (keyguardState.isShowing && !keyguardState.isUnlocked) {
            keyguardUpdateMonitor.registerCallback(keyguardUpdateMonitorCallback)
        }
        activityStarter.dismissKeyguardThenExecute(onDismissKeyguardAction, null, true)
    }

    private fun getSecureLaunchIntent(): Intent? {
        val packageName = currentApp?.packageName ?: return null
        if (packageName in denyPackageList || !allowList.isAllowed(packageName)) return null
        return Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
            .setPackage(packageName)
            .putExtra(KEY_QUICK_TAP_IS_SOURCE, true)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private fun launchSecure(intent: Intent): Boolean {
        val options =
            ActivityOptions.makeBasic().apply {
                setDisallowEnterPictureInPictureWhileLaunching(true)
                setRotationAnimationHint(WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS)
            }
        return try {
            ActivityTaskManager.getService()
                .startActivityAsUser(
                    null,
                    context.basePackageName,
                    context.attributionTag,
                    intent,
                    intent.resolveTypeIfNeeded(context.contentResolver),
                    null,
                    null,
                    0,
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                    null,
                    options.toBundle(),
                    userTracker.userId,
                )
            uiEventLogger.log(
                ColumbusEvent.COLUMBUS_INVOKED_LAUNCH_APP_SECURE,
                0,
                currentApp?.packageName,
            )
            true
        } catch (e: RemoteException) {
            Log.e(tag, "Unable to start secure activity for", e)
            false
        }
    }

    private fun usingShortcut(): Boolean =
        currentShortcut.isNotEmpty() && currentShortcut != currentApp?.flattenToString()

    private fun updateAvailable() {
        if (usingShortcut()) {
            setAvailable(
                availableShortcuts[currentApp?.packageName]?.containsKey(currentShortcut) == true
            )
        } else {
            setAvailable(availableApps.containsKey(currentApp))
        }
    }

    private fun updateDenyList(denyList: String?) {
        denyPackageList.clear()
        if (denyList == null) return
        denyPackageList.addAll(denyList.split(",").map { it.trim() })
    }

    private fun updateAvailableAppsAndShortcutsAsync() {
        bgExecutor.execute {
            traceSection("updateAvailableAppsAndShortcutsAsync") {
                val currentUser = ActivityManager.getCurrentUser()
                val userHandle = UserHandle.of(currentUser)
                if (!userManager.isUserUnlocked(currentUser)) {
                    Log.d(tag, "Did not update apps and shortcuts, user $currentUser not unlocked")
                    return@traceSection
                }
                availableApps.clear()
                availableShortcuts.clear()
                val apps = launcherApps.getActivityLaunchIntentForAllApps(userHandle)
                val shortcuts = launcherApps.getAvailableShortcuts(userHandle)
                // Mirrors LauncherApps#maybeUpdateDisabledMessage(), which getShortcuts() applies
                // but getAvailableShortcuts() does not.
                shortcuts.forEach { shortcut ->
                    ShortcutInfo.getDisabledReasonForRestoreIssue(context, shortcut.disabledReason)
                        ?.let { shortcut.setDisabledMessage(it) }
                }
                apps.forEach { (componentName, launchIntent) ->
                    availableApps[componentName] =
                        Intent(launchIntent).putExtra(KEY_QUICK_TAP_IS_SOURCE, true)
                }
                shortcuts.forEach {
                    availableShortcuts.getOrPut(it.`package`) { mutableMapOf() }[it.id] = it
                }
                mainExecutor.execute { updateAvailable() }
            }
        }
    }

    override fun toString(): String =
        if (usingShortcut()) {
            "Launch $currentApp shortcut $currentShortcut"
        } else {
            "Launch $currentApp"
        }

    private companion object {
        const val SECURE_DENY_LIST = "systemui_google_columbus_secure_deny_list"
        const val KEY_QUICK_TAP_IS_SOURCE = "systemui_google_quick_tap_is_source"
    }
}
