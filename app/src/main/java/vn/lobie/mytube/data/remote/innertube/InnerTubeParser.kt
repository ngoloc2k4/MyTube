package vn.lobie.mytube.data.remote.innertube

import kotlinx.serialization.json.*
import vn.lobie.mytube.domain.model.*

object InnerTubeParser {

    fun parseBrowseVideos(root: JsonObject): List<Video> {
        val videoRenderers = mutableListOf<JsonObject>()
        findRenderers(root, "videoRenderer", videoRenderers)
        return videoRenderers.mapNotNull { parseVideoRenderer(it) }
    }

    fun parseSearchResults(root: JsonObject): List<SearchResult> {
        val videoRenderers = mutableListOf<JsonObject>()
        findRenderers(root, "videoRenderer", videoRenderers)
        return videoRenderers.mapNotNull {
            parseVideoRenderer(it)?.let { video -> SearchResult.VideoItem(video) }
        }
    }

    fun parsePlayerResponse(root: JsonObject, videoId: String): Pair<Video?, StreamInfo> {
        val details = root["videoDetails"]?.jsonObject
        val title = details?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
        val author = details?.get("author")?.jsonPrimitive?.contentOrNull.orEmpty()
        val channelId = details?.get("channelId")?.jsonPrimitive?.contentOrNull.orEmpty()
        val lengthSeconds = details?.get("lengthSeconds")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val viewCount = details?.get("viewCount")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val description = details?.get("shortDescription")?.jsonPrimitive?.contentOrNull.orEmpty()

        val thumbs = details?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
        val bestThumb = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val video = if (title.isNotEmpty()) {
            Video(
                id = videoId,
                title = title,
                channel = Channel(
                    id = channelId,
                    name = author,
                    avatarUrl = "https://picsum.photos/seed/$channelId/120/120",
                    subscriberCountText = ""
                ),
                durationSeconds = lengthSeconds,
                viewCount = viewCount,
                publishedTimeText = "",
                thumbnailUrl = bestThumb,
                description = description
            )
        } else null

        val streamingData = root["streamingData"]?.jsonObject
        val formats = streamingData?.get("formats")?.jsonArray.orEmpty()
        val adaptiveFormats = streamingData?.get("adaptiveFormats")?.jsonArray.orEmpty()
        val hlsUrl = streamingData?.get("hlsManifestUrl")?.jsonPrimitive?.contentOrNull

        val videoStreams = mutableListOf<VideoStream>()
        val audioStreams = mutableListOf<AudioStream>()

        formats.forEach { element ->
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull
            if (!url.isNullOrEmpty()) {
                val quality = obj["qualityLabel"]?.jsonPrimitive?.contentOrNull
                    ?: obj["quality"]?.jsonPrimitive?.contentOrNull ?: "360p"
                val mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val bitrate = obj["bitrate"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                videoStreams.add(
                    VideoStream(
                        url = url,
                        quality = quality,
                        format = if (mimeType.contains("mp4")) "mp4" else "webm",
                        bitrate = bitrate
                    )
                )
            }
        }

        adaptiveFormats.forEach { element ->
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull
            if (!url.isNullOrEmpty()) {
                val mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val bitrate = obj["bitrate"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

                if (mimeType.startsWith("audio/")) {
                    val quality = obj["audioQuality"]?.jsonPrimitive?.contentOrNull ?: "AUDIO"
                    audioStreams.add(
                        AudioStream(
                            url = url,
                            quality = quality,
                            format = if (mimeType.contains("mp4")) "m4a" else "webm",
                            bitrate = bitrate
                        )
                    )
                } else if (mimeType.startsWith("video/")) {
                    val quality = obj["qualityLabel"]?.jsonPrimitive?.contentOrNull
                        ?: obj["quality"]?.jsonPrimitive?.contentOrNull ?: "720p"
                    videoStreams.add(
                        VideoStream(
                            url = url,
                            quality = quality,
                            format = if (mimeType.contains("mp4")) "mp4" else "webm",
                            bitrate = bitrate
                        )
                    )
                }
            }
        }

        val streamInfo = StreamInfo(
            videoId = videoId,
            title = title,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            hlsUrl = hlsUrl,
            dashUrl = null
        )

        return Pair(video, streamInfo)
    }

    private fun parseVideoRenderer(renderer: JsonObject): Video? {
        val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = getText(renderer["title"]) ?: return null
        val channelName = getText(renderer["ownerText"])
            ?: getText(renderer["shortBylineText"])
            ?: "YouTube Creator"
        val channelId = renderer["ownerText"]?.jsonObject
            ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("navigationEndpoint")?.jsonObject
            ?.get("browseEndpoint")?.jsonObject
            ?.get("browseId")?.jsonPrimitive?.contentOrNull.orEmpty()

        val lengthText = getText(renderer["lengthText"])
        val durationSeconds = parseDurationSeconds(lengthText)
        val publishedTime = getText(renderer["publishedTimeText"]).orEmpty()
        val viewCountText = getText(renderer["viewCountText"]).orEmpty()
        val viewCount = parseViewCount(viewCountText)

        val thumbs = renderer["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
        val bestThumb = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return Video(
            id = videoId,
            title = title,
            channel = Channel(
                id = channelId,
                name = channelName,
                avatarUrl = "https://picsum.photos/seed/$channelId/120/120",
                subscriberCountText = ""
            ),
            durationSeconds = durationSeconds,
            viewCount = viewCount,
            publishedTimeText = publishedTime,
            thumbnailUrl = bestThumb,
            description = ""
        )
    }

    private fun findRenderers(element: JsonElement, targetKey: String, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey(targetKey)) {
                    element[targetKey]?.let { if (it is JsonObject) out.add(it) }
                }
                element.values.forEach { findRenderers(it, targetKey, out) }
            }
            is JsonArray -> {
                element.forEach { findRenderers(it, targetKey, out) }
            }
            else -> {}
        }
    }

    private fun getText(element: JsonElement?): String? {
        if (element == null) return null
        if (element is JsonObject) {
            element["simpleText"]?.jsonPrimitive?.contentOrNull?.let { return it }
            val runs = element["runs"]?.jsonArray
            if (!runs.isNullOrEmpty()) {
                return runs.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
            }
        }
        return null
    }

    private fun parseDurationSeconds(text: String?): Long {
        if (text == null) return 0L
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private fun parseViewCount(text: String): Long {
        val cleaned = text.lowercase().replace(",", "").replace(".", "").replace("views", "").trim()
        val num = cleaned.filter { it.isDigit() }.toLongOrNull() ?: 0L
        return when {
            text.contains("M", ignoreCase = true) -> (num * 1_000_000)
            text.contains("K", ignoreCase = true) -> (num * 1_000)
            else -> num
        }
    }
}
