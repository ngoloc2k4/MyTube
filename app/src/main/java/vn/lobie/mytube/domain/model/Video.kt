package vn.lobie.mytube.domain.model

data class Video(
    val id: String,
    val title: String,
    val channel: Channel,
    val durationSeconds: Long,
    val viewCount: Long,
    val publishedTimeText: String,
    val thumbnailUrl: String,
    val description: String = ""
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes % 60, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }

    val formattedViews: String
        get() {
            return when {
                viewCount >= 1_000_000 -> "%.1fM views".format(viewCount / 1_000_000.0)
                viewCount >= 1_000 -> "%.1fK views".format(viewCount / 1_000.0)
                else -> "$viewCount views"
            }
        }
}
