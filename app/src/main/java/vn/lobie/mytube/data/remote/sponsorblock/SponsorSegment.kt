package vn.lobie.mytube.data.remote.sponsorblock

import kotlinx.serialization.Serializable

@Serializable
data class SponsorSegmentDto(
    val category: String = "sponsor",
    val actionType: String = "skip",
    val segment: List<Float> = emptyList(),
    val UUID: String = "",
    val videoDuration: Float = 0f
)

data class SponsorSegment(
    val category: String,
    val startMs: Long,
    val endMs: Long,
    val actionType: String = "skip",
    val uuid: String = ""
) {
    val durationSeconds: Long
        get() = ((endMs - startMs) / 1000L).coerceAtLeast(1L)
}
