package vn.lobie.mytube.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_videos")
data class HiddenVideoEntity(
    @PrimaryKey val videoId: String,
    val hiddenAt: Long = System.currentTimeMillis()
)
