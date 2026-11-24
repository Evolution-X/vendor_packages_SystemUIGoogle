package com.google.android.systemui.ambientmusic

import com.android.systemui.axdynamicbar.data.source.AmbientMusic
import com.android.systemui.axdynamicbar.data.source.AmbientMusicSource
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.keyguard.domain.interactor.AmbientIndicationInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Feeds the recognized Now Playing song to the dynamic island. */
@SysUISingleton
class AmbientMusicSourceImpl
@Inject
constructor(private val ambientIndicationInteractor: AmbientIndicationInteractor) :
    AmbientMusicSource {

    override val ambientMusic: Flow<AmbientMusic?> =
        ambientIndicationInteractor.ambientMusicState.map { music ->
            music?.let {
                val extended = it.extendedIndication
                AmbientMusic(
                    text = it.text?.toString(),
                    songTitle = extended?.songTitle?.toString(),
                    artistName = extended?.artistName?.toString(),
                    isSearching = extended?.isSongSearching == true,
                    openIntent = it.openIntent,
                )
            }
        }
}
