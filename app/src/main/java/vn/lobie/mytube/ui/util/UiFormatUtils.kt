package vn.lobie.mytube.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vn.lobie.mytube.R

@Composable
fun formatViews(views: Long): String {
    val countStr = when {
        views >= 1_000_000 -> stringResource(R.string.views_millions, views / 1_000_000.0)
        views >= 1_000 -> stringResource(R.string.views_thousands, views / 1_000.0)
        views > 0 -> "$views"
        else -> "0"
    }
    return stringResource(R.string.views_format, countStr)
}

fun formatDuration(durationSeconds: Long): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatCompactNumber(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        count > 0 -> "$count"
        else -> ""
    }
}
