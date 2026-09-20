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
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.lobie.mytube.data.repository.InvidiousYouTubeRepository
import vn.lobie.mytube.ui.home.HomeScreen
import vn.lobie.mytube.ui.home.HomeViewModel
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
                    val repository = remember { InvidiousYouTubeRepository() }
                    val homeViewModel: HomeViewModel = viewModel {
                        HomeViewModel(repository)
                    }
                    val playerViewModel: PlayerViewModel = viewModel {
                        PlayerViewModel(application, repository)
                    }

                    val playerUiState by playerViewModel.uiState.collectAsState()

                    Box(modifier = Modifier.fillMaxSize()) {
                        val miniPlayerHeight = if (playerUiState.currentVideo != null && !playerUiState.isExpanded) 68.dp else 0.dp

                        HomeScreen(
                            viewModel = homeViewModel,
                            bottomPadding = miniPlayerHeight,
                            onVideoClick = { video ->
                                playerViewModel.playVideo(video)
                            }
                        )

                        if (playerUiState.currentVideo != null && !playerUiState.isExpanded) {
                            MiniPlayer(
                                uiState = playerUiState,
                                onExpand = { playerViewModel.expand() },
                                onTogglePlayPause = { playerViewModel.togglePlayPause() },
                                onClose = { playerViewModel.close() },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                            )
                        }

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
