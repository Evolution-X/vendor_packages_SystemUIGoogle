package com.google.android.systemui.battery

import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.BatterySaverTile
import com.android.systemui.qs.tiles.base.domain.interactor.QSTileAvailabilityInteractor
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.base.ui.viewmodel.QSTileViewModel
import com.android.systemui.qs.tiles.base.ui.viewmodel.QSTileViewModelFactory
import com.android.systemui.qs.tiles.impl.battery.domain.interactor.BatterySaverTileUserActionInteractor
import com.android.systemui.qs.tiles.impl.battery.domain.model.BatterySaverTileModel
import com.android.systemui.qs.tiles.impl.battery.ui.mapper.BatterySaverTileMapper
import com.android.systemui.res.R
import com.google.android.systemui.qs.tiles.BatterySaverTileGoogle
import com.google.android.systemui.qs.tiles.impl.battery.domain.interactor.BatterySaverTileDataInteractorGoogle
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface BatterySaverModuleGoogle {

    /** Inject BatterySaverTileGoogle into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(BatterySaverTile.TILE_SPEC)
    fun bindBatterySaverTile(batterySaverTile: BatterySaverTileGoogle): QSTileImpl<*>

    @Binds
    @IntoMap
    @StringKey(BATTERY_SAVER_TILE_SPEC)
    fun provideBatterySaverAvailabilityInteractor(
        impl: BatterySaverTileDataInteractorGoogle
    ): QSTileAvailabilityInteractor

    companion object {
        private const val BATTERY_SAVER_TILE_SPEC = "battery"

        @Provides
        @IntoMap
        @StringKey(BATTERY_SAVER_TILE_SPEC)
        fun provideBatterySaverTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(BATTERY_SAVER_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.qs_battery_saver_icon_off,
                        labelRes = R.string.battery_detail_switch_title,
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        /** Inject BatterySaverTile into tileViewModelMap in QSModule */
        @Provides
        @IntoMap
        @StringKey(BATTERY_SAVER_TILE_SPEC)
        fun provideBatterySaverTileViewModel(
            factory: QSTileViewModelFactory.Static<BatterySaverTileModel>,
            mapper: BatterySaverTileMapper,
            stateInteractor: BatterySaverTileDataInteractorGoogle,
            userActionInteractor: BatterySaverTileUserActionInteractor,
        ): QSTileViewModel =
            factory.create(
                TileSpec.create(BATTERY_SAVER_TILE_SPEC),
                userActionInteractor,
                stateInteractor,
                mapper,
            )
    }
}
