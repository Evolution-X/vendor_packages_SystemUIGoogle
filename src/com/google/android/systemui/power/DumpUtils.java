package com.google.android.systemui.power;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DumpUtils {

    private DumpUtils() {}

    public static String toReadableDateTime(long timeMillis) {
        return new SimpleDateFormat("MMM dd,yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(timeMillis));
    }
}
