package vn.lobie.mytube.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InvidiousVideoDto(
    @SerialName("videoId") val videoId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("author") val author: String = "",
    @SerialName("authorId") val authorId: String = "",
    @SerialName("authorUrl") val authorUrl: String = "",
    @SerialName("videoThumbnails") val videoThumbnails: List<InvidiousThumbnailDto> = emptyList(),
    @SerialName("authorThumbnails") val authorThumbnails: List<InvidiousThumbnailDto> = emptyList(),
    @SerialName("description") val description: String = "",
    @SerialName("lengthSeconds") val lengthSeconds: Long = 0,
    @SerialName("viewCount") val viewCount: Long = 0,
    @SerialName("publishedText") val publishedText: String = "",
    @SerialName("formatStreams") val formatStreams: List<InvidiousFormatStreamDto> = emptyList(),
    @SerialName("adaptiveFormats") val adaptiveFormats: List<InvidiousAdaptiveFormatDto> = emptyList(),
    @SerialName("hlsUrl") val hlsUrl: String? = null,
    @SerialName("dashUrl") val dashUrl: String? = null
)

@Serializable
data class InvidiousThumbnailDto(
    @SerialName("quality") val quality: String = "",
    @SerialName("url") val url: String = "",
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0
)

@Serializable
data class InvidiousFormatStreamDto(
    @SerialName("url") val url: String = "",
    @SerialName("quality") val quality: String = "",
    @SerialName("qualityLabel") val qualityLabel: String = "",
    @SerialName("container") val container: String = "",
    @SerialName("bitrate") val bitrate: Long = 0
)

@Serializable
data class InvidiousAdaptiveFormatDto(
    @SerialName("url") val url: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("qualityLabel") val qualityLabel: String? = null,
    @SerialName("container") val container: String? = null,
    @SerialName("bitrate") val bitrate: Long = 0,
    @SerialName("resolution") val resolution: String? = null
)

@Serializable
data class InvidiousCommentsResponseDto(
    @SerialName("commentCount") val commentCount: Int = 0,
    @SerialName("videoId") val videoId: String = "",
    @SerialName("comments") val comments: List<InvidiousCommentDto> = emptyList()
)

@Serializable
data class InvidiousCommentDto(
    @SerialName("author") val author: String = "",
    @SerialName("authorThumbnails") val authorThumbnails: List<InvidiousThumbnailDto> = emptyList(),
    @SerialName("authorId") val authorId: String = "",
    @SerialName("authorUrl") val authorUrl: String = "",
    @SerialName("isEdited") val isEdited: Boolean = false,
    @SerialName("isPinned") val isPinned: Boolean = false,
    @SerialName("content") val content: String = "",
    @SerialName("contentHtml") val contentHtml: String = "",
    @SerialName("publishedText") val publishedText: String = "",
    @SerialName("likeCount") val likeCount: Long = 0,
    @SerialName("commentId") val commentId: String = "",
    @SerialName("authorIsChannelOwner") val authorIsChannelOwner: Boolean = false,
    @SerialName("replyCount") val replyCount: Int = 0
)

