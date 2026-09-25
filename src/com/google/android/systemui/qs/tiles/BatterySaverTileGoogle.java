package com.google.android.systemui.qs.tiles;

import android.content.ContentResolver;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tiles.BatterySaverTile;
import com.android.systemui.res.R;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.util.settings.SecureSettings;

import com.google.android.systemui.power.PowerUtils;

import javax.inject.Inject;

public final class BatterySaverTileGoogle extends BatterySaverTile {

    protected final UserTracker mContentResolverProvider;
    private boolean mExtremeAggressive;
    private boolean mExtremeEnabled;

    @Inject
    public BatterySaverTileGoogle(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BatteryController batteryController,
            SecureSettings secureSettings,
            UserTracker userTracker) {
        super(
                host,
                uiEventLogger,
                backgroundLooper,
                mainHandler,
                falsingManager,
                metricsLogger,
                statusBarStateController,
                activityStarter,
                qsLogger,
                batteryController,
                secureSettings);
        mExtremeEnabled = false;
        mExtremeAggressive = false;
        mContentResolverProvider = userTracker;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        super.handleUpdateState(state, arg);
        if (state.state == Tile.STATE_ACTIVE) {
            boolean isExtremeAggressive = mExtremeEnabled;
            if (!mExtremeEnabled) {
                ContentResolver contentResolver =
                        mContentResolverProvider.getUserContext().getContentResolver();
                isExtremeAggressive = PowerUtils.isFlipendoSelected(contentResolver);
            }
            mExtremeAggressive = isExtremeAggressive;
            state.secondaryLabel =
                    mContext.getString(
                            isExtremeAggressive
                                    ? R.string.extreme_battery_saver_text
                                    : R.string.standard_battery_saver_text);
        } else {
            state.secondaryLabel = "";
        }
        state.stateDescription = state.secondaryLabel;
    }

    @Override
    public void onExtremeBatterySaverChanged(boolean isExtreme) {
        mExtremeEnabled = isExtreme;
        if (!isExtreme || mExtremeAggressive) {
            return;
        }
        refreshState(null);
    }
}
