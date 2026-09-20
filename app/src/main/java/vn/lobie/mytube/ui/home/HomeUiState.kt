package vn.lobie.mytube.ui.home

import androidx.annotation.StringRes
import vn.lobie.mytube.R
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video

enum class VideoCategory(
    @StringRes val titleRes: Int,
    val searchQuery: String?
) {
    ALL(R.string.category_all, null),
    MUSIC(R.string.category_music, "music"),
    GAMING(R.string.category_gaming, "gaming"),
    NEWS(R.string.category_news, "news"),
    TECH(R.string.category_tech, "technology"),
    PODCASTS(R.string.category_podcasts, "podcast")
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val videos: List<Video>,
        val searchResults: List<SearchResult>? = null,
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val selectedCategory: VideoCategory = VideoCategory.ALL
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
