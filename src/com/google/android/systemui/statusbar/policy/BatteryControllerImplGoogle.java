package com.google.android.systemui.statusbar.policy;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.demomode.DemoModeController;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.power.EnhancedEstimates;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryControllerImpl;
import com.android.systemui.statusbar.policy.BatteryControllerLogger;
import com.android.systemui.util.settings.SecureSettings;

import com.google.android.systemui.power.PowerUtils;

import java.io.PrintWriter;

public class BatteryControllerImplGoogle extends BatteryControllerImpl {

    public static final boolean DEBUG = Log.isLoggable("BatteryControllerGoogle", 3);
    private static final String TAG = "BatteryControllerGoogle";

    public static final Uri IS_EBS_ENABLED_OBSERVABLE_URI =
            new Uri.Builder()
                    .scheme("content")
                    .authority("com.google.android.flipendo.api")
                    .appendPath("get_flipendo_state")
                    .build();

    protected final ContentObserver mContentObserver;
    public final UserTracker mContentResolverProvider;
    public boolean mExtremeSaver;
    public final SecureSettings mSecureSettings;
    public final UserTracker mUserTracker;

    public BatteryControllerImplGoogle(
            Context context,
            EnhancedEstimates enhancedEstimates,
            PowerManager powerManager,
            BroadcastDispatcher broadcastDispatcher,
            DemoModeController demoModeController,
            DumpManager dumpManager,
            BatteryControllerLogger batteryControllerLogger,
            @Main Handler handler,
            @Background Handler handler2,
            UserTracker userTracker,
            SecureSettings secureSettings) {
        super(
                context,
                enhancedEstimates,
                powerManager,
                broadcastDispatcher,
                demoModeController,
                dumpManager,
                batteryControllerLogger,
                handler,
                handler2);
        mContentResolverProvider = userTracker;
        mSecureSettings = secureSettings;
        mUserTracker = userTracker;
        mContentObserver =
                new ContentObserver(handler2) {
                    @Override
                    public void onChange(boolean z, Uri uri) {
                        if (BatteryControllerImplGoogle.DEBUG) {
                            Log.d(
                                    TAG,
                                    "Change in EBS value "
                                            + (uri != null ? uri.toSafeString() : null));
                        }
                        boolean isFlipendoEnabled =
                                PowerUtils.isFlipendoEnabled(
                                        mContentResolverProvider
                                                .getUserContext()
                                                .getContentResolver());
                        if (isFlipendoEnabled == mExtremeSaver) {
                            return;
                        }
                        mExtremeSaver = isFlipendoEnabled;
                        dispatchSafeChange(
                                callback -> callback.onExtremeBatterySaverChanged(mExtremeSaver));
                    }
                };
    }

    // Reverse charging (ReverseChargingController) is not ported.
    @Override
    public void addCallback(BatteryController.BatteryStateChangeCallback callback) {
        super.addCallback(callback);
        callback.onExtremeBatterySaverChanged(mExtremeSaver);
    }

    @Override
    public void dump(PrintWriter printWriter, String[] strArr) {
        super.dump(printWriter, strArr);
        printWriter.print("  mExtremeSaver=");
        printWriter.println(mExtremeSaver);
    }

    @Override
    public void init() {
        super.init();
        try {
            ContentResolver contentResolver =
                    mContentResolverProvider.getUserContext().getContentResolver();
            contentResolver.registerContentObserver(
                    IS_EBS_ENABLED_OBSERVABLE_URI, false, mContentObserver, -1);
            mContentObserver.onChange(false, IS_EBS_ENABLED_OBSERVABLE_URI);
        } catch (Exception e) {
            Log.w(TAG, "Couldn't register to observe provider", e);
        }
    }

    @Override
    public boolean isBatteryDefenderMode(int chargingStatus) {
        if (chargingStatus != 4) {
            return false;
        }
        boolean isChargeLimitEnabled =
                PowerUtils.isChargeLimitEnabledForUser(mSecureSettings, mUserTracker.getUserId());
        if (isChargeLimitEnabled) {
            return mLevel >= 80;
        }
        return true;
    }
}
