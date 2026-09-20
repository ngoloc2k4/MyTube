package vn.lobie.mytube.data.remote.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class InnerTubeClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://www.youtube.com/youtubei/v1"

    @Volatile
    var region: String = "VN"

    @Volatile
    var language: String = "vi"

    suspend fun browse(browseId: String = "FEwhat_to_watch"): Result<JsonObject> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", createWebContext())
            put("browseId", browseId)
        }
        post("$baseUrl/browse", payload)
    }

    suspend fun search(query: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", createWebContext())
            put("query", query)
        }
        post("$baseUrl/search", payload)
    }

    suspend fun player(videoId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", createAndroidTestSuiteContext())
            put("videoId", videoId)
            put("playbackContext", buildJsonObject {
                put("contentPlaybackContext", buildJsonObject {
                    put("html5Preference", "HTML5_PREF_WANTS")
                })
            })
        }
        post("$baseUrl/player", payload)
    }

    private fun post(url: String, body: JsonObject): Result<JsonObject> {
        return try {
            val requestBody = body.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("X-YouTube-Client-Name", "1")
                .addHeader("X-YouTube-Client-Version", "2.20240901.00.00")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(IOException("InnerTube HTTP error: ${resp.code}"))
                }

                val responseBody = resp.body?.string().orEmpty()
                val jsonObject = json.parseToJsonElement(responseBody).jsonObject
                Result.success(jsonObject)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createWebContext(): JsonObject {
        return buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", "WEB")
                put("clientVersion", "2.20240901.00.00")
                put("hl", language)
                put("gl", region)
                put("utcOffsetMinutes", 420)
            })
        }
    }

    private fun createAndroidTestSuiteContext(): JsonObject {
        return buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", "ANDROID_TESTSUITE")
                put("clientVersion", "1.9")
                put("androidSdkVersion", 34)
                put("hl", language)
                put("gl", region)
                put("utcOffsetMinutes", 420)
            })
        }
    }
}
