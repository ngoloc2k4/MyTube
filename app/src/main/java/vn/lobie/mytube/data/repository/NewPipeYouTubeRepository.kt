package vn.lobie.mytube.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import vn.lobie.mytube.data.remote.newpipe.NewPipeDownloader
import vn.lobie.mytube.domain.model.*
import vn.lobie.mytube.domain.repository.YouTubeRepository
import java.util.concurrent.atomic.AtomicBoolean

class NewPipeYouTubeRepository(
    private val downloader: NewPipeDownloader = NewPipeDownloader()
) : YouTubeRepository {

    @Volatile
    var region: String = "VN"
        private set

    @Volatile
    var language: String = "vi"
        private set

    companion object {
        private val isInitialized = AtomicBoolean(false)

        fun ensureInitialized(downloader: NewPipeDownloader, localization: Localization? = null, contentCountry: ContentCountry? = null) {
            if (localization != null && contentCountry != null) {
                NewPipe.init(downloader, localization, contentCountry)
                isInitialized.set(true)
            } else if (isInitialized.compareAndSet(false, true)) {
                NewPipe.init(downloader)
            }
        }
    }

    init {
        ensureInitialized(downloader)
    }

    fun setRegion(newRegion: String) {
        if (newRegion.isNotBlank() && this.region != newRegion) {
            this.region = newRegion
            updateNewPipeLocalization()
        }
    }

    fun setLanguage(newLanguage: String) {
        if (newLanguage.isNotBlank() && this.language != newLanguage) {
            this.language = newLanguage
            updateNewPipeLocalization()
        }
    }

    private fun updateNewPipeLocalization() {
        try {
            val loc = Localization.fromLocalizationCode(language).orElse(Localization.DEFAULT)
            val cc = ContentCountry(region)
            NewPipe.init(downloader, loc, cc)
            Log.d("NewPipeRepo", "NewPipe initialized with region=$region, language=$language")
        } catch (e: Exception) {
            Log.w("NewPipeRepo", "Failed to update NewPipe localization/contentCountry: ${e.message}")
        }
    }

    override suspend fun getTrendingVideos(): Result<List<Video>> = withContext(Dispatchers.IO) {
        runCatching {
            val kiosk = ServiceList.YouTube.kioskList.defaultKioskExtractor
            try {
                kiosk.forceContentCountry(ContentCountry(region))
                val loc = Localization.fromLocalizationCode(language).orElse(Localization.DEFAULT)
                kiosk.forceLocalization(loc)
            } catch (e: Exception) {
                Log.w("NewPipeRepo", "Unable to set forced content country on kiosk: ${e.message}")
            }
            kiosk.fetchPage()
            kiosk.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toDomainModel() }
        }.onFailure {
            Log.e("NewPipeRepo", "getTrendingVideos failed", it)
        }
    }

    override suspend fun search(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            try {
                searchExtractor.forceContentCountry(ContentCountry(region))
                val loc = Localization.fromLocalizationCode(language).orElse(Localization.DEFAULT)
                searchExtractor.forceLocalization(loc)
            } catch (e: Exception) {
                Log.w("NewPipeRepo", "Unable to set forced content country on search: ${e.message}")
            }
            searchExtractor.fetchPage()
            searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().map {
                SearchResult.VideoItem(it.toDomainModel())
            }
        }.onFailure {
            Log.e("NewPipeRepo", "search($query) failed", it)
        }
    }

    override suspend fun getVideoDetails(videoId: String): Result<Video> = withContext(Dispatchers.IO) {
        runCatching {
            val streamExtractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            streamExtractor.fetchPage()

            val bestThumb = streamExtractor.thumbnails.maxByOrNull { it.width }?.url
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val bestAvatar = streamExtractor.uploaderAvatars.maxByOrNull { it.width }?.url
                ?: "https://picsum.photos/seed/$videoId/120/120"

            Video(
                id = videoId,
                title = streamExtractor.name.orEmpty(),
                channel = Channel(
                    id = streamExtractor.uploaderUrl?.substringAfterLast("/").orEmpty(),
                    name = streamExtractor.uploaderName.orEmpty(),
                    avatarUrl = bestAvatar,
                    subscriberCountText = ""
                ),
                durationSeconds = streamExtractor.length,
                viewCount = streamExtractor.viewCount,
                publishedTimeText = streamExtractor.textualUploadDate.orEmpty(),
                thumbnailUrl = bestThumb,
                description = streamExtractor.description?.content.orEmpty()
            )
        }.onFailure {
            Log.e("NewPipeRepo", "getVideoDetails($videoId) failed", it)
        }
    }

    override suspend fun getStreamInfo(videoId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val streamExtractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            streamExtractor.fetchPage()

            val videoStreams = mutableListOf<VideoStream>()
            streamExtractor.videoStreams?.forEach { vs ->
                val streamUrl = vs.url
                if (!streamUrl.isNullOrEmpty()) {
                    videoStreams.add(
                        VideoStream(
                            url = streamUrl,
                            quality = vs.resolution.orEmpty(),
                            format = vs.format?.name.orEmpty().lowercase(),
                            bitrate = vs.bitrate.toLong()
                        )
                    )
                }
            }

            // Also include video-only streams if progressive streams are unavailable
            if (videoStreams.isEmpty()) {
                streamExtractor.videoOnlyStreams?.forEach { vs ->
                    val streamUrl = vs.url
                    if (!streamUrl.isNullOrEmpty()) {
                        videoStreams.add(
                            VideoStream(
                                url = streamUrl,
                                quality = vs.resolution.orEmpty(),
                                format = vs.format?.name.orEmpty().lowercase(),
                                bitrate = vs.bitrate.toLong()
                            )
                        )
                    }
                }
            }

            val audioStreams = streamExtractor.audioStreams?.mapNotNull { asStream ->
                val audioUrl = asStream.url
                if (audioUrl.isNullOrEmpty()) null
                else AudioStream(
                    url = audioUrl,
                    quality = asStream.quality.orEmpty(),
                    format = asStream.format?.name.orEmpty().lowercase(),
                    bitrate = asStream.bitrate.toLong()
                )
            } ?: emptyList()

            StreamInfo(
                videoId = videoId,
                title = streamExtractor.name.orEmpty(),
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                hlsUrl = streamExtractor.hlsUrl,
                dashUrl = streamExtractor.dashMpdUrl
            )
        }.onFailure {
            Log.e("NewPipeRepo", "getStreamInfo($videoId) failed", it)
            vn.lobie.mytube.core.common.AppLogger.w("NewPipe", "getStreamInfo failed: ${it.message}", videoId, raw = it.stackTraceToString())
        }
    }

    private fun StreamInfoItem.toDomainModel(): Video {
        val videoId = url.substringAfter("v=").substringBefore("&")
        val thumb = thumbnails.maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val avatar = uploaderAvatars.maxByOrNull { it.width }?.url
            ?: "https://picsum.photos/seed/$videoId/120/120"

        return Video(
            id = videoId,
            title = name.orEmpty(),
            channel = Channel(
                id = uploaderUrl?.substringAfterLast("/").orEmpty(),
                name = uploaderName.orEmpty(),
                avatarUrl = avatar,
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
