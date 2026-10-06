package com.google.android.systemui.columbus.legacy

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.provider.Settings
import android.util.Log
import com.android.app.tracing.traceSection
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.settings.UserTracker
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.res.R
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

/** Lets allowlisted apps ask the user to make them the Quick Tap launch target. */
class ColumbusTargetRequestService
@Inject
constructor(
    private val sysUIContext: Context,
    private val userTracker: UserTracker,
    private val columbusSettings: ColumbusSettings,
    private val columbusStructuredDataManager: ColumbusStructuredDataManager,
    private val uiEventLogger: UiEventLogger,
    @Main private val mainHandler: Handler,
    @Background looper: Looper,
    private val columbusTargetRequestDialogDelegateProvider:
        Provider<ColumbusTargetRequestDialogDelegate>,
) : Service() {
    private val messenger = Messenger(IncomingMessageHandler(looper))
    private val allowList = QuickTapAllowList(sysUIContext)
    private var launcherApps: LauncherApps? = null

    override fun onCreate() {
        super.onCreate()
        launcherApps = getSystemService(LauncherApps::class.java)
    }

    override fun onBind(intent: Intent): IBinder? =
        if (sysUIContext.packageManager.hasSystemFeature(FEATURE_QUICK_TAP)) {
            messenger.binder
        } else {
            null
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    private inner class IncomingMessageHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val packageName = packageManager.getPackagesForUid(msg.sendingUid)?.get(0)
            when (msg.what) {
                MSG_REQUEST_TARGET -> handleTargetRequest(msg, packageName)
                MSG_CHECK_TARGET_REQUEST -> handleTargetRequestCheck(msg, packageName)
                else -> Log.e(TAG, "Invalid request type: ${msg.what}")
            }
        }

        private fun handleTargetRequest(msg: Message, packageName: String?) {
            if (packageName == null || !allowList.isAllowed(packageName)) {
                replyToMessenger(msg.replyTo, msg.what, RESULT_UNSUPPORTED_CALLER)
                Log.d(TAG, "Unsupported caller: $packageName")
                return
            }
            if (packageIsTarget(packageName)) {
                replyToMessenger(msg.replyTo, msg.what, RESULT_OK)
                Log.d(TAG, "Caller already target: $packageName")
                return
            }
            if (packageNeedsToCoolDown(packageName)) {
                replyToMessenger(msg.replyTo, msg.what, RESULT_THROTTLED)
                Log.d(TAG, "Caller throttled: $packageName")
                return
            }
            if (
                columbusStructuredDataManager.getPackageShownCount(packageName) >= MAX_SHOWN_COUNT
            ) {
                replyToMessenger(msg.replyTo, msg.what, RESULT_MAX_SHOWN)
                Log.d(TAG, "Caller already shown max times: $packageName")
                return
            }
            val appInfo = getAppInfoForPackage(packageName)
            if (appInfo == null) {
                replyToMessenger(msg.replyTo, msg.what, RESULT_NOT_LAUNCHABLE)
                Log.d(TAG, "Caller not launchable: $packageName")
                return
            }
            val replyTo = msg.replyTo
            val requestCode = msg.what
            mainHandler.post { displayDialog(appInfo, replyTo, requestCode) }
        }

        private fun handleTargetRequestCheck(msg: Message, packageName: String?) {
            val result =
                when {
                    packageName == null ||
                        !allowList.isAllowed(packageName) ||
                        columbusStructuredDataManager.getPackageShownCount(packageName) >=
                            MAX_SHOWN_COUNT ||
                        getAppInfoForPackage(packageName) == null -> CHECK_NOT_ALLOWED
                    packageIsTarget(packageName) -> CHECK_ALREADY_TARGET
                    packageNeedsToCoolDown(packageName) -> CHECK_THROTTLED
                    else -> CHECK_ALLOWED
                }
            replyToMessenger(msg.replyTo, msg.what, result)
        }

        private fun getAppInfoForPackage(packageName: String): LauncherActivityInfo? =
            traceSection("getAppInfoForPackage pkg=$packageName") {
                launcherApps?.getActivityList(packageName, userTracker.userHandle)?.firstOrNull {
                    try {
                        traceSection("getMainActivityLaunchIntent component=${it.componentName}") {
                            launcherApps?.getMainActivityLaunchIntent(
                                it.componentName,
                                null,
                                userTracker.userHandle,
                            ) != null
                        }
                    } catch (e: RuntimeException) {
                        false
                    }
                }
            }

        private fun packageIsTarget(packageName: String): Boolean =
            columbusSettings.isColumbusEnabled() &&
                ACTION_LAUNCH == columbusSettings.selectedAction() &&
                packageName ==
                    ComponentName.unflattenFromString(columbusSettings.selectedApp())?.packageName

        private fun packageNeedsToCoolDown(packageName: String): Boolean =
            columbusStructuredDataManager.timeSinceLastDeny(packageName) < COOL_DOWN_DURATION
    }

    private fun displayDialog(
        appInfo: LauncherActivityInfo,
        replyTo: Messenger?,
        requestCode: Int,
    ) {
        val packageName = appInfo.componentName.packageName
        val flattenedComponent = appInfo.componentName.flattenToString()
        val previousCount = columbusStructuredDataManager.getPackageShownCount(packageName)
        uiEventLogger.log(ColumbusEvent.COLUMBUS_RETARGET_DIALOG_SHOWN, 0, packageName)

        val dialogDelegate = columbusTargetRequestDialogDelegateProvider.get()
        val dialog = dialogDelegate.createDialog()
        dialog.show()

        val onClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    val userId = userTracker.userId
                    Settings.Secure.putIntForUser(
                        contentResolver,
                        ColumbusSettings.COLUMBUS_ENABLED,
                        1,
                        userId,
                    )
                    Settings.Secure.putStringForUser(
                        contentResolver,
                        ColumbusSettings.COLUMBUS_ACTION,
                        ACTION_LAUNCH,
                        userId,
                    )
                    Settings.Secure.putStringForUser(
                        contentResolver,
                        ColumbusSettings.COLUMBUS_LAUNCH_APP,
                        flattenedComponent,
                        userId,
                    )
                    Settings.Secure.putStringForUser(
                        contentResolver,
                        ColumbusSettings.COLUMBUS_LAUNCH_APP_SHORTCUT,
                        flattenedComponent,
                        userId,
                    )
                    replyToMessenger(replyTo, requestCode, RESULT_OK)
                    Log.d(TAG, "Target changed to $flattenedComponent")
                    uiEventLogger.log(
                        if (previousCount == 0) {
                            ColumbusEvent.COLUMBUS_RETARGET_APPROVED
                        } else {
                            ColumbusEvent.COLUMBUS_RETARGET_FOLLOW_ON_APPROVED
                        },
                        0,
                        flattenedComponent,
                    )
                }
                DialogInterface.BUTTON_NEGATIVE -> {
                    columbusStructuredDataManager.setLastDenyTimestamp(packageName)
                    replyToMessenger(replyTo, requestCode, RESULT_DENIED)
                    Log.d(TAG, "Target change denied by user: $flattenedComponent")
                    logNotApproved(previousCount, flattenedComponent)
                }
                else -> Log.e(TAG, "Invalid dialog option: $which")
            }
        }

        val title =
            dialog.context.getString(R.string.columbus_target_request_dialog_title, appInfo.label)
        dialog.window?.setTitle(title)
        dialog.window?.attributes?.title = title
        dialogDelegate.title.text = title
        dialogDelegate.content.text =
            dialog.context.getString(R.string.columbus_target_request_dialog_summary, appInfo.label)
        dialogDelegate.positiveButton.setText(R.string.columbus_target_request_dialog_allow)
        dialogDelegate.positiveButton.setOnClickListener {
            onClickListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
            dialog.dismiss()
        }
        dialogDelegate.negativeButton.setText(R.string.columbus_target_request_dialog_deny)
        dialogDelegate.negativeButton.setOnClickListener {
            onClickListener.onClick(dialog, DialogInterface.BUTTON_NEGATIVE)
            dialog.dismiss()
        }
        dialog.setOnCancelListener {
            replyToMessenger(replyTo, requestCode, RESULT_DISMISSED)
            Log.d(TAG, "Target change dismissed by user: $flattenedComponent")
            logNotApproved(previousCount, flattenedComponent)
        }
        dialog.setCanceledOnTouchOutside(true)
        columbusStructuredDataManager.incrementPackageShownCount(packageName)
    }

    private fun logNotApproved(previousCount: Int, flattenedComponent: String) {
        uiEventLogger.log(
            if (previousCount == 0) {
                ColumbusEvent.COLUMBUS_RETARGET_NOT_APPROVED
            } else {
                ColumbusEvent.COLUMBUS_RETARGET_FOLLOW_ON_NOT_APPROVED
            },
            0,
            flattenedComponent,
        )
    }

    private fun replyToMessenger(messenger: Messenger?, requestCode: Int, result: Int) {
        if (messenger == null) return
        val reply = Message.obtain().setWhat(result).apply { arg1 = requestCode }
        runCatching { messenger.send(reply) }
            .onFailure {
                Log.e(TAG, "Could not send response $result for request $requestCode", it)
            }
    }

    // The message codes are part of the client contract; stock names are obfuscated.
    private companion object {
        const val TAG = "Columbus/TargetRequest"
        const val FEATURE_QUICK_TAP = "com.google.android.feature.QUICK_TAP"
        const val ACTION_LAUNCH = "launch"
        const val MAX_SHOWN_COUNT = 3
        val COOL_DOWN_DURATION = TimeUnit.DAYS.toMillis(5)

        const val MSG_REQUEST_TARGET = 1
        const val MSG_CHECK_TARGET_REQUEST = 2

        const val RESULT_OK = 0
        const val RESULT_UNSUPPORTED_CALLER = 1
        const val RESULT_THROTTLED = 2
        const val RESULT_MAX_SHOWN = 3
        const val RESULT_NOT_LAUNCHABLE = 4
        const val RESULT_DENIED = 5
        const val RESULT_DISMISSED = 6

        const val CHECK_ALREADY_TARGET = 0
        const val CHECK_ALLOWED = 1
        const val CHECK_NOT_ALLOWED = 2
        const val CHECK_THROTTLED = 3
    }
}
