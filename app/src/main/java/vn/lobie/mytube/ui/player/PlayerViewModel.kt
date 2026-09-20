package vn.lobie.mytube.ui.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository
import vn.lobie.mytube.playback.PlaybackService

class PlayerViewModel(
    application: Application,
    private val repository: YouTubeRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var player: Player? by mutableStateOf(null)
        private set

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null
    private var pendingMediaItem: MediaItem? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTicker()
            } else {
                progressJob?.cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
                Player.STATE_READY -> {
                    val p = player
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            durationMs = (p?.duration ?: 0L).coerceAtLeast(0L),
                            currentPositionMs = (p?.currentPosition ?: 0L).coerceAtLeast(0L),
                            bufferedPositionMs = (p?.bufferedPosition ?: 0L).coerceAtLeast(0L)
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _uiState.update { it.copy(isPlaying = false) }
                    progressJob?.cancel()
                }
                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = error.localizedMessage ?: "Playback error"
                )
            }
        }
    }

    init {
        initializeController()
    }

    private fun initializeController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val controller = future.get()
                this.player = controller
                controller.addListener(playerListener)
                pendingMediaItem?.let { item ->
                    controller.setMediaItem(item)
                    controller.prepare()
                    controller.play()
                    pendingMediaItem = null
                }
                _uiState.update {
                    it.copy(
                        isPlaying = controller.isPlaying,
                        durationMs = controller.duration.coerceAtLeast(0L),
                        currentPositionMs = controller.currentPosition.coerceAtLeast(0L)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun playVideo(video: Video) {
        _uiState.update {
            it.copy(
                currentVideo = video,
                isLoading = true,
                isExpanded = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val streamResult = repository.getStreamInfo(video.id)
            val streamInfo = streamResult.getOrNull()

            // Ưu tiên:
            // 1. Combined VideoStream chất lượng tốt (mp4 720p hoặc bất kỳ)
            // 2. HLS stream (m3u8)
            // 3. AudioStream nếu chỉ có audio
            // 4. Test fallback MP4
            val playableUrl = streamInfo?.videoStreams?.firstOrNull { it.url.isNotEmpty() }?.url
                ?: streamInfo?.hlsUrl
                ?: streamInfo?.audioStreams?.firstOrNull { it.url.isNotEmpty() }?.url
                ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

            val mediaItem = MediaItem.Builder()
                .setUri(playableUrl)
                .setMediaId(video.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(video.title)
                        .setArtist(video.channel.name)
                        .setArtworkUri(Uri.parse(video.thumbnailUrl))
                        .build()
                )
                .build()

            val p = player
            if (p != null) {
                p.setMediaItem(mediaItem)
                p.prepare()
                p.play()
            } else {
                pendingMediaItem = mediaItem
            }
        }
    }

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekBy(offsetMs: Long) {
        player?.let { p ->
            val target = (p.currentPosition + offsetMs).coerceIn(0L, p.duration.coerceAtLeast(0L))
            p.seekTo(target)
            _uiState.update { it.copy(currentPositionMs = target) }
        }
    }

    fun expand() {
        _uiState.update { it.copy(isExpanded = true) }
    }

    fun collapse() {
        _uiState.update { it.copy(isExpanded = false) }
    }

    fun close() {
        progressJob?.cancel()
        pendingMediaItem = null
        player?.let { p ->
            p.stop()
            p.clearMediaItems()
        }
        _uiState.update { PlayerUiState() }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                player?.let { p ->
                    _uiState.update {
                        it.copy(
                            currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                            durationMs = p.duration.coerceAtLeast(0L),
                            bufferedPositionMs = p.bufferedPosition.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCleared() {
        progressJob?.cancel()
        player?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
