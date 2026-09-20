package vn.lobie.mytube.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import vn.lobie.mytube.data.remote.newpipe.NewPipeDownloader
import vn.lobie.mytube.domain.model.*
import vn.lobie.mytube.domain.repository.YouTubeRepository
import java.util.concurrent.atomic.AtomicBoolean

class NewPipeYouTubeRepository(
    private val downloader: NewPipeDownloader = NewPipeDownloader()
) : YouTubeRepository {

    companion object {
        private val isInitialized = AtomicBoolean(false)

        fun ensureInitialized(downloader: NewPipeDownloader) {
            if (isInitialized.compareAndSet(false, true)) {
                NewPipe.init(downloader)
            }
        }
    }

    init {
        ensureInitialized(downloader)
    }

    override suspend fun getTrendingVideos(): Result<List<Video>> = withContext(Dispatchers.IO) {
        runCatching {
            val kiosk = ServiceList.YouTube.kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            kiosk.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toDomainModel() }
        }
    }

    override suspend fun search(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()
            searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().map {
                SearchResult.VideoItem(it.toDomainModel())
            }
        }
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> = withContext(Dispatchers.IO) {
        runCatching {
            val streamExtractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            streamExtractor.fetchPage()

            val bestThumb = streamExtractor.thumbnails.maxByOrNull { it.width }?.url
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            Video(
                id = videoId,
                title = streamExtractor.name.orEmpty(),
                channel = Channel(
                    id = streamExtractor.uploaderUrl?.substringAfterLast("/").orEmpty(),
                    name = streamExtractor.uploaderName.orEmpty(),
                    avatarUrl = streamExtractor.uploaderAvatarUrl.orEmpty(),
                    subscriberCountText = ""
                ),
                durationSeconds = streamExtractor.length,
                viewCount = streamExtractor.viewCount,
                publishedTimeText = streamExtractor.textualUploadDate.orEmpty(),
                thumbnailUrl = bestThumb,
                description = streamExtractor.description?.content.orEmpty()
            )
        }
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val streamExtractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            streamExtractor.fetchPage()

            val videoStreams = streamExtractor.videoStreams.map { vs ->
                VideoStream(
                    url = vs.url,
                    quality = vs.resolution.orEmpty(),
                    format = vs.format?.name.orEmpty().lowercase(),
                    bitrate = vs.bitrate.toLong()
                )
            }

            val audioStreams = streamExtractor.audioStreams.map { asStream ->
                AudioStream(
                    url = asStream.url,
                    quality = asStream.quality.orEmpty(),
                    format = asStream.format?.name.orEmpty().lowercase(),
                    bitrate = asStream.bitrate.toLong()
                )
            }

            StreamInfo(
                videoId = videoId,
                title = streamExtractor.name.orEmpty(),
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                hlsUrl = streamExtractor.hlsUrl,
                dashUrl = streamExtractor.dashUrl
            )
        }
    }

    private fun StreamInfoItem.toDomainModel(): Video {
        val videoId = url.substringAfter("v=").substringBefore("&")
        val thumb = thumbnails.maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return Video(
            id = videoId,
            title = name.orEmpty(),
            channel = Channel(
                id = uploaderUrl?.substringAfterLast("/").orEmpty(),
                name = uploaderName.orEmpty(),
                avatarUrl = uploaderAvatarUrl.orEmpty(),
                subscriberCountText = ""
            ),
            durationSeconds = duration,
            viewCount = viewCount,
            publishedTimeText = textualUploadDate.orEmpty(),
            thumbnailUrl = thumb,
            description = ""
        )
    }
}
