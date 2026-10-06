package com.google.android.systemui.assist

import android.content.Context

fun interface OpaEnabledListener {
    fun onOpaEnabledReceived(
        context: Context,
        isOpaEligible: Boolean,
        isAgsaAssistant: Boolean,
        isOpaEnabled: Boolean,
    )
}
