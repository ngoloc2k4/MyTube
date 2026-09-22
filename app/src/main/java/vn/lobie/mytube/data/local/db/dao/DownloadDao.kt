package vn.lobie.mytube.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.lobie.mytube.data.local.db.entity.DownloadEntity

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT * FROM downloads ORDER BY startedAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY startedAt DESC")
    fun getByStatus(status: Int): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun getById(videoId: String): DownloadEntity?

    @Query("UPDATE downloads SET progressPercent = :progress, status = :status WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: String, progress: Int, status: Int)

    @Query("UPDATE downloads SET status = :status, completedAt = :completedAt WHERE videoId = :videoId")
    suspend fun updateCompleted(videoId: String, completedAt: Long, status: Int)

    @Query("UPDATE downloads SET status = :status, completedAt = :completedAt, fileSizeBytes = :fileSizeBytes, filePath = :filePath WHERE videoId = :videoId")
    suspend fun updateCompletedDetails(videoId: String, completedAt: Long, status: Int, fileSizeBytes: Long, filePath: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
