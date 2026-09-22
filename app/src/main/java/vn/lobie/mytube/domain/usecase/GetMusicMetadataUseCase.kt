package vn.lobie.mytube.domain.usecase

import vn.lobie.mytube.data.remote.musicbrainz.MusicBrainzClient
import vn.lobie.mytube.domain.model.ArtistInfo
import vn.lobie.mytube.domain.model.MusicMetadata

class GetMusicMetadataUseCase(
    private val client: MusicBrainzClient = MusicBrainzClient()
) {
    suspend operator fun invoke(rawTitle: String, channelName: String): Result<MusicMetadata> {
        return client.getEnrichedMetadata(rawTitle, channelName)
    }

    suspend fun getArtistInfo(artistName: String, artistMbid: String? = null): Result<ArtistInfo> {
        return client.getArtistInfo(artistName, artistMbid)
    }
}
