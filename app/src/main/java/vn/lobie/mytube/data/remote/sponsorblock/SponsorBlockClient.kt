package vn.lobie.mytube.data.remote.sponsorblock

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class SponsorBlockClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val categoriesParam = "%5B%22sponsor%22%2C%22intro%22%2C%22outro%22%2C%22selfpromo%22%2C%22interaction%22%5D"

    suspend fun getSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categoriesParam"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyTube-Android/2.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    // No segments submitted for this video
                    return@withContext emptyList()
                }
                if (!response.isSuccessful) {
                    Log.w(TAG, "SponsorBlock query failed: HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                val dtoList = json.decodeFromString<List<SponsorSegmentDto>>(body)

                dtoList.mapNotNull { dto ->
                    if (dto.segment.size >= 2) {
                        val startSec = dto.segment[0]
                        val endSec = dto.segment[1]
                        if (endSec > startSec) {
                            SponsorSegment(
                                category = dto.category,
                                startMs = (startSec * 1000L).toLong(),
                                endMs = (endSec * 1000L).toLong(),
                                actionType = dto.actionType,
                                uuid = dto.UUID
                            )
                        } else null
                    } else null
                }.sortedBy { it.startMs }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SponsorBlock network exception for $videoId: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "SponsorBlockClient"
    }
}
