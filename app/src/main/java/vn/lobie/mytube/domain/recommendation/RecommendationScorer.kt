package vn.lobie.mytube.domain.recommendation

import vn.lobie.mytube.data.local.db.entity.WatchHistoryEntity
import vn.lobie.mytube.domain.model.Video
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

class RecommendationScorer {

    companion object {
        private const val HALF_LIFE_MILLIS = 7.0 * 24.0 * 60.0 * 60.0 * 1000.0 // 7 days decay
        private val DECAY_LAMBDA = ln(2.0) / HALF_LIFE_MILLIS

        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "video", "official", "full", "ep", "tập", "cua", "của",
            "va", "và", "la", "là", "trong", "tren", "trên", "mot", "một", "nhung", "những",
            "mv", "hd", "4k", "mới", "moi", "hot", "live", "audio", "nhạc", "nhac", "official"
        )
    }

    /**
     * Extracts top interest keywords with their accumulated affinity scores.
     */
    fun extractInterestKeywords(signals: UserSignals, maxKeywords: Int = 15): Map<String, Double> {
        val now = System.currentTimeMillis()
        val keywordScores = mutableMapOf<String, Double>()

        // 1. History signals with recency decay and watch completion
        signals.watchHistory.forEach { history ->
            val delta = (now - history.timestamp).coerceAtLeast(0L).toDouble()
            val recencyWeight = exp(-DECAY_LAMBDA * delta).coerceIn(0.1, 1.0)
            val completionWeight = calculateCompletionRate(history).coerceIn(0.2, 1.0)
            val weight = recencyWeight * completionWeight

            val tokens = tokenize(history.title)
            tokens.forEach { token ->
                keywordScores[token] = (keywordScores[token] ?: 0.0) + weight
            }

            if (history.category.isNotBlank()) {
                val cat = history.category.trim().lowercase()
                keywordScores[cat] = (keywordScores[cat] ?: 0.0) + (weight * 1.5)
            }
        }

        // 2. Liked videos (strong deliberate preference signal)
        signals.likedVideos.forEach { liked ->
            val tokens = tokenize(liked.title)
            tokens.forEach { token ->
                keywordScores[token] = (keywordScores[token] ?: 0.0) + 2.0
            }
        }

        return keywordScores.entries
            .sortedByDescending { it.value }
            .take(maxKeywords)
            .associate { it.key to it.value }
    }

    /**
     * Computes channel affinity score mapping: channelName/channelId -> score.
     */
    fun extractChannelAffinities(signals: UserSignals): Map<String, Double> {
        val now = System.currentTimeMillis()
        val channelScores = mutableMapOf<String, Double>()

        // Subscriptions provide direct baseline affinity
        signals.subscriptions.forEach { sub ->
            if (sub.channelName.isNotBlank()) {
                val name = sub.channelName.trim().lowercase()
                channelScores[name] = (channelScores[name] ?: 0.0) + 2.0
            }
        }

        // Liked videos add strong channel affinity
        signals.likedVideos.forEach { liked ->
            if (liked.channelName.isNotBlank()) {
                val name = liked.channelName.trim().lowercase()
                channelScores[name] = (channelScores[name] ?: 0.0) + 1.8
            }
        }

        // Watch history with time decay
        signals.watchHistory.forEach { history ->
            if (history.channelName.isNotBlank()) {
                val name = history.channelName.trim().lowercase()
                val delta = (now - history.timestamp).coerceAtLeast(0L).toDouble()
                val recency = exp(-DECAY_LAMBDA * delta)
                val completion = calculateCompletionRate(history)
                val weight = (recency * completion * 1.5)
                channelScores[name] = (channelScores[name] ?: 0.0) + weight
            }
        }

        return channelScores
    }

    /**
     * Scores an individual candidate video given the user's signals.
     */
    fun scoreCandidate(
        video: Video,
        signals: UserSignals,
        keywordAffinities: Map<String, Double>,
        channelAffinities: Map<String, Double>,
        selectedChannelCounts: Map<String, Int> = emptyMap(),
        isTrendingSource: Boolean = false
    ): ScoredCandidate {
        if (signals.hiddenVideoIds.contains(video.id)) {
            return ScoredCandidate(
                video = video,
                totalScore = -9999.0,
                watchedPenalty = 9999.0
            )
        }

        val channelLower = video.channel.name.trim().lowercase()
        val videoTokens = tokenize(video.title)

        // 1. Topic Relevance Score
        var rawTopicScore = 0.0
        for (token in videoTokens) {
            val kwScore = keywordAffinities[token]
            if (kwScore != null) {
                rawTopicScore += kwScore
            }
        }
        val topicScore = min(3.5, rawTopicScore)

        // 2. Channel Affinity Score
        val channelScore = min(3.0, (channelAffinities[channelLower] ?: 0.0) * 0.8)

        // 3. Recency / Freshness Bonus (from view count / verified channel)
        var recencyScore = 0.0
        if (video.channel.isVerified) {
            recencyScore += 0.2
        }

        // 4. Exploration / Serendipity (Trending discovery source)
        val explorationScore = if (isTrendingSource) 0.5 else 0.0

        // 5. Novelty Score (bonus for unseen videos that match user's broad interests)
        val isSubscribed = signals.subscriptions.any { it.channelName.equals(video.channel.name, ignoreCase = true) }
        val noveltyScore = if (isSubscribed) 0.4 else 0.2

        // 6. Already Watched Penalty (Suppression)
        var watchedPenalty = 0.0
        val watchedHistory = signals.watchHistory.find { it.videoId == video.id }
        val progress = signals.watchProgressMap[video.id]
            ?: watchedHistory?.let { calculateCompletionRate(it).toFloat() }
            ?: 0f

        val isMusicVideo = isMusicContent(video)
        if (watchedHistory != null || progress > 0f) {
            if (progress >= 0.70f) {
                // If completely watched:
                // Non-music videos get heavy suppression (-10.0)
                // Music videos are replayable, so minor discount (-1.0)
                watchedPenalty = if (isMusicVideo) 1.2 else 10.0
            } else if (progress in 0.15f..0.70f) {
                // Partially watched
                watchedPenalty = 2.0
            } else if (progress < 0.15f && watchedHistory != null) {
                // Dropped off very quickly -> user didn't like it
                watchedPenalty = 3.5
            }
        }

        // 7. Repetition / Channel Saturation Penalty
        // Strict cap: max 2 videos per channel in top recommendations
        val currentChannelCount = selectedChannelCounts[channelLower] ?: 0
        val repetitionPenalty = when {
            currentChannelCount == 1 -> 0.8
            currentChannelCount >= 2 -> 15.0
            else -> 0.0
        }

        val totalScore = topicScore + channelScore + recencyScore + explorationScore + noveltyScore - watchedPenalty - repetitionPenalty

        return ScoredCandidate(
            video = video,
            totalScore = totalScore,
            topicScore = topicScore,
            channelScore = channelScore,
            recencyScore = recencyScore,
            explorationScore = explorationScore,
            noveltyScore = noveltyScore,
            watchedPenalty = watchedPenalty,
            repetitionPenalty = repetitionPenalty
        )
    }

    /**
     * Re-ranks candidates greedily to enforce diversity across channels and topics.
     */
    fun rankWithDiversity(
        candidates: List<Video>,
        signals: UserSignals,
        limit: Int = 20,
        isTrendingCandidate: (Video) -> Boolean = { false }
    ): List<Video> {
        val keywordAffinities = extractInterestKeywords(signals)
        val channelAffinities = extractChannelAffinities(signals)

        val unselected = candidates.distinctBy { it.id }.toMutableList()
        val selected = mutableListOf<Video>()
        val channelCounts = mutableMapOf<String, Int>()

        while (selected.size < limit && unselected.isNotEmpty()) {
            var bestCandidate: Video? = null
            var bestScore = Double.NEGATIVE_INFINITY

            for (candidate in unselected) {
                val scored = scoreCandidate(
                    video = candidate,
                    signals = signals,
                    keywordAffinities = keywordAffinities,
                    channelAffinities = channelAffinities,
                    selectedChannelCounts = channelCounts,
                    isTrendingSource = isTrendingCandidate(candidate)
                )

                if (scored.totalScore > bestScore) {
                    bestScore = scored.totalScore
                    bestCandidate = candidate
                }
            }

            if (bestCandidate != null && bestScore > -5.0) {
                selected.add(bestCandidate)
                unselected.remove(bestCandidate)
                val ch = bestCandidate.channel.name.trim().lowercase()
                channelCounts[ch] = (channelCounts[ch] ?: 0) + 1
            } else {
                // If remaining candidates all have heavy penalties, break or allow remaining up to limit
                if (selected.size < limit && unselected.isNotEmpty()) {
                    val fallback = unselected.removeAt(0)
                    selected.add(fallback)
                } else {
                    break
                }
            }
        }

        return selected
    }

    fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && !STOP_WORDS.contains(it) }
    }

    private fun calculateCompletionRate(history: WatchHistoryEntity): Double {
        val totalMs = history.durationSeconds * 1000.0
        return if (totalMs > 0.0) {
            (history.watchedDurationMs.toDouble() / totalMs).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
    }

    private fun isMusicContent(video: Video): Boolean {
        val title = video.title.lowercase()
        val keywords = listOf("official audio", "official mv", "official music video", "lyrics", "nhạc", "song", "ost", "album", "remix")
        return keywords.any { title.contains(it) } || (video.durationSeconds in 90..400 && title.contains("ft."))
    }
}
