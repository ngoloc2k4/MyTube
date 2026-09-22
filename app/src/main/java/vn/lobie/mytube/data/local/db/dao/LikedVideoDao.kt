package vn.lobie.mytube.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vn.lobie.mytube.data.local.db.entity.LikedVideoEntity

@Dao
interface LikedVideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LikedVideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LikedVideoEntity>)

    @Query("DELETE FROM liked_videos WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT * FROM liked_videos ORDER BY likedAt DESC")
    fun getAll(): Flow<List<LikedVideoEntity>>

    @Query("SELECT * FROM liked_videos ORDER BY likedAt DESC")
    suspend fun getAllList(): List<LikedVideoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_videos WHERE videoId = :videoId)")
    fun isLiked(videoId: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM liked_videos")
    fun count(): Flow<Int>
}
