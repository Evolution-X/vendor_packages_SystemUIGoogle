package com.google.android.systemui.power;

import android.content.ContentResolver;
import android.os.Bundle;
import android.util.Log;

public final class PowerUtils {

    private static final String TAG = "PowerUtils";

    private PowerUtils() {}

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
}
