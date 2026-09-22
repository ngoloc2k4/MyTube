package vn.lobie.mytube.domain.usecase

import kotlinx.coroutines.flow.first
import vn.lobie.mytube.data.local.prefs.SettingsDataStore
import vn.lobie.mytube.data.remote.listenbrainz.ListenBrainzClient

class ScrobbleListenUseCase(
    private val settingsDataStore: SettingsDataStore,
    private val client: ListenBrainzClient = ListenBrainzClient()
) {
    suspend operator fun invoke(
        artistName: String,
        trackName: String,
        releaseName: String = ""
    ): Result<Unit> {
        val isEnabled = settingsDataStore.listenBrainzEnabled.first()
        if (!isEnabled) return Result.success(Unit)

        val token = settingsDataStore.listenBrainzToken.first()
        if (token.isBlank()) return Result.success(Unit)

        return client.submitListen(
            token = token,
            artistName = artistName,
            trackName = trackName,
            releaseName = releaseName
        )
    }

    suspend fun validateToken(token: String): Result<String> {
        return client.validateToken(token)
    }
}
