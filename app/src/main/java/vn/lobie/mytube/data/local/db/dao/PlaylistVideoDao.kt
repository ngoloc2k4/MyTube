package vn.lobie.mytube.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import vn.lobie.mytube.data.local.db.entity.PlaylistVideoEntity

@Dao
interface PlaylistVideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: PlaylistVideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<PlaylistVideoEntity>)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun delete(playlistId: Long, videoId: String)

    @Query("DELETE FROM playlist_videos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY sortOrder ASC, addedAt ASC")
    fun getByPlaylist(playlistId: Long): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT * FROM playlist_videos ORDER BY playlistId, sortOrder ASC, addedAt ASC")
    suspend fun getAllList(): List<PlaylistVideoEntity>

    @Query("UPDATE playlist_videos SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId)")
    suspend fun isInPlaylist(playlistId: Long, videoId: String): Boolean

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    fun getVideoCount(playlistId: Long): Flow<Int>
}
