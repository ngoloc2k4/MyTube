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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
                        SettingsViewModel(application, settingsDataStore)
                    }
                    val musicViewModel: MusicViewModel = viewModel {
                        MusicViewModel(application, repository)
                    }

                    val playerUiState by playerViewModel.uiState.collectAsState()
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
                                        onRemoveFromQueue = { playerViewModel.removeFromQueue(it) }
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
