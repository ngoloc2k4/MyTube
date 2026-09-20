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
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
