package vn.lobie.mytube.ui.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import vn.lobie.mytube.R
import vn.lobie.mytube.ui.util.formatViews

@OptIn(UnstableApi::class)
@Composable
fun FullPlayer(
    uiState: PlayerUiState,
    player: Player?,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit = {},
    onToggleLike: () -> Unit = {},
    onToggleSubscribe: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val video = uiState.currentVideo ?: return
    var controlsVisible by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var isSaved by remember(video.id) { mutableStateOf(false) }
    var isDescriptionExpanded by remember(video.id) { mutableStateOf(false) }

    // Intercept back button to collapse to mini-player
    BackHandler {
        onCollapse()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.minimize_player),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            // Video Player Surface
            VideoPlayerSurface(
                player = player,
                uiState = uiState,
                controlsVisible = controlsVisible,
                onToggleControls = { controlsVisible = !controlsVisible },
                onTogglePlayPause = onTogglePlayPause,
                onSeekBy = onSeekBy,
                onSetSpeed = onSetSpeed,
                onCollapse = onCollapse,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            // Seek Bar & Time Indicators
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Slider(
                    value = uiState.currentPositionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..uiState.durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(uiState.currentPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(uiState.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Video Details & Description Scrollable section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${formatViews(video.viewCount)} • ${video.publishedTimeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Channel Info Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = video.channel.avatarUrl,
                        contentDescription = video.channel.name,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.channel.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (video.channel.subscriberCountText.isNotEmpty()) {
                            Text(
                                text = video.channel.subscriberCountText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onToggleSubscribe,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface,
                            contentColor = if (uiState.isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = stringResource(if (uiState.isSubscribed) R.string.subscribed_button else R.string.subscribe_button),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Bar: Like, Share, Save, Download
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilledTonalButton(
                            onClick = onToggleLike,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (uiState.isLiked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (uiState.isLiked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = if (uiState.isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = stringResource(if (uiState.isLiked) R.string.action_liked else R.string.action_like),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isLiked) stringResource(R.string.action_liked) else stringResource(R.string.action_like),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    item {
                        FilledTonalButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://youtu.be/${video.id}")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_video_title))
                                context.startActivity(shareIntent)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.action_share), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    item {
                        FilledTonalButton(
                            onClick = { isSaved = !isSaved },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(if (isSaved) R.string.action_saved else R.string.action_save),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaved) stringResource(R.string.action_saved) else stringResource(R.string.action_save),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    item {
                        FilledTonalButton(
                            onClick = { /* Future: Offline Download */ },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(R.string.action_download),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.action_download), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (video.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                            .animateContentSize()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = video.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(if (isDescriptionExpanded) R.string.show_less else R.string.show_more),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerSurface(
    player: Player?,
    uiState: PlayerUiState,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackIsForward by remember { mutableStateOf(true) }
    var isSpeedBoosted by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 50f) {
                            onCollapse()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val isForward = offset.x >= size.width / 2
                        seekFeedbackIsForward = isForward
                        if (isForward) {
                            onSeekBy(10_000L)
                            seekFeedbackText = "+10s"
                        } else {
                            onSeekBy(-10_000L)
                            seekFeedbackText = "-10s"
                        }
                        coroutineScope.launch {
                            delay(650)
                            seekFeedbackText = null
                        }
                    },
                    onLongPress = {
                        isSpeedBoosted = true
                        onSetSpeed(2.0f)
                        coroutineScope.launch {
                            delay(2500)
                            isSpeedBoosted = false
                            onSetSpeed(1.0f)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        // Seek Feedback Indicator (+10s / -10s)
        if (seekFeedbackText != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = if (seekFeedbackIsForward) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (seekFeedbackIsForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = seekFeedbackText.orEmpty(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Speed Boost Indicator (2X Speed)
        if (isSpeedBoosted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "2X Speed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible || uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { onSeekBy(-10_000L) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = stringResource(R.string.rewind_10s),
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(if (uiState.isPlaying) R.string.pause_action else R.string.play_action),
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        IconButton(
                            onClick = { onSeekBy(10_000L) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = stringResource(R.string.forward_10s),
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMinutes = minutes % 60
        "%d:%02d:%02d".format(hours, remMinutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
