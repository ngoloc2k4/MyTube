package vn.lobie.mytube.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vn.lobie.mytube.data.local.db.entity.HiddenVideoEntity

@Dao
interface HiddenVideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hidden: HiddenVideoEntity)

    @Query("DELETE FROM hidden_videos WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT videoId FROM hidden_videos")
    fun getAllIds(): Flow<List<String>>

    @Query("SELECT videoId FROM hidden_videos")
    suspend fun getAllHiddenIdsList(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM hidden_videos WHERE videoId = :videoId)")
    suspend fun isHidden(videoId: String): Boolean
}
