package vn.lobie.mytube.ui.player

import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.ui.util.Chapter

enum class LoopMode {
    OFF, ONE, ALL
}

enum class ResizeMode {
    FIT, ZOOM
}

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
    val loopMode: LoopMode = LoopMode.OFF,
    val isShuffleEnabled: Boolean = false,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val sleepTimerRemainingSeconds: Int? = null,
    val isSleepTimerAtEnd: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val isSponsorBlockEnabled: Boolean = true,
    val sponsorSegments: List<vn.lobie.mytube.data.remote.sponsorblock.SponsorSegment> = emptyList(),
    val lastSkippedSegment: vn.lobie.mytube.data.remote.sponsorblock.SponsorSegment? = null,
    val doubleTapSeekSeconds: Int = 10,
    val abLoopStartMs: Long? = null,
    val abLoopEndMs: Long? = null,
    val isSubtitlesEnabled: Boolean = false,
    val availableSubtitles: List<String> = emptyList(),
    val selectedSubtitle: String? = null,
    val subtitleFontSize: Float = 1.0f,
    val dislikesCount: Long? = null,
    val likesCount: Long? = null,
    val currentSourceName: String? = null,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val hasNextVideo: Boolean
        get() = when (loopMode) {
            LoopMode.ALL -> queue.isNotEmpty()
            LoopMode.ONE -> true
            LoopMode.OFF -> (queue.isNotEmpty() && currentQueueIndex < queue.lastIndex) || relatedVideos.isNotEmpty()
        }

    val hasPreviousVideo: Boolean
        get() = currentQueueIndex > 0 || (loopMode == LoopMode.ALL && queue.isNotEmpty())
}

