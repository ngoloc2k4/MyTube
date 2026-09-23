package vn.lobie.mytube.ui.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import vn.lobie.mytube.R
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
    val sponsorBlockEnabled by viewModel.sponsorBlockEnabled.collectAsState()
    val returnDislikeEnabled by viewModel.returnDislikeEnabled.collectAsState()
    val audioNormalizationEnabled by viewModel.audioNormalizationEnabled.collectAsState()
    val crossfadeDurationSeconds by viewModel.crossfadeDurationSeconds.collectAsState()
    val listenBrainzEnabled by viewModel.listenBrainzEnabled.collectAsState()
    val listenBrainzToken by viewModel.listenBrainzToken.collectAsState()
    val listenBrainzValidation by viewModel.listenBrainzValidation.collectAsState()
    val isValidatingToken by viewModel.isValidatingToken.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showRegionDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showUiModeDialog by remember { mutableStateOf(false) }
    var showSourceEngineDialog by remember { mutableStateOf(false) }
    var showInvidiousDialog by remember { mutableStateOf(false) }
    var showAddInstanceDialog by remember { mutableStateOf(false) }
    var showDebugLogsDialog by remember { mutableStateOf(false) }
    var showListenBrainzDialog by remember { mutableStateOf(false) }
    var newInstanceInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val backupStatus by viewModel.backupStatus.collectAsState()
    LaunchedEffect(backupStatus) {
        backupStatus?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearBackupStatus()
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackup(uri)
        }
    }

    val importSubsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importSubscriptions(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cài đặt & Tùy biến",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Trải nghiệm nghe nhìn cá nhân hóa",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

            item {
                SettingSwitchItem(
                    icon = Icons.Default.GraphicEq,
                    title = "Chuẩn hóa âm lượng (Audio Normalization)",
                    subtitle = "Cân bằng độ lớn âm thanh giữa các bài hát khác nhau, tránh bài quá nhỏ hoặc quá to",
                    checked = audioNormalizationEnabled,
                    onCheckedChange = { viewModel.setAudioNormalizationEnabled(it) }
                )
            }

            item {
                val crossfadeLabel = if (crossfadeDurationSeconds > 0) "${crossfadeDurationSeconds}s" else "Tắt"
                SettingClickableItem(
                    icon = Icons.Default.Tune,
                    title = "Crossfade chuyển bài",
                    subtitle = "$crossfadeLabel (Chuyển tiếp âm thanh mượt mà giữa các video)",
                    onClick = { showCrossfadeDialog = true }
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
                SectionHeader("Bảo mật & Quyền riêng tư (Privacy)")
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Default.FastForward,
                    title = "Bỏ qua tài trợ (SponsorBlock)",
                    subtitle = "Tự động tua qua phân đoạn quảng cáo/tài trợ do cộng đồng đóng góp",
                    checked = sponsorBlockEnabled,
                    onCheckedChange = { viewModel.setSponsorBlockEnabled(it) }
                )
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Default.ThumbDown,
                    title = "Hiện số lượt Dislike (Return YouTube Dislike)",
                    subtitle = "Lấy dữ liệu lượt không thích thực tế từ RYD API",
                    checked = returnDislikeEnabled,
                    onCheckedChange = { viewModel.setReturnDislikeEnabled(it) }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Xóa lịch sử xem",
                    subtitle = "Xóa toàn bộ dữ liệu lịch sử xem đã lưu cục bộ",
                    onClick = { showClearHistoryDialog = true }
                )
            }

            // Music Ecosystem & Scrobbling Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader("Âm nhạc & Dữ liệu mở")
            }

            item {
                SettingSwitchItem(
                    icon = Icons.Default.MusicNote,
                    title = "Đồng bộ ListenBrainz (Scrobbling)",
                    subtitle = if (listenBrainzToken.isNotBlank()) "Đã liên kết token cá nhân" else "Gửi lịch sử nghe nhạc lên máy chủ mã nguồn mở ListenBrainz",
                    checked = listenBrainzEnabled,
                    onCheckedChange = { viewModel.setListenBrainzEnabled(it) }
                )
            }

            if (listenBrainzEnabled) {
                item {
                    SettingClickableItem(
                        icon = Icons.Default.Lock,
                        title = "ListenBrainz User Token",
                        subtitle = if (listenBrainzToken.isNotBlank()) "••••••••" + listenBrainzToken.takeLast(6) else "Nhấn để nhập token cá nhân",
                        onClick = { showListenBrainzDialog = true }
                    )
                }
            }

            // Diagnostics & About Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader("Gỡ lỗi & Giới thiệu")
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.BugReport,
                    title = "Nhật ký gỡ lỗi (Debug Logs)",
                    subtitle = "Xem log ứng dụng realtime, lọc theo nguồn và sao chép báo cáo lỗi",
                    onClick = { showDebugLogsDialog = true }
                )
            }

            // Backup & Restore Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SectionHeader(stringResource(R.string.backup_and_restore))
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.backup_external_save),
                    subtitle = stringResource(R.string.backup_external_save_desc),
                    onClick = {
                        if (!vn.lobie.mytube.data.importer.ExternalDataManager.hasStoragePermission(context)) {
                            vn.lobie.mytube.data.importer.ExternalDataManager.requestStoragePermission(context)
                        } else {
                            viewModel.saveToExternalStorage()
                        }
                    }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.Refresh,
                    title = stringResource(R.string.backup_external_restore),
                    subtitle = stringResource(R.string.backup_external_restore_desc),
                    onClick = {
                        if (!vn.lobie.mytube.data.importer.ExternalDataManager.hasStoragePermission(context)) {
                            vn.lobie.mytube.data.importer.ExternalDataManager.requestStoragePermission(context)
                        } else {
                            viewModel.restoreFromExternalStorage()
                        }
                    }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.CloudUpload,
                    title = stringResource(R.string.backup_export),
                    subtitle = stringResource(R.string.backup_export_desc),
                    onClick = {
                        exportBackupLauncher.launch("mytube_backup_${System.currentTimeMillis()}.json")
                    }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.CloudDownload,
                    title = stringResource(R.string.backup_restore),
                    subtitle = stringResource(R.string.backup_restore_desc),
                    onClick = {
                        restoreBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
            }

            item {
                SettingClickableItem(
                    icon = Icons.Default.Subscriptions,
                    title = stringResource(R.string.import_subscriptions),
                    subtitle = stringResource(R.string.import_subscriptions_desc),
                    onClick = {
                        importSubsLauncher.launch(arrayOf("*/*", "text/csv", "application/json"))
                    }
                )
            }

            item {
                val context = LocalContext.current
                val clipboardManager = LocalClipboardManager.current
                SettingInfoItem(
                    icon = Icons.Default.Info,
                    title = "MyTube",
                    subtitle = "Version 2.0.0 (Chạm để sao chép thông tin thiết bị)",
                    onClick = {
                        val devInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nSource Priority: ${enginePriority.joinToString(" > ")}"
                        clipboardManager.setText(AnnotatedString(devInfo))
                        Toast.makeText(context, "Đã sao chép thông tin thiết bị!", Toast.LENGTH_SHORT).show()
                    }
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

    if (showCrossfadeDialog) {
        val crossfadeOptions = listOf(
            0 to "Tắt (Mặc định)",
            3 to "3 giây",
            5 to "5 giây",
            8 to "8 giây"
        )
        OptionDialog(
            title = "Thời gian Crossfade",
            options = crossfadeOptions,
            currentValue = crossfadeDurationSeconds,
            onSelect = {
                viewModel.setCrossfadeDurationSeconds(it)
                showCrossfadeDialog = false
            },
            onDismiss = { showCrossfadeDialog = false }
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

    if (showDebugLogsDialog) {
        DebugLogsDialog(
            enginePriority = enginePriority,
            onDismiss = { showDebugLogsDialog = false }
        )
    }

    if (showListenBrainzDialog) {
        ListenBrainzTokenDialog(
            currentToken = listenBrainzToken,
            validationResult = listenBrainzValidation,
            isValidating = isValidatingToken,
            onSaveToken = { token ->
                viewModel.setListenBrainzToken(token)
            },
            onValidate = { token ->
                viewModel.validateListenBrainzToken(token)
            },
            onDismiss = { showListenBrainzDialog = false }
        )
    }
}

@Composable
private fun ListenBrainzTokenDialog(
    currentToken: String,
    validationResult: String?,
    isValidating: Boolean,
    onSaveToken: (String) -> Unit,
    onValidate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(currentToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cấu hình ListenBrainz") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nhập User Token từ tài khoản ListenBrainz (listenbrainz.org/profile) để tự động scrobble khi nghe nhạc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("User Token") },
                    placeholder = { Text("vd: 5b4e8...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (validationResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationResult,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (validationResult.startsWith("Hợp lệ")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onValidate(input) },
                        enabled = input.isNotBlank() && !isValidating
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Kiểm tra Token")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveToken(input)
                onDismiss()
            }) {
                Text(stringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsDialog(
    enginePriority: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val logs by vn.lobie.mytube.core.common.AppLogger.logs.collectAsState()

    var selectedTag by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }

    val filterTags = listOf("ALL", "Player", "Source", "Invidious", "NewPipe", "InnerTube", "Fallback")

    val filteredLogs = remember(logs, selectedTag, searchQuery) {
        logs.filter { entry ->
            val matchesTag = when (selectedTag) {
                "ALL" -> true
                else -> entry.tag.equals(selectedTag, ignoreCase = true)
            }
            val matchesQuery = searchQuery.isBlank() ||
                entry.message.contains(searchQuery, ignoreCase = true) ||
                entry.tag.contains(searchQuery, ignoreCase = true) ||
                (entry.videoId?.contains(searchQuery, ignoreCase = true) == true) ||
                (entry.raw?.contains(searchQuery, ignoreCase = true) == true)

            matchesTag && matchesQuery
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.88f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Debug Logs (${filteredLogs.size}/${logs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Lọc theo videoId, lỗi, URL...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar: Copy All, Share, Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val text = vn.lobie.mytube.core.common.AppLogger.getExportText(enginePriority)
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Đã sao chép toàn bộ log!", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sao chép", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            val text = vn.lobie.mytube.core.common.AppLogger.getExportText(enginePriority)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ MyTube Debug Log"))
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chia sẻ", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            vn.lobie.mytube.core.common.AppLogger.clear()
                            Toast.makeText(context, "Đã xóa toàn bộ log", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xóa")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Log Items List
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Không có log nào phù hợp",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs.size) { idx ->
                            val entry = filteredLogs[idx]
                            val isExpanded = expandedLogId == entry.id
                            LogEntryCard(
                                entry = entry,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedLogId = if (isExpanded) null else entry.id
                                },
                                onVideoIdClick = { vid ->
                                    searchQuery = vid
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
private fun LogEntryCard(
    entry: vn.lobie.mytube.core.common.LogEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onVideoIdClick: (String) -> Unit
) {
    val levelColor = when (entry.level) {
        "E" -> Color(0xFFE53935) // Red
        "W" -> Color(0xFFFB8C00) // Amber / Orange
        "I" -> Color(0xFF1E88E5) // Blue
        else -> Color(0xFF757575) // Gray
    }

    val cardBg = when (entry.level) {
        "E" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        "W" -> Color(0xFFFB8C00).copy(alpha = 0.12f)
        "I" -> Color(0xFF1E88E5).copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (entry.raw != null) onToggleExpand()
            },
        shape = RoundedCornerShape(8.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, levelColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Level Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = levelColor
                ) {
                    Text(
                        text = entry.level,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Tag Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = entry.tag,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (!entry.videoId.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { onVideoIdClick(entry.videoId) }
                    ) {
                        Text(
                            text = entry.videoId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = entry.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!entry.raw.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "▲ Ẩn chi tiết" else "▼ Xem chi tiết lỗi / trace",
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = entry.raw,
                                color = Color(0xFFFFB4AB),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp
                                ),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
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
