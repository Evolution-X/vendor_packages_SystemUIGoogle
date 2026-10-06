package com.google.android.systemui.columbus.legacy.actions

import android.app.ActivityOptions
import android.app.IActivityManager
import android.app.SynchronousUserSwitchObserver
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import com.google.android.systemui.columbus.legacy.gates.SilenceAlertsDisabled
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import com.google.android.systemui.columbus.util.Listenable
import java.util.concurrent.Executor

abstract class DeskClockAction(
    context: Context,
    private val silenceAlertsDisabled: SilenceAlertsDisabled,
    activityManager: IActivityManager,
    executor: Executor,
) : Action(context, executor, null) {
    private var alertFiring = false
    private var receiverRegistered = false

    protected abstract val alertAction: String
    protected abstract val doneAction: String

    protected abstract fun createDismissIntent(): Intent

    private val alertReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    alertAction -> alertFiring = true
                    doneAction -> alertFiring = false
                }
                setAvailable(alertFiring)
            }
        }

    init {
        silenceAlertsDisabled.registerListener(Listenable.Listener { updateBroadcastReceiver() })
        try {
            activityManager.registerUserSwitchObserver(
                object : SynchronousUserSwitchObserver() {
                    override fun onUserSwitching(newUserId: Int) {
                        updateBroadcastReceiver()
                    }
                },
                TAG,
            )
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to register user switch observer", e)
        }
        updateBroadcastReceiver()
    }

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        val intent =
            createDismissIntent().apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
            }
        val options =
            ActivityOptions.makeBasic().apply {
                setDisallowEnterPictureInPictureWhileLaunching(true)
            }
        try {
            context.startActivityAsUser(intent, options.toBundle(), UserHandle.CURRENT)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Failed to dismiss alert", e)
        }
        alertFiring = false
        setAvailable(false)
    }

    private fun updateBroadcastReceiver() {
        alertFiring = false
        if (receiverRegistered) {
            context.unregisterReceiver(alertReceiver)
            receiverRegistered = false
        }
        if (!silenceAlertsDisabled.isBlocking()) {
            val intentFilter =
                IntentFilter().apply {
                    addAction(alertAction)
                    addAction(doneAction)
                }
            context.registerReceiverAsUser(
                alertReceiver,
                UserHandle.CURRENT,
                intentFilter,
                PERMISSION_SEND_ALERT_BROADCASTS,
                null,
                Context.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
        setAvailable(alertFiring)
    }

    override fun toString(): String =
        super.toString() + " [receiverRegistered -> $receiverRegistered]"

    private companion object {
        const val TAG = "Columbus/DeskClockAct"
        const val PERMISSION_SEND_ALERT_BROADCASTS =
            "com.android.systemui.permission.SEND_ALERT_BROADCASTS"
    }
}
