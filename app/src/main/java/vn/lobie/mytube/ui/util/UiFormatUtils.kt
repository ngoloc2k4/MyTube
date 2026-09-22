package vn.lobie.mytube.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vn.lobie.mytube.R

@Composable
fun formatViews(views: Long): String {
    val countStr = when {
        views >= 1_000_000_000 -> {
            val num = views / 1_000_000_000.0
            if (num >= 100.0 || (views % 1_000_000_000 == 0L)) {
                stringResource(R.string.views_billions, num).replace(".0", "").replace(",0", "")
            } else {
                stringResource(R.string.views_billions, num)
            }
        }
        views >= 1_000_000 -> {
            val num = views / 1_000_000.0
            if (num >= 100.0 || (views % 1_000_000 == 0L)) {
                stringResource(R.string.views_millions, num).replace(".0", "").replace(",0", "")
            } else {
                stringResource(R.string.views_millions, num)
            }
        }
        views >= 1_000 -> {
            val num = views / 1_000.0
            if (num >= 100.0 || (views % 1_000 == 0L)) {
                stringResource(R.string.views_thousands, num).replace(".0", "").replace(",0", "")
            } else {
                stringResource(R.string.views_thousands, num)
            }
        }
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
        count >= 1_000_000_000 -> {
            val num = count / 1_000_000_000.0
            if (num >= 100.0 || (count % 1_000_000_000 == 0L)) "%.0fB".format(java.util.Locale.US, num)
            else "%.1fB".format(java.util.Locale.US, num)
        }
        count >= 1_000_000 -> {
            val num = count / 1_000_000.0
            if (num >= 100.0 || (count % 1_000_000 == 0L)) "%.0fM".format(java.util.Locale.US, num)
            else "%.1fM".format(java.util.Locale.US, num)
        }
        count >= 1_000 -> {
            val num = count / 1_000.0
            if (num >= 100.0 || (count % 1_000 == 0L)) "%.0fK".format(java.util.Locale.US, num)
            else "%.1fK".format(java.util.Locale.US, num)
        }
        count > 0 -> "$count"
        else -> ""
    }
}
