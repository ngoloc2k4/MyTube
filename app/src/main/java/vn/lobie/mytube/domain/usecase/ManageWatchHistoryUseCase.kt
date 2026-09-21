package vn.lobie.mytube.domain.usecase

import kotlinx.coroutines.flow.Flow
import vn.lobie.mytube.data.local.db.dao.WatchHistoryDao
import vn.lobie.mytube.data.local.db.entity.WatchHistoryEntity
import vn.lobie.mytube.domain.model.Video

/**
 * UseCase to encapsulate Watch History recording, querying, and management.
 */
class ManageWatchHistoryUseCase(
    private val watchHistoryDao: WatchHistoryDao
) {
    fun getAllHistory(): Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAll()

    suspend fun getRecent(limit: Int): List<WatchHistoryEntity> = watchHistoryDao.getRecentList(limit)

    suspend fun recordProgress(video: Video, watchedDurationMs: Long, totalDurationSeconds: Long) {
        val entity = WatchHistoryEntity(
            videoId = video.id,
            title = video.title,
            channelId = video.channel.id,
            channelName = video.channel.name,
            thumbnailUrl = video.thumbnailUrl,
            durationSeconds = if (totalDurationSeconds > 0) totalDurationSeconds else video.durationSeconds,
            watchedDurationMs = watchedDurationMs,
            timestamp = System.currentTimeMillis()
        )
        watchHistoryDao.insert(entity)
    }

    suspend fun deleteById(videoId: String) = watchHistoryDao.deleteById(videoId)

    suspend fun clearAll() = watchHistoryDao.deleteAll()
}
