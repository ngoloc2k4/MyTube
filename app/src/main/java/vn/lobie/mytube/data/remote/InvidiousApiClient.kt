package vn.lobie.mytube.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.lobie.mytube.data.remote.dto.InvidiousVideoDto
import java.io.IOException
import java.util.concurrent.TimeUnit

class InvidiousApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        val DEFAULT_INSTANCES = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://invidious.projectsegfau.lt",
            "https://inv.tux.pizza"
        )
    }

    // Danh sách các instance Invidious public
    val instances: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList(DEFAULT_INSTANCES)

    fun addInstance(hostOrUrl: String): Boolean {
        val formatted = if (hostOrUrl.startsWith("http://") || hostOrUrl.startsWith("https://")) {
            hostOrUrl.trimEnd('/')
        } else {
            "https://${hostOrUrl.trim().trimEnd('/')}"
        }
        if (!instances.contains(formatted)) {
            instances.add(formatted)
            return true
        }
        return false
    }

    fun removeInstance(url: String) {
        if (instances.size > 1) {
            instances.remove(url)
        }
    }

    suspend fun pingInstance(instanceUrl: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("$instanceUrl/api/v1/stats")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                Pair(response.isSuccessful, elapsed)
            }
        } catch (e: Exception) {
            Pair(false, -1L)
        }
    }

    private var currentInstanceIndex = 0

    @Volatile
    var region: String = "VN"

    private fun getBaseUrl(): String {
        return instances[currentInstanceIndex % instances.size]
    }

    private fun rotateInstance() {
        currentInstanceIndex = (currentInstanceIndex + 1) % instances.size
    }

    suspend fun getTrending(): Result<List<InvidiousVideoDto>> = withContext(Dispatchers.IO) {
        executeWithFallback { baseUrl ->
            "$baseUrl/api/v1/trending?type=music,default&region=$region"
        }
    }

    suspend fun search(query: String): Result<List<InvidiousVideoDto>> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        executeWithFallback { baseUrl ->
            "$baseUrl/api/v1/search?q=$encoded&type=video"
        }
    }

    suspend fun getVideo(videoId: String): Result<InvidiousVideoDto> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (attempt in instances.indices) {
            val baseUrl = getBaseUrl()
            val url = "$baseUrl/api/v1/videos/$videoId"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: throw IOException("Empty body")
                        val dto = json.decodeFromString<InvidiousVideoDto>(body)
                        return@withContext Result.success(dto)
                    } else {
                        rotateInstance()
                        val code = response.code
                        vn.lobie.mytube.core.common.AppLogger.w("Invidious", "Instance $baseUrl HTTP $code for video $videoId, rotating", videoId)
                        lastError = IOException("HTTP $code from $baseUrl")
                    }
                }
            } catch (e: Exception) {
                rotateInstance()
                vn.lobie.mytube.core.common.AppLogger.w("Invidious", "Instance $baseUrl failed: ${e.message}, rotating", videoId)
                lastError = e
            }
        }
        vn.lobie.mytube.core.common.AppLogger.e("Invidious", "All Invidious instances exhausted for video $videoId", videoId)
        Result.failure(lastError ?: IOException("All Invidious instances failed"))
    }

    private inline fun <reified T> executeWithFallback(
        urlBuilder: (String) -> String
    ): Result<T> {
        var lastError: Exception? = null
        for (attempt in instances.indices) {
            val baseUrl = getBaseUrl()
            val url = urlBuilder(baseUrl)
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: throw IOException("Empty body")
                        val result = json.decodeFromString<T>(body)
                        return Result.success(result)
                    } else {
                        rotateInstance()
                        lastError = IOException("HTTP ${response.code} from $baseUrl")
                    }
                }
            } catch (e: Exception) {
                rotateInstance()
                lastError = e
            }
        }
        return Result.failure(lastError ?: IOException("All Invidious instances failed"))
    }
}
