package vn.lobie.mytube.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vn.lobie.mytube.data.local.db.entity.SubscriptionEntity

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelId = :channelId")
    suspend fun delete(channelId: String)

    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    fun getAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    suspend fun getAllList(): List<SubscriptionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelId = :channelId)")
    fun isSubscribed(channelId: String): Flow<Boolean>

    @Query("SELECT channelId FROM subscriptions")
    suspend fun getAllChannelIds(): List<String>
}
