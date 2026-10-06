package com.google.android.systemui.assist

import android.app.ActivityManager
import android.content.Context
import android.os.RemoteException
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.internal.app.AssistUtils
import com.android.internal.widget.ILockSettings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.util.Assert
import javax.inject.Inject

@SysUISingleton
class OpaEnabledSettings @Inject constructor(private val context: Context) {
    private val lockSettings: ILockSettings =
        ILockSettings.Stub.asInterface(ServiceManager.getService("lock_settings"))

    fun isOpaEligible(): Boolean {
        Assert.isNotMainThread()
        return Settings.Secure.getIntForUser(
            context.contentResolver,
            OPA_ELIGIBLE,
            0,
            ActivityManager.getCurrentUser(),
        ) != 0
    }

    fun setOpaEligible(eligible: Boolean) {
        Assert.isNotMainThread()
        Settings.Secure.putIntForUser(
            context.contentResolver,
            OPA_ELIGIBLE,
            if (eligible) 1 else 0,
            ActivityManager.getCurrentUser(),
        )
    }

    fun isAgsaAssistant(): Boolean {
        Assert.isNotMainThread()
        return AssistUtils(context)
            .getAssistComponentForUser(UserHandle.USER_CURRENT)
            ?.flattenToString() == GSA_VOICE_INTERACTION_SERVICE
    }

    fun isOpaEnabled(): Boolean {
        Assert.isNotMainThread()
        return try {
            lockSettings.getBoolean(OPA_USER_ENABLED, false, ActivityManager.getCurrentUser())
        } catch (e: RemoteException) {
            Log.e(TAG, "isOpaEnabled RemoteException", e)
            false
        }
    }

    fun setOpaEnabled(enabled: Boolean) {
        Assert.isNotMainThread()
        try {
            lockSettings.setBoolean(OPA_USER_ENABLED, enabled, ActivityManager.getCurrentUser())
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException on OPA_USER_ENABLED", e)
        }
    }

    private companion object {
        const val TAG = "OpaEnabledSettings"
        const val OPA_ELIGIBLE = "systemui.google.opa_enabled"
        const val OPA_USER_ENABLED = "systemui.google.opa_user_enabled"
        const val GSA_VOICE_INTERACTION_SERVICE =
            "com.google.android.googlequicksearchbox/" +
                "com.google.android.voiceinteraction.GsaVoiceInteractionService"
    }
}
