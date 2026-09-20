package vn.lobie.mytube.ui.home

import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val videos: List<Video>,
        val searchResults: List<SearchResult>? = null,
        val searchQuery: String = "",
        val isSearching: Boolean = false
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
