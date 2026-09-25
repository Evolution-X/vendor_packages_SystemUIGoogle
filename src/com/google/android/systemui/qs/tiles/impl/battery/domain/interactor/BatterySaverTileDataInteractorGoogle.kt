package com.google.android.systemui.qs.tiles.impl.battery.domain.interactor

import android.os.UserHandle
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.impl.battery.domain.interactor.BatterySaverTileDataInteractor
import com.android.systemui.qs.tiles.impl.battery.domain.model.BatterySaverTileModel
import com.android.systemui.statusbar.pipeline.battery.data.repository.BatteryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class BatterySaverTileDataInteractorGoogle
@Inject
constructor(private val batteryRepository: BatteryRepository) :
    BatterySaverTileDataInteractor(batteryRepository) {

    override fun tileData(
        user: UserHandle,
        triggers: Flow<DataUpdateTrigger>,
    ): Flow<BatterySaverTileModel> =
        combine(super.tileData(user, triggers), batteryRepository.isExtremePowerSaveEnabled) {
            model,
            isExtreme ->
            BatterySaverTileModel.Extreme(
                isPluggedIn = model.isPluggedIn,
                isPowerSaving = model.isPowerSaving,
                isExtremeSaving = isExtreme,
            )
        }
}
