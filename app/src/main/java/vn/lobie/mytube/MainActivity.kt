package vn.lobie.mytube

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.prefs.SettingsDataStore
import vn.lobie.mytube.data.repository.CascadingYouTubeRepository
import vn.lobie.mytube.ui.home.HomeScreen
import vn.lobie.mytube.ui.home.HomeViewModel
import vn.lobie.mytube.ui.library.LibraryScreen
import vn.lobie.mytube.ui.library.LibraryViewModel
import vn.lobie.mytube.ui.music.MusicScreen
import vn.lobie.mytube.ui.music.MusicViewModel
import vn.lobie.mytube.ui.navigation.AppBottomBar
import vn.lobie.mytube.ui.navigation.AppTab
import vn.lobie.mytube.ui.player.FullPlayer
import vn.lobie.mytube.ui.player.MiniPlayer
import vn.lobie.mytube.ui.player.PlayerViewModel
import vn.lobie.mytube.ui.settings.SettingsScreen
import vn.lobie.mytube.ui.settings.SettingsViewModel
import vn.lobie.mytube.ui.subscriptions.SubscriptionsScreen
import vn.lobie.mytube.ui.subscriptions.SubscriptionsViewModel
import vn.lobie.mytube.ui.theme.MyTubeTheme

class MainActivity : ComponentActivity() {

