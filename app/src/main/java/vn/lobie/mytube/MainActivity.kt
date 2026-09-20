package vn.lobie.mytube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.prefs.SettingsDataStore
import vn.lobie.mytube.data.repository.CascadingYouTubeRepository
import vn.lobie.mytube.ui.home.HomeScreen
import vn.lobie.mytube.ui.home.HomeViewModel
import vn.lobie.mytube.ui.navigation.AppBottomBar
import vn.lobie.mytube.ui.navigation.AppTab
import vn.lobie.mytube.ui.navigation.PlaceholderTabScreen
import vn.lobie.mytube.ui.player.FullPlayer
import vn.lobie.mytube.ui.player.MiniPlayer
import vn.lobie.mytube.ui.player.PlayerViewModel
import vn.lobie.mytube.ui.theme.MyTubeTheme

class MainActivity : ComponentActivity() {
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
                        HomeViewModel(repository)
                    }
                    val playerViewModel: PlayerViewModel = viewModel {
                        PlayerViewModel(application, repository)
                    }

                    val playerUiState by playerViewModel.uiState.collectAsState()
                    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }

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
                                    }
                                )
                            }
                            AppTab.SUBSCRIPTIONS -> {
                                PlaceholderTabScreen(
                                    title = "Subscriptions",
                                    icon = AppTab.SUBSCRIPTIONS.icon,
                                    description = "Track channels & new uploads locally without Google login",
                                    bottomPadding = contentBottomPadding
                                )
                            }
                            AppTab.MUSIC -> {
                                PlaceholderTabScreen(
                                    title = "YouTube Music",
                                    icon = AppTab.MUSIC.icon,
                                    description = "Stream songs, audio-only playback, and charts",
                                    bottomPadding = contentBottomPadding
                                )
                            }
                            AppTab.LIBRARY -> {
                                PlaceholderTabScreen(
                                    title = "Library",
                                    icon = AppTab.LIBRARY.icon,
                                    description = "Playlists, watch history, liked videos, and offline downloads",
                                    bottomPadding = contentBottomPadding
                                )
                            }
                            AppTab.SETTINGS -> {
                                PlaceholderTabScreen(
                                    title = "Settings",
                                    icon = AppTab.SETTINGS.icon,
                                    description = "Playback speed, stream quality, gestures, and cache control",
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
                                onSeekBy = { playerViewModel.seekBy(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
