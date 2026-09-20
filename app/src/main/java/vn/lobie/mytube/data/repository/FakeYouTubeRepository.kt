package vn.lobie.mytube.data.repository

import kotlinx.coroutines.delay
import vn.lobie.mytube.domain.model.*
import vn.lobie.mytube.domain.repository.YouTubeRepository

class FakeYouTubeRepository : YouTubeRepository {

    private val sampleVideos = listOf(
        Video(
            id = "mock_1",
            title = "Building a Modern Android App with Jetpack Compose & Clean Architecture",
            channel = Channel(
                id = "chan_android",
                name = "Android Developers",
                avatarUrl = "https://picsum.photos/seed/avatar1/120/120",
                subscriberCountText = "1.2M subscribers"
            ),
            durationSeconds = 14 * 60 + 25,
            viewCount = 245_000,
            publishedTimeText = "2 days ago",
            thumbnailUrl = "https://picsum.photos/seed/thumb1/640/360",
            description = "Learn how to structure Android projects using Clean Architecture, ViewModel, and Material 3."
        ),
        Video(
            id = "mock_2",
            title = "Kotlin Coroutines & Flow: Best Practices for Background Processing",
            channel = Channel(
                id = "chan_kotlin",
                name = "Kotlin by JetBrains",
                avatarUrl = "https://picsum.photos/seed/avatar2/120/120",
                subscriberCountText = "580K subscribers"
            ),
            durationSeconds = 28 * 60 + 10,
            viewCount = 180_000,
            publishedTimeText = "1 week ago",
            thumbnailUrl = "https://picsum.photos/seed/thumb2/640/360",
            description = "Deep dive into Coroutines scopes, dispatchers, StateFlow, and SharedFlow."
        ),
        Video(
            id = "mock_3",
            title = "AndroidX Media3 ExoPlayer: Complete Background Audio Service Tutorial",
            channel = Channel(
                id = "chan_dev",
                name = "Mobile Mastery",
                avatarUrl = "https://picsum.photos/seed/avatar3/120/120",
                subscriberCountText = "340K subscribers"
            ),
            durationSeconds = 42 * 60 + 5,
            viewCount = 92_000,
            publishedTimeText = "3 weeks ago",
            thumbnailUrl = "https://picsum.photos/seed/thumb3/640/360",
            description = "Step-by-step setup of MediaSessionService and notification controls in modern Android."
        ),
        Video(
            id = "mock_4",
            title = "Building Multi-Engine Fallback Architecture in Production",
            channel = Channel(
                id = "chan_arch",
                name = "Software Craft",
                avatarUrl = "https://picsum.photos/seed/avatar4/120/120",
                subscriberCountText = "210K subscribers"
            ),
            durationSeconds = 19 * 60 + 45,
            viewCount = 64_000,
            publishedTimeText = "1 month ago",
            thumbnailUrl = "https://picsum.photos/seed/thumb4/640/360",
            description = "Design patterns for multi-source extraction, circuit breakers, and graceful degradation."
        )
    )

    override suspend fun getTrendingVideos(): Result<List<Video>> {
        delay(400) // Mô phỏng latency network
        return Result.success(sampleVideos)
    }

    override suspend fun search(query: String): Result<List<SearchResult>> {
        delay(300)
        val filtered = sampleVideos.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.channel.name.contains(query, ignoreCase = true)
        }.map { SearchResult.VideoItem(it) }
        return Result.success(filtered)
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> {
        delay(200)
        val video = sampleVideos.find { it.id == videoId }
        return if (video != null) {
            Result.success(video)
        } else {
            Result.failure(NoSuchElementException("Video not found"))
        }
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> {
        delay(300)
        return Result.success(
            StreamInfo(
                videoId = videoId,
                title = sampleVideos.find { it.id == videoId }?.title ?: "Sample Video",
                videoStreams = listOf(
                    VideoStream(
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        quality = "720p",
                        format = "mp4",
                        bitrate = 2_500_000
                    )
                ),
                audioStreams = listOf(
                    AudioStream(
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        quality = "128kbps",
                        format = "m4a",
                        bitrate = 128_000
                    )
                )
            )
        )
    }
}
