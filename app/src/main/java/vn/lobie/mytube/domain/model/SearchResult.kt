package vn.lobie.mytube.domain.model

sealed interface SearchResult {
    data class VideoItem(val video: Video) : SearchResult
    data class ChannelItem(val channel: Channel) : SearchResult
}
