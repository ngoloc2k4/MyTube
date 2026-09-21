package vn.lobie.mytube.ui.player

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import vn.lobie.mytube.ui.components.CompactVideoCard
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.lobie.mytube.R
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.ui.components.VideoCard
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
    modifier: Modifier = Modifier
) {
    val video = uiState.currentVideo ?: return
    var controlsVisible by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var isSaved by remember(video.id) { mutableStateOf(false) }
    var isDescriptionExpanded by remember(video.id) { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
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
                                    contentDescription = null,
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
                            onRemoveFromQueue = onRemoveFromQueue
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
                        if (dragAmount > 50f && !uiState.isFullscreen) {
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
                            onSetSpeed(uiState.playbackSpeed)
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

                // Center playback controls: SkipPrevious | Play/Pause | SkipNext
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

                        Spacer(modifier = Modifier.width(36.dp))

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

                        Spacer(modifier = Modifier.width(36.dp))

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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(uiState.currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
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
                                if (!isCurrent) {
                                    IconButton(
                                        onClick = { onRemoveFromQueue(idx) },
                                        modifier = Modifier.size(36.dp).padding(end = 4.dp)
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
