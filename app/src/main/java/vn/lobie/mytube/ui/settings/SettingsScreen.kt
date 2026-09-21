package vn.lobie.mytube.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.lobie.mytube.data.local.prefs.SettingsDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val defaultSpeed by viewModel.defaultSpeed.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val cacheSizeMb by viewModel.cacheSizeMb.collectAsState()
    val cacheBytes by viewModel.currentCacheBytes.collectAsState()
    val contentRegion by viewModel.contentRegion.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val uiMode by viewModel.uiMode.collectAsState()
    val enginePriority by viewModel.enginePriority.collectAsState()
    val invidiousInstances by viewModel.invidiousInstances.collectAsState()
    val instancePings by viewModel.instancePings.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showRegionDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showUiModeDialog by remember { mutableStateOf(false) }
    var showSourceEngineDialog by remember { mutableStateOf(false) }
    var showInvidiousDialog by remember { mutableStateOf(false) }
    var showAddInstanceDialog by remember { mutableStateOf(false) }
    var newInstanceInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
        ) {
            // Playback Section
            item {
                SectionHeader("Playback")
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.HighQuality,
                    title = "Default Video Quality",
                    subtitle = defaultQuality.uppercase(),
                    onClick = { showQualityDialog = true }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.Speed,
                    title = "Default Playback Speed",
                    subtitle = "${defaultSpeed}x",
                    onClick = { showSpeedDialog = true }
                )
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Default.Headphones,
                    title = "Background Audio Playback",
                    subtitle = "Continue playing audio when screen is turned off or app is minimized",
                    checked = backgroundPlayback,
                    onCheckedChange = { viewModel.setBackgroundPlayback(it) }
                )
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Default.SkipNext,
                    title = "Auto-play Next Video",
                    subtitle = "Automatically load and play recommended next video",
                    checked = autoPlayNext,
                    onCheckedChange = { viewModel.setAutoPlayNext(it) }
                )
            }

            // Source Engine Manager Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader("Source Engine Manager")
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.Dns,
                    title = "Playback Engine Pipeline",
                    subtitle = "Cascading: NewPipe → Invidious → InnerTube → Fallback",
                    onClick = { showSourceEngineDialog = true }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.Cloud,
                    title = "Invidious Instances",
                    subtitle = "yewtu.be, invidious.nerdvpn.de, inv.tux.pizza (Failover pool)",
                    onClick = { showInvidiousDialog = true }
                )
            }

            // Cache & Storage Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader("Storage & Cache Management")
            }

            item {
                val formattedCache = formatBytes(cacheBytes)
                SettingClickableItem(
                    icon = Icons.Default.Storage,
                    title = "Cache Limit",
                    subtitle = "Current used: $formattedCache / Limit: $cacheSizeMb MB",
                    onClick = { showCacheDialog = true }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.CleaningServices,
                    title = "Clear Cache Now",
                    subtitle = "Free temporary cached images and video chunks",
                    onClick = { viewModel.clearCache() }
                )
            }

            // Content & Region Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader("Region & Language")
            }

            item {
                val regionLabel = when (contentRegion) {
                    "VN" -> "Việt Nam (VN)"
                    "US" -> "United States (US)"
                    "JP" -> "Japan (JP)"
                    "KR" -> "Korea (KR)"
                    "GB" -> "United Kingdom (GB)"
                    "FR" -> "France (FR)"
                    "DE" -> "Germany (DE)"
                    "IN" -> "India (IN)"
                    else -> contentRegion
                }
                SettingClickableItem(
                    icon = Icons.Default.Public,
                    title = "Content Region",
                    subtitle = "$regionLabel (Personalizes trending & recommendations)",
                    onClick = { showRegionDialog = true }
                )
            }

            item {
                val langLabel = when (appLanguage) {
                    "vi" -> "Tiếng Việt"
                    "en" -> "English"
                    else -> "Tiếng Việt"
                }
                SettingClickableItem(
                    icon = Icons.Default.Language,
                    title = "App Language",
                    subtitle = langLabel,
                    onClick = { showLanguageDialog = true }
                )
            }

            // Appearance Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader("Appearance")
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = when (themeMode) {
                        SettingsDataStore.THEME_DARK -> "Dark theme"
                        SettingsDataStore.THEME_LIGHT -> "Light theme"
                        else -> "System default"
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            item {
                val uiModeLabel = when (uiMode) {
                    SettingsDataStore.UI_MODE_PHONE -> "Điện thoại (Phone layout)"
                    SettingsDataStore.UI_MODE_TABLET -> "Máy tính bảng (Tablet layout)"
                    else -> "Tự động (Theo thiết bị)"
                }
                SettingClickableItem(
                    icon = Icons.Default.Devices,
                    title = "Chế độ giao diện",
                    subtitle = "$uiModeLabel (Tối ưu bố cục 1 cột hoặc 2 cột)",
                    onClick = { showUiModeDialog = true }
                )
            }

            // Data & Privacy Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader("Data & Privacy")
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear Watch History",
                    subtitle = "Delete all locally recorded watch history",
                    onClick = { showClearHistoryDialog = true }
                )
            }

            // About Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader("About")
            }

            item {
                SettingInfoItem(
                    icon = Icons.Default.Info,
                    title = "MyTube",
                    subtitle = "Version 2.0.0 (Ad-free, Local-first YouTube client)"
                )
            }
        }
    }

    // Dialogs
    if (showQualityDialog) {
        val qualities = listOf(
            SettingsDataStore.QUALITY_AUTO to "Auto",
            SettingsDataStore.QUALITY_1080P to "1080p Full HD",
            SettingsDataStore.QUALITY_720P to "720p HD",
            SettingsDataStore.QUALITY_480P to "480p",
            SettingsDataStore.QUALITY_360P to "360p"
        )
        OptionDialog(
            title = "Default Video Quality",
            options = qualities,
            currentValue = defaultQuality,
            onSelect = {
                viewModel.setQuality(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(
            0.75f to "0.75x",
            1.0f to "1.0x (Normal)",
            1.25f to "1.25x",
            1.5f to "1.5x",
            1.75f to "1.75x",
            2.0f to "2.0x"
        )
        OptionDialog(
            title = "Default Playback Speed",
            options = speeds,
            currentValue = defaultSpeed,
            onSelect = {
                viewModel.setSpeed(it)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showThemeDialog) {
        val themes = listOf(
            SettingsDataStore.THEME_SYSTEM to "System default",
            SettingsDataStore.THEME_DARK to "Dark theme",
            SettingsDataStore.THEME_LIGHT to "Light theme"
        )
        OptionDialog(
            title = "Choose Theme",
            options = themes,
            currentValue = themeMode,
            onSelect = {
                viewModel.setTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showCacheDialog) {
        val cacheLimits = listOf(
            100 to "100 MB",
            250 to "250 MB (Recommended)",
            500 to "500 MB",
            1000 to "1 GB"
        )
        OptionDialog(
            title = "Cache Size Limit",
            options = cacheLimits,
            currentValue = cacheSizeMb,
            onSelect = {
                viewModel.setCacheLimit(it)
                showCacheDialog = false
            },
            onDismiss = { showCacheDialog = false }
        )
    }

    if (showRegionDialog) {
        val regions = listOf(
            "VN" to "Việt Nam (VN)",
            "US" to "United States (US)",
            "JP" to "Japan (JP)",
            "KR" to "Korea (KR)",
            "GB" to "United Kingdom (GB)",
            "FR" to "France (FR)",
            "DE" to "Germany (DE)",
            "IN" to "India (IN)"
        )
        OptionDialog(
            title = "Choose Content Region",
            options = regions,
            currentValue = contentRegion,
            onSelect = {
                viewModel.setRegion(it)
                showRegionDialog = false
            },
            onDismiss = { showRegionDialog = false }
        )
    }

    if (showLanguageDialog) {
        val languages = listOf(
            "vi" to "Tiếng Việt",
            "en" to "English"
        )
        OptionDialog(
            title = "Choose App Language",
            options = languages,
            currentValue = appLanguage,
            onSelect = {
                viewModel.setAppLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showUiModeDialog) {
        val uiModes = listOf(
            SettingsDataStore.UI_MODE_AUTO to "Tự động (Theo thiết bị)",
            SettingsDataStore.UI_MODE_PHONE to "Điện thoại (Phone layout - 1 cột)",
            SettingsDataStore.UI_MODE_TABLET to "Máy tính bảng (Tablet layout - 2 cột)"
        )
        OptionDialog(
            title = "Chế độ giao diện",
            options = uiModes,
            currentValue = uiMode,
            onSelect = {
                viewModel.setUiMode(it)
                showUiModeDialog = false
            },
            onDismiss = { showUiModeDialog = false }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Watch History?") },
            text = { Text("This will remove all videos from your local watch history. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearWatchHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSourceEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSourceEngineDialog = false },
            title = { Text("Đổi thứ tự ưu tiên nguồn phát") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Thứ tự ưu tiên hiện tại (bấm mũi tên để thay đổi):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    enginePriority.forEachIndexed { index, engine ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Text("${index + 1}")
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(engine, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = when (engine) {
                                                "InnerTube" -> "Client chính thức YouTube"
                                                "NewPipe" -> "Bóc tách DASH trực tiếp"
                                                "Invidious" -> "API Mirror & xoay vòng IP"
                                                else -> engine
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = { viewModel.moveEnginePriority(index, index - 1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Lên", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveEnginePriority(index, index + 1) },
                                        enabled = index < enginePriority.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Xuống", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = "Chuỗi ưu tiên: ${enginePriority.joinToString(" > ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceEngineDialog = false }) {
                    Text("Xong")
                }
            }
        )
    }

    if (showInvidiousDialog) {
        AlertDialog(
            onDismissRequest = { showInvidiousDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invidious Instances")
                    IconButton(
                        onClick = { viewModel.pingAllInstances() },
                        enabled = !isPinging
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Đo độ trễ")
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Danh sách mirror tự động chuyển đổi khi gặp lỗi. Bấm vào instance để kiểm tra độ trễ:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    invidiousInstances.forEach { instanceUrl ->
                        val ping = instancePings[instanceUrl]
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.pingInstance(instanceUrl) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = instanceUrl.removePrefix("https://").removePrefix("http://"),
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val (pingText, pingColor) = when {
                                        ping == null -> "Chạm để đo độ trễ" to MaterialTheme.colorScheme.onSurfaceVariant
                                        ping >= 0L -> "Online (${ping}ms)" to Color(0xFF4CAF50)
                                        else -> "Không phản hồi (Offline)" to MaterialTheme.colorScheme.error
                                    }
                                    Text(text = pingText, style = MaterialTheme.typography.labelSmall, color = pingColor)
                                }

                                if (invidiousInstances.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.removeInvidiousInstance(instanceUrl) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Xóa",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddInstanceDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Thêm instance tùy chọn")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInvidiousDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (showAddInstanceDialog) {
        AlertDialog(
            onDismissRequest = { showAddInstanceDialog = false },
            title = { Text("Thêm Invidious Instance") },
            text = {
                Column {
                    Text("Nhập domain hoặc URL của instance Invidious (ví dụ: yewtu.be):", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newInstanceInput,
                        onValueChange = { newInstanceInput = it },
                        placeholder = { Text("vd: invidious.nerdvpn.de") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newInstanceInput.isNotBlank()) {
                            viewModel.addInvidiousInstance(newInstanceInput.trim())
                            newInstanceInput = ""
                            showAddInstanceDialog = false
                        }
                    }
                ) {
                    Text("Thêm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddInstanceDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(text = title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(text = title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
private fun SettingInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(text = title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<Pair<T, String>>,
    currentValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == currentValue,
                            onClick = { onSelect(value) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.1f KB", kb)
    }
}
