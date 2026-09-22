package vn.lobie.mytube.domain.recommendation

import vn.lobie.mytube.data.local.db.entity.LikedVideoEntity
import vn.lobie.mytube.data.local.db.entity.SubscriptionEntity
import vn.lobie.mytube.data.local.db.entity.WatchHistoryEntity
import vn.lobie.mytube.domain.model.Video

data class UserSignals(
    val watchHistory: List<WatchHistoryEntity> = emptyList(),
    val likedVideos: List<LikedVideoEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val hiddenVideoIds: Set<String> = emptySet(),
    val watchProgressMap: Map<String, Float> = emptyMap()
)

data class ScoredCandidate(
    val video: Video,
    val totalScore: Double,
    val topicScore: Double = 0.0,
    val channelScore: Double = 0.0,
    val recencyScore: Double = 0.0,
    val explorationScore: Double = 0.0,
    val noveltyScore: Double = 0.0,
    val watchedPenalty: Double = 0.0,
    val repetitionPenalty: Double = 0.0
)

data class EvaluationMetrics(
    val precisionAtK: Double,
    val recallAtK: Double,
    val diversityAtK: Double,
    val noveltyAtK: Double,
    val alreadyWatchedRatio: Double,
    val topChannelsCovered: Int,
    val topCategoriesCovered: Int
)
