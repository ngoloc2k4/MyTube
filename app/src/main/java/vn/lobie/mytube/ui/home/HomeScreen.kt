package vn.lobie.mytube.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onVideoClick: (Video) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var currentQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MyTube",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = currentQuery,
                onValueChange = {
                    currentQuery = it
                    viewModel.onSearchQueryChanged(it)
                    if (it.isBlank()) {
                        viewModel.clearSearch()
                    } else {
                        viewModel.performSearch(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search YouTube videos...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (currentQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            currentQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Content Area based on State
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.Red)
                    }
                }

                is HomeUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.loadTrendingVideos() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is HomeUiState.Success -> {
                    val displayVideos = if (state.searchResults != null) {
                        state.searchResults.mapNotNull {
                            (it as? SearchResult.VideoItem)?.video
                        }
                    } else {
                        state.videos
                    }

                    if (displayVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No videos found", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + bottomPadding)
                        ) {
                            items(
                                items = displayVideos,
                                key = { it.id }
                            ) { video ->
                                VideoCard(
                                    video = video,
                                    onClick = { onVideoClick(video) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
