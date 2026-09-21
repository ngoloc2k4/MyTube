package vn.lobie.mytube.ui.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import vn.lobie.mytube.ui.util.formatCompactNumber
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.lobie.mytube.R
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.ui.components.CompactVideoCard
import vn.lobie.mytube.ui.components.VideoCard
import vn.lobie.mytube.ui.util.Chapter
import vn.lobie.mytube.ui.util.formatViews
import kotlin.math.abs

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
    onPlayNext: () -> Unit = {},
    onPlayPrevious: () -> Unit = {},
    onSelectQuality: (String) -> Unit = {},
    onToggleAudioOnly: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onToggleSubscribe: () -> Unit = {},
    onSelectVideo: (Video) -> Unit = {},
    onEnterPip: () -> Unit = {},
    onToggleAutoPlay: () -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onToggleLoopMode: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleResizeMode: () -> Unit = {},
    onSetSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onSeekToChapter: (Chapter) -> Unit = {},
    onToggleSponsorBlock: () -> Unit = {},
    onUnskipSponsor: () -> Unit = {},
    onDismissSponsorNotice: () -> Unit = {},
    onSetDoubleTapSeekSeconds: (Int) -> Unit = {},
    onToggleSubtitles: () -> Unit = {},
    onSelectSubtitle: (String?) -> Unit = {},
    onSetAbLoopA: () -> Unit = {},
    onSetAbLoopB: () -> Unit = {},
    onClearAbLoop: () -> Unit = {},
    onSetSubtitleFontSize: (Float) -> Unit = {},
    onSetSubtitleBgColor: (SubtitleBgColor) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val video = uiState.currentVideo ?: return
    var controlsVisible by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var isSaved by remember(video.id) { mutableStateOf(false) }
    var showSeekDurationDialog by remember { mutableStateOf(false) }
    var showSubtitlesDialog by remember { mutableStateOf(false) }
    var showAbLoopDialog by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember(video.id) { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }
    var isQueueExpanded by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isTablet = vn.lobie.mytube.ui.theme.LocalIsTablet.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val useDualPane = isTablet && isLandscape

    // Manage screen orientation for fullscreen mode
    DisposableEffect(uiState.isFullscreen) {
        val activity = context as? Activity
        if (uiState.isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Intercept back button: exit fullscreen first, or collapse to miniplayer
    BackHandler {
        if (uiState.isFullscreen) {
            onToggleFullscreen()
        } else {
            onCollapse()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (uiState.isFullscreen) {
            // In Fullscreen mode, the video fills the entire screen
            Box(modifier = Modifier.fillMaxSize()) {
                VideoPlayerSurface(
                    player = player,
                    uiState = uiState,
                    controlsVisible = controlsVisible,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekBy = onSeekBy,
                    onSetSpeed = onSetSpeed,
                    onPlayNext = onPlayNext,
                    onPlayPrevious = onPlayPrevious,
                    onToggleFullscreen = onToggleFullscreen,
                    onOpenQualityDialog = { showQualityDialog = true },
                    onOpenSpeedDialog = { showSpeedDialog = true },
                    onCollapse = onCollapse,
                    onSeek = onSeek,
                    onEnterPip = onEnterPip,
                    onToggleAutoPlay = onToggleAutoPlay,
                    onToggleLoopMode = onToggleLoopMode,
                    onToggleShuffle = onToggleShuffle,
                    onToggleResizeMode = onToggleResizeMode,
                    onOpenSleepTimerDialog = { showSleepTimerDialog = true },
                    onOpenChaptersDialog = { showChaptersDialog = true },
                    onOpenSubtitlesDialog = { showSubtitlesDialog = true },
                    onUnskipSponsor = onUnskipSponsor,
                    onDismissSponsorNotice = onDismissSponsorNotice,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (useDualPane) {
            // Tablet Dual-Pane Landscape/Wide screen layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Left Column: Player & Video Info (weight 0.58f)
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.fullscreen_toggle),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                        onPlayNext = onPlayNext,
                        onPlayPrevious = onPlayPrevious,
                        onToggleFullscreen = onToggleFullscreen,
                        onOpenQualityDialog = { showQualityDialog = true },
                        onOpenSpeedDialog = { showSpeedDialog = true },
                        onCollapse = onCollapse,
                        onSeek = onSeek,
                        onEnterPip = onEnterPip,
                        onToggleAutoPlay = onToggleAutoPlay,
                        onToggleLoopMode = onToggleLoopMode,
                        onToggleShuffle = onToggleShuffle,
                        onToggleResizeMode = onToggleResizeMode,
                        onOpenSleepTimerDialog = { showSleepTimerDialog = true },
                        onOpenChaptersDialog = { showChaptersDialog = true },
                        onOpenSubtitlesDialog = { showSubtitlesDialog = true },
                        onUnskipSponsor = onUnskipSponsor,
                        onDismissSponsorNotice = onDismissSponsorNotice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )

                    // Seek Bar & Time Indicators
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(uiState.currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (uiState.chapters.isNotEmpty()) {
                                val chapTitle = uiState.currentChapter?.title ?: stringResource(R.string.chapters_title)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.clickable { showChaptersDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FormatListBulleted,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = chapTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 160.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = formatTime(uiState.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Channel Info Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = video.channel.avatarUrl,
                            contentDescription = video.channel.name,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.channel.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Bar
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Surface(
                                shape = CircleShape,
                                color = if (uiState.isLiked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (uiState.isLiked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clip(CircleShape)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { onToggleLike() }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        val likes = uiState.likesCount ?: 0L
                                        val likeText = if (likes > 0L) formatCompactNumber(likes) else if (uiState.isLiked) stringResource(R.string.action_liked) else stringResource(R.string.action_like)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = likeText, style = MaterialTheme.typography.labelMedium)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(18.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ThumbDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        val dislikes = uiState.dislikesCount ?: 0L
                                        if (dislikes > 0L) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = formatCompactNumber(dislikes),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = onToggleAudioOnly,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (uiState.isAudioOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (uiState.isAudioOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isAudioOnly) "Audio ON" else stringResource(R.string.audio_mode),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = { showQualityDialog = true },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = uiState.selectedQuality, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = { showSpeedDialog = true },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "${uiState.playbackSpeed}x", style = MaterialTheme.typography.labelMedium)
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
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                    contentDescription = null,
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
                                onClick = onEnterPip,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "PiP",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "PiP", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = onToggleLoopMode,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (uiState.loopMode != LoopMode.OFF) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (uiState.loopMode != LoopMode.OFF) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = when (uiState.loopMode) {
                                        LoopMode.ONE -> Icons.Default.RepeatOne
                                        else -> Icons.Default.Repeat
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (uiState.loopMode) {
                                        LoopMode.OFF -> stringResource(R.string.loop_mode_off)
                                        LoopMode.ONE -> stringResource(R.string.loop_mode_one)
                                        LoopMode.ALL -> stringResource(R.string.loop_mode_all)
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = onToggleShuffle,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (uiState.isShuffleEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (uiState.isShuffleEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.shuffle_mode),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = { showSleepTimerDialog = true },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (uiState.sleepTimerRemainingSeconds != null || uiState.isSleepTimerAtEnd) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (uiState.sleepTimerRemainingSeconds != null || uiState.isSleepTimerAtEnd) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val timerText = if (uiState.sleepTimerRemainingSeconds != null) {
                                    val mins = (uiState.sleepTimerRemainingSeconds + 59) / 60
                                    "${mins}m"
                                } else if (uiState.isSleepTimerAtEnd) {
                                    "End"
                                } else {
                                    stringResource(R.string.sleep_timer)
                                }
                                Text(text = timerText, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (uiState.chapters.isNotEmpty()) {
                            item {
                                FilledTonalButton(
                                    onClick = { showChaptersDialog = true },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "${stringResource(R.string.chapters_title)} (${uiState.chapters.size})", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = onToggleSponsorBlock,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (uiState.isSponsorBlockEnabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (uiState.isSponsorBlockEnabled) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isSponsorBlockEnabled) stringResource(R.string.sponsorblock_title) else "${stringResource(R.string.sponsorblock_title)}: Off",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = { showSeekDurationDialog = true },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoubleArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${uiState.doubleTapSeekSeconds}s",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        item {
                            FilledTonalButton(
                                onClick = { showSubtitlesDialog = true },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (uiState.isSubtitlesEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (uiState.isSubtitlesEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isSubtitlesEnabled) (uiState.selectedSubtitle ?: stringResource(R.string.subtitles_on)) else stringResource(R.string.subtitles_off),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        item {
                            val isLoopActive = uiState.abLoopStartMs != null || uiState.abLoopEndMs != null
                            FilledTonalButton(
                                onClick = { showAbLoopDialog = true },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isLoopActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (isLoopActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.abLoopStartMs != null && uiState.abLoopEndMs != null) {
                                        "Loop ${formatTime(uiState.abLoopStartMs)} - ${formatTime(uiState.abLoopEndMs)}"
                                    } else if (uiState.abLoopStartMs != null) {
                                        "A: ${formatTime(uiState.abLoopStartMs)}"
                                    } else {
                                        stringResource(R.string.ab_loop_title)
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    // Description Box
                    if (video.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Right Column: Vertical Playlist Queue & Related videos (weight 0.42f)
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (uiState.queue.size > 1) {
                        VerticalPlaylistQueue(
                            queue = uiState.queue,
                            currentIndex = uiState.currentQueueIndex,
                            isExpanded = isQueueExpanded,
                            onToggleExpand = { isQueueExpanded = !isQueueExpanded },
                            onSelectVideo = onSelectVideo,
                            onRemoveFromQueue = onRemoveFromQueue,
                            onMoveQueueItem = onMoveQueueItem,
                            onClearQueue = onClearQueue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    RelatedVideosSection(
                        relatedVideos = uiState.relatedVideos,
                        isLoading = uiState.isLoadingRelated,
                        onSelectVideo = onSelectVideo,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // Phone single-column layout with bottom-docked collapsible vertical queue
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .widthIn(max = if (isTablet) 800.dp else androidx.compose.ui.unit.Dp.Unspecified)
                ) {
                    // Top Header Bar
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.fullscreen_toggle),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                        onPlayNext = onPlayNext,
                        onPlayPrevious = onPlayPrevious,
                        onToggleFullscreen = onToggleFullscreen,
                        onOpenQualityDialog = { showQualityDialog = true },
                        onOpenSpeedDialog = { showSpeedDialog = true },
                        onCollapse = onCollapse,
                        onSeek = onSeek,
                        onEnterPip = onEnterPip,
                        onToggleAutoPlay = onToggleAutoPlay,
                        onToggleLoopMode = onToggleLoopMode,
                        onToggleShuffle = onToggleShuffle,
                        onToggleResizeMode = onToggleResizeMode,
                        onOpenSleepTimerDialog = { showSleepTimerDialog = true },
                        onOpenChaptersDialog = { showChaptersDialog = true },
                        onOpenSubtitlesDialog = { showSubtitlesDialog = true },
                        onUnskipSponsor = onUnskipSponsor,
                        onDismissSponsorNotice = onDismissSponsorNotice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )

                    // Seek Bar & Time Indicators
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(uiState.currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (uiState.chapters.isNotEmpty()) {
                                val chapTitle = uiState.currentChapter?.title ?: stringResource(R.string.chapters_title)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.clickable { showChaptersDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FormatListBulleted,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = chapTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 160.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = formatTime(uiState.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Video Details, Actions & Related Videos
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
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.channel.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
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

                        // Action Bar: Like, Audio Mode, Quality, Speed, Share, Save
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Surface(
                                    shape = CircleShape,
                                    color = if (uiState.isLiked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (uiState.isLiked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.clip(CircleShape)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable { onToggleLike() }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                                contentDescription = stringResource(if (uiState.isLiked) R.string.action_liked else R.string.action_like),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            val likes = uiState.likesCount ?: 0L
                                            val likeText = if (likes > 0L) formatCompactNumber(likes) else if (uiState.isLiked) stringResource(R.string.action_liked) else stringResource(R.string.action_like)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = likeText, style = MaterialTheme.typography.labelMedium)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(18.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ThumbDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            val dislikes = uiState.dislikesCount ?: 0L
                                            if (dislikes > 0L) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = formatCompactNumber(dislikes),
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                FilledTonalButton(
                                    onClick = onToggleAudioOnly,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (uiState.isAudioOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (uiState.isAudioOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = stringResource(R.string.audio_mode),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.isAudioOnly) "Audio ON" else stringResource(R.string.audio_mode),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            item {
                                FilledTonalButton(
                                    onClick = { showQualityDialog = true },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HighQuality,
                                        contentDescription = stringResource(R.string.video_quality),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = uiState.selectedQuality,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            item {
                                FilledTonalButton(
                                    onClick = { showSpeedDialog = true },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = stringResource(R.string.playback_speed),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${uiState.playbackSpeed}x",
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
                                    onClick = onEnterPip,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "PiP",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "PiP", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            item {
                                FilledTonalButton(
                                    onClick = onToggleLoopMode,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (uiState.loopMode != LoopMode.OFF) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (uiState.loopMode != LoopMode.OFF) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = when (uiState.loopMode) {
                                            LoopMode.ONE -> Icons.Default.RepeatOne
                                            else -> Icons.Default.Repeat
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (uiState.loopMode) {
                                            LoopMode.OFF -> stringResource(R.string.loop_mode_off)
                                            LoopMode.ONE -> stringResource(R.string.loop_mode_one)
                                            LoopMode.ALL -> stringResource(R.string.loop_mode_all)
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            item {
                                FilledTonalButton(
                                    onClick = onToggleShuffle,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (uiState.isShuffleEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (uiState.isShuffleEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.shuffle_mode),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            item {
                                FilledTonalButton(
                                    onClick = { showSleepTimerDialog = true },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (uiState.sleepTimerRemainingSeconds != null || uiState.isSleepTimerAtEnd) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (uiState.sleepTimerRemainingSeconds != null || uiState.isSleepTimerAtEnd) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val timerText = if (uiState.sleepTimerRemainingSeconds != null) {
                                        val mins = (uiState.sleepTimerRemainingSeconds + 59) / 60
                                        "${mins}m"
                                    } else if (uiState.isSleepTimerAtEnd) {
                                        "End"
                                    } else {
                                        stringResource(R.string.sleep_timer)
                                    }
                                    Text(text = timerText, style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (uiState.chapters.isNotEmpty()) {
                                item {
                                    FilledTonalButton(
                                        onClick = { showChaptersDialog = true },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "${stringResource(R.string.chapters_title)} (${uiState.chapters.size})", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            item {
                                FilledTonalButton(
                                    onClick = onToggleSponsorBlock,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (uiState.isSponsorBlockEnabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = if (uiState.isSponsorBlockEnabled) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.isSponsorBlockEnabled) stringResource(R.string.sponsorblock_title) else "${stringResource(R.string.sponsorblock_title)}: Off",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                                item {
                                    FilledTonalButton(
                                        onClick = { showSeekDurationDialog = true },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DoubleArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${uiState.doubleTapSeekSeconds}s",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }

                                item {
                                    FilledTonalButton(
                                        onClick = { showSubtitlesDialog = true },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (uiState.isSubtitlesEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = if (uiState.isSubtitlesEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ClosedCaption,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (uiState.isSubtitlesEnabled) (uiState.selectedSubtitle ?: stringResource(R.string.subtitles_on)) else stringResource(R.string.subtitles_off),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }

                                item {
                                    val isLoopActive = uiState.abLoopStartMs != null || uiState.abLoopEndMs != null
                                    FilledTonalButton(
                                        onClick = { showAbLoopDialog = true },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isLoopActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = if (isLoopActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Repeat,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (uiState.abLoopStartMs != null && uiState.abLoopEndMs != null) {
                                                "Loop ${formatTime(uiState.abLoopStartMs)} - ${formatTime(uiState.abLoopEndMs)}"
                                            } else if (uiState.abLoopStartMs != null) {
                                                "A: ${formatTime(uiState.abLoopStartMs)}"
                                            } else {
                                                stringResource(R.string.ab_loop_title)
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }

                        // Expandable Description Box
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

                        // Related Videos / Releases Section
                        Spacer(modifier = Modifier.height(16.dp))
                        RelatedVideosSection(
                            relatedVideos = uiState.relatedVideos,
                            isLoading = uiState.isLoadingRelated,
                            onSelectVideo = onSelectVideo
                        )
                        Spacer(modifier = Modifier.height(if (uiState.queue.size > 1) 76.dp else 16.dp))
                    }
                }

                // Bottom Docked Vertical Queue on Phone
                if (uiState.queue.size > 1) {
                    VerticalPlaylistQueue(
                        queue = uiState.queue,
                        currentIndex = uiState.currentQueueIndex,
                        isExpanded = isQueueExpanded,
                        onToggleExpand = { isQueueExpanded = !isQueueExpanded },
                        onSelectVideo = onSelectVideo,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onMoveQueueItem = onMoveQueueItem,
                        onClearQueue = onClearQueue,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = if (isTablet) 800.dp else androidx.compose.ui.unit.Dp.Unspecified)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // Quality Selection Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.video_quality)) },
            text = {
                Column {
                    uiState.availableQualities.forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectQuality(q)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = q == uiState.selectedQuality,
                                onClick = {
                                    onSelectQuality(q)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = q, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(stringResource(R.string.clear_action))
                }
            }
        )
    }

    // Speed Selection Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(R.string.playback_speed)) },
            text = {
                Column {
                    speeds.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetSpeed(s)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = s == uiState.playbackSpeed,
                                onClick = {
                                    onSetSpeed(s)
                                    showSpeedDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (s == 1.0f) "1.0x (Normal)" else "${s}x",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.clear_action))
                }
            }
        )
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text(stringResource(R.string.sleep_timer)) },
            text = {
                Column {
                    if (uiState.sleepTimerRemainingSeconds != null) {
                        val mins = (uiState.sleepTimerRemainingSeconds + 59) / 60
                        Text(
                            text = "Đang hẹn giờ: còn khoảng $mins phút",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        FilledTonalButton(
                            onClick = {
                                onCancelSleepTimer()
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.sleep_timer_off))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (uiState.isSleepTimerAtEnd) {
                        Text(
                            text = "Đang hẹn giờ: Khi hết video này sẽ dừng",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        FilledTonalButton(
                            onClick = {
                                onCancelSleepTimer()
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.sleep_timer_off))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val options = listOf(
                        15 to stringResource(R.string.sleep_timer_15m),
                        30 to stringResource(R.string.sleep_timer_30m),
                        45 to stringResource(R.string.sleep_timer_45m),
                        60 to stringResource(R.string.sleep_timer_60m),
                        -1 to stringResource(R.string.sleep_timer_end_of_video)
                    )

                    options.forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (minutes == -1) Icons.Default.StopCircle else Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text(stringResource(R.string.clear_action))
                }
            }
        )
    }

    // Chapters Selection Dialog
    if (showChaptersDialog && uiState.chapters.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showChaptersDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatListBulleted,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${stringResource(R.string.chapters_title)} (${uiState.chapters.size})")
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(uiState.chapters) { chapter ->
                        val isCurrent = chapter == uiState.currentChapter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onSeekToChapter(chapter)
                                    showChaptersDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = formatTime(chapter.timeMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChaptersDialog = false }) {
                    Text(stringResource(R.string.clear_action))
                }
            }
        )
    }

    if (showSeekDurationDialog) {
        SeekDurationDialog(
            currentSeconds = uiState.doubleTapSeekSeconds,
            onSelect = onSetDoubleTapSeekSeconds,
            onDismiss = { showSeekDurationDialog = false }
        )
    }

    if (showSubtitlesDialog) {
        SubtitleSelectionDialog(
            availableSubtitles = uiState.availableSubtitles,
            selectedSubtitle = uiState.selectedSubtitle,
            isSubtitlesEnabled = uiState.isSubtitlesEnabled,
            currentFontSize = uiState.subtitleFontSize,
            onSetFontSize = onSetSubtitleFontSize,
            currentBgColor = uiState.subtitleBgColor,
            onSetBgColor = onSetSubtitleBgColor,
            onSelect = onSelectSubtitle,
            onDismiss = { showSubtitlesDialog = false }
        )
    }

    if (showAbLoopDialog) {
        AbLoopDialog(
            startMs = uiState.abLoopStartMs,
            endMs = uiState.abLoopEndMs,
            currentPositionMs = uiState.currentPositionMs,
            onSetA = onSetAbLoopA,
            onSetB = onSetAbLoopB,
            onClear = onClearAbLoop,
            onDismiss = { showAbLoopDialog = false }
        )
    }
}

@Composable
private fun SubtitleSelectionDialog(
    availableSubtitles: List<String>,
    selectedSubtitle: String?,
    isSubtitlesEnabled: Boolean,
    currentFontSize: Float = 1.0f,
    onSetFontSize: (Float) -> Unit = {},
    currentBgColor: SubtitleBgColor = SubtitleBgColor.BLACK_TRANSLUCENT,
    onSetBgColor: (SubtitleBgColor) -> Unit = {},
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subtitles_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(null)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.subtitles_off),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (!isSubtitlesEnabled) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isSubtitlesEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (!isSubtitlesEnabled) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (availableSubtitles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_subtitles_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    availableSubtitles.forEach { sub ->
                        val isSelected = isSubtitlesEnabled && (selectedSubtitle == sub)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(sub)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = stringResource(R.string.subtitle_size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sizes = listOf(
                        0.75f to stringResource(R.string.subtitle_size_small),
                        1.0f to stringResource(R.string.subtitle_size_medium),
                        1.35f to stringResource(R.string.subtitle_size_large)
                    )
                    sizes.forEach { (scale, label) ->
                        val isCurrentSize = (currentFontSize == scale)
                        FilterChip(
                            selected = isCurrentSize,
                            onClick = { onSetFontSize(scale) },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Màu nền phụ đề",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubtitleBgColor.entries.forEach { bgOption ->
                        val isCurrent = (currentBgColor == bgOption)
                        FilterChip(
                            selected = isCurrent,
                            onClick = { onSetBgColor(bgOption) },
                            label = { Text(bgOption.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}

@Composable
private fun AbLoopDialog(
    startMs: Long?,
    endMs: Long?,
    currentPositionMs: Long,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ab_loop_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.ab_loop_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "${stringResource(R.string.current_time)}: ${formatTime(currentPositionMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = stringResource(R.string.point_a_label), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (startMs != null) formatTime(startMs) else stringResource(R.string.not_set),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(onClick = onSetA) {
                        Text(stringResource(R.string.set_point_a))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = stringResource(R.string.point_b_label), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (endMs != null) formatTime(endMs) else stringResource(R.string.not_set),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = onSetB,
                        enabled = startMs != null && currentPositionMs > startMs
                    ) {
                        Text(stringResource(R.string.set_point_b))
                    }
                }

                if (startMs != null || endMs != null) {
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.clear_loop))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}

@Composable
private fun SeekDurationDialog(
    currentSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(5, 10, 15, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.seek_duration)) },
        text = {
            Column {
                options.forEach { sec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(sec)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = when (sec) {
                                5 -> stringResource(R.string.seconds_5)
                                10 -> stringResource(R.string.seconds_10)
                                15 -> stringResource(R.string.seconds_15)
                                else -> stringResource(R.string.seconds_30)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (sec == currentSeconds) FontWeight.Bold else FontWeight.Normal,
                            color = if (sec == currentSeconds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (sec == currentSeconds) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
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
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenQualityDialog: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onCollapse: () -> Unit,
    onSeek: (Long) -> Unit,
    onEnterPip: () -> Unit = {},
    onToggleAutoPlay: () -> Unit = {},
    onToggleLoopMode: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleResizeMode: () -> Unit = {},
    onOpenSleepTimerDialog: () -> Unit = {},
    onOpenChaptersDialog: () -> Unit = {},
    onOpenSubtitlesDialog: () -> Unit = {},
    onUnskipSponsor: () -> Unit = {},
    onDismissSponsorNotice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackIsForward by remember { mutableStateOf(true) }
    var isSpeedBoosted by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var gestureIndicatorIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gestureIndicatorPercent by remember { mutableStateOf<Float?>(null) }
    var gestureIndicatorJob by remember { mutableStateOf<Job?>(null) }
    var isDraggingLeft by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val isForward = offset.x >= size.width / 2
                        seekFeedbackIsForward = isForward
                        val sec = uiState.doubleTapSeekSeconds
                        val delta = sec * 1000L
                        if (isForward) {
                            onSeekBy(delta)
                            seekFeedbackText = "+${sec}s"
                        } else {
                            onSeekBy(-delta)
                            seekFeedbackText = "-${sec}s"
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
                            onSetSpeed(uiState.playbackSpeed)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDraggingLeft = offset.x < size.width / 2
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (!uiState.isFullscreen && dragAmount.y > 60f && abs(dragAmount.x) < 30f) {
                            onCollapse()
                            return@detectDragGestures
                        }

                        val deltaY = -dragAmount.y
                        if (isDraggingLeft) {
                            activity?.let { act ->
                                val layoutParams = act.window.attributes
                                var currentBrightness = layoutParams.screenBrightness
                                if (currentBrightness < 0f) currentBrightness = 0.5f
                                val newBrightness = (currentBrightness + deltaY / 400f).coerceIn(0.01f, 1.0f)
                                layoutParams.screenBrightness = newBrightness
                                act.window.attributes = layoutParams

                                val percent = (newBrightness * 100).toInt()
                                gestureIndicatorText = "$percent%"
                                gestureIndicatorPercent = newBrightness
                                gestureIndicatorIcon = when {
                                    newBrightness > 0.6f -> Icons.Default.BrightnessHigh
                                    newBrightness > 0.3f -> Icons.Default.BrightnessMedium
                                    else -> Icons.Default.BrightnessLow
                                }
                                gestureIndicatorJob?.cancel()
                                gestureIndicatorJob = coroutineScope.launch {
                                    delay(1200)
                                    gestureIndicatorText = null
                                    gestureIndicatorIcon = null
                                    gestureIndicatorPercent = null
                                }
                            }
                        } else {
                            audioManager?.let { am ->
                                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val deltaStep = (deltaY / 30f).toInt()
                                if (deltaStep != 0) {
                                    val newVol = (currentVol + deltaStep).coerceIn(0, maxVolume)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    val percent = ((newVol.toFloat() / maxVolume) * 100).toInt()
                                    gestureIndicatorText = "$percent%"
                                    gestureIndicatorPercent = newVol.toFloat() / maxVolume
                                    gestureIndicatorIcon = when {
                                        newVol == 0 -> Icons.Default.VolumeOff
                                        newVol < maxVolume / 2 -> Icons.Default.VolumeDown
                                        else -> Icons.Default.VolumeUp
                                    }
                                    gestureIndicatorJob?.cancel()
                                    gestureIndicatorJob = coroutineScope.launch {
                                        delay(1200)
                                        gestureIndicatorText = null
                                        gestureIndicatorIcon = null
                                        gestureIndicatorPercent = null
                                    }
                                }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isAudioOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF23202A), Color(0xFF0F0E13))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = uiState.currentVideo?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chế độ chỉ âm thanh (Tiết kiệm pin)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            // ExoPlayer View with reactive resizeMode (FIT / ZOOM)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode = when (uiState.resizeMode) {
                            ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        val captionStyle = androidx.media3.ui.CaptionStyleCompat(
                            uiState.subtitleBgColor.foregroundInt,
                            uiState.subtitleBgColor.colorInt,
                            android.graphics.Color.TRANSPARENT,
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                        subtitleView?.setStyle(captionStyle)
                        subtitleView?.setFractionalTextSize(0.0533f * uiState.subtitleFontSize)
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = when (uiState.resizeMode) {
                        ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    val captionStyle = androidx.media3.ui.CaptionStyleCompat(
                        uiState.subtitleBgColor.foregroundInt,
                        uiState.subtitleBgColor.colorInt,
                        android.graphics.Color.TRANSPARENT,
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null
                    )
                    view.subtitleView?.setStyle(captionStyle)
                    view.subtitleView?.setFractionalTextSize(0.0533f * uiState.subtitleFontSize)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

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

        // Brightness & Volume HUD Indicator
        if (gestureIndicatorText != null && gestureIndicatorIcon != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = gestureIndicatorIcon!!,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = gestureIndicatorText.orEmpty(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (gestureIndicatorPercent != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { gestureIndicatorPercent!! },
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }

        // Floating SponsorBlock Auto-Skip Notice
        val lastSkipped = uiState.lastSkippedSegment
        AnimatedVisibility(
            visible = lastSkipped != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (controlsVisible) 76.dp else 24.dp)
        ) {
            Surface(
                color = Color(0xEE1E1E1E),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D166)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color(0xFF00D166),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.sponsor_skipped),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = onUnskipSponsor,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.unskip),
                            color = Color(0xFF00D166),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismissSponsorNotice,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.dismiss),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        LaunchedEffect(lastSkipped) {
            if (lastSkipped != null) {
                delay(4000)
                onDismissSponsorNotice()
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
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top controls row inside overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Autoplay toggle button
                    IconButton(onClick = onToggleAutoPlay) {
                        Icon(
                            imageVector = if (uiState.isAutoPlayEnabled) Icons.Default.PlayCircle else Icons.Default.PauseCircleOutline,
                            contentDescription = "Tự động phát",
                            tint = if (uiState.isAutoPlayEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // PiP button
                    IconButton(onClick = onEnterPip) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Hình trong hình (PiP)",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Zoom / Aspect Ratio button
                    IconButton(onClick = onToggleResizeMode) {
                        Icon(
                            imageVector = if (uiState.resizeMode == ResizeMode.ZOOM) Icons.Default.CropFree else Icons.Default.AspectRatio,
                            contentDescription = stringResource(R.string.aspect_ratio),
                            tint = if (uiState.resizeMode == ResizeMode.ZOOM) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Sleep Timer button
                    IconButton(onClick = onOpenSleepTimerDialog) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = stringResource(R.string.sleep_timer),
                                tint = if (uiState.sleepTimerRemainingSeconds != null || uiState.isSleepTimerAtEnd) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Subtitles (CC) button
                    IconButton(onClick = onOpenSubtitlesDialog) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = stringResource(R.string.subtitles),
                            tint = if (uiState.isSubtitlesEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    val sourceName = uiState.currentSourceName ?: "Auto"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (sourceName == "Fallback") Color.Red else Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                "[$sourceName · ${uiState.selectedQuality}]",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                    // Quality indicator chip
                    AssistChip(
                        onClick = onOpenQualityDialog,
                        label = { Text(uiState.selectedQuality, fontSize = 11.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Speed indicator chip
                    AssistChip(
                        onClick = onOpenSpeedDialog,
                        label = { Text("${uiState.playbackSpeed}x", fontSize = 11.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Fullscreen toggle button
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(
                            imageVector = if (uiState.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.fullscreen_toggle),
                            tint = Color.White
                        )
                    }
                }

                // Center playback controls: Loop | SkipPrevious | Play/Pause | SkipNext | Shuffle
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                    ) {
                        // Loop button
                        IconButton(
                            onClick = onToggleLoopMode,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = when (uiState.loopMode) {
                                    LoopMode.ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = stringResource(R.string.loop_mode),
                                tint = if (uiState.loopMode != LoopMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Previous Video Button
                        IconButton(
                            onClick = onPlayPrevious,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.skip_previous),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Play/Pause Button
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(if (uiState.isPlaying) R.string.pause_action else R.string.play_action),
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Next Video Button
                        IconButton(
                            onClick = onPlayNext,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.skip_next),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Shuffle button
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = stringResource(R.string.shuffle_mode),
                                tint = if (uiState.isShuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // In Fullscreen mode: show seek bar at the bottom inside the overlay
                if (uiState.isFullscreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(uiState.currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )

                            if (uiState.chapters.isNotEmpty()) {
                                val chapTitle = uiState.currentChapter?.title ?: stringResource(R.string.chapters_title)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable { onOpenChaptersDialog() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FormatListBulleted,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = chapTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 200.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = formatTime(uiState.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
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

@Composable
fun VerticalPlaylistQueue(
    queue: List<Video>,
    currentIndex: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectVideo: (Video) -> Unit,
    onRemoveFromQueue: (Int) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.queue_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text("${currentIndex + 1} / ${queue.size}")
                            }
                        }
                        if (!isExpanded && currentIndex + 1 < queue.size) {
                            Text(
                                text = "Tiếp: ${queue[currentIndex + 1].title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExpanded) {
                        TextButton(
                            onClick = onClearQueue,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.clear_queue),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded vertical list
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(queue) { idx, qVideo ->
                            val isCurrent = idx == currentIndex
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CompactVideoCard(
                                        video = qVideo,
                                        onClick = { onSelectVideo(qVideo) },
                                        isHighlighted = isCurrent
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (idx > 0) {
                                        IconButton(
                                            onClick = { onMoveQueueItem(idx, idx - 1) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = stringResource(R.string.move_up),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (idx < queue.size - 1) {
                                        IconButton(
                                            onClick = { onMoveQueueItem(idx, idx + 1) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = stringResource(R.string.move_down),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (!isCurrent) {
                                        IconButton(
                                            onClick = { onRemoveFromQueue(idx) },
                                            modifier = Modifier.size(32.dp).padding(end = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Xóa khỏi hàng đợi",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RelatedVideosSection(
    relatedVideos: List<Video>,
    isLoading: Boolean,
    onSelectVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.related_videos_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (relatedVideos.isEmpty()) {
            Text(
                text = stringResource(R.string.no_videos_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                relatedVideos.forEach { relVideo ->
                    CompactVideoCard(
                        video = relVideo,
                        onClick = { onSelectVideo(relVideo) }
                    )
                }
            }
        }
    }
}
