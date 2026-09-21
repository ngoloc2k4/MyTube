package vn.lobie.mytube.ui.util

data class Chapter(
    val title: String,
    val timeMs: Long
)

object ChapterParser {
    // Matches formats like:
    // 00:00 Intro
    // 01:23 - The Beginning
    // 1:04:22 End Credits
    // [05:12] Topic
    // (12:34) Something
    private val TIMESTAMP_REGEX = Regex(
        """(?:^|\n)\s*[\[(]?(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[\])]?\s*[-–—:]?\s*([^\n\r]+)"""
    )

    fun parse(description: String?): List<Chapter> {
        if (description.isNullOrBlank()) return emptyList()

        val chapters = mutableListOf<Chapter>()
        val matches = TIMESTAMP_REGEX.findAll(description)

        for (match in matches) {
            val hoursStr = match.groupValues[1]
            val minutesStr = match.groupValues[2]
            val secondsStr = match.groupValues[3]
            val rawTitle = match.groupValues[4].trim()

            val hours = if (hoursStr.isNotEmpty()) hoursStr.toLongOrNull() ?: 0L else 0L
            val minutes = minutesStr.toLongOrNull() ?: continue
            val seconds = secondsStr.toLongOrNull() ?: continue

            val timeMs = (hours * 3600 + minutes * 60 + seconds) * 1000L
            // Filter out common non-chapter lines like links or credits if too long or empty
            val cleanTitle = rawTitle.replace(Regex("""^[-\s|:•]+"""), "").trim()
            if (cleanTitle.isNotEmpty() && cleanTitle.length < 120) {
                chapters.add(Chapter(title = cleanTitle, timeMs = timeMs))
            }
        }

        return chapters.distinctBy { it.timeMs }.sortedBy { it.timeMs }
    }

    fun getCurrentChapter(chapters: List<Chapter>, currentPositionMs: Long): Chapter? {
        if (chapters.isEmpty()) return null
        return chapters.lastOrNull { it.timeMs <= currentPositionMs } ?: chapters.firstOrNull()
    }
}
