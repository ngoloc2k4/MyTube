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

    private fun getSortedStreamPipeline(): List<Pair<String, YouTubeRepository>> {
        val map = mapOf(
            "NewPipe" to newPipeRepository,
            "Invidious" to invidiousRepository,
            "InnerTube" to innerTubeRepository
        )
        val ordered = enginePriority.mapNotNull { name -> map[name]?.let { name to it } }
        return if (ordered.isNotEmpty()) ordered else listOf("NewPipe" to newPipeRepository, "Invidious" to invidiousRepository, "InnerTube" to innerTubeRepository)
    }

    private fun getSortedBrowsePipeline(): List<Pair<String, YouTubeRepository>> {
        return getSortedStreamPipeline()
    }

    fun setRegion(region: String) {
        (invidiousRepository as? InvidiousYouTubeRepository)?.setRegion(region)
        (innerTubeRepository as? InnerTubeYouTubeRepository)?.setRegion(region)
    }

    fun setLanguage(language: String) {
        (innerTubeRepository as? InnerTubeYouTubeRepository)?.setLanguage(language)
    }

    override suspend fun getTrendingVideos(): Result<List<Video>> {
        for ((name, repo) in getSortedBrowsePipeline()) {
            val result = repo.getTrendingVideos()
            val list = result.getOrNull()
            if (result.isSuccess && !list.isNullOrEmpty()) {
                Log.d("CascadingRepo", "getTrendingVideos: succeeded using $name (${list.size} videos)")
                return result
            } else {
                Log.w("CascadingRepo", "getTrendingVideos: $name failed -> ${result.exceptionOrNull()?.message}")
            }
        }
        Log.w("CascadingRepo", "getTrendingVideos: all engines failed, using FakeRepository")
        return fallbackRepository.getTrendingVideos()
    }

    override suspend fun search(query: String): Result<List<SearchResult>> {
        for ((name, repo) in getSortedBrowsePipeline()) {
            val result = repo.search(query)
            val list = result.getOrNull()
            if (result.isSuccess && !list.isNullOrEmpty()) {
                Log.d("CascadingRepo", "search($query): succeeded using $name (${list.size} results)")
                return result
            } else {
                Log.w("CascadingRepo", "search($query): $name failed -> ${result.exceptionOrNull()?.message}")
            }
        }
        Log.w("CascadingRepo", "search($query): all engines failed, using FakeRepository")
        return fallbackRepository.search(query)
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
        Log.w("CascadingRepo", "getVideoDetails($videoId): all engines failed, using FakeRepository")
        return fallbackRepository.getVideoDetails(videoId)
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> {
        for ((name, repo) in getSortedStreamPipeline()) {
            try {
                Log.d("CascadingRepo", "getStreamInfo($videoId): trying $name...")
                val result = repo.getStreamInfo(videoId)
                val info = result.getOrNull()
                if (result.isSuccess && info != null && (info.videoStreams.isNotEmpty() || !info.hlsUrl.isNullOrEmpty() || info.audioStreams.isNotEmpty())) {
                    Log.d("CascadingRepo", "getStreamInfo($videoId): succeeded using $name (hls=${!info.hlsUrl.isNullOrEmpty()}, videoStreams=${info.videoStreams.size}, audioStreams=${info.audioStreams.size})")
                    return Result.success(info.copy(source = name))
                } else {
                    Log.w("CascadingRepo", "getStreamInfo($videoId): $name returned no playable streams or failed -> ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("CascadingRepo", "getStreamInfo($videoId): $name threw exception", e)
            }
        }
        Log.w("CascadingRepo", "getStreamInfo($videoId): all engines failed, falling back to sample stream")
        return fallbackRepository.getStreamInfo(videoId).map { it.copy(source = "Fallback") }
    }
}
