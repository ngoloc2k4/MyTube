package vn.lobie.mytube.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val filePath: String = "",
    val format: String = "",
    val quality: String = "",
    val fileSizeBytes: Long = 0,
    val status: Int = 0, // 0=pending, 1=downloading, 2=completed, 3=failed, 4=paused
    val progressPercent: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0
)
