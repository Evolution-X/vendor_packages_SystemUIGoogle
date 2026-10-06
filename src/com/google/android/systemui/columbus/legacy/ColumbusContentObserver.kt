package com.google.android.systemui.columbus.legacy

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.settings.UserTracker
import com.android.systemui.util.settings.SecureSettings
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.launch

class ColumbusContentObserver(
    private val settingsUri: Uri,
    private val callback: (Uri) -> Unit,
    private val userTracker: UserTracker,
    private val executor: Executor,
    handler: Handler,
    private val secureSettings: SecureSettings,
) : ContentObserver(handler) {
    private val userTrackerCallback =
        object : UserTracker.Callback {
            override fun onUserChanged(newUser: Int, userContext: Context) {
                updateContentObserver()
                callback(settingsUri)
            }
        }

    fun activate() {
        userTracker.addCallback(userTrackerCallback, executor)
        updateContentObserver()
    }

    private fun updateContentObserver() {
        secureSettings.settingsScope.launch {
            secureSettings.unregisterContentObserver(this@ColumbusContentObserver)
            secureSettings.registerContentObserverForUser(
                settingsUri,
                false,
                this@ColumbusContentObserver,
                userTracker.userId,
            )
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (uri != null) {
            callback(uri)
        }
    }

    @SysUISingleton
    class Factory
    @Inject
    constructor(
        private val userTracker: UserTracker,
        @Main private val handler: Handler,
        @Main private val executor: Executor,
        private val secureSettings: SecureSettings,
    ) {
        fun create(settingsUri: Uri, callback: (Uri) -> Unit) =
            ColumbusContentObserver(
                settingsUri,
                callback,
                userTracker,
                executor,
                handler,
                secureSettings,
            )
    }
}
