package vn.lobie.mytube.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository
import vn.lobie.mytube.domain.usecase.GetRecommendationsUseCase

class MusicViewModel(
    application: Application,
    private val repository: YouTubeRepository,
    private val settingsDataStore: vn.lobie.mytube.data.local.prefs.SettingsDataStore? = null,
    private val recommendationsUseCase: GetRecommendationsUseCase = GetRecommendationsUseCase(
        watchHistoryDao = MyTubeDatabase.getInstance(application).watchHistoryDao(),
        repository = repository,
        likedVideoDao = MyTubeDatabase.getInstance(application).likedVideoDao(),
        subscriptionDao = MyTubeDatabase.getInstance(application).subscriptionDao(),
        hiddenVideoDao = MyTubeDatabase.getInstance(application).hiddenVideoDao()
    )
) : AndroidViewModel(application) {

    val genres = listOf(
        "Dành cho bạn",
        "Mới phát hành",
        "Bảng xếp hạng",
        "Trending",
        "V-Pop",
        "K-Pop",
        "US-UK",
        "Lofi Chill",
        "EDM / Remix",
        "Acoustic",
        "Piano"
    )

    private val _selectedGenre = MutableStateFlow(genres[0])
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _tracks = MutableStateFlow<List<Video>>(emptyList())
    val tracks: StateFlow<List<Video>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Genre track cache for instantaneous tab switching without reloading
    private val genreCache = mutableMapOf<String, List<Video>>()

    init {
        if (settingsDataStore != null) {
            viewModelScope.launch {
                settingsDataStore.contentRegion.collect {
                    genreCache.clear()
                    loadGenreTracks(_selectedGenre.value)
                }
            }
        } else {
            loadGenreTracks(genres[0])
        }
    }

    fun selectGenre(genre: String) {
        _selectedGenre.value = genre
        // Immediate render from cache if already loaded
        val cached = genreCache[genre]
        if (!cached.isNullOrEmpty()) {
            _tracks.value = cached
            _isLoading.value = false
            _error.value = null
        } else {
            loadGenreTracks(genre)
        }
    }

    fun refresh() {
        genreCache.remove(_selectedGenre.value)
        loadGenreTracks(_selectedGenre.value)
    }

    private fun loadGenreTracks(genre: String) {
        viewModelScope.launch {
            val cached = genreCache[genre]
            if (!cached.isNullOrEmpty()) {
                _tracks.value = cached
            } else {
                _isLoading.value = true
            }
            _error.value = null

            if (genre == "Dành cho bạn" || genre == "For You") {
                val recResult = recommendationsUseCase()
                recResult.onSuccess { list ->
                    val musicTracks = if (list.isNotEmpty()) list else {
                        // Fallback to trending music if user has no music signals
                        repository.search("top trending music official").getOrNull()?.mapNotNull { item ->
                            if (item is SearchResult.VideoItem) item.video else null
                        } ?: emptyList()
                    }
                    _tracks.value = musicTracks
                    genreCache[genre] = musicTracks
                }.onFailure { err ->
                    val fallback = repository.search("top trending music official").getOrNull()?.mapNotNull { item ->
                        if (item is SearchResult.VideoItem) item.video else null
                    } ?: emptyList()
                    if (fallback.isNotEmpty()) {
                        _tracks.value = fallback
                        genreCache[genre] = fallback
                    } else {
                        _error.value = err.localizedMessage ?: "Chưa có dữ liệu đề xuất"
                    }
                }
            } else {
                val query = when (genre) {
                    "Mới phát hành" -> "nhạc mới phát hành official music video new release"
                    "Bảng xếp hạng" -> "bảng xếp hạng âm nhạc hot top charts billboard official"
                    "Trending" -> "nhạc trending hot tiktok youtube official"
                    "V-Pop" -> "vpop official music video"
                    "K-Pop" -> "kpop official music video"
                    "US-UK" -> "us uk pop official music video"
                    "Lofi Chill" -> "lofi chill beats to relax study to"
                    "EDM / Remix" -> "edm remix bass boosted music"
                    "Acoustic" -> "acoustic guitar cover music"
                    "Piano" -> "peaceful piano music relaxing"
                    else -> "$genre music official"
                }

                val result = repository.search(query)
                result.onSuccess { results ->
                    val list = results.mapNotNull { item ->
                        if (item is SearchResult.VideoItem) item.video else null
                    }.distinctBy { it.id }
                    _tracks.value = list
                    genreCache[genre] = list
                }.onFailure { err ->
                    _error.value = err.localizedMessage ?: "Failed to load music"
                }
            }
            _isLoading.value = false
        }
    }
}
