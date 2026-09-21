package vn.lobie.mytube.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vn.lobie.mytube.data.local.db.entity.WatchHistoryEntity

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentList(limit: Int): List<WatchHistoryEntity>

    @Query("SELECT category FROM watch_history WHERE category != '' GROUP BY category ORDER BY COUNT(*) DESC LIMIT :limit")
    suspend fun getTopCategories(limit: Int): List<String>

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteById(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM watch_history WHERE videoId = :videoId)")
    suspend fun exists(videoId: String): Boolean

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId LIMIT 1")
    suspend fun getEntry(videoId: String): WatchHistoryEntity?

    @Query("UPDATE watch_history SET watchedDurationMs = :watchedDurationMs, timestamp = :timestamp WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: String, watchedDurationMs: Long, timestamp: Long = System.currentTimeMillis())
}
