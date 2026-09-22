package vn.lobie.mytube.domain.model

data class MusicMetadata(
    val mbid: String = "",
    val trackTitle: String = "",
    val artistName: String = "",
    val artistMbid: String = "",
    val albumTitle: String = "",
    val releaseMbid: String = "",
    val releaseYear: String = "",
    val coverArtUrl: String = "",
    val genres: List<String> = emptyList()
)

data class ArtistInfo(
    val mbid: String = "",
    val name: String = "",
    val type: String = "", // Person, Group
    val country: String = "",
    val lifeSpan: String = "",
    val biography: String = "",
    val tags: List<String> = emptyList()
)
