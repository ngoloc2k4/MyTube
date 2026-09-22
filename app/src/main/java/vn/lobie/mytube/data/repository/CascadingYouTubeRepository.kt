package vn.lobie.mytube.data.repository

import android.util.Log
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.StreamInfo
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

/**
 * Cascading multi-engine repository pipeline:
 * 1. Primary for streams: NewPipeExtractor (direct client extraction from YouTube HTML/JS)
 * 2. Secondary: Invidious API (fast, lightweight REST with multi-instance rotator)
 * 3. Tertiary: Native InnerTube Engine (direct YouTube v1 API)
 * 4. Fallback: Fake repository (deterministic local sample data & verified test stream)
 */
class CascadingYouTubeRepository(
    private val newPipeRepository: YouTubeRepository = NewPipeYouTubeRepository(),
    private val invidiousRepository: YouTubeRepository = InvidiousYouTubeRepository(),
    private val innerTubeRepository: YouTubeRepository = InnerTubeYouTubeRepository(),
    private val fallbackRepository: YouTubeRepository = FakeYouTubeRepository()
) : YouTubeRepository {

    val invidiousClient: vn.lobie.mytube.data.remote.InvidiousApiClient?
        get() = (invidiousRepository as? InvidiousYouTubeRepository)?.invidiousClient

    @Volatile
    var enginePriority: List<String> = listOf("NewPipe", "Invidious", "InnerTube")

    fun updateEnginePriority(order: List<String>) {
        if (order.isNotEmpty()) {
            enginePriority = order
            Log.d("CascadingRepo", "Engine priority updated: $order")
        }
    }

    // SEC-24: Circuit breakers per engine to mitigate request amplification and retry storms
    private val circuitBreakers = mapOf(
        "NewPipe" to vn.lobie.mytube.core.common.SecurityUtils.CircuitBreaker(failureThreshold = 4, resetTimeoutMs = 30_000L),
        "Invidious" to vn.lobie.mytube.core.common.SecurityUtils.CircuitBreaker(failureThreshold = 4, resetTimeoutMs = 30_000L),
        "InnerTube" to vn.lobie.mytube.core.common.SecurityUtils.CircuitBreaker(failureThreshold = 4, resetTimeoutMs = 30_000L)
    )

    private fun getSortedStreamPipeline(): List<Pair<String, YouTubeRepository>> {
        val map = mapOf(
            "NewPipe" to newPipeRepository,
            "Invidious" to invidiousRepository,
            "InnerTube" to innerTubeRepository
        )
        val ordered = enginePriority.mapNotNull { name -> map[name]?.let { name to it } }
        val pipeline = if (ordered.isNotEmpty()) ordered else listOf("NewPipe" to newPipeRepository, "Invidious" to invidiousRepository, "InnerTube" to innerTubeRepository)
        // Filter out tripped circuit breakers if at least one healthy engine remains
        val available = pipeline.filter { circuitBreakers[it.first]?.canExecute() != false }
        return if (available.isNotEmpty()) available else pipeline
    }

    private fun getSortedBrowsePipeline(): List<Pair<String, YouTubeRepository>> {
        val map = mapOf(
            "InnerTube" to innerTubeRepository,
            "NewPipe" to newPipeRepository,
            "Invidious" to invidiousRepository
        )
        // For browse/search/trending, InnerTube is 10x-50x faster (<500ms) than HTML scraping.
        // If user customized order, prioritize InnerTube first among available, then user order.
        val browsePriority = listOf("InnerTube", "NewPipe", "Invidious")
        val ordered = browsePriority.mapNotNull { name -> map[name]?.let { name to it } }
        val available = ordered.filter { circuitBreakers[it.first]?.canExecute() != false }
        return if (available.isNotEmpty()) available else ordered
    }

    @Volatile
    var currentRegion: String = "VN"
        private set

    @Volatile
    var currentLanguage: String = "vi"
        private set

    fun setRegion(region: String) {
        if (region.isNotBlank()) {
            currentRegion = region
            (newPipeRepository as? NewPipeYouTubeRepository)?.setRegion(region)
            (invidiousRepository as? InvidiousYouTubeRepository)?.setRegion(region)
            (innerTubeRepository as? InnerTubeYouTubeRepository)?.setRegion(region)
            Log.d("CascadingRepo", "Propagated region=$region to NewPipe, Invidious, InnerTube")
        }
    }

    fun setLanguage(language: String) {
        if (language.isNotBlank()) {
            currentLanguage = language
            (newPipeRepository as? NewPipeYouTubeRepository)?.setLanguage(language)
            (innerTubeRepository as? InnerTubeYouTubeRepository)?.setLanguage(language)
            Log.d("CascadingRepo", "Propagated language=$language to NewPipe, InnerTube")
        }
    }

    @Volatile
    var enableTestFallback: Boolean = false

    override suspend fun getTrendingVideos(): Result<List<Video>> {
        for ((name, repo) in getSortedBrowsePipeline()) {
            val result = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                repo.getTrendingVideos()
            } ?: Result.failure(java.util.concurrent.TimeoutException("$name timed out after 6s"))
            val list = result.getOrNull()
            if (result.isSuccess && !list.isNullOrEmpty()) {
                Log.d("CascadingRepo", "getTrendingVideos: succeeded using $name (${list.size} videos)")
                return result
            } else {
                Log.w("CascadingRepo", "getTrendingVideos: $name failed -> ${result.exceptionOrNull()?.message}")
            }
        }
        if (enableTestFallback) {
            Log.w("CascadingRepo", "getTrendingVideos: all engines failed, using FakeRepository (test mode)")
            return fallbackRepository.getTrendingVideos()
        }
        val errMsg = "Không thể kết nối đến máy chủ YouTube qua các nguồn bóc tách (${enginePriority.joinToString(", ")}). Vui lòng kiểm tra mạng hoặc thử lại."
        Log.e("CascadingRepo", "getTrendingVideos: $errMsg")
        return Result.failure(java.io.IOException(errMsg))
    }

    override suspend fun search(query: String): Result<List<SearchResult>> {
        for ((name, repo) in getSortedBrowsePipeline()) {
            val result = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                repo.search(query)
            } ?: Result.failure(java.util.concurrent.TimeoutException("$name search timed out after 6s"))
            val list = result.getOrNull()
            if (result.isSuccess && !list.isNullOrEmpty()) {
                Log.d("CascadingRepo", "search($query): succeeded using $name (${list.size} results)")
                return result
            } else {
                Log.w("CascadingRepo", "search($query): $name failed -> ${result.exceptionOrNull()?.message}")
            }
        }
        if (enableTestFallback) {
            Log.w("CascadingRepo", "search($query): all engines failed, using FakeRepository (test mode)")
            return fallbackRepository.search(query)
        }
        val errMsg = "Tìm kiếm thất bại. Tất cả nguồn bóc tách (${enginePriority.joinToString(", ")}) đều không phản hồi."
        Log.e("CascadingRepo", "search($query): $errMsg")
        return Result.failure(java.io.IOException(errMsg))
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> {
        for ((name, repo) in getSortedBrowsePipeline()) {
            val result = repo.getVideoDetails(videoId)
            val video = result.getOrNull()
            if (result.isSuccess && video != null) {
                Log.d("CascadingRepo", "getVideoDetails($videoId): succeeded using $name")
                return result
            } else {
                Log.w("CascadingRepo", "getVideoDetails($videoId): $name failed -> ${result.exceptionOrNull()?.message}")
            }
        }
        if (enableTestFallback) {
            Log.w("CascadingRepo", "getVideoDetails($videoId): all engines failed, using FakeRepository (test mode)")
            return fallbackRepository.getVideoDetails(videoId)
        }
        val errMsg = "Không thể tải thông tin video ($videoId)."
        Log.e("CascadingRepo", "getVideoDetails($videoId): $errMsg")
        return Result.failure(java.io.IOException(errMsg))
    }

    override suspend fun getComments(videoId: String): Result<List<vn.lobie.mytube.domain.model.Comment>> {
        for ((name, repo) in getSortedBrowsePipeline()) {
            val result = repo.getComments(videoId)
            val list = result.getOrNull()
            if (result.isSuccess && !list.isNullOrEmpty()) {
                Log.d("CascadingRepo", "getComments($videoId): succeeded using $name (${list.size} comments)")
                return result
            }
        }
        val invidiousResult = invidiousRepository.getComments(videoId)
        if (invidiousResult.isSuccess) {
            return invidiousResult
        }
        return Result.success(emptyList())
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> {
        vn.lobie.mytube.core.common.AppLogger.d("Source", "Requesting stream info (pipeline: ${enginePriority.joinToString(" -> ")})", videoId)
        for ((name, repo) in getSortedStreamPipeline()) {
            try {
                Log.d("CascadingRepo", "getStreamInfo($videoId): trying $name...")
                vn.lobie.mytube.core.common.AppLogger.d("Source", "Trying engine: $name", videoId)
                val result = repo.getStreamInfo(videoId)
                val info = result.getOrNull()
                if (result.isSuccess && info != null && (info.videoStreams.isNotEmpty() || !info.hlsUrl.isNullOrEmpty() || info.audioStreams.isNotEmpty())) {
                    circuitBreakers[name]?.recordSuccess()
                    val sampleStream = info.videoStreams.firstOrNull()?.url ?: info.hlsUrl ?: info.audioStreams.firstOrNull()?.url
                    val masked = sampleStream?.let { vn.lobie.mytube.core.common.AppLogger.maskUrl(it) } ?: "none"
                    Log.d("CascadingRepo", "getStreamInfo($videoId): succeeded using $name (hls=${!info.hlsUrl.isNullOrEmpty()}, videoStreams=${info.videoStreams.size}, audioStreams=${info.audioStreams.size})")
                    vn.lobie.mytube.core.common.AppLogger.i("Source", "$name succeeded [streams=${info.videoStreams.size}, hls=${!info.hlsUrl.isNullOrEmpty()}], url=$masked", videoId)
                    return Result.success(info.copy(source = name))
                } else {
                    circuitBreakers[name]?.recordFailure()
                    val errMsg = result.exceptionOrNull()?.message ?: "no playable streams"
                    Log.w("CascadingRepo", "getStreamInfo($videoId): $name returned no playable streams or failed -> $errMsg")
                    vn.lobie.mytube.core.common.AppLogger.w("Source", "$name failed: $errMsg", videoId)
                    vn.lobie.mytube.core.common.AppLogger.i("Fallback", "Cascading: falling back from $name to next engine in pipeline", videoId)
                }
            } catch (e: Exception) {
                circuitBreakers[name]?.recordFailure()
                Log.e("CascadingRepo", "getStreamInfo($videoId): $name threw exception", e)
                vn.lobie.mytube.core.common.AppLogger.w("Source", "$name threw exception: ${e.message}", videoId, raw = e.stackTraceToString())
                vn.lobie.mytube.core.common.AppLogger.i("Fallback", "Cascading: falling back from $name after exception", videoId)
            }
        }
        if (enableTestFallback) {
            Log.w("CascadingRepo", "getStreamInfo($videoId): all engines failed, falling back to sample stream (test mode)")
            vn.lobie.mytube.core.common.AppLogger.e("Source", "All engines exhausted for $videoId, using Fallback test stream", videoId)
            return fallbackRepository.getStreamInfo(videoId).map { it.copy(source = "Fallback") }
        }
        val errMsg = "Tất cả nguồn phát (${enginePriority.joinToString(", ")}) đều không trích xuất được luồng video. Vui lòng thử lại hoặc đổi nguồn ưu tiên trong Cài đặt."
        Log.e("CascadingRepo", "getStreamInfo($videoId): $errMsg")
        vn.lobie.mytube.core.common.AppLogger.e("Source", "All engines exhausted for $videoId, playback failed", videoId)
        return Result.failure(java.io.IOException(errMsg))
    }
}