    private var isInPipMode by mutableStateOf(false)
    private var playerViewModelRef: PlayerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SEC-14: Mitigate Tapjacking / Overlay attacks by ignoring obscured touches across the activity window
        findViewById<android.view.View>(android.R.id.content)?.filterTouchesWhenObscured = true
        vn.lobie.mytube.core.common.AppLogger.init(applicationContext)
        // SEC-22 & SEC-23: Device posture and integrity validation
        val isRooted = vn.lobie.mytube.core.common.SecurityUtils.isDeviceRooted()
        val isSignatureValid = vn.lobie.mytube.core.common.SecurityUtils.verifyAppSignature(this)
        vn.lobie.mytube.core.common.AppLogger.i("Security", "Device integrity: isRooted=$isRooted, isSignatureValid=$isSignatureValid")
        enableEdgeToEdge()
        setContent {
            MyTubeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val repository = remember { CascadingYouTubeRepository() }
                    val database = remember { MyTubeDatabase.getInstance(applicationContext) }
                    val settingsDataStore = remember { SettingsDataStore(applicationContext) }

                    androidx.compose.runtime.LaunchedEffect(settingsDataStore) {
                        launch {
                            settingsDataStore.contentRegion.collect { region ->
                                repository.setRegion(region)
                            }
                        }
                        launch {
                            settingsDataStore.contentLanguage.collect { lang ->
                                repository.setLanguage(lang)
                            }
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            vn.lobie.mytube.data.importer.ExternalDataManager.checkAndAutoRestoreIfEmpty(
                                applicationContext,
                                database
                            )
                        }
                    }

                    val hasPromptedPermission by settingsDataStore.hasPromptedStoragePermission.collectAsState(initial = true)
                    var showStoragePermissionDialog by rememberSaveable { mutableStateOf(false) }

                    androidx.compose.runtime.LaunchedEffect(hasPromptedPermission) {
                        if (!hasPromptedPermission && !vn.lobie.mytube.data.importer.ExternalDataManager.hasStoragePermission(applicationContext)) {
                            showStoragePermissionDialog = true
                        }
                    }

                    if (showStoragePermissionDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = {
                                showStoragePermissionDialog = false
                                lifecycleScope.launch {
                                    settingsDataStore.setPromptedStoragePermission(true)
                                }
                            },
                            icon = {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            title = {
                                androidx.compose.material3.Text(
                                    text = stringResource(R.string.storage_permission_dialog_title),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                androidx.compose.material3.Text(
                                    text = stringResource(R.string.storage_permission_dialog_msg)
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.Button(
                                    onClick = {
                                        showStoragePermissionDialog = false
                                        lifecycleScope.launch {
                                            settingsDataStore.setPromptedStoragePermission(true)
                                        }
                                        vn.lobie.mytube.data.importer.ExternalDataManager.requestStoragePermission(this@MainActivity)
                                    }
                                ) {
                                    androidx.compose.material3.Text(stringResource(R.string.storage_permission_dialog_grant))
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        showStoragePermissionDialog = false
                                        lifecycleScope.launch {
                                            settingsDataStore.setPromptedStoragePermission(true)
                                        }
                                    }
                                ) {
                                    androidx.compose.material3.Text(stringResource(R.string.storage_permission_dialog_later))
                                }
                            }
                        )
                    }

                    val homeViewModel: HomeViewModel = viewModel {
                        HomeViewModel(repository, database, settingsDataStore)
                    }
                    val playerViewModel: PlayerViewModel = viewModel {
                        PlayerViewModel(application, repository)
                    }
                    playerViewModelRef = playerViewModel

                    val subscriptionsViewModel: SubscriptionsViewModel = viewModel {
                        SubscriptionsViewModel(application, repository)
                    }
                    val libraryViewModel: LibraryViewModel = viewModel {
                        LibraryViewModel(application)
                    }
                    val settingsViewModel: SettingsViewModel = viewModel {
                        SettingsViewModel(application, settingsDataStore, repository)
                    }
                    val musicViewModel: MusicViewModel = viewModel {
                        MusicViewModel(application, repository)
                    }

                    val playerUiState by playerViewModel.uiState.collectAsState()
                    val comments by playerViewModel.comments.collectAsState()
                    val isCommentsLoading by playerViewModel.isCommentsLoading.collectAsState()
                    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }

                    val uiMode by settingsDataStore.uiMode.collectAsState(initial = SettingsDataStore.UI_MODE_AUTO)
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isTablet = when (uiMode) {
                        SettingsDataStore.UI_MODE_PHONE -> false
                        SettingsDataStore.UI_MODE_TABLET -> true
                        else -> configuration.screenWidthDp >= 600
                    }

                    androidx.compose.runtime.CompositionLocalProvider(
                        vn.lobie.mytube.ui.theme.LocalIsTablet provides isTablet
                    ) {
                        if (isInPipMode) {
                            PipPlayerSurface(player = playerViewModel.player)
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val bottomBarHeight = 80.dp
                                val miniPlayerHeight = if (playerUiState.currentVideo != null && !playerUiState.isExpanded) 80.dp else 0.dp
                                val contentBottomPadding = bottomBarHeight + miniPlayerHeight

                                when (currentTab) {
                                    AppTab.HOME -> {
                                        HomeScreen(
                                            viewModel = homeViewModel,
                                            bottomPadding = contentBottomPadding,
                                            onVideoClick = { video ->
                                                playerViewModel.playVideo(video)
                                            },
                                            onPlayNext = { video ->
                                                playerViewModel.playNextInQueue(video)
                                            },
                                            onAddToQueue = { video ->
                                                playerViewModel.addToQueue(video)
                                            }
                                        )
                                    }
                                    AppTab.SUBSCRIPTIONS -> {
                                        SubscriptionsScreen(
                                            viewModel = subscriptionsViewModel,
                                            bottomPadding = contentBottomPadding,
                                            onVideoClick = { video ->
                                                playerViewModel.playVideo(video)
                                            }
                                        )
                                    }
                                    AppTab.MUSIC -> {
                                        MusicScreen(
                                            viewModel = musicViewModel,
                                            bottomPadding = contentBottomPadding,
                                            onTrackClick = { track, tracks, index ->
                                                playerViewModel.playVideo(track, tracks, index)
                                            }
                                        )
                                    }
                                    AppTab.LIBRARY -> {
                                        LibraryScreen(
                                            viewModel = libraryViewModel,
                                            bottomPadding = contentBottomPadding,
                                            onVideoClick = { video ->
                                                playerViewModel.playVideo(video)
                                            }
                                        )
                                    }
                                    AppTab.SETTINGS -> {
                                        SettingsScreen(
                                            viewModel = settingsViewModel,
                                            bottomPadding = contentBottomPadding
                                        )
                                    }
                                }

                                // Bottom docked controls (MiniPlayer + NavigationBar)
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                ) {
                                    if (playerUiState.currentVideo != null && !playerUiState.isExpanded) {
                                        MiniPlayer(
                                            uiState = playerUiState,
                                            onExpand = { playerViewModel.expand() },
                                            onTogglePlayPause = { playerViewModel.togglePlayPause() },
                                            onClose = { playerViewModel.close() },
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    AppBottomBar(
                                        currentTab = currentTab,
                                        onTabSelected = { currentTab = it }
                                    )
                                }

                                // Fullscreen Player overlay
                                AnimatedVisibility(
                                    visible = playerUiState.isExpanded && playerUiState.currentVideo != null,
                                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                ) {
                                    FullPlayer(
                                        uiState = playerUiState,
                                        player = playerViewModel.player,
                                        comments = comments,
                                        isCommentsLoading = isCommentsLoading,
                                        onRefreshComments = { playerViewModel.loadComments() },
                                        onCollapse = { playerViewModel.collapse() },
                                        onTogglePlayPause = { playerViewModel.togglePlayPause() },
                                        onSeek = { playerViewModel.seekTo(it) },
                                        onSeekBy = { playerViewModel.seekBy(it) },
                                        onSetSpeed = { playerViewModel.setSpeed(it) },
                                        onPlayNext = { playerViewModel.playNext() },
                                        onPlayPrevious = { playerViewModel.playPrevious() },
                                        onSelectQuality = { playerViewModel.selectQuality(it) },
                                        onToggleAudioOnly = { playerViewModel.toggleAudioOnly() },
                                        onToggleFullscreen = { playerViewModel.toggleFullscreen() },
                                        onToggleLike = { playerViewModel.toggleLike() },
                                        onToggleSubscribe = { playerViewModel.toggleSubscribe() },
                                        onSelectVideo = { playerViewModel.playVideo(it) },
                                        onEnterPip = { enterPip() },
                                        onToggleAutoPlay = { playerViewModel.toggleAutoPlay() },
                                        onRemoveFromQueue = { playerViewModel.removeFromQueue(it) },
                                        onToggleLoopMode = { playerViewModel.toggleLoopMode() },
                                        onToggleShuffle = { playerViewModel.toggleShuffle() },
                                        onToggleResizeMode = { playerViewModel.toggleResizeMode() },
                                        onSetSleepTimer = { playerViewModel.setSleepTimer(it) },
                                        onCancelSleepTimer = { playerViewModel.cancelSleepTimer() },
                                        onSeekToChapter = { playerViewModel.seekToChapter(it) },
                                        onToggleSponsorBlock = { playerViewModel.toggleSponsorBlock() },
                                        onUnskipSponsor = { playerViewModel.unskipLastSegment() },
                                        onDismissSponsorNotice = { playerViewModel.dismissSkippedNotice() },
                                        onSetDoubleTapSeekSeconds = { playerViewModel.setDoubleTapSeekSeconds(it) },
                                        onToggleSubtitles = { playerViewModel.toggleSubtitles() },
                                        onSelectSubtitle = { playerViewModel.selectSubtitle(it) },
                                        onSetAbLoopA = { playerViewModel.setAbLoopA() },
                                        onSetAbLoopB = { playerViewModel.setAbLoopB() },
                                        onClearAbLoop = { playerViewModel.clearAbLoop() },
                                        onSetSubtitleFontSize = { playerViewModel.setSubtitleFontSize(it) },
                                        onSetSubtitleBgColor = { playerViewModel.setSubtitleBgColor(it) },
                                        onMoveQueueItem = { from, to -> playerViewModel.moveQueueItem(from, to) },
                                        onClearQueue = { playerViewModel.clearQueue() },
                                        onDownloadVideo = { playerViewModel.downloadCurrentVideo() },
                                        onDownloadOptionSelected = { playerViewModel.downloadWithOption(it) },
                                        onCancelDownload = { playerViewModel.cancelCurrentDownload() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val vm = playerViewModelRef ?: return
        val state = vm.uiState.value
        if (state.currentVideo != null && state.isPlaying && !state.isAudioOnly) {
            enterPip()
        }
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPip = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            if (hasPip) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to enter Picture-in-Picture mode", e)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (vn.lobie.mytube.data.importer.ExternalDataManager.hasStoragePermission(applicationContext)) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val db = MyTubeDatabase.getInstance(applicationContext)
                    vn.lobie.mytube.data.importer.ExternalDataManager.checkAndAutoRestoreIfEmpty(applicationContext, db)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Auto-restore external data onResume: ${e.message}")
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (vn.lobie.mytube.data.importer.ExternalDataManager.hasStoragePermission(applicationContext)) {
            androidx.lifecycle.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val db = MyTubeDatabase.getInstance(applicationContext)
                    vn.lobie.mytube.data.importer.ExternalDataManager.saveToExternalStorage(applicationContext, db)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Auto-save external data onStop: ${e.message}")
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PipPlayerSurface(
    player: androidx.media3.common.Player?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
