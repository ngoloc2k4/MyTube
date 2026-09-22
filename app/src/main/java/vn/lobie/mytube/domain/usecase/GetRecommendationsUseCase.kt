package vn.lobie.mytube.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import vn.lobie.mytube.data.local.db.dao.WatchHistoryDao
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

class GetRecommendationsUseCase(
    private val watchHistoryDao: WatchHistoryDao,
    private val repository: YouTubeRepository
) {
    /**
     * Tạo danh sách video đề xuất cá nhân hóa 100% cục bộ (On-Device Local Recommendation).
     * Dựa trên các kênh và thể loại người dùng xem nhiều nhất, sau đó truy vấn video liên quan và loại bỏ các video đã xem.
     */
    suspend operator fun invoke(limit: Int = 20): Result<List<Video>> = coroutineScope {
        try {
            val topChannels = watchHistoryDao.getTopChannels(3)
            val topCategories = watchHistoryDao.getTopCategories(2)
            val watchedVideoIds = watchHistoryDao.getRecentList(50).map { it.videoId }.toSet()

            // Nếu chưa có lịch sử xem, đề xuất danh sách thịnh hành (trending)
            if (topChannels.isEmpty() && topCategories.isEmpty()) {
                val trending = repository.getTrendingVideos()
                return@coroutineScope trending.map { list -> list.take(limit) }
            }

            // Truy vấn song song video từ các kênh và chủ đề yêu thích
            val queries = mutableListOf<String>()
            topChannels.forEach { queries.add("$it official") }
            topCategories.forEach { queries.add("$it trending") }

            val deferredResults = queries.take(4).map { query ->
                async { repository.search(query) }
            }

            val recommendedVideos = mutableListOf<Video>()
            for (deferred in deferredResults) {
                val searchResult = deferred.await()
                searchResult.onSuccess { items ->
                    items.forEach { item ->
                        if (item is SearchResult.VideoItem && item.video.id !in watchedVideoIds) {
                            recommendedVideos.add(item.video)
                        }
                    }
                }
            }

            val distinct = recommendedVideos.distinctBy { it.id }.shuffled().take(limit)
            if (distinct.isNotEmpty()) {
                Result.success(distinct)
            } else {
                repository.getTrendingVideos().map { it.take(limit) }
            }
        } catch (e: Exception) {
            // Fallback sang trending nếu xảy ra bất kỳ lỗi tính toán nào
            repository.getTrendingVideos()
        }
    }
}
