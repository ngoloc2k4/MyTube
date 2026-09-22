package vn.lobie.mytube.domain.repository

import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.StreamInfo
import vn.lobie.mytube.domain.model.Video

interface YouTubeRepository {
    suspend fun getTrendingVideos(): Result<List<Video>>
    suspend fun search(query: String): Result<List<SearchResult>>
    suspend fun getVideoDetails(videoId: String): Result<Video>
    suspend fun getStreamInfo(videoId: String): Result<StreamInfo>
    suspend fun getComments(videoId: String): Result<List<vn.lobie.mytube.domain.model.Comment>> = Result.success(emptyList())
}
