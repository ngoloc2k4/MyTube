package vn.lobie.mytube.ui.player

import vn.lobie.mytube.domain.model.Video

data class PlayerUiState(
    val currentVideo: Video? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isExpanded: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isLiked: Boolean = false,
    val isSubscribed: Boolean = false,
    val availableQualities: List<String> = emptyList(),
    val selectedQuality: String = "Auto",
    val playbackSpeed: Float = 1.0f,
    val isAudioOnly: Boolean = false,
    val isFullscreen: Boolean = false,
    val queue: List<Video> = emptyList(),
    val currentQueueIndex: Int = 0,
    val relatedVideos: List<Video> = emptyList(),
    val isLoadingRelated: Boolean = false,
    val isAutoPlayEnabled: Boolean = true,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val hasNextVideo: Boolean
        get() = queue.isNotEmpty() && currentQueueIndex < queue.lastIndex || relatedVideos.isNotEmpty()

    val hasPreviousVideo: Boolean
        get() = currentQueueIndex > 0
}
