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
