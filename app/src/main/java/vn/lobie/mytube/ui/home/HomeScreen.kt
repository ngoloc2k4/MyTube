package vn.lobie.mytube.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.lobie.mytube.R
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
    var currentQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
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
            // Modern 28dp Pill Search Input Field
            OutlinedTextField(
                value = currentQuery,
                onValueChange = {
                    currentQuery = it
                    if (it.isBlank()) {
                        viewModel.clearSearch()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = currentQuery.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = {
                                currentQuery = ""
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_action),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledIconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    if (currentQuery.isNotBlank()) {
                                        viewModel.performSearch(currentQuery)
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search_action),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (currentQuery.isNotBlank()) {
                            viewModel.performSearch(currentQuery)
                        }
                    }
                ),
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )

            // Category Filter Chips Row
            val selectedCat = (uiState as? HomeUiState.Success)?.selectedCategory ?: VideoCategory.ALL
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VideoCategory.entries.toTypedArray()) { category ->
                    FilterChip(
                        selected = category == selectedCat,
                        onClick = {
                            currentQuery = ""
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.selectCategory(category)
                        },
                        label = { Text(stringResource(category.titleRes)) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

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
                                Text(stringResource(R.string.retry_button))
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
                            Text(stringResource(R.string.no_videos_found), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        val isTablet = vn.lobie.mytube.ui.theme.LocalIsTablet.current

                        LazyVerticalGrid(
                            columns = if (isTablet) GridCells.Adaptive(minSize = 340.dp) else GridCells.Fixed(1),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + bottomPadding),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
