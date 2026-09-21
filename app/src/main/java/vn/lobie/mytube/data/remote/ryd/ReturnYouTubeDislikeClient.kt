package vn.lobie.mytube.data.remote.ryd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RydDislikeInfo(
    val likes: Long = 0,
    val dislikes: Long = 0,
    val rating: Double = 0.0,
    val viewCount: Long = 0
)

class ReturnYouTubeDislikeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    suspend fun getDislikeInfo(videoId: String): RydDislikeInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://returnyoutubedislikeapi.com/votes?videoId=$videoId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyTube/2.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                RydDislikeInfo(
                    likes = json.optLong("likes", 0L),
                    dislikes = json.optLong("dislikes", 0L),
                    rating = json.optDouble("rating", 0.0),
                    viewCount = json.optLong("viewCount", 0L)
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
