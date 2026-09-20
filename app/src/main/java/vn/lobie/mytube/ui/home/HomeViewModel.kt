package vn.lobie.mytube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.lobie.mytube.domain.repository.YouTubeRepository

class HomeViewModel(
    private val repository: YouTubeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTrendingVideos()
    }

    fun loadTrendingVideos() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            repository.getTrendingVideos()
                .onSuccess { videos ->
                    _uiState.value = HomeUiState.Success(videos = videos)
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState.Error(error.localizedMessage ?: "Failed to load videos")
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        val current = _uiState.value as? HomeUiState.Success ?: return
        _uiState.value = current.copy(searchQuery = query)
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            loadTrendingVideos()
            return
        }
        viewModelScope.launch {
            val current = _uiState.value as? HomeUiState.Success
            _uiState.value = current?.copy(isSearching = true) ?: HomeUiState.Loading
            repository.search(query)
                .onSuccess { results ->
                    val base = current ?: HomeUiState.Success(videos = emptyList())
                    _uiState.value = base.copy(
                        searchResults = results,
                        searchQuery = query,
                        isSearching = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState.Error(error.localizedMessage ?: "Search failed")
                }
        }
    }

    fun clearSearch() {
        loadTrendingVideos()
    }
}
