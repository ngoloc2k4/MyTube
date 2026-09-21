package vn.lobie.mytube.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.db.entity.LikedVideoEntity
import vn.lobie.mytube.data.local.db.entity.WatchHistoryEntity
import vn.lobie.mytube.domain.model.Channel
import vn.lobie.mytube.data.local.db.entity.PlaylistEntity
import vn.lobie.mytube.domain.model.Video

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MyTubeDatabase.getInstance(application)

    val history: StateFlow<List<WatchHistoryEntity>> = database.watchHistoryDao()
        .getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedVideos: StateFlow<List<LikedVideoEntity>> = database.likedVideoDao()
        .getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = database.playlistDao()
        .getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<vn.lobie.mytube.data.local.db.entity.DownloadEntity>> = database.downloadDao()
        .getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.watchHistoryDao().deleteAll()
        }
    }

    fun removeHistoryItem(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.watchHistoryDao().deleteById(videoId)
        }
    }

    fun deleteDownload(videoId: String) {
        vn.lobie.mytube.data.download.DownloadManager.getInstance(getApplication()).deleteDownload(videoId)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            database.playlistDao().insert(PlaylistEntity(name = name.trim()))
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.playlistDao().deleteById(id)
        }
    }
}

fun vn.lobie.mytube.data.local.db.entity.DownloadEntity.toVideo(): Video = Video(
    id = videoId,
    title = title,
    channel = Channel(id = "", name = channelName, avatarUrl = ""),
    durationSeconds = 0L,
    viewCount = 0L,
    publishedTimeText = "Đã tải xuống",
    thumbnailUrl = thumbnailUrl
)

fun WatchHistoryEntity.toVideo(): Video = Video(
    id = videoId,
    title = title,
    channel = Channel(id = channelId, name = channelName, avatarUrl = ""),
    durationSeconds = durationSeconds,
    viewCount = 0L,
    publishedTimeText = "",
    thumbnailUrl = thumbnailUrl
)

fun LikedVideoEntity.toVideo(): Video = Video(
    id = videoId,
    title = title,
    channel = Channel(id = channelId, name = channelName, avatarUrl = ""),
    durationSeconds = durationSeconds,
    viewCount = 0L,
    publishedTimeText = "",
    thumbnailUrl = thumbnailUrl
)
