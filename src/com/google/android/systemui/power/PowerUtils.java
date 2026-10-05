package com.google.android.systemui.power;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.android.systemui.util.settings.SecureSettings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PowerUtils {

    private static final String TAG = "PowerUtils";

    public static final List<String> NON_EU_COUNTRY_CODES = Arrays.asList("us", "in", "sg", "my");

    private PowerUtils() {}

    public static PendingIntent createHelpArticlePendingIntentAsUser(int resId, Context context) {
        return PendingIntent.getActivityAsUser(
                context,
                0,
                new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(resId))),
                PendingIntent.FLAG_IMMUTABLE,
                null,
                UserHandle.CURRENT);
    }

    public static boolean isChargeLimitEnabledForUser(SecureSettings secureSettings, int userId) {
        return secureSettings.getIntForUser("charge_optimization_mode", 0, userId) == 1;
    }

    public static boolean isSimInEuCountry(SubscriptionManager subscriptionManager) {
        List<SubscriptionInfo> infos = subscriptionManager.getActiveSubscriptionInfoList();
        if (infos == null || infos.isEmpty()) {
            return true;
        }
        for (SubscriptionInfo info : infos) {
            String countryIso = info.getCountryIso();
            Log.d(TAG, "countryIso = " + countryIso);
            if (!TextUtils.isEmpty(countryIso)
                    && !NON_EU_COUNTRY_CODES.contains(countryIso.toLowerCase(Locale.ENGLISH))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFlipendoEnabled(ContentResolver contentResolver) {
        try {
            Bundle bundle =
                    contentResolver.call(
                            "com.google.android.flipendo.api",
                            "get_flipendo_state",
                            (String) null,
                            Bundle.EMPTY);
            return bundle != null && bundle.getBoolean("flipendo_state", false);
        } catch (Exception e) {
            Log.e(TAG, "isFlipendoEnabled() failed", e);
            return false;
        }
    }

    public static boolean isFlipendoSelected(ContentResolver contentResolver) {
        try {
            Bundle bundle =
                    contentResolver.call(
                            "com.google.android.flipendo.api",
                            "get_flipendo_state",
                            (String) null,
                            Bundle.EMPTY);
            return bundle != null && bundle.getBoolean("is_flipendo_aggressive", false);
        } catch (Exception e) {
            Log.e(TAG, "isFlipendoSelected() failed", e);
            return false;
        }
    }

    public static PendingIntent createPendingIntent(Context context, String action, Bundle bundle) {
        Intent intent =
                new Intent(action)
                        .setPackage(context.getPackageName())
                        .setFlags(
                                Intent.FLAG_RECEIVER_FOREGROUND
                                        | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        if (bundle != null) {
            intent.putExtras(bundle);
        }
        return PendingIntent.getBroadcastAsUser(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
                        | (bundle != null ? PendingIntent.FLAG_UPDATE_CURRENT : 0),
                UserHandle.CURRENT);
    }

    public static void overrideNotificationAppName(
            Context context, NotificationCompat.Builder notificationCompatBuilder) {
        Bundle bundle = new Bundle(1);
        bundle.putString(
                Notification.EXTRA_SUBSTITUTE_APP_NAME,
                context.getString(com.android.internal.R.string.android_system_label));
        notificationCompatBuilder.addExtras(bundle);
    }

    public static void applyExtremeSaverMode(Context context) {
        try {
            Bundle bundle = new Bundle(1);
            bundle.putInt("update_flipendo_mode", 1);
            context.getContentResolver()
                    .call(
                            "com.google.android.flipendo.api",
                            "update_flipendo_mode_method",
                            (String) null,
                            bundle);
        } catch (Exception e) {
            Log.e(TAG, "applyExtremeSaverMode() failed", e);
        }
    }
}
