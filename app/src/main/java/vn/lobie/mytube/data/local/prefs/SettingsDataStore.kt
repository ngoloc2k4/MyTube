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

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val QUALITY_AUTO = "auto"
        const val QUALITY_1080P = "1080p"
        const val QUALITY_720P = "720p"
        const val QUALITY_480P = "480p"
        const val QUALITY_360P = "360p"
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
        preferences[CONTENT_LANGUAGE] ?: "en"
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
}
