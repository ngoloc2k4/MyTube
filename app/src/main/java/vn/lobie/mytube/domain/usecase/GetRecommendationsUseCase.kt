package vn.lobie.mytube.domain.usecase

import vn.lobie.mytube.data.local.db.dao.HiddenVideoDao
import vn.lobie.mytube.data.local.db.dao.LikedVideoDao
import vn.lobie.mytube.data.local.db.dao.SubscriptionDao
import vn.lobie.mytube.data.local.db.dao.WatchHistoryDao
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.recommendation.LocalRecommendationEngine
import vn.lobie.mytube.domain.repository.YouTubeRepository

class GetRecommendationsUseCase(
    private val watchHistoryDao: WatchHistoryDao?,
    private val repository: YouTubeRepository,
    private val likedVideoDao: LikedVideoDao? = null,
    private val subscriptionDao: SubscriptionDao? = null,
    private val hiddenVideoDao: HiddenVideoDao? = null
) {
    private val engine = LocalRecommendationEngine(
        watchHistoryDao = watchHistoryDao,
        likedVideoDao = likedVideoDao,
        subscriptionDao = subscriptionDao,
        hiddenVideoDao = hiddenVideoDao,
        repository = repository
    )

    /**
     * Produces a personalized recommendation list based on on-device user signals:
     * - Watch history (recency decay, completion rate)
     * - Liked videos (strong deliberate preference)
     * - Subscriptions (channel affinity)
     * - Regional trending from selected country (exploration & diversity)
     * - Watched and fatigue suppression
     */
    suspend operator fun invoke(limit: Int = 20): Result<List<Video>> {
        return engine.getRecommendations(limit)
    }
}
