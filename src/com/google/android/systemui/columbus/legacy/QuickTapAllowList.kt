package com.google.android.systemui.columbus.legacy

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.systemui.res.R
import java.security.MessageDigest

/** Packages that may launch over the keyguard or request to become the Quick Tap target. */
class QuickTapAllowList(private val context: Context) {
    private val messageDigest = MessageDigest.getInstance("SHA-256")
    private val allowPackageList =
        context.resources.getStringArray(R.array.columbus_sumatra_package_allow_list).toSet()
    private val allowCertList =
        context.resources.getStringArray(R.array.columbus_sumatra_cert_allow_list).toSet()

    fun isAllowed(packageName: String): Boolean {
        if (packageName !in allowPackageList) return false
        val signingInfo =
            checkNotNull(
                context.packageManager
                    .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
            )
        val signers =
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        return signers
            .map { String(messageDigest.digest(it.toByteArray()), Charsets.UTF_16) }
            .any { it in allowCertList }
    }
}
