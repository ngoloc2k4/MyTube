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
    private val settingsDataStore: SettingsDataStore
) : AndroidViewModel(app) {

    private val db = MyTubeDatabase.getInstance(app)

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

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

    fun clearWatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.watchHistoryDao().deleteAll()
        }
    }
}
