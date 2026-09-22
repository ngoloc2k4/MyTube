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

    init {
        loadGenreTracks(genres[0])
    }

    fun selectGenre(genre: String) {
        _selectedGenre.value = genre
        loadGenreTracks(genre)
    }

    fun refresh() {
        loadGenreTracks(_selectedGenre.value)
    }

    private fun loadGenreTracks(genre: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            if (genre == "Dành cho bạn" || genre == "For You") {
                val recResult = recommendationsUseCase()
                recResult.onSuccess { list ->
                    _tracks.value = list
                }.onFailure { err ->
                    _error.value = err.localizedMessage ?: "Chưa có dữ liệu đề xuất"
                }
            } else {
                val query = if (genre == "Trending") "top trending music" else "$genre music official"
                val result = repository.search(query)
                result.onSuccess { results ->
                    val list = results.mapNotNull { item ->
                        if (item is SearchResult.VideoItem) item.video else null
                    }.distinctBy { it.id }
                    _tracks.value = list
                }.onFailure { err ->
                    _error.value = err.localizedMessage ?: "Failed to load music"
                }
            }
            _isLoading.value = false
        }
    }
}
