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

        fun isValidPublicInstance(url: String): Boolean {
            return try {
                val uri = android.net.Uri.parse(url)
                if (!uri.scheme.equals("https", ignoreCase = true)) return false
                val host = uri.host?.lowercase() ?: return false
                if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan")) {
                    return false
                }
                // Reject loopback and RFC 1918 / RFC 3927 private IP addresses
                val isPrivateIp = host.matches(Regex("^(127\\.|10\\.|192\\.168\\.|169\\.254\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.).*"))
                if (isPrivateIp || host == "::1") return false
                // Must be a valid domain with at least one dot
                host.matches(Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$"))
            } catch (_: Exception) {
                false
            }
        }
    }

    // Danh sách các instance Invidious public
    val instances: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList(DEFAULT_INSTANCES)

    fun addInstance(hostOrUrl: String): Boolean {
        val trimmed = hostOrUrl.trim().trimEnd('/')
        val formatted = if (trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://" + trimmed.substring(7)
        } else {
            "https://$trimmed"
        }
        if (!isValidPublicInstance(formatted)) {
            return false
        }
        if (!instances.contains(formatted)) {
            instances.add(formatted)
            return true
        }
        return false
    }

    fun setInstances(newInstances: List<String>) {
        val valid = newInstances.filter { isValidPublicInstance(it) }
        if (valid.isNotEmpty()) {
            instances.clear()
            instances.addAll(valid)
        }
    }

    fun sortByLatency(pings: Map<String, Long>) {
        val sorted = instances.sortedWith(compareBy<String> { url ->
            val latency = pings[url] ?: -1L
            if (latency > 0) 0 else 1
        }.thenBy { url ->
            val latency = pings[url] ?: Long.MAX_VALUE
            if (latency > 0) latency else Long.MAX_VALUE
        })
        instances.clear()
        instances.addAll(sorted)
    }

    fun removeInstance(url: String) {
        if (instances.size > 1) {
            instances.remove(url)
        }
    }

    suspend fun pingInstance(instanceUrl: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        if (!isValidPublicInstance(instanceUrl)) {
            return@withContext Pair(false, -1L)
        }
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

    // SEC-15: Throttle requests to avoid spamming Invidious instances and getting IP-blocked
    @Volatile
    private var lastRequestTimeMs = 0L
    private val requestThrottleLock = Any()

    private fun throttleRequest() {
        val minIntervalMs = 150L // Cap burst to ~6-7 req/sec
        val now = System.currentTimeMillis()
        val waitTime: Long
        synchronized(requestThrottleLock) {
            val elapsed = now - lastRequestTimeMs
            if (elapsed < minIntervalMs) {
                waitTime = minIntervalMs - elapsed
                lastRequestTimeMs = now + waitTime
            } else {
                waitTime = 0L
                lastRequestTimeMs = now
            }
        }
        if (waitTime > 0L) {
            try {
                Thread.sleep(waitTime)
            } catch (_: InterruptedException) {}
        }
    }

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
            "$baseUrl/api/v1/search?q=$encoded&type=video&region=$region"
        }
    }

    suspend fun getVideo(videoId: String): Result<InvidiousVideoDto> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (attempt in instances.indices) {
            val baseUrl = getBaseUrl()
            val url = "$baseUrl/api/v1/videos/$videoId"
            try {
                throttleRequest()
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

    suspend fun getComments(videoId: String): Result<List<vn.lobie.mytube.data.remote.dto.InvidiousCommentDto>> = withContext(Dispatchers.IO) {
        val res: Result<vn.lobie.mytube.data.remote.dto.InvidiousCommentsResponseDto> = executeWithFallback { baseUrl ->
            "$baseUrl/api/v1/comments/$videoId"
        }
        res.map { it.comments }
    }

    private inline fun <reified T> executeWithFallback(
        urlBuilder: (String) -> String
    ): Result<T> {
        var lastError: Exception? = null
        for (attempt in instances.indices) {
            val baseUrl = getBaseUrl()
            val url = urlBuilder(baseUrl)
            try {
                throttleRequest()
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
