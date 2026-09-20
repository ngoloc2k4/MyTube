package vn.lobie.mytube.data.repository

import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.StreamInfo
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

/**
 * Cascading multi-engine repository:
 * 1. Primary: Invidious API (fast, lightweight REST)
 * 2. Secondary: NewPipeExtractor (direct client extraction from YouTube)
 * 3. Fallback: Fake repository (deterministic local sample data & test stream)
 */
class CascadingYouTubeRepository(
    private val primaryRepository: YouTubeRepository = InvidiousYouTubeRepository(),
    private val secondaryRepository: YouTubeRepository = NewPipeYouTubeRepository(),
    private val fallbackRepository: YouTubeRepository = FakeYouTubeRepository()
) : YouTubeRepository {

    override suspend fun getTrendingVideos(): Result<List<Video>> {
        val r1 = primaryRepository.getTrendingVideos()
        if (r1.isSuccess && r1.getOrNull()?.isNotEmpty() == true) {
            return r1
        }

        val r2 = secondaryRepository.getTrendingVideos()
        if (r2.isSuccess && r2.getOrNull()?.isNotEmpty() == true) {
            return r2
        }

        return fallbackRepository.getTrendingVideos()
    }

    override suspend fun search(query: String): Result<List<SearchResult>> {
        val r1 = primaryRepository.search(query)
        if (r1.isSuccess && r1.getOrNull()?.isNotEmpty() == true) {
            return r1
        }

        val r2 = secondaryRepository.search(query)
        if (r2.isSuccess && r2.getOrNull()?.isNotEmpty() == true) {
            return r2
        }

        return fallbackRepository.search(query)
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> {
        val r1 = primaryRepository.getVideoDetails(videoId)
        if (r1.isSuccess && r1.getOrNull() != null) {
            return r1
        }

        val r2 = secondaryRepository.getVideoDetails(videoId)
        if (r2.isSuccess && r2.getOrNull() != null) {
            return r2
        }

        return fallbackRepository.getVideoDetails(videoId)
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> {
        val r1 = primaryRepository.getStreamInfo(videoId)
        if (r1.isSuccess && r1.getOrNull()?.videoStreams?.isNotEmpty() == true) {
            return r1
        }

        val r2 = secondaryRepository.getStreamInfo(videoId)
        if (r2.isSuccess && r2.getOrNull()?.videoStreams?.isNotEmpty() == true) {
            return r2
        }

        return fallbackRepository.getStreamInfo(videoId)
    }
}
