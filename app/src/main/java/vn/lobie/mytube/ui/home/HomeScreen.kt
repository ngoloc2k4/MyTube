package vn.lobie.mytube.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.lobie.mytube.R
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.ui.components.ShimmerVideoCard
import vn.lobie.mytube.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onVideoClick: (Video) -> Unit = {},
    onPlayNext: (Video) -> Unit = {},
    onAddToQueue: (Video) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    var currentQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val filteredHistory = remember(searchHistory, currentQuery) {
        if (currentQuery.isBlank()) {
            searchHistory.take(8)
        } else {
            searchHistory.filter { it.contains(currentQuery.trim(), ignoreCase = true) }.take(8)
        }
    }

    BackHandler(enabled = isSearchFocused) {
        isSearchFocused = false
        focusManager.clearFocus()
    }

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
            // Modern 28dp Pill Search Input Field with Filter Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = currentQuery,
                    onValueChange = {
                        currentQuery = it
                        if (it.isBlank()) {
                            viewModel.clearSearch()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isSearchFocused = it.isFocused },
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
                                    isSearchFocused = false
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
                                        isSearchFocused = false
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
                            isSearchFocused = false
                            focusManager.clearFocus()
                            if (currentQuery.isNotBlank()) {
                                viewModel.performSearch(currentQuery)
                            }
                        }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.filter_title),
                        tint = if (searchFilter.sort != SearchSort.RELEVANCE || searchFilter.duration != SearchDuration.ALL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Autocomplete / Search History Dropdown Card
            AnimatedVisibility(
                visible = isSearchFocused && filteredHistory.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.search_history_title),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { viewModel.clearSearchHistory() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.clear_all_history),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        filteredHistory.forEach { queryItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentQuery = queryItem
                                        isSearchFocused = false
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.performSearch(queryItem)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = queryItem,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { currentQuery = queryItem },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NorthWest,
                                        contentDescription = "Điền từ khóa",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSearchHistory(queryItem) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear_action),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomPadding)
                    ) {
                        repeat(4) {
                            ShimmerVideoCard()
                        }
                    }
                }

                is HomeUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Text(
                                text = "Không thể kết nối máy chủ",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Button(
                                onClick = { viewModel.loadTrendingVideos() },
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.retry_button))
                            }
                        }
                    }
                }

                is HomeUiState.Success -> {
                    if (state.isSearching) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomPadding)
                        ) {
                            repeat(4) {
                                ShimmerVideoCard()
                            }
                        }
                    } else {
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
                                        onClick = { onVideoClick(video) },
                                        onPlayNext = { onPlayNext(video) },
                                        onAddToQueue = { onAddToQueue(video) },
                                        watchProgress = watchProgressMap[video.id]
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        SearchFilterDialog(
            currentFilter = searchFilter,
            onApply = { viewModel.setFilter(it) },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
private fun SearchFilterDialog(
    currentFilter: SearchFilter,
    onApply: (SearchFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSort by remember { mutableStateOf(currentFilter.sort) }
    var selectedDuration by remember { mutableStateOf(currentFilter.duration) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.filter_sort_by),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SearchSort.entries.forEach { sort ->
                        FilterChip(
                            selected = selectedSort == sort,
                            onClick = { selectedSort = sort },
                            label = { Text(stringResource(sort.titleRes), fontSize = 11.sp) }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.filter_duration),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SearchDuration.entries.take(2).forEach { dur ->
                            FilterChip(
                                selected = selectedDuration == dur,
                                onClick = { selectedDuration = dur },
                                label = { Text(stringResource(dur.titleRes), fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SearchDuration.entries.drop(2).forEach { dur ->
                            FilterChip(
                                selected = selectedDuration == dur,
                                onClick = { selectedDuration = dur },
                                label = { Text(stringResource(dur.titleRes), fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(SearchFilter(sort = selectedSort, duration = selectedDuration))
                onDismiss()
            }) {
                Text(stringResource(R.string.apply_filter))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}
