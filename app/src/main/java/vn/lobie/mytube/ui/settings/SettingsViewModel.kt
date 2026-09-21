package vn.lobie.mytube.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.prefs.SettingsDataStore
import java.io.File

class SettingsViewModel(
    private val app: Application,
    private val settingsDataStore: SettingsDataStore,
    private val repository: vn.lobie.mytube.data.repository.CascadingYouTubeRepository? = null
) : AndroidViewModel(app) {

    private val db = MyTubeDatabase.getInstance(app)

    private val _enginePriority = MutableStateFlow(repository?.enginePriority ?: listOf("InnerTube", "NewPipe", "Invidious"))
    val enginePriority: StateFlow<List<String>> = _enginePriority.asStateFlow()

    private val _invidiousInstances = MutableStateFlow<List<String>>(
        repository?.invidiousClient?.instances?.toList() ?: vn.lobie.mytube.data.remote.InvidiousApiClient.DEFAULT_INSTANCES
    )
    val invidiousInstances: StateFlow<List<String>> = _invidiousInstances.asStateFlow()

    private val _instancePings = MutableStateFlow<Map<String, Long>>(emptyMap())
    val instancePings: StateFlow<Map<String, Long>> = _instancePings.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    fun moveEnginePriority(fromIndex: Int, toIndex: Int) {
        val current = _enginePriority.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _enginePriority.value = current
            repository?.setEnginePriority(current)
        }
    }

    fun addInvidiousInstance(url: String) {
        if (url.isBlank()) return
        repository?.invidiousClient?.addInstance(url)
        val formatted = if (url.startsWith("http")) url.trimEnd('/') else "https://${url.trim().trimEnd('/')}"
        val current = _invidiousInstances.value.toMutableList()
        if (!current.contains(formatted)) {
            current.add(formatted)
            _invidiousInstances.value = current
            pingInstance(formatted)
        }
    }

    fun removeInvidiousInstance(url: String) {
        repository?.invidiousClient?.removeInstance(url)
        val current = _invidiousInstances.value.toMutableList()
        current.remove(url)
        _invidiousInstances.value = current
    }

    fun pingAllInstances() {
        viewModelScope.launch {
            _isPinging.value = true
            val client = repository?.invidiousClient ?: vn.lobie.mytube.data.remote.InvidiousApiClient()
            val map = _instancePings.value.toMutableMap()
            _invidiousInstances.value.forEach { inst ->
                val (alive, latency) = client.pingInstance(inst)
                map[inst] = if (alive) latency else -1L
            }
            _instancePings.value = map
            _isPinging.value = false
        }
    }

    fun pingInstance(inst: String) {
        viewModelScope.launch {
            val client = repository?.invidiousClient ?: vn.lobie.mytube.data.remote.InvidiousApiClient()
            val (alive, latency) = client.pingInstance(inst)
            val map = _instancePings.value.toMutableMap()
            map[inst] = if (alive) latency else -1L
            _instancePings.value = map
        }
    }

    val themeMode = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.THEME_SYSTEM)

    val defaultQuality = settingsDataStore.defaultQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.QUALITY_AUTO)

    val defaultSpeed = settingsDataStore.defaultSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val backgroundPlayback = settingsDataStore.backgroundPlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoPlayNext = settingsDataStore.autoPlayNext
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val cacheSizeMb = settingsDataStore.cacheSizeMb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 250)

    val contentLanguage = settingsDataStore.contentLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "vi")

    val contentRegion = settingsDataStore.contentRegion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.DEFAULT_REGION)

    val appLanguage = settingsDataStore.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.DEFAULT_APP_LANGUAGE)

    val uiMode = settingsDataStore.uiMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.UI_MODE_AUTO)

    private val _currentCacheBytes = MutableStateFlow<Long>(0)
    val currentCacheBytes: StateFlow<Long> = _currentCacheBytes.asStateFlow()

    init {
        calculateCacheSize()
    }

    fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = app.cacheDir
            val size = getDirSize(cacheDir)
            _currentCacheBytes.value = size
        }
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                deleteDir(app.cacheDir)
            } catch (_: Exception) {}
            calculateCacheSize()
        }
    }

    private fun deleteDir(dir: File): Boolean {
        val files = dir.listFiles() ?: return true
        for (file in files) {
            if (file.isDirectory) deleteDir(file)
            file.delete()
        }
        return true
    }

    fun setTheme(mode: String) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setQuality(quality: String) {
        viewModelScope.launch { settingsDataStore.setDefaultQuality(quality) }
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch { settingsDataStore.setDefaultSpeed(speed) }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setBackgroundPlayback(enabled) }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoPlayNext(enabled) }
    }

    fun setCacheLimit(sizeMb: Int) {
        viewModelScope.launch { settingsDataStore.setCacheSizeMb(sizeMb) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { settingsDataStore.setContentLanguage(lang) }
    }

    fun setRegion(region: String) {
        viewModelScope.launch { settingsDataStore.setContentRegion(region) }
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch { settingsDataStore.setAppLanguage(language) }
    }

    fun setUiMode(mode: String) {
        viewModelScope.launch { settingsDataStore.setUiMode(mode) }
    }

    fun clearWatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.watchHistoryDao().deleteAll()
        }
    }
}
