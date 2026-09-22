package vn.lobie.mytube.ui.player

import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.ui.util.Chapter

enum class LoopMode {
    OFF, ONE, ALL
}

enum class ResizeMode {
    FIT, ZOOM
}

enum class SubtitleBgColor(val label: String, val colorInt: Int, val foregroundInt: Int) {
    BLACK_TRANSLUCENT("Mờ", 0x80000000.toInt(), 0xFFFFFFFF.toInt()),
    BLACK_SOLID("Đen", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    TRANSPARENT("Trong suốt", 0x00000000, 0xFFFFFFFF.toInt()),
    YELLOW_ON_BLACK("Chữ vàng", 0xCC000000.toInt(), 0xFFFFD700.toInt())
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
    val subtitleBgColor: SubtitleBgColor = SubtitleBgColor.BLACK_TRANSLUCENT,
    val dislikesCount: Long? = null,
    val likesCount: Long? = null,
    val currentSourceName: String? = null,
    val downloadStatus: Int = 0,
    val downloadProgress: Int = 0,
    val downloadOptions: List<DownloadOption> = emptyList(),
    val showDownloadDialog: Boolean = false,
    val musicMetadata: vn.lobie.mytube.domain.model.MusicMetadata? = null,
    val artistInfo: vn.lobie.mytube.domain.model.ArtistInfo? = null,
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

data class DownloadOption(
    val title: String,
    val quality: String,
    val format: String,
    val isAudioOnly: Boolean,
    val videoUrl: String,
    val audioUrl: String? = null,
    val bitrate: Long = 0L
)
