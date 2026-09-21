package vn.lobie.mytube.domain.model

data class StreamInfo(
    val videoId: String,
    val title: String,
    val videoStreams: List<VideoStream> = emptyList(),
    val audioStreams: List<AudioStream> = emptyList(),
    val hlsUrl: String? = null,
    val dashUrl: String? = null,
    val source: String = ""
)

data class VideoStream(
    val url: String,
    val quality: String,
    val format: String,
    val bitrate: Long
)

data class AudioStream(
    val url: String,
    val quality: String,
    val format: String,
    val bitrate: Long
)
