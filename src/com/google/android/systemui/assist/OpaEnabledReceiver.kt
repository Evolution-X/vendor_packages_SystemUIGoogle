package com.google.android.systemui.assist

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * Tracks whether the Google app reports Assistant as eligible and enabled for the current user.
 *
 * Only the state consumed by Quick Tap is kept; stock also tracks long press home for the Opa
 * navigation bar button, which is not ported.
 */
@SysUISingleton
class OpaEnabledReceiver
@Inject
constructor(
    private val context: Context,
    @Main private val fgExecutor: Executor,
    @Background private val bgExecutor: Executor,
    @Background private val bgHandler: Handler,
    private val opaEnabledSettings: OpaEnabledSettings,
    keyguardUpdateMonitor: KeyguardUpdateMonitor,
) {
    private val contentResolver = context.contentResolver
    private val listeners = mutableListOf<OpaEnabledListener>()
    private var isOpaEligible = false
    private var isAgsaAssistant = false
    private var isOpaEnabled = false

    private val contentObserver =
        object : ContentObserver(Handler(context.mainLooper)) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                updateOpaEnabledState(true, null)
            }
        }

    private val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_OPA_ENABLED ->
                        opaEnabledSettings.setOpaEligible(
                            intent.getBooleanExtra(EXTRA_OPA_ENABLED, false)
                        )
                    ACTION_OPA_USER_ENABLED ->
                        opaEnabledSettings.setOpaEnabled(
                            intent.getBooleanExtra(EXTRA_OPA_USER_ENABLED, false)
                        )
                }
                updateOpaEnabledState(true, goAsync())
            }
        }

    // Stock re-registers from AssistManagerGoogle, which is not ported.
    private val keyguardUpdateMonitorCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onUserSwitching(userId: Int) {
                updateOpaEnabledState(true, null)
                contentResolver.unregisterContentObserver(contentObserver)
                registerContentObserver()
                context.unregisterReceiver(broadcastReceiver)
                registerEnabledReceiver(userId)
            }
        }

    init {
        updateOpaEnabledState(false, null)
        registerContentObserver()
        registerEnabledReceiver(UserHandle.USER_CURRENT)
        keyguardUpdateMonitor.registerCallback(keyguardUpdateMonitorCallback)
    }

    fun addOpaEnabledListener(listener: OpaEnabledListener) {
        listeners.add(listener)
        listener.onOpaEnabledReceived(context, isOpaEligible, isAgsaAssistant, isOpaEnabled)
    }

    fun dispatchOpaEnabledState() {
        listeners.forEach {
            it.onOpaEnabledReceived(context, isOpaEligible, isAgsaAssistant, isOpaEnabled)
        }
    }

    private fun updateOpaEnabledState(
        dispatch: Boolean,
        pendingResult: BroadcastReceiver.PendingResult?,
    ) {
        bgExecutor.execute {
            isOpaEligible = opaEnabledSettings.isOpaEligible()
            isAgsaAssistant = opaEnabledSettings.isAgsaAssistant()
            isOpaEnabled = opaEnabledSettings.isOpaEnabled()
            if (dispatch) {
                fgExecutor.execute { dispatchOpaEnabledState() }
            }
            if (pendingResult != null) {
                fgExecutor.execute { pendingResult.finish() }
            }
        }
    }

    private fun registerContentObserver() {
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ASSISTANT),
            false,
            contentObserver,
            UserHandle.USER_CURRENT,
        )
    }

    private fun registerEnabledReceiver(userId: Int) {
        val user = UserHandle(userId)
        context.registerReceiverAsUser(
            broadcastReceiver,
            user,
            IntentFilter(ACTION_OPA_ENABLED),
            Manifest.permission.CAPTURE_AUDIO_HOTWORD,
            bgHandler,
            Context.RECEIVER_EXPORTED,
        )
        context.registerReceiverAsUser(
            broadcastReceiver,
            user,
            IntentFilter(ACTION_OPA_USER_ENABLED),
            Manifest.permission.CAPTURE_AUDIO_HOTWORD,
            bgHandler,
            Context.RECEIVER_EXPORTED,
        )
    }

    private companion object {
        const val ACTION_OPA_ENABLED = "com.google.android.systemui.OPA_ENABLED"
        const val ACTION_OPA_USER_ENABLED = "com.google.android.systemui.OPA_USER_ENABLED"
        const val EXTRA_OPA_ENABLED = "OPA_ENABLED"
        const val EXTRA_OPA_USER_ENABLED = "OPA_USER_ENABLED"
    }
}
