package vn.lobie.mytube.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String,
    val category: String = "",
    val durationSeconds: Long = 0,
    val watchedDurationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
