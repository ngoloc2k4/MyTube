package vn.lobie.mytube.data.repository

import vn.lobie.mytube.data.remote.InvidiousApiClient
import vn.lobie.mytube.data.remote.dto.InvidiousVideoDto
import vn.lobie.mytube.domain.model.*
import vn.lobie.mytube.domain.repository.YouTubeRepository

class InvidiousYouTubeRepository(
    private val api: InvidiousApiClient = InvidiousApiClient(),
    private val fallbackRepository: YouTubeRepository = FakeYouTubeRepository()
) : YouTubeRepository {

    override suspend fun getTrendingVideos(): Result<List<Video>> {
        val result = api.getTrending()
        return if (result.isSuccess) {
            val list = result.getOrNull()?.map { it.toDomainModel() } ?: emptyList()
            if (list.isNotEmpty()) Result.success(list) else fallbackRepository.getTrendingVideos()
        } else {
            // Tự động fallback sang mock repository nếu mạng lỗi
            fallbackRepository.getTrendingVideos()
        }
    }

    override suspend fun search(query: String): Result<List<SearchResult>> {
        val result = api.search(query)
        return if (result.isSuccess) {
            val list = result.getOrNull()?.map {
                SearchResult.VideoItem(it.toDomainModel())
            } ?: emptyList()
            Result.success(list)
        } else {
            fallbackRepository.search(query)
        }
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> {
        val result = api.getVideo(videoId)
        return if (result.isSuccess) {
            val dto = result.getOrNull()
            if (dto != null) Result.success(dto.toDomainModel())
            else fallbackRepository.getVideoDetails(videoId)
        } else {
            fallbackRepository.getVideoDetails(videoId)
        }
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> {
        val result = api.getVideo(videoId)
        return if (result.isSuccess) {
            val dto = result.getOrNull()
            if (dto != null) {
                val videoStreams = dto.formatStreams.map {
                    VideoStream(
                        url = it.url,
                        quality = it.qualityLabel.ifEmpty { it.quality },
                        format = it.container.ifEmpty { "mp4" },
                        bitrate = it.bitrate
                    )
                }
                val audioStreams = dto.adaptiveFormats
                    .filter { it.type.startsWith("audio") }
                    .map {
                        AudioStream(
                            url = it.url,
                            quality = it.qualityLabel ?: "audio",
                            format = it.container ?: "m4a",
                            bitrate = it.bitrate
                        )
                    }

                Result.success(
                    StreamInfo(
                        videoId = dto.videoId,
                        title = dto.title,
                        videoStreams = videoStreams,
                        audioStreams = audioStreams,
                        hlsUrl = dto.hlsUrl,
                        dashUrl = dto.dashUrl
                    )
                )
            } else {
                fallbackRepository.getStreamInfo(videoId)
            }
        } else {
            fallbackRepository.getStreamInfo(videoId)
        }
    }

    private fun InvidiousVideoDto.toDomainModel(): Video {
        val thumb = videoThumbnails.maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val fullThumbUrl = if (thumb.startsWith("http")) thumb else "https://i.ytimg.com$thumb"

        val avatar = authorThumbnails.maxByOrNull { it.width }?.url
            ?: "https://picsum.photos/seed/$authorId/120/120"
        val fullAvatarUrl = if (avatar.startsWith("http")) avatar else "https://picsum.photos/seed/$authorId/120/120"

        return Video(
            id = videoId,
            title = title,
            channel = Channel(
                id = authorId,
                name = author,
                avatarUrl = fullAvatarUrl,
                subscriberCountText = ""
            ),
            durationSeconds = lengthSeconds,
            viewCount = viewCount,
            publishedTimeText = publishedText,
            thumbnailUrl = fullThumbUrl,
            description = description
        )
    }
}
