package vn.lobie.mytube.data.repository

import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.StreamInfo
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

/**
 * Cascading multi-engine repository pipeline:
 * 1. Primary: Invidious API (fast, lightweight REST with multi-instance rotator)
 * 2. Secondary: Native InnerTube Engine (direct YouTube v1 API without proxy)
 * 3. Tertiary: NewPipeExtractor (direct client extraction from YouTube HTML/JS)
 * 4. Fallback: Fake repository (deterministic local sample data & test stream)
 */
class CascadingYouTubeRepository(
    private val invidiousRepository: YouTubeRepository = InvidiousYouTubeRepository(),
    private val innerTubeRepository: YouTubeRepository = InnerTubeYouTubeRepository(),
    private val newPipeRepository: YouTubeRepository = NewPipeYouTubeRepository(),
    private val fallbackRepository: YouTubeRepository = FakeYouTubeRepository()
) : YouTubeRepository {

    private val pipeline: List<YouTubeRepository> = listOf(
        invidiousRepository,
        innerTubeRepository,
        newPipeRepository,
        fallbackRepository
    )

    override suspend fun getTrendingVideos(): Result<List<Video>> {
        for (repo in pipeline) {
            val result = repo.getTrendingVideos()
            if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
                return result
            }
        }
        return fallbackRepository.getTrendingVideos()
    }

    override suspend fun search(query: String): Result<List<SearchResult>> {
        for (repo in pipeline) {
            val result = repo.search(query)
            if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
                return result
            }
        }
        return fallbackRepository.search(query)
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> {
        for (repo in pipeline) {
            val result = repo.getVideoDetails(videoId)
            if (result.isSuccess && result.getOrNull() != null) {
                return result
            }
        }
        return fallbackRepository.getVideoDetails(videoId)
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> {
        for (repo in pipeline) {
            val result = repo.getStreamInfo(videoId)
            val info = result.getOrNull()
            if (result.isSuccess && info != null && (info.videoStreams.isNotEmpty() || !info.hlsUrl.isNullOrEmpty() || info.audioStreams.isNotEmpty())) {
                return result
            }
        }
        return fallbackRepository.getStreamInfo(videoId)
    }
}
