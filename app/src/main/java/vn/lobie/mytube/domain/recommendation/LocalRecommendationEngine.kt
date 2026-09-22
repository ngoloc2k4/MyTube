package vn.lobie.mytube.domain.recommendation

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import vn.lobie.mytube.data.local.db.dao.HiddenVideoDao
import vn.lobie.mytube.data.local.db.dao.LikedVideoDao
import vn.lobie.mytube.data.local.db.dao.SubscriptionDao
import vn.lobie.mytube.data.local.db.dao.WatchHistoryDao
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

class LocalRecommendationEngine(
    private val watchHistoryDao: WatchHistoryDao?,
    private val likedVideoDao: LikedVideoDao?,
    private val subscriptionDao: SubscriptionDao?,
    private val hiddenVideoDao: HiddenVideoDao?,
    private val repository: YouTubeRepository,
    private val scorer: RecommendationScorer = RecommendationScorer()
) {

    /**
     * Produces a personalized recommendation list based on on-device user signals.
     */
    suspend fun getRecommendations(limit: Int = 20): Result<List<Video>> = coroutineScope {
        try {
            // 1. Collect signals from local Room DB
            val signals = collectUserSignals()

            // 2. Cold-start fallback: If no history, liked videos, or subscriptions, fallback to regional trending
            val hasSignals = signals.watchHistory.isNotEmpty() ||
                    signals.likedVideos.isNotEmpty() ||
                    signals.subscriptions.isNotEmpty()

            if (!hasSignals) {
                Log.d("RecEngine", "Cold start detected: fetching regional trending videos")
                val trending = repository.getTrendingVideos().getOrDefault(emptyList())
                val filtered = trending.filterNot { signals.hiddenVideoIds.contains(it.id) }
                return@coroutineScope Result.success(filtered.take(limit))
            }

            // 3. Multi-source candidate generation
            // Extract top channels & keywords for candidate retrieval queries
            val channelAffinities = scorer.extractChannelAffinities(signals)
            val topChannels = channelAffinities.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

            val keywordAffinities = scorer.extractInterestKeywords(signals)
            val topKeywords = keywordAffinities.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

            val queries = mutableListOf<String>()
            topChannels.forEach { queries.add(it) }
            topKeywords.forEach { queries.add(it) }

            // Concurrent candidate retrieval
            val deferredQueries = queries.take(4).map { query ->
                async {
                    repository.search(query).getOrNull()?.mapNotNull { item ->
                        if (item is SearchResult.VideoItem) item.video else null
                    } ?: emptyList()
                }
            }

            // Also fetch regional trending to inject exploration & serendipity (30% pool)
            val deferredTrending = async {
                repository.getTrendingVideos().getOrDefault(emptyList())
            }

            val queryResults = deferredQueries.awaitAll().flatten()
            val trendingResults = deferredTrending.await()

            val trendingIds = trendingResults.map { it.id }.toSet()
            val allCandidates = (queryResults + trendingResults)
                .filterNot { signals.hiddenVideoIds.contains(it.id) }
                .distinctBy { it.id }

            // 4. Score and rank candidates with diversity constraints & watched suppression
            val ranked = scorer.rankWithDiversity(
                candidates = allCandidates,
                signals = signals,
                limit = limit,
                isTrendingCandidate = { trendingIds.contains(it.id) }
            )

            if (ranked.isNotEmpty()) {
                Log.d("RecEngine", "Successfully ranked ${ranked.size} personalized recommendations")
                Result.success(ranked)
            } else {
                Log.w("RecEngine", "Ranked list empty, falling back to regional trending")
                Result.success(trendingResults.take(limit))
            }
        } catch (e: Exception) {
            Log.e("RecEngine", "Error computing local recommendations", e)
            repository.getTrendingVideos().map { it.take(limit) }
        }
    }

    private suspend fun collectUserSignals(): UserSignals {
        val watchHistory = try {
            watchHistoryDao?.getRecentList(50) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val likedVideos = try {
            likedVideoDao?.getAllList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val subscriptions = try {
            subscriptionDao?.getAllList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val hiddenVideoIds = try {
            hiddenVideoDao?.getAllHiddenIdsList()?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }

        val watchProgressMap = watchHistory.associate { entity ->
            val dur = entity.durationSeconds * 1000L
            val prog = if (dur > 0) (entity.watchedDurationMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            entity.videoId to prog
        }

        return UserSignals(
            watchHistory = watchHistory,
            likedVideos = likedVideos,
            subscriptions = subscriptions,
            hiddenVideoIds = hiddenVideoIds,
            watchProgressMap = watchProgressMap
        )
    }
}
