package com.google.android.systemui.statusbar.policy;

import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.demomode.DemoModeController;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.power.EnhancedEstimates;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryControllerLogger;
import com.android.systemui.util.settings.SecureSettings;

import dagger.Module;
import dagger.Provides;

@Module
public class GooglePolicyModule {

    @Provides
    @SysUISingleton
    static BatteryController provideBatteryController(
            Context context,
            EnhancedEstimates enhancedEstimates,
            PowerManager powerManager,
            BroadcastDispatcher broadcastDispatcher,
            DemoModeController demoModeController,
            DumpManager dumpManager,
            BatteryControllerLogger batteryControllerLogger,
            @Main Handler mainHandler,
            @Background Handler bgHandler,
            UserTracker userTracker,
            SecureSettings secureSettings) {
        BatteryControllerImplGoogle bC =
                new BatteryControllerImplGoogle(
                        context,
                        enhancedEstimates,
                        powerManager,
                        broadcastDispatcher,
                        demoModeController,
                        dumpManager,
                        batteryControllerLogger,
                        mainHandler,
                        bgHandler,
                        userTracker,
                        secureSettings);
        bC.init();
        return bC;
    }
}
