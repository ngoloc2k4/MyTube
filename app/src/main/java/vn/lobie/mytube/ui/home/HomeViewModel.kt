package vn.lobie.mytube.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

class HomeViewModel(
    private val repository: YouTubeRepository,
    private val database: MyTubeDatabase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecommendedVideos()
    }

    fun loadRecommendedVideos() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            try {
                // 1. Fetch base trending videos
                val trendingResult = repository.getTrendingVideos()
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
                            repository.search(query).getOrNull()?.mapNotNull { item ->
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
                    _uiState.value = HomeUiState.Success(
                        videos = deduplicatedVideos,
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
                _uiState.value = HomeUiState.Error(e.localizedMessage ?: "Failed to load feed")
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

            repository.search(category.searchQuery ?: "")
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

    fun performSearch(query: String) {
        if (query.isBlank()) {
            loadRecommendedVideos()
            return
        }
        viewModelScope.launch {
            val current = _uiState.value as? HomeUiState.Success
            _uiState.value = current?.copy(isSearching = true) ?: HomeUiState.Loading
            val hiddenIds = try {
                database?.hiddenVideoDao()?.getAllHiddenIdsList()?.toSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }

            repository.search(query)
                .onSuccess { results ->
                    val filteredResults = results
                        .filterNot { item ->
                            item is SearchResult.VideoItem && hiddenIds.contains(item.video.id)
                        }
                        .distinctBy { item ->
                            if (item is SearchResult.VideoItem) item.video.id else item.hashCode().toString()
                        }

                    val base = current ?: HomeUiState.Success(videos = emptyList())
                    _uiState.value = base.copy(
                        searchResults = filteredResults,
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
        loadRecommendedVideos()
    }
}
