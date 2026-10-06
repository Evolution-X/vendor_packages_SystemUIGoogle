package com.google.android.systemui.columbus.legacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.UserTracker
import com.google.android.systemui.res.R
import java.time.DateTimeException
import java.util.concurrent.Executor
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Tracks how often each app was offered as the Quick Tap target and when it was last denied. */
@SysUISingleton
class ColumbusStructuredDataManager
@Inject
constructor(
    context: Context,
    private val userTracker: UserTracker,
    @Background executor: Executor,
) {
    private val contentResolver = context.contentResolver
    private val allowPackageList =
        context.resources.getStringArray(R.array.columbus_sumatra_package_allow_list).toSet()
    private val lock = Any()
    private var packageStats: JSONArray

    private val userTrackerCallback =
        object : UserTracker.Callback {
            override fun onUserChanged(newUser: Int, userContext: Context) {
                synchronized(lock) { packageStats = fetchPackageStats() }
            }
        }

    private val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                val tokens = intent?.dataString?.split(":") ?: return
                if (tokens.size != 2) {
                    Log.e(TAG, "Unexpected package name tokens: ${tokens.joinToString(",")}")
                    return
                }
                val packageName = tokens[1]
                if (
                    intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) ||
                        packageName !in allowPackageList
                ) {
                    return
                }
                synchronized(lock) {
                    val index = indexOf(packageName)
                    if (index >= 0) {
                        packageStats.remove(index)
                        storePackageStats()
                    }
                }
            }
        }

    init {
        packageStats = fetchPackageStats()
        userTracker.addCallback(userTrackerCallback, executor)
        val intentFilter =
            IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    fun getPackageShownCount(packageName: String): Int =
        synchronized(lock) {
            val index = indexOf(packageName)
            if (index >= 0) packageStats.getJSONObject(index).getInt(KEY_SHOWN_COUNT) else 0
        }

    fun getLastDenyTimestamp(packageName: String): Long =
        synchronized(lock) {
            val index = indexOf(packageName)
            if (index >= 0) packageStats.getJSONObject(index).getLong(KEY_LAST_DENY) else 0
        }

    fun incrementPackageShownCount(packageName: String) {
        synchronized(lock) {
            val index = indexOf(packageName)
            if (index >= 0) {
                val stats = packageStats.getJSONObject(index)
                stats.put(KEY_SHOWN_COUNT, stats.getInt(KEY_SHOWN_COUNT) + 1)
                packageStats.put(index, stats)
            } else {
                packageStats.put(makeJSONObject(packageName, shownCount = 1))
            }
            storePackageStats()
        }
    }

    fun setLastDenyTimestamp(packageName: String) {
        synchronized(lock) {
            val now = currentTimeMillis()
            val index = indexOf(packageName)
            if (index >= 0) {
                val stats = packageStats.getJSONObject(index)
                stats.put(KEY_LAST_DENY, now)
                packageStats.put(index, stats)
            } else {
                packageStats.put(makeJSONObject(packageName, lastDeny = now))
            }
            storePackageStats()
        }
    }

    /** Time since the given package was last denied, measured with network time if available. */
    fun timeSinceLastDeny(packageName: String): Long =
        synchronized(lock) { currentTimeMillis() - getLastDenyTimestamp(packageName) }

    private fun indexOf(packageName: String): Int =
        (0 until packageStats.length()).firstOrNull {
            packageName == packageStats.getJSONObject(it).getString(KEY_PACKAGE_NAME)
        } ?: -1

    private fun fetchPackageStats(): JSONArray =
        synchronized(lock) {
            val stats =
                Settings.Secure.getStringForUser(contentResolver, PACKAGE_STATS, userTracker.userId)
                    ?: "[]"
            try {
                JSONArray(stats)
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse package counts", e)
                JSONArray()
            }
        }

    private fun storePackageStats() {
        synchronized(lock) {
            Settings.Secure.putStringForUser(
                contentResolver,
                PACKAGE_STATS,
                packageStats.toString(),
                userTracker.userId,
            )
        }
    }

    private fun makeJSONObject(packageName: String, shownCount: Int = 0, lastDeny: Long = 0) =
        JSONObject().apply {
            put(KEY_PACKAGE_NAME, packageName)
            put(KEY_SHOWN_COUNT, shownCount)
            put(KEY_LAST_DENY, lastDeny)
        }

    private fun currentTimeMillis(): Long =
        try {
            SystemClock.currentNetworkTimeMillis()
        } catch (e: DateTimeException) {
            System.currentTimeMillis()
        }

    private companion object {
        const val TAG = "Columbus/StructuredData"
        const val PACKAGE_STATS = "columbus_package_stats"
        const val KEY_PACKAGE_NAME = "packageName"
        const val KEY_SHOWN_COUNT = "shownCount"
        const val KEY_LAST_DENY = "lastDeny"
    }
}
