package vn.lobie.mytube.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mytube_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val CONTENT_REGION = stringPreferencesKey("content_region")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val UI_MODE = stringPreferencesKey("ui_mode")
        val SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")
        val RETURN_DISLIKE_ENABLED = booleanPreferencesKey("return_dislike_enabled")
        val INVIDIOUS_INSTANCES = stringPreferencesKey("invidious_instances")
        val AUDIO_NORMALIZATION_ENABLED = booleanPreferencesKey("audio_normalization_enabled")
        val CROSSFADE_DURATION_SECONDS = intPreferencesKey("crossfade_duration_seconds")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val QUALITY_AUTO = "auto"
        const val QUALITY_1080P = "1080p"
        const val QUALITY_720P = "720p"
        const val QUALITY_480P = "480p"
        const val QUALITY_360P = "360p"

        const val DEFAULT_REGION = "VN"
        const val DEFAULT_APP_LANGUAGE = "vi"

        const val UI_MODE_AUTO = "auto"
        const val UI_MODE_PHONE = "phone"
        const val UI_MODE_TABLET = "tablet"
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: THEME_SYSTEM
    }

    val defaultQuality: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_QUALITY] ?: QUALITY_AUTO
    }

    val defaultSpeed: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_SPEED] ?: 1.0f
    }

    val backgroundPlayback: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BACKGROUND_PLAYBACK] ?: false
    }

    val autoPlayNext: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_PLAY_NEXT] ?: true
    }

    val cacheSizeMb: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CACHE_SIZE_MB] ?: 250
    }

    val contentLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CONTENT_LANGUAGE] ?: "vi"
    }

    val contentRegion: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CONTENT_REGION] ?: DEFAULT_REGION
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE] ?: DEFAULT_APP_LANGUAGE
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_QUALITY] = quality
        }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_SPEED] = speed
        }
    }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BACKGROUND_PLAYBACK] = enabled
        }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PLAY_NEXT] = enabled
        }
    }

    suspend fun setCacheSizeMb(sizeMb: Int) {
        context.dataStore.edit { preferences ->
            preferences[CACHE_SIZE_MB] = sizeMb
        }
    }

    suspend fun setContentLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[CONTENT_LANGUAGE] = language
        }
    }

    suspend fun setContentRegion(region: String) {
        context.dataStore.edit { preferences ->
            preferences[CONTENT_REGION] = region
        }
    }

    val uiMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[UI_MODE] ?: UI_MODE_AUTO
    }

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = language
        }
    }

    suspend fun setUiMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[UI_MODE] = mode
        }
    }

    val sponsorBlockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SPONSOR_BLOCK_ENABLED] ?: true
    }

    suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SPONSOR_BLOCK_ENABLED] = enabled
        }
    }

    val returnDislikeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[RETURN_DISLIKE_ENABLED] ?: true
    }

    suspend fun setReturnDislikeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RETURN_DISLIKE_ENABLED] = enabled
        }
    }

    val invidiousInstances: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[INVIDIOUS_INSTANCES]
        if (raw.isNullOrBlank()) {
            vn.lobie.mytube.data.remote.InvidiousApiClient.DEFAULT_INSTANCES
        } else {
            raw.split("\n").filter { it.isNotBlank() }
        }
    }

    suspend fun setInvidiousInstances(instances: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[INVIDIOUS_INSTANCES] = instances.joinToString("\n")
        }
    }

    val audioNormalizationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUDIO_NORMALIZATION_ENABLED] ?: true
    }

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUDIO_NORMALIZATION_ENABLED] = enabled
        }
    }

    val crossfadeDurationSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CROSSFADE_DURATION_SECONDS] ?: 0
    }

    suspend fun setCrossfadeDurationSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[CROSSFADE_DURATION_SECONDS] = seconds
        }
    }

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[SEARCH_HISTORY]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    suspend fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[SEARCH_HISTORY]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toMutableList() ?: mutableListOf()
            current.remove(trimmed)
            current.add(0, trimmed)
            val limited = current.take(20)
            preferences[SEARCH_HISTORY] = limited.joinToString("\n")
        }
    }

    suspend fun removeSearchHistory(query: String) {
        val trimmed = query.trim()
        context.dataStore.edit { preferences ->
            val current = preferences[SEARCH_HISTORY]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toMutableList() ?: mutableListOf()
            current.remove(trimmed)
            preferences[SEARCH_HISTORY] = current.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_HISTORY] = ""
        }
    }
}
