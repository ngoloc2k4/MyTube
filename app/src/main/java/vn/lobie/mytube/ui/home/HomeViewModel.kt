package vn.lobie.mytube.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.prefs.SettingsDataStore
import vn.lobie.mytube.data.repository.CascadingYouTubeRepository
import vn.lobie.mytube.R
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

enum class SearchSort(val titleRes: Int) {
    RELEVANCE(R.string.filter_relevance),
    UPLOAD_DATE(R.string.filter_upload_date),
    VIEW_COUNT(R.string.filter_view_count)
}

enum class SearchDuration(val titleRes: Int) {
    ALL(R.string.filter_duration_all),
    SHORT(R.string.filter_duration_short),
    MEDIUM(R.string.filter_duration_medium),
    LONG(R.string.filter_duration_long)
}

data class SearchFilter(
    val sort: SearchSort = SearchSort.RELEVANCE,
    val duration: SearchDuration = SearchDuration.ALL
)

class HomeViewModel(
    private val repository: YouTubeRepository,
    private val database: MyTubeDatabase? = null,
    private val settingsDataStore: SettingsDataStore? = null,
    private val getTrendingVideosUseCase: vn.lobie.mytube.domain.usecase.GetTrendingVideosUseCase = vn.lobie.mytube.domain.usecase.GetTrendingVideosUseCase(repository),
    private val searchVideosUseCase: vn.lobie.mytube.domain.usecase.SearchVideosUseCase = vn.lobie.mytube.domain.usecase.SearchVideosUseCase(repository)
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilter())
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    companion object {
        private var cachedHomeVideos: List<Video> = emptyList()
    }

    val watchProgressMap: StateFlow<Map<String, Float>> = (database?.watchHistoryDao()?.getAll() ?: flowOf(emptyList()))
        .map { list ->
            list.associate { entity ->
                val dur = entity.durationSeconds * 1000L
                val prog = if (dur > 0) (entity.watchedDurationMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                entity.videoId to prog
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val searchHistory: StateFlow<List<String>> = (settingsDataStore?.searchHistory ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (settingsDataStore != null) {
            viewModelScope.launch {
                settingsDataStore.contentRegion.collect { region ->
                    (repository as? CascadingYouTubeRepository)?.setRegion(region)
                    loadRecommendedVideos()
                }
            }
        } else {
            loadRecommendedVideos()
        }
    }

    fun loadRecommendedVideos() {
        viewModelScope.launch {
            if (cachedHomeVideos.isNotEmpty()) {
                _uiState.value = HomeUiState.Success(
                    videos = cachedHomeVideos,
                    selectedCategory = VideoCategory.ALL
                )
            } else {
                _uiState.value = HomeUiState.Loading
            }

            try {
                // 1. Fetch base trending videos
                val trendingResult = getTrendingVideosUseCase()
                val trendingVideos = trendingResult.getOrDefault(emptyList())

                // 2. Fetch recommendations based on Watch History & Subscriptions
                val recommendedVideos = mutableListOf<Video>()
                val hiddenIds = try {
                    database?.hiddenVideoDao()?.getAllHiddenIdsList()?.toSet() ?: emptySet()
                } catch (e: Exception) {
                    emptySet()
                }

                val recentHistory = try {
                    database?.watchHistoryDao()?.getRecentList(5) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                val subscriptions = try {
                    database?.subscriptionDao()?.getAllList() ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                // Gather target search queries from history and subs (channels and topics)
                val recommendationQueries = mutableListOf<String>()
                subscriptions.take(3).forEach { sub ->
                    if (sub.channelName.isNotBlank() && !recommendationQueries.contains(sub.channelName)) {
                        recommendationQueries.add(sub.channelName)
                    }
                }
                recentHistory.take(3).forEach { history ->
                    if (history.channelName.isNotBlank() && !recommendationQueries.contains(history.channelName)) {
                        recommendationQueries.add(history.channelName)
                    }
                }

                // Query related videos asynchronously
                if (recommendationQueries.isNotEmpty()) {
                    val deferredResults = recommendationQueries.take(3).map { query ->
                        async {
                            searchVideosUseCase(query).getOrNull()?.mapNotNull { item ->
                                if (item is SearchResult.VideoItem) item.video else null
                            } ?: emptyList()
                        }
                    }
                    val queryResults = deferredResults.awaitAll()
                    queryResults.forEach { videos ->
                        recommendedVideos.addAll(videos.take(4))
                    }
                }

                // 3. Smart Interleaving: Interleave recommendations and trending videos
                val combined = mutableListOf<Video>()
                var recIdx = 0
                var trendIdx = 0

                while (recIdx < recommendedVideos.size || trendIdx < trendingVideos.size) {
                    // Add 2 recommended
                    var addedRec = 0
                    while (recIdx < recommendedVideos.size && addedRec < 2) {
                        combined.add(recommendedVideos[recIdx++])
                        addedRec++
                    }
                    // Add 2 trending
                    var addedTrend = 0
                    while (trendIdx < trendingVideos.size && addedTrend < 2) {
                        combined.add(trendingVideos[trendIdx++])
                        addedTrend++
                    }
                }

                // If combined is still empty (e.g. offline or API issues), fallback to trending
                val finalList = if (combined.isNotEmpty()) combined else trendingVideos

                // 4. Strict Deduplication: filter hidden videos & ensure NO DUPLICATES
                val deduplicatedVideos = finalList
                    .filterNot { hiddenIds.contains(it.id) }
                    .distinctBy { it.id }

                if (deduplicatedVideos.isNotEmpty()) {
                    cachedHomeVideos = deduplicatedVideos
                    _uiState.value = HomeUiState.Success(
                        videos = deduplicatedVideos,
                        selectedCategory = VideoCategory.ALL
                    )
                } else if (cachedHomeVideos.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(
                        videos = cachedHomeVideos,
                        selectedCategory = VideoCategory.ALL
                    )
                } else if (trendingResult.isFailure) {
                    _uiState.value = HomeUiState.Error(
                        trendingResult.exceptionOrNull()?.localizedMessage ?: "Failed to load videos"
                    )
                } else {
                    _uiState.value = HomeUiState.Success(
                        videos = emptyList(),
                        selectedCategory = VideoCategory.ALL
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading recommended feed", e)
                if (cachedHomeVideos.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(
                        videos = cachedHomeVideos,
                        selectedCategory = VideoCategory.ALL
                    )
                } else {
                    _uiState.value = HomeUiState.Error(e.localizedMessage ?: "Failed to load feed")
                }
            }
        }
    }

    fun loadTrendingVideos() {
        loadRecommendedVideos()
    }

    fun selectCategory(category: VideoCategory) {
        if (category == VideoCategory.ALL) {
            loadRecommendedVideos()
            return
        }
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val hiddenIds = try {
                database?.hiddenVideoDao()?.getAllHiddenIdsList()?.toSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }

            searchVideosUseCase(category.searchQuery ?: "")
                .onSuccess { results ->
                    val filteredResults = results
                        .filterNot { item ->
                            item is SearchResult.VideoItem && hiddenIds.contains(item.video.id)
                        }
                        .distinctBy { item ->
                            if (item is SearchResult.VideoItem) item.video.id else item.hashCode().toString()
                        }

                    _uiState.value = HomeUiState.Success(
                        videos = emptyList(),
                        searchResults = filteredResults,
                        selectedCategory = category
                    )
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState.Error(error.localizedMessage ?: "Failed to load category")
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        val current = _uiState.value as? HomeUiState.Success ?: return
        _uiState.value = current.copy(searchQuery = query)
    }

    fun setFilter(filter: SearchFilter) {
        _searchFilter.value = filter
        val current = _uiState.value as? HomeUiState.Success ?: return
        if (current.searchQuery.isNotBlank()) {
            performSearch(current.searchQuery)
        }
    }

    private fun applyFilters(results: List<SearchResult>): List<SearchResult> {
        val filter = _searchFilter.value
        var filtered = results

        // Duration filter
        if (filter.duration != SearchDuration.ALL) {
            filtered = filtered.filter { item ->
                if (item is SearchResult.VideoItem) {
                    val sec = item.video.durationSeconds
                    when (filter.duration) {
                        SearchDuration.SHORT -> sec in 1..240
                        SearchDuration.MEDIUM -> sec in 241..1200
                        SearchDuration.LONG -> sec > 1200
                        else -> true
                    }
                } else true
            }
        }

        // Sort filter
        if (filter.sort == SearchSort.VIEW_COUNT) {
            filtered = filtered.sortedByDescending {
                if (it is SearchResult.VideoItem) it.video.viewCount else 0L
            }
        } else if (filter.sort == SearchSort.UPLOAD_DATE) {
            filtered = filtered.sortedByDescending {
                if (it is SearchResult.VideoItem) it.video.publishedTimeText else ""
            }
        }

        return filtered
    }

    fun performSearch(query: String) {
        val sanitized = vn.lobie.mytube.core.common.SecurityUtils.sanitizeSearchQuery(query)
        if (sanitized.isBlank()) {
            loadRecommendedVideos()
            return
        }
        val trimmedQuery = sanitized
        viewModelScope.launch {
            settingsDataStore?.addSearchHistory(trimmedQuery)
        }
        viewModelScope.launch {
            val current = _uiState.value as? HomeUiState.Success
            _uiState.value = current?.copy(isSearching = true) ?: HomeUiState.Loading
            val hiddenIds = try {
                database?.hiddenVideoDao()?.getAllHiddenIdsList()?.toSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }

            searchVideosUseCase(trimmedQuery)
                .onSuccess { results ->
                    val rawFiltered = results
                        .filterNot { item ->
                            item is SearchResult.VideoItem && hiddenIds.contains(item.video.id)
                        }
                        .distinctBy { item ->
                            if (item is SearchResult.VideoItem) item.video.id else item.hashCode().toString()
                        }

                    val filteredResults = applyFilters(rawFiltered)

                    val base = current ?: HomeUiState.Success(videos = emptyList())
                    _uiState.value = base.copy(
                        searchResults = filteredResults,
                        searchQuery = trimmedQuery,
                        isSearching = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState.Error(error.localizedMessage ?: "Search failed")
                }
        }
    }

    fun deleteSearchHistory(query: String) {
        viewModelScope.launch {
            settingsDataStore?.removeSearchHistory(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            settingsDataStore?.clearSearchHistory()
        }
    }

    fun clearSearch() {
        loadRecommendedVideos()
    }
}
