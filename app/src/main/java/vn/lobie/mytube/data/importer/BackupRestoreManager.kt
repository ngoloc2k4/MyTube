package vn.lobie.mytube.data.importer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import androidx.room.withTransaction
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.db.entity.LikedVideoEntity
import vn.lobie.mytube.data.local.db.entity.PlaylistEntity
import vn.lobie.mytube.data.local.db.entity.PlaylistVideoEntity
import vn.lobie.mytube.data.local.db.entity.SubscriptionEntity
import vn.lobie.mytube.data.local.db.entity.WatchHistoryEntity
import java.io.BufferedReader
import java.io.InputStreamReader

@Serializable
data class MyTubeBackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val subscriptions: List<BackupSubscription> = emptyList(),
    val watchHistory: List<BackupWatchHistory> = emptyList(),
    val likedVideos: List<BackupLikedVideo> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val playlistVideos: List<BackupPlaylistVideo> = emptyList()
)

@Serializable
data class BackupSubscription(
    val channelId: String,
    val channelName: String,
    val avatarUrl: String = "",
    val subscribedAt: Long = System.currentTimeMillis()
)

@Serializable
data class BackupWatchHistory(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String,
    val category: String = "",
    val durationSeconds: Long = 0,
    val watchedDurationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class BackupLikedVideo(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long = 0,
    val likedAt: Long = System.currentTimeMillis()
)

@Serializable
data class BackupPlaylist(
    val id: Long = 0,
    val name: String,
    val isSystem: Boolean = false,
    val isMusicPlaylist: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class BackupPlaylistVideo(
    val playlistId: Long,
    val videoId: String,
    val title: String,
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

data class RestoreStats(
    val subscriptionsCount: Int,
    val historyCount: Int,
    val likedCount: Int,
    val playlistsCount: Int,
    val playlistVideosCount: Int
)

class BackupRestoreManager(
    private val context: Context,
    private val database: MyTubeDatabase
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun exportBackupJson(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val subs = database.subscriptionDao().getAllList().map {
                BackupSubscription(it.channelId, it.channelName, it.avatarUrl, it.subscribedAt)
            }
            val history = database.watchHistoryDao().getAllList().map {
                BackupWatchHistory(
                    it.videoId, it.title, it.channelId, it.channelName,
                    it.thumbnailUrl, it.category, it.durationSeconds,
                    it.watchedDurationMs, it.timestamp
                )
            }
            val liked = database.likedVideoDao().getAllList().map {
                BackupLikedVideo(
                    it.videoId, it.title, it.channelId, it.channelName,
                    it.thumbnailUrl, it.durationSeconds, it.likedAt
                )
            }
            val playlists = database.playlistDao().getAllList().map {
                BackupPlaylist(
                    it.id, it.name, it.isSystem, it.isMusicPlaylist,
                    it.createdAt, it.updatedAt
                )
            }
            val playlistVideos = database.playlistVideoDao().getAllList().map {
                BackupPlaylistVideo(
                    it.playlistId, it.videoId, it.title, it.channelName,
                    it.thumbnailUrl, it.durationSeconds, it.sortOrder, it.addedAt
                )
            }

            val backup = MyTubeBackupData(
                subscriptions = subs,
                watchHistory = history,
                likedVideos = liked,
                playlists = playlists,
                playlistVideos = playlistVideos
            )
            val jsonString = json.encodeToString(backup)

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(jsonString.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return@withContext Result.failure(Exception("Cannot open output stream"))

            val totalItems = subs.size + history.size + liked.size + playlists.size + playlistVideos.size
            Result.success(totalItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024L // 20 MB memory limit
    }

    private fun readBoundedText(uri: Uri): String {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val len = afd.length
                if (len > MAX_IMPORT_BYTES) {
                    throw IllegalArgumentException("Tệp vượt quá dung lượng cho phép (tối đa 20MB).")
                }
            }
        } catch (_: Exception) {}

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream")
        return inputStream.use { stream ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var totalRead = 0L
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > MAX_IMPORT_BYTES) {
                    throw IllegalArgumentException("Tệp vượt quá dung lượng cho phép (tối đa 20MB).")
                }
                output.write(buffer, 0, bytesRead)
            }
            output.toString("UTF-8")
        }
    }

    suspend fun restoreBackupJson(uri: Uri): Result<RestoreStats> = withContext(Dispatchers.IO) {
        try {
            val content = readBoundedText(uri)
            val backup = json.decodeFromString<MyTubeBackupData>(content)

            database.withTransaction {
                if (backup.subscriptions.isNotEmpty()) {
                    database.subscriptionDao().insertAll(backup.subscriptions.map {
                        SubscriptionEntity(it.channelId, it.channelName, it.avatarUrl, it.subscribedAt)
                    })
                }
                if (backup.watchHistory.isNotEmpty()) {
                    database.watchHistoryDao().insertAll(backup.watchHistory.map {
                        WatchHistoryEntity(
                            it.videoId, it.title, it.channelId, it.channelName,
                            it.thumbnailUrl, it.category, it.durationSeconds,
                            it.watchedDurationMs, it.timestamp
                        )
                    })
                }
                if (backup.likedVideos.isNotEmpty()) {
                    database.likedVideoDao().insertAll(backup.likedVideos.map {
                        LikedVideoEntity(
                            it.videoId, it.title, it.channelId, it.channelName,
                            it.thumbnailUrl, it.durationSeconds, it.likedAt
                        )
                    })
                }
                if (backup.playlists.isNotEmpty()) {
                    database.playlistDao().insertAll(backup.playlists.map {
                        PlaylistEntity(
                            it.id, it.name, it.isSystem, it.isMusicPlaylist,
                            it.createdAt, it.updatedAt
                        )
                    })
                }
                if (backup.playlistVideos.isNotEmpty()) {
                    val validPlaylistIds = database.playlistDao().getAllList().map { it.id }.toSet()
                    val validPlaylistVideos = backup.playlistVideos.filter { it.playlistId in validPlaylistIds }
                    if (validPlaylistVideos.isNotEmpty()) {
                        database.playlistVideoDao().insertAll(validPlaylistVideos.map {
                            PlaylistVideoEntity(
                                playlistId = it.playlistId,
                                videoId = it.videoId,
                                title = it.title,
                                channelName = it.channelName,
                                thumbnailUrl = it.thumbnailUrl,
                                durationSeconds = it.durationSeconds,
                                sortOrder = it.sortOrder,
                                addedAt = it.addedAt
                            )
                        })
                    }
                }
            }

            Result.success(
                RestoreStats(
                    subscriptionsCount = backup.subscriptions.size,
                    historyCount = backup.watchHistory.size,
                    likedCount = backup.likedVideos.size,
                    playlistsCount = backup.playlists.size,
                    playlistVideosCount = backup.playlistVideos.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importSubscriptionsFromFile(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = readBoundedText(uri)
            val imported = parseSubscriptions(content)
            if (imported.isEmpty()) {
                return@withContext Result.failure(Exception("Không tìm thấy kênh đăng ký hợp lệ trong tệp."))
            }

            database.withTransaction {
                database.subscriptionDao().insertAll(imported)
            }
            Result.success(imported.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseSubscriptions(content: String): List<SubscriptionEntity> {
        val trimmed = content.trim()
        val results = mutableListOf<SubscriptionEntity>()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val root = json.parseToJsonElement(trimmed)
                if (root is JsonObject && root.containsKey("subscriptions")) {
                    val arr = root["subscriptions"] as? JsonArray
                    arr?.forEach { element ->
                        val obj = element as? JsonObject ?: return@forEach
                        val url = obj["url"]?.toString()?.trim('"') ?: ""
                        val name = obj["name"]?.toString()?.trim('"') ?: ""
                        val avatar = obj["avatar_url"]?.toString()?.trim('"') ?: ""
                        val channelId = extractChannelIdFromUrl(url)
                        if (channelId.isNotBlank()) {
                            results.add(
                                SubscriptionEntity(
                                    channelId = channelId,
                                    channelName = name.ifBlank { "Channel" },
                                    avatarUrl = avatar
                                )
                            )
                        }
                    }
                } else if (root is JsonArray) {
                    root.forEach { element ->
                        val obj = element as? JsonObject ?: return@forEach
                        val channelId = obj["channelId"]?.toString()?.trim('"')
                            ?: obj["url"]?.toString()?.trim('"')?.let { extractChannelIdFromUrl(it) }
                            ?: ""
                        val name = obj["channelName"]?.toString()?.trim('"')
                            ?: obj["name"]?.toString()?.trim('"')
                            ?: "Channel"
                        val avatar = obj["avatarUrl"]?.toString()?.trim('"')
                            ?: obj["avatar_url"]?.toString()?.trim('"')
                            ?: ""
                        if (channelId.isNotBlank()) {
                            results.add(
                                SubscriptionEntity(
                                    channelId = channelId,
                                    channelName = name,
                                    avatarUrl = avatar
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (results.isEmpty()) {
            val lines = content.lines()
            for (line in lines) {
                val lineTrimmed = line.trim()
                if (lineTrimmed.isBlank() || lineTrimmed.startsWith("Channel Id", ignoreCase = true)) {
                    continue
                }
                val tokens = parseCsvLine(lineTrimmed)
                if (tokens.isNotEmpty()) {
                    val channelId = tokens.getOrNull(0)?.trim() ?: ""
                    val channelTitle = tokens.getOrNull(2)?.trim() ?: tokens.getOrNull(1)?.trim() ?: "Channel"
                    if (channelId.startsWith("UC") || channelId.length in 18..34) {
                        results.add(
                            SubscriptionEntity(
                                channelId = channelId,
                                channelName = channelTitle.ifBlank { "Channel" },
                                avatarUrl = ""
                            )
                        )
                    }
                }
            }
        }

        return results.distinctBy { it.channelId }
    }

    private fun extractChannelIdFromUrl(url: String): String {
        val clean = url.trim().trimEnd('/')
        return when {
            clean.contains("/channel/") -> clean.substringAfterLast("/channel/").substringBefore('/')
            clean.contains("/c/") -> clean.substringAfterLast("/c/").substringBefore('/')
            clean.startsWith("UC") -> clean
            else -> clean.substringAfterLast('/')
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        sb.append(ch)
                    } else {
                        result.add(sb.toString().trim().removeSurrounding("\""))
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim().removeSurrounding("\""))
        return result
    }
}
