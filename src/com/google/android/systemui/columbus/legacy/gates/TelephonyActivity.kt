package com.google.android.systemui.columbus.legacy.gates

import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.telephony.TelephonyListenerManager
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class TelephonyActivity
@Inject
constructor(
    private val telephonyManager: Lazy<TelephonyManager>,
    private val telephonyListenerManager: Lazy<TelephonyListenerManager>,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : Gate() {
    private var isCallBlocked = false

    private val phoneStateListener = TelephonyCallback.CallStateListener { state ->
        coroutineScope.launch {
            isCallBlocked = isCallBlocked(state)
            updateBlocking()
        }
    }

    override fun onActivate() {
        telephonyListenerManager.get().addCallStateListener(phoneStateListener)
        coroutineScope.launch(mainDispatcher) {
            isCallBlocked =
                withContext(bgDispatcher) { isCallBlocked(telephonyManager.get().callState) }
            updateBlocking()
        }
    }

    override fun onDeactivate() {
        telephonyListenerManager.get().removeCallStateListener(phoneStateListener)
    }

    private fun updateBlocking() {
        coroutineScope.launch { setBlocking(isCallBlocked) }
    }

    private fun isCallBlocked(state: Int) = state == TelephonyManager.CALL_STATE_OFFHOOK
}
