package vn.lobie.mytube.data.local.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vn.lobie.mytube.domain.model.Channel
import vn.lobie.mytube.domain.model.Video
import java.io.File

object HomeFeedCache {
    private const val CACHE_FILE_NAME = "home_feed_cache.json"

    suspend fun saveHomeFeed(context: Context, videos: List<Video>) = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext
        try {
            val file = File(context.cacheDir, CACHE_FILE_NAME)
            val jsonArray = JSONArray()
            videos.take(30).forEach { video ->
                val obj = JSONObject().apply {
                    put("id", video.id)
                    put("title", video.title)
                    put("durationSeconds", video.durationSeconds)
                    put("viewCount", video.viewCount)
                    put("publishedTimeText", video.publishedTimeText)
                    put("thumbnailUrl", video.thumbnailUrl)
                    put("description", video.description)
                    val chObj = JSONObject().apply {
                        put("id", video.channel.id)
                        put("name", video.channel.name)
                        put("avatarUrl", video.channel.avatarUrl)
                        put("subscriberCountText", video.channel.subscriberCountText)
                    }
                    put("channel", chObj)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("HomeFeedCache", "Failed to save home feed cache", e)
        }
    }

    suspend fun loadHomeFeed(context: Context): List<Video> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, CACHE_FILE_NAME)
            if (!file.exists()) return@withContext emptyList()
            val text = file.readText()
            if (text.isBlank()) return@withContext emptyList()

            val jsonArray = JSONArray(text)
            val result = mutableListOf<Video>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val chObj = obj.optJSONObject("channel")
                val channel = Channel(
                    id = chObj?.optString("id").orEmpty(),
                    name = chObj?.optString("name").orEmpty(),
                    avatarUrl = chObj?.optString("avatarUrl").orEmpty(),
                    subscriberCountText = chObj?.optString("subscriberCountText").orEmpty()
                )
                result.add(
                    Video(
                        id = obj.getString("id"),
                        title = obj.optString("title"),
                        channel = channel,
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        viewCount = obj.optLong("viewCount", 0L),
                        publishedTimeText = obj.optString("publishedTimeText"),
                        thumbnailUrl = obj.optString("thumbnailUrl"),
                        description = obj.optString("description")
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e("HomeFeedCache", "Failed to load home feed cache", e)
            emptyList()
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, CACHE_FILE_NAME)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }
}
