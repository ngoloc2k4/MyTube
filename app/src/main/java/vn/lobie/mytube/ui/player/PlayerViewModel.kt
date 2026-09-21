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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.db.entity.LikedVideoEntity
import vn.lobie.mytube.data.local.db.entity.SubscriptionEntity
import vn.lobie.mytube.data.local.db.entity.WatchHistoryEntity
import vn.lobie.mytube.data.local.prefs.SettingsDataStore
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.StreamInfo
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository
import vn.lobie.mytube.playback.PlaybackService
import vn.lobie.mytube.ui.util.Chapter
import vn.lobie.mytube.ui.util.ChapterParser

class PlayerViewModel(
    application: Application,
    private val repository: YouTubeRepository,
    private val getStreamWithFallbackUseCase: vn.lobie.mytube.domain.usecase.GetStreamWithFallbackUseCase = vn.lobie.mytube.domain.usecase.GetStreamWithFallbackUseCase(repository),
    private val searchVideosUseCase: vn.lobie.mytube.domain.usecase.SearchVideosUseCase = vn.lobie.mytube.domain.usecase.SearchVideosUseCase(repository),
    private val getTrendingVideosUseCase: vn.lobie.mytube.domain.usecase.GetTrendingVideosUseCase = vn.lobie.mytube.domain.usecase.GetTrendingVideosUseCase(repository)
) : AndroidViewModel(application) {

    companion object {
        const val FALLBACK_SAMPLE_STREAM = "https://media.w3.org/2010/05/sintel/trailer.mp4"
    }

    private val database = MyTubeDatabase.getInstance(application)
    private val settingsDataStore = SettingsDataStore(application)
    private var likeObservationJob: Job? = null
    private var subObservationJob: Job? = null
    private var downloadObservationJob: Job? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var player: Player? by mutableStateOf(null)
        private set

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null
    private var pendingMediaItem: MediaItem? = null
    private var progressSaveCounter = 0

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTicker()
            } else {
                progressJob?.cancel()
                persistCurrentProgress()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
                Player.STATE_READY -> {
                    val p = player
                    val dur = (p?.duration ?: 0L).coerceAtLeast(0L)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isPlaying = p?.isPlaying == true,
                            durationMs = dur,
                            currentPositionMs = (p?.currentPosition ?: 0L).coerceAtLeast(0L),
                            bufferedPositionMs = (p?.bufferedPosition ?: 0L).coerceAtLeast(0L)
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _uiState.update { it.copy(isPlaying = false) }
                    progressJob?.cancel()
                    persistCurrentProgress()
                    handlePlaybackEnded()
                }
                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            updateAvailableTracks()
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("PlayerViewModel", "Playback error encountered: ${error.errorCodeName} (${error.errorCode})", error)
            val currentVid = _uiState.value.currentVideo
            val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
            val maskedUri = currentUri?.let { vn.lobie.mytube.core.common.AppLogger.maskUrl(it) } ?: "unknown"

            vn.lobie.mytube.core.common.AppLogger.e(
                tag = "Player",
                msg = "Playback error: ${error.errorCodeName} (${error.errorCode}) - ${error.message} [url: $maskedUri]",
                videoId = currentVid?.id,
                raw = error.stackTraceToString()
            )

            // If audio-only mode was active and failed, retry with main video stream
            if (_uiState.value.isAudioOnly && currentVid != null) {
                val fallbackMainUrl = currentStreamInfo?.hlsUrl ?: currentStreamInfo?.videoStreams?.firstOrNull()?.url
                if (fallbackMainUrl != null && fallbackMainUrl != currentUri) {
                    android.util.Log.w("PlayerViewModel", "Audio stream failed, retrying with main stream in audio-only mode")
                    vn.lobie.mytube.core.common.AppLogger.w("Fallback", "Audio stream failed, retrying with main stream in audio-only mode", currentVid.id)
                    setPlayerMedia(fallbackMainUrl, currentVid, player?.currentPosition ?: 0L)
                    return
                }
            }

            // Only attempt fallback if we haven't already failed playing the fallback stream itself
            if (currentVid != null && currentUri != FALLBACK_SAMPLE_STREAM) {
                android.util.Log.w("PlayerViewModel", "Attempting fallback test stream for video ${currentVid.id}")
                vn.lobie.mytube.core.common.AppLogger.w("Fallback", "Attempting fallback test stream for video ${currentVid.id}", currentVid.id)
                val fallbackItem = MediaItem.Builder()
                    .setUri(FALLBACK_SAMPLE_STREAM)
                    .setMediaId(currentVid.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(currentVid.title)
                            .setArtist(currentVid.channel.name)
                            .setArtworkUri(Uri.parse(currentVid.thumbnailUrl))
                            .build()
                    )
                    .build()
                player?.let { p ->
                    p.setMediaItem(fallbackItem)
                    p.prepare()
                    p.play()
                    return
                }
            }

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
        viewModelScope.launch {
            settingsDataStore.autoPlayNext.collect { enabled ->
                _uiState.update { it.copy(isAutoPlayEnabled = enabled) }
            }
        }
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

    private val sponsorBlockClient = vn.lobie.mytube.data.remote.sponsorblock.SponsorBlockClient()
    private var sponsorBlockJob: Job? = null
    private val rydClient = vn.lobie.mytube.data.remote.ryd.ReturnYouTubeDislikeClient()
    private var rydJob: Job? = null
    private var currentStreamInfo: StreamInfo? = null
    private var relatedVideosJob: Job? = null

    fun playVideo(
        video: Video,
        queue: List<Video> = emptyList(),
        queueIndex: Int = 0
    ) {
        persistCurrentProgress()
        val effectiveQueue = if (queue.isNotEmpty()) queue else listOf(video)
        val effectiveIndex = if (queue.isNotEmpty()) queueIndex else 0

        relatedVideosJob?.cancel()
        sponsorBlockJob?.cancel()
        rydJob?.cancel()
        val parsedChapters = ChapterParser.parse(video.description)
        _uiState.update {
            it.copy(
                currentVideo = video,
                queue = effectiveQueue,
                currentQueueIndex = effectiveIndex,
                isLoading = true,
                isExpanded = true,
                currentPositionMs = 0L,
                errorMessage = null,
                isLoadingRelated = true,
                chapters = parsedChapters,
                currentChapter = parsedChapters.firstOrNull(),
                sponsorSegments = emptyList(),
                lastSkippedSegment = null,
                abLoopStartMs = null,
                abLoopEndMs = null,
                isSubtitlesEnabled = false,
                availableSubtitles = emptyList(),
                selectedSubtitle = null,
                dislikesCount = null,
                likesCount = null,
                currentSourceName = null
            )
        }

        // Fetch SponsorBlock skip segments asynchronously
        sponsorBlockJob = viewModelScope.launch {
            val enabled = settingsDataStore.sponsorBlockEnabled.first()
            if (enabled) {
                val segments = sponsorBlockClient.getSegments(video.id)
                if (segments.isNotEmpty()) {
                    _uiState.update { it.copy(sponsorSegments = segments) }
                }
            } else {
                _uiState.update { it.copy(sponsorSegments = emptyList()) }
            }
        }

        // Fetch ReturnYouTubeDislike stats asynchronously
        rydJob = viewModelScope.launch {
            val enabled = settingsDataStore.returnDislikeEnabled.first()
            if (enabled) {
                val ryd = rydClient.getDislikeInfo(video.id)
                if (ryd != null) {
                    _uiState.update {
                        it.copy(
                            dislikesCount = ryd.dislikes,
                            likesCount = if (ryd.likes > 0) ryd.likes else it.likesCount
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(dislikesCount = null) }
            }
        }

        // Load related videos asynchronously with multi-query enrichment
        relatedVideosJob = viewModelScope.launch {
            val results = mutableListOf<Video>()

            // 1. Search by channel
            if (video.channel.name.isNotBlank()) {
                val channelResult = searchVideosUseCase(video.channel.name)
                channelResult.getOrNull()?.mapNotNull {
                    if (it is SearchResult.VideoItem && it.video.id != video.id) it.video else null
                }?.let { results.addAll(it) }
            }

            // 2. Search by key title keywords
            val cleanTitleWords = video.title
                .replace(Regex("[\\[\\]()|•\\-–—_#/@!?,.]"), " ")
                .split(" ")
                .filter { it.isNotBlank() && it.length > 2 }
                .take(4)
                .joinToString(" ")
            if (cleanTitleWords.isNotBlank() && cleanTitleWords != video.channel.name) {
                val titleResult = searchVideosUseCase(cleanTitleWords)
                titleResult.getOrNull()?.mapNotNull {
                    if (it is SearchResult.VideoItem && it.video.id != video.id) it.video else null
                }?.let { results.addAll(it) }
            }

            // 3. Complement with trending if few results
            if (results.size < 6) {
                getTrendingVideosUseCase().getOrNull()?.filter { it.id != video.id }?.let {
                    results.addAll(it)
                }
            }

            val deduped = results.distinctBy { it.id }
            _uiState.update { it.copy(relatedVideos = deduped, isLoadingRelated = false) }
        }

        // Observe liked & subscribed status for current video
        likeObservationJob?.cancel()
        likeObservationJob = viewModelScope.launch {
            database.likedVideoDao().isLiked(video.id).collect { liked ->
                _uiState.update { it.copy(isLiked = liked) }
            }
        }

        subObservationJob?.cancel()
        subObservationJob = viewModelScope.launch {
            database.subscriptionDao().isSubscribed(video.channel.id).collect { sub ->
                _uiState.update { it.copy(isSubscribed = sub) }
            }
        }

        downloadObservationJob?.cancel()
        downloadObservationJob = viewModelScope.launch {
            database.downloadDao().getAll().collect { list ->
                val dl = list.firstOrNull { it.videoId == video.id }
                _uiState.update { it.copy(downloadStatus = dl?.status ?: 0) }
            }
        }

        viewModelScope.launch {
            // Check existing history to resume playback
            val existing = withContext(Dispatchers.IO) {
                database.watchHistoryDao().getEntry(video.id)
            }
            val totalDurationMs = if (video.durationSeconds > 0) video.durationSeconds * 1000L else (existing?.durationSeconds ?: 0L) * 1000L
            val resumePositionMs = if (existing != null && existing.watchedDurationMs > 5000L) {
                if (totalDurationMs <= 0L || existing.watchedDurationMs < (totalDurationMs - 10000L)) {
                    existing.watchedDurationMs
                } else {
                    0L // Video was already watched to completion, restart from 0
                }
            } else {
                0L
            }

            // Record watch history asynchronously
            withContext(Dispatchers.IO) {
                database.watchHistoryDao().insert(
                    WatchHistoryEntity(
                        videoId = video.id,
                        title = video.title,
                        channelId = video.channel.id,
                        channelName = video.channel.name,
                        thumbnailUrl = video.thumbnailUrl,
                        category = "",
                        durationSeconds = video.durationSeconds,
                        watchedDurationMs = resumePositionMs,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // Check if downloaded locally for offline playback
            val downloadedFile = vn.lobie.mytube.data.download.DownloadManager.getInstance(getApplication()).getDownloadedFile(video.id)
            if (downloadedFile != null) {
                _uiState.update {
                    it.copy(
                        availableQualities = listOf("Offline"),
                        selectedQuality = "Offline",
                        currentSourceName = "Ngoại tuyến"
                    )
                }
                setPlayerMedia(android.net.Uri.fromFile(downloadedFile).toString(), video, resumePositionMs)
                return@launch
            }

            val streamResult = getStreamWithFallbackUseCase(video.id)
            val streamInfo = streamResult.getOrNull()
            currentStreamInfo = streamInfo

            val qualities = streamInfo?.videoStreams
                ?.map { it.quality }
                ?.filter { it.isNotBlank() }
                ?.distinct() ?: emptyList()
            val availableQualities = if (qualities.isNotEmpty()) listOf("Auto") + qualities else listOf("Auto")

            val sourceName = streamInfo?.source?.takeIf { it.isNotBlank() } ?: "Native"
            _uiState.update {
                it.copy(
                    availableQualities = availableQualities,
                    selectedQuality = "Auto",
                    currentSourceName = sourceName
                )
            }

            val playableUrl = streamInfo?.hlsUrl?.takeIf { it.isNotBlank() }
                ?: streamInfo?.videoStreams?.firstOrNull { it.url.isNotBlank() }?.url
                ?: streamInfo?.audioStreams?.firstOrNull { it.url.isNotBlank() }?.url
                ?: FALLBACK_SAMPLE_STREAM

            setPlayerMedia(playableUrl, video, resumePositionMs)
        }
    }

    private fun setPlayerMedia(url: String, video: Video, startPositionMs: Long) {
        val maskedUrl = vn.lobie.mytube.core.common.AppLogger.maskUrl(url)
        vn.lobie.mytube.core.common.AppLogger.i(
            tag = "Player",
            msg = "Loading media '${video.title}' [source=${_uiState.value.currentSourceName}, quality=${_uiState.value.selectedQuality}, url=$maskedUrl]",
            videoId = video.id
        )

        val mediaItem = MediaItem.Builder()
            .setUri(url)
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
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, _uiState.value.isAudioOnly)
                .build()
            p.prepare()
            if (startPositionMs > 0L) {
                p.seekTo(startPositionMs)
            }
            p.setPlaybackSpeed(_uiState.value.playbackSpeed)
            p.play()
        } else {
            pendingMediaItem = mediaItem
        }
    }

    fun selectQuality(quality: String) {
        val info = currentStreamInfo ?: return
        val video = _uiState.value.currentVideo ?: return
        val currentPos = player?.currentPosition ?: 0L

        val url = if (quality == "Auto" || quality.isBlank()) {
            info.hlsUrl?.takeIf { it.isNotBlank() }
                ?: info.videoStreams.firstOrNull { it.url.isNotBlank() }?.url
                ?: info.audioStreams.firstOrNull { it.url.isNotBlank() }?.url
        } else {
            info.videoStreams.firstOrNull { it.quality.equals(quality, ignoreCase = true) }?.url
                ?: info.videoStreams.firstOrNull { it.quality.contains(quality, ignoreCase = true) }?.url
                ?: info.videoStreams.firstOrNull()?.url
        } ?: return

        _uiState.update { it.copy(selectedQuality = quality) }
        vn.lobie.mytube.core.common.AppLogger.d(
            tag = "Player",
            msg = "Quality selected '$quality' [${vn.lobie.mytube.core.common.AppLogger.maskUrl(url)}]",
            videoId = video.id
        )
        setPlayerMedia(url, video, currentPos)
    }

    fun toggleAudioOnly() {
        val newAudioOnly = !_uiState.value.isAudioOnly
        _uiState.update { it.copy(isAudioOnly = newAudioOnly) }

        player?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, newAudioOnly)
                .build()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun playNext() {
        playNextVideo()
    }

    fun playNextVideo() {
        val state = _uiState.value
        if (state.isShuffleEnabled && state.queue.size > 1) {
            val candidates = state.queue.indices.filter { it != state.currentQueueIndex }
            if (candidates.isNotEmpty()) {
                val randomIndex = candidates.random()
                playVideo(state.queue[randomIndex], state.queue, randomIndex)
                return
            }
        }
        if (state.queue.isNotEmpty() && state.currentQueueIndex < state.queue.lastIndex) {
            val nextIndex = state.currentQueueIndex + 1
            playVideo(state.queue[nextIndex], state.queue, nextIndex)
        } else if (state.loopMode == LoopMode.ALL && state.queue.isNotEmpty()) {
            playVideo(state.queue[0], state.queue, 0)
        } else if (state.relatedVideos.isNotEmpty()) {
            val nextVideo = state.relatedVideos.first()
            val newQueue = state.queue + nextVideo
            playVideo(nextVideo, newQueue, newQueue.lastIndex)
        }
    }

    fun addToQueue(video: Video) {
        val state = _uiState.value
        if (state.currentVideo == null || state.queue.isEmpty()) {
            playVideo(video, listOf(video), 0)
        } else {
            val newQueue = state.queue + video
            _uiState.update { it.copy(queue = newQueue) }
        }
    }

    fun playNextInQueue(video: Video) {
        val state = _uiState.value
        if (state.currentVideo == null || state.queue.isEmpty()) {
            playVideo(video, listOf(video), 0)
        } else {
            val insertIndex = (state.currentQueueIndex + 1).coerceAtMost(state.queue.size)
            val mutable = state.queue.toMutableList()
            mutable.add(insertIndex, video)
            _uiState.update { it.copy(queue = mutable) }
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _uiState.value
        if (index !in state.queue.indices) return
        val mutable = state.queue.toMutableList()
        mutable.removeAt(index)
        val newIndex = when {
            index < state.currentQueueIndex -> state.currentQueueIndex - 1
            index == state.currentQueueIndex -> state.currentQueueIndex.coerceAtMost(mutable.lastIndex)
            else -> state.currentQueueIndex
        }
        _uiState.update {
            it.copy(
                queue = mutable,
                currentQueueIndex = newIndex
            )
        }
    }

    fun clearQueue() {
        val state = _uiState.value
        val cur = state.currentVideo ?: return
        _uiState.update {
            it.copy(queue = listOf(cur), currentQueueIndex = 0)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value
        val q = state.queue.toMutableList()
        if (fromIndex in q.indices && toIndex in q.indices && fromIndex != toIndex) {
            val item = q.removeAt(fromIndex)
            q.add(toIndex, item)
            val currentVid = state.currentVideo
            val newCurrentIndex = if (currentVid != null) q.indexOfFirst { it.id == currentVid.id }.coerceAtLeast(0) else 0
            _uiState.update { it.copy(queue = q, currentQueueIndex = newCurrentIndex) }
        }
    }

    fun toggleAutoPlay() {
        val next = !_uiState.value.isAutoPlayEnabled
        _uiState.update { it.copy(isAutoPlayEnabled = next) }
        viewModelScope.launch {
            settingsDataStore.setAutoPlayNext(next)
        }
    }

    fun playPrevious() {
        val state = _uiState.value
        val currentPos = player?.currentPosition ?: 0L
        if (currentPos > 3000L) {
            seekTo(0L)
        } else if (state.currentQueueIndex > 0 && state.queue.isNotEmpty()) {
            val prevIndex = state.currentQueueIndex - 1
            playVideo(state.queue[prevIndex], state.queue, prevIndex)
        } else if (state.loopMode == LoopMode.ALL && state.queue.isNotEmpty()) {
            val lastIndex = state.queue.lastIndex
            playVideo(state.queue[lastIndex], state.queue, lastIndex)
        } else {
            seekTo(0L)
        }
    }

    private fun handlePlaybackEnded() {
        val state = _uiState.value
        if (state.isSleepTimerAtEnd) {
            cancelSleepTimer()
            player?.pause()
            return
        }

        if (state.loopMode == LoopMode.ONE) {
            player?.seekTo(0L)
            player?.play()
            return
        }

        if (state.isShuffleEnabled && state.queue.size > 1) {
            val candidates = state.queue.indices.filter { it != state.currentQueueIndex }
            if (candidates.isNotEmpty()) {
                val randomIndex = candidates.random()
                playVideo(state.queue[randomIndex], state.queue, randomIndex)
                return
            }
        }

        if (state.queue.isNotEmpty() && state.currentQueueIndex < state.queue.lastIndex) {
            val nextIndex = state.currentQueueIndex + 1
            playVideo(state.queue[nextIndex], state.queue, nextIndex)
        } else if (state.loopMode == LoopMode.ALL && state.queue.isNotEmpty()) {
            playVideo(state.queue[0], state.queue, 0)
        } else if (state.isAutoPlayEnabled && state.relatedVideos.isNotEmpty()) {
            val nextVideo = state.relatedVideos.first()
            val newQueue = state.queue + nextVideo
            playVideo(nextVideo, newQueue, newQueue.lastIndex)
        }
    }

    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes == -1) {
            _uiState.update { it.copy(isSleepTimerAtEnd = true, sleepTimerRemainingSeconds = null) }
            return
        }
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }

        val totalSeconds = minutes * 60
        _uiState.update { it.copy(isSleepTimerAtEnd = false, sleepTimerRemainingSeconds = totalSeconds) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSeconds = remaining) }
            }
            if (remaining <= 0) {
                player?.pause()
                _uiState.update { it.copy(sleepTimerRemainingSeconds = null, isSleepTimerAtEnd = false) }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerRemainingSeconds = null, isSleepTimerAtEnd = false) }
    }

    fun toggleLoopMode() {
        val next = when (_uiState.value.loopMode) {
            LoopMode.OFF -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.OFF
        }
        _uiState.update { it.copy(loopMode = next) }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun toggleResizeMode() {
        val next = when (_uiState.value.resizeMode) {
            ResizeMode.FIT -> ResizeMode.ZOOM
            ResizeMode.ZOOM -> ResizeMode.FIT
        }
        _uiState.update { it.copy(resizeMode = next) }
    }

    fun seekToChapter(chapter: Chapter) {
        seekTo(chapter.timeMs)
    }

    fun toggleSponsorBlock() {
        _uiState.update { it.copy(isSponsorBlockEnabled = !it.isSponsorBlockEnabled) }
    }

    fun unskipLastSegment() {
        val seg = _uiState.value.lastSkippedSegment ?: return
        player?.seekTo(seg.startMs)
        _uiState.update { it.copy(lastSkippedSegment = null) }
    }

    fun dismissSkippedNotice() {
        _uiState.update { it.copy(lastSkippedSegment = null) }
    }

    fun setDoubleTapSeekSeconds(seconds: Int) {
        _uiState.update { it.copy(doubleTapSeekSeconds = seconds) }
    }

    fun setAbLoopA() {
        val pos = player?.currentPosition ?: 0L
        _uiState.update { it.copy(abLoopStartMs = pos) }
    }

    fun setAbLoopB() {
        val pos = player?.currentPosition ?: 0L
        val start = _uiState.value.abLoopStartMs ?: 0L
        if (pos > start) {
            _uiState.update { it.copy(abLoopEndMs = pos) }
        }
    }

    fun clearAbLoop() {
        _uiState.update { it.copy(abLoopStartMs = null, abLoopEndMs = null) }
    }

    @OptIn(UnstableApi::class)
    fun updateAvailableTracks() {
        val p = player ?: return
        val currentTracks = p.currentTracks
        val subtitleLabels = mutableListOf<String>()
        var isSubEnabled = false
        var activeSubLabel: String? = null

        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Track ${i + 1}"
                    subtitleLabels.add(label)
                    if (group.isTrackSelected(i)) {
                        isSubEnabled = true
                        activeSubLabel = label
                    }
                }
            }
        }
        val distinctSubs = subtitleLabels.distinct()
        _uiState.update {
            it.copy(
                availableSubtitles = distinctSubs,
                isSubtitlesEnabled = isSubEnabled,
                selectedSubtitle = activeSubLabel
            )
        }
    }

    @OptIn(UnstableApi::class)
    fun toggleSubtitles() {
        val p = player ?: return
        val currentlyEnabled = _uiState.value.isSubtitlesEnabled
        val newEnabled = !currentlyEnabled
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !newEnabled)
            .build()
        _uiState.update { it.copy(isSubtitlesEnabled = newEnabled) }
    }

    @OptIn(UnstableApi::class)
    fun selectSubtitle(label: String?) {
        val p = player ?: return
        if (label == null || label == "Off" || label == "Tắt") {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            _uiState.update { it.copy(isSubtitlesEnabled = false, selectedSubtitle = null) }
            return
        }

        val currentTracks = p.currentTracks
        var foundGroup: androidx.media3.common.Tracks.Group? = null
        var foundIndex: Int = -1

        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val trackLabel = format.label ?: format.language ?: "Track ${i + 1}"
                    if (trackLabel.equals(label, ignoreCase = true)) {
                        foundGroup = group
                        foundIndex = i
                        break
                    }
                }
            }
            if (foundGroup != null) break
        }

        if (foundGroup != null && foundIndex != -1) {
            val override = androidx.media3.common.TrackSelectionOverride(
                foundGroup.mediaTrackGroup,
                listOf(foundIndex)
            )
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(override)
                .build()
        } else {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(label.lowercase())
                .build()
        }

        _uiState.update { it.copy(isSubtitlesEnabled = true, selectedSubtitle = label) }
    }

    fun setSubtitleFontSize(size: Float) {
        _uiState.update { it.copy(subtitleFontSize = size) }
    }

    fun setSubtitleBgColor(color: SubtitleBgColor) {
        _uiState.update { it.copy(subtitleBgColor = color) }
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

    fun setSpeed(speed: Float) {
        setPlaybackSpeed(speed)
    }

    fun expand() {
        _uiState.update { it.copy(isExpanded = true) }
    }

    fun collapse() {
        _uiState.update { it.copy(isExpanded = false) }
    }

    fun toggleLike() {
        val video = _uiState.value.currentVideo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val currentlyLiked = _uiState.value.isLiked
            if (currentlyLiked) {
                database.likedVideoDao().delete(video.id)
            } else {
                database.likedVideoDao().insert(
                    LikedVideoEntity(
                        videoId = video.id,
                        title = video.title,
                        channelId = video.channel.id,
                        channelName = video.channel.name,
                        thumbnailUrl = video.thumbnailUrl,
                        durationSeconds = video.durationSeconds,
                        likedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun toggleSubscribe() {
        val video = _uiState.value.currentVideo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val currentlySubbed = _uiState.value.isSubscribed
            if (currentlySubbed) {
                database.subscriptionDao().delete(video.channel.id)
            } else {
                database.subscriptionDao().insert(
                    SubscriptionEntity(
                        channelId = video.channel.id,
                        channelName = video.channel.name,
                        avatarUrl = video.channel.avatarUrl,
                        subscribedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun downloadCurrentVideo() {
        val video = _uiState.value.currentVideo ?: return
        viewModelScope.launch {
            val stream = currentStreamInfo ?: getStreamWithFallbackUseCase(video.id).getOrNull()
            val url = stream?.videoStreams?.firstOrNull { it.url.isNotBlank() }?.url
                ?: stream?.hlsUrl
                ?: stream?.audioStreams?.firstOrNull { it.url.isNotBlank() }?.url
            if (url != null) {
                vn.lobie.mytube.data.download.DownloadManager.getInstance(getApplication()).startDownload(
                    video = video,
                    streamUrl = url,
                    quality = stream.videoStreams.firstOrNull()?.quality ?: "720p"
                )
            }
        }
    }

    fun cancelCurrentDownload() {
        val video = _uiState.value.currentVideo ?: return
        vn.lobie.mytube.data.download.DownloadManager.getInstance(getApplication()).deleteDownload(video.id)
    }

    fun close() {
        persistCurrentProgress()
        progressJob?.cancel()
        likeObservationJob?.cancel()
        subObservationJob?.cancel()
        downloadObservationJob?.cancel()
        pendingMediaItem = null
        player?.let { p ->
            p.stop()
            p.clearMediaItems()
        }
        _uiState.update { PlayerUiState() }
    }

    fun persistCurrentProgress() {
        val v = _uiState.value.currentVideo ?: return
        val pos = player?.currentPosition ?: return
        if (pos > 1000L) {
            viewModelScope.launch(Dispatchers.IO) {
                database.watchHistoryDao().updateProgress(v.id, pos)
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                player?.let { p ->
                    val pos = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration.coerceAtLeast(0L)
                    val buf = p.bufferedPosition.coerceAtLeast(0L)
                    val curChapters = _uiState.value.chapters
                    val activeChapter = ChapterParser.getCurrentChapter(curChapters, pos)
                    val state = _uiState.value

                    // A-B Loop wrap-around
                    val loopStart = state.abLoopStartMs
                    val loopEnd = state.abLoopEndMs
                    if (loopStart != null && loopEnd != null && loopEnd > loopStart && pos >= loopEnd) {
                        p.seekTo(loopStart)
                    }

                    // SponsorBlock automatic segment skip
                    var skippedSeg: vn.lobie.mytube.data.remote.sponsorblock.SponsorSegment? = null
                    if (state.isSponsorBlockEnabled && state.sponsorSegments.isNotEmpty()) {
                        val seg = state.sponsorSegments.firstOrNull { pos >= it.startMs && pos < (it.endMs - 400L) }
                        if (seg != null && seg.actionType == "skip") {
                            p.seekTo(seg.endMs)
                            skippedSeg = seg
                        }
                    }

                    _uiState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = buf,
                            currentChapter = activeChapter,
                            lastSkippedSegment = skippedSeg ?: it.lastSkippedSegment
                        )
                    }

                    progressSaveCounter++
                    if (progressSaveCounter % 8 == 0 && pos > 1000L) {
                        _uiState.value.currentVideo?.let { v ->
                            viewModelScope.launch(Dispatchers.IO) {
                                database.watchHistoryDao().updateProgress(v.id, pos)
                            }
                        }
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
