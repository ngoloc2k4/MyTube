package vn.lobie.mytube.data.repository

import vn.lobie.mytube.data.remote.innertube.InnerTubeClient
import vn.lobie.mytube.data.remote.innertube.InnerTubeParser
import vn.lobie.mytube.domain.model.*
import vn.lobie.mytube.domain.repository.YouTubeRepository

class InnerTubeYouTubeRepository(
    private val client: InnerTubeClient = InnerTubeClient()
) : YouTubeRepository {

    fun setRegion(region: String) {
        client.region = region
    }

    fun setLanguage(language: String) {
        client.language = language
    }

    override suspend fun getTrendingVideos(): Result<List<Video>> {
        val result = client.browse("FEtrending")
        return result.mapCatching { json ->
            val list = InnerTubeParser.parseBrowseVideos(json)
            if (list.isEmpty()) {
                val homeResult = client.browse("FEwhat_to_watch").getOrNull()
                if (homeResult != null) InnerTubeParser.parseBrowseVideos(homeResult) else emptyList()
            } else list
        }
    }

    override suspend fun search(query: String): Result<List<SearchResult>> {
        val result = client.search(query)
        return result.mapCatching { json ->
            InnerTubeParser.parseSearchResults(json)
        }
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> {
        val result = client.player(videoId)
        return result.mapCatching { json ->
            val (video, _) = InnerTubeParser.parsePlayerResponse(json, videoId)
            video ?: throw NoSuchElementException("Video details not found for $videoId")
        }
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> {
        val result = client.player(videoId)
        return result.mapCatching { json ->
            val (_, streamInfo) = InnerTubeParser.parsePlayerResponse(json, videoId)
            streamInfo
        }.onFailure {
            vn.lobie.mytube.core.common.AppLogger.w("InnerTube", "InnerTube stream extraction failed: ${it.message}", videoId, raw = it.stackTraceToString())
        }
    }
}
