package vn.lobie.mytube.data.repository

import vn.lobie.mytube.data.remote.InvidiousApiClient
import vn.lobie.mytube.data.remote.dto.InvidiousVideoDto
import vn.lobie.mytube.domain.model.*
import vn.lobie.mytube.domain.repository.YouTubeRepository

class InvidiousYouTubeRepository(
    private val api: InvidiousApiClient = InvidiousApiClient()
) : YouTubeRepository {

    val invidiousClient: InvidiousApiClient get() = api

    fun setRegion(region: String) {
        api.region = region
    }

    override suspend fun getTrendingVideos(): Result<List<Video>> {
        val result = api.getTrending()
        return result.mapCatching { dtos ->
            val list = dtos.map { it.toDomainModel() }
            if (list.isEmpty()) throw NoSuchElementException("Empty trending list from Invidious")
            list
        }
    }

    override suspend fun search(query: String): Result<List<SearchResult>> {
        val result = api.search(query)
        return result.mapCatching { dtos ->
            val list = dtos.map { SearchResult.VideoItem(it.toDomainModel()) }
            if (list.isEmpty()) throw NoSuchElementException("Empty search results from Invidious for $query")
            list
        }
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> {
        val result = api.getVideo(videoId)
        return result.mapCatching { dto ->
            dto.toDomainModel()
        }
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> {
        val result = api.getVideo(videoId)
        return result.mapCatching { dto ->
            val videoStreams = mutableListOf<VideoStream>()
            dto.formatStreams.forEach {
                if (it.url.isNotBlank()) {
                    videoStreams.add(
                        VideoStream(
                            url = it.url,
                            quality = it.qualityLabel.ifEmpty { it.quality },
                            format = it.container.ifEmpty { "mp4" },
                            bitrate = it.bitrate
                        )
                    )
                }
            }

            // If no progressive formatStreams, include video-only adaptive formats
            if (videoStreams.isEmpty()) {
                dto.adaptiveFormats.filter { it.type.startsWith("video") }.forEach {
                    if (it.url.isNotBlank()) {
                        videoStreams.add(
                            VideoStream(
                                url = it.url,
                                quality = it.qualityLabel ?: it.resolution ?: "video",
                                format = it.container ?: "mp4",
                                bitrate = it.bitrate
                            )
                        )
                    }
                }
            }

            val audioStreams = dto.adaptiveFormats
                .filter { it.type.startsWith("audio") && it.url.isNotBlank() }
                .map {
                    AudioStream(
                        url = it.url,
                        quality = it.qualityLabel ?: "audio",
                        format = it.container ?: "m4a",
                        bitrate = it.bitrate
                    )
                }

            StreamInfo(
                videoId = dto.videoId,
                title = dto.title,
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                hlsUrl = dto.hlsUrl,
                dashUrl = dto.dashUrl
            )
        }
    }

    private fun InvidiousVideoDto.toDomainModel(): Video {
        // Direct YouTube CDN URL always bypasses Invidious captcha/proxy issues
        val fullThumbUrl = if (videoId.isNotBlank()) {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        } else {
            val thumb = videoThumbnails.maxByOrNull { it.width }?.url.orEmpty()
            if (thumb.contains("/vi/")) {
                "https://i.ytimg.com/vi/" + thumb.substringAfter("/vi/")
            } else if (thumb.startsWith("http")) {
                thumb
            } else {
                "https://i.ytimg.com$thumb"
            }
        }

        val rawAvatar = authorThumbnails.maxByOrNull { it.width }?.url.orEmpty()
        val fullAvatarUrl = when {
            rawAvatar.contains("yt3.ggpht.com") || rawAvatar.contains("googleusercontent.com") -> {
                if (rawAvatar.startsWith("http")) rawAvatar else "https:$rawAvatar"
            }
            rawAvatar.contains("/ggpht/") -> {
                "https://yt3.ggpht.com/" + rawAvatar.substringAfter("/ggpht/")
            }
            rawAvatar.startsWith("http") && !rawAvatar.contains("invidious") -> rawAvatar
            rawAvatar.isNotBlank() -> "https://yt3.ggpht.com$rawAvatar"
            else -> "https://picsum.photos/seed/$authorId/120/120"
        }

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
