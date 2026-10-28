package com.animeshadow.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.animeshadow.app.data.AppJson
import com.animeshadow.app.domain.LibraryFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

enum class AppLanguage(val tag: String) { SYSTEM(""), ARABIC("ar"), ENGLISH("en") }

/** v1.3: «أنمي | مانجا». Each mode has its own data (anime.db / manga.db); only this key says which one is showing. */
enum class AppMode { ANIME, MANGA }

data class Profile(
    val name: String = "",
    val handle: String = "",
    val bio: String = "",
    val avatarPath: String? = null,
)

data class Settings(
    val language: AppLanguage = AppLanguage.ARABIC,
    val notifications: Boolean = false,
    val saveFilters: Boolean = true,
    val savedFilter: LibraryFilter = LibraryFilter(),
    val defaultEpisodeMinutes: Int = 24,
    val gridMode: Boolean = true,
    val externalPlayer: String = "none",
    val seekSeconds: Int = 10,
    val profile: Profile = Profile(),
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val notifications = booleanPreferencesKey("notifications")
        val saveFilters = booleanPreferencesKey("save_filters")
        val savedFilter = stringPreferencesKey("saved_filter")
        val defaultMinutes = intPreferencesKey("default_episode_minutes")
        val gridMode = booleanPreferencesKey("grid_mode")
        val externalPlayer = stringPreferencesKey("external_player")
        val seekSeconds = intPreferencesKey("seek_seconds")
        val name = stringPreferencesKey("profile_name")
        val handle = stringPreferencesKey("profile_handle")
        val bio = stringPreferencesKey("profile_bio")
        val avatar = stringPreferencesKey("profile_avatar")
        val mode = stringPreferencesKey("app_mode")
        val mangaGrid = booleanPreferencesKey("manga_grid_mode")
    }

    /** v1.3 mode switch (instant: a DataStore key, nothing is reloaded or deleted). */
    val mode: Flow<AppMode> = store.data.map { p -> AppMode.entries.firstOrNull { it.name == p[Keys.mode] } ?: AppMode.ANIME }
    suspend fun setMode(mode: AppMode) = store.edit { it[Keys.mode] = mode.name }
    val mangaGrid: Flow<Boolean> = store.data.map { p -> p[Keys.mangaGrid] ?: true }
    suspend fun setMangaGrid(grid: Boolean) = store.edit { it[Keys.mangaGrid] = grid }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            language = AppLanguage.entries.firstOrNull { it.name == p[Keys.language] } ?: AppLanguage.ARABIC,
            notifications = p[Keys.notifications] ?: false,
            saveFilters = p[Keys.saveFilters] ?: true,
            savedFilter = p[Keys.savedFilter]?.let { runCatching { AppJson.decodeFromString<LibraryFilter>(it) }.getOrNull() }
                ?: LibraryFilter(),
            defaultEpisodeMinutes = p[Keys.defaultMinutes] ?: 24,
            gridMode = p[Keys.gridMode] ?: true,
            externalPlayer = p[Keys.externalPlayer] ?: "none",
            seekSeconds = p[Keys.seekSeconds] ?: 10,
            profile = Profile(p[Keys.name].orEmpty(), p[Keys.handle].orEmpty(), p[Keys.bio].orEmpty(), p[Keys.avatar]),
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setLanguage(language: AppLanguage) = store.edit { it[Keys.language] = language.name }
    suspend fun setNotifications(on: Boolean) = store.edit { it[Keys.notifications] = on }
    suspend fun setSaveFilters(on: Boolean) = store.edit { it[Keys.saveFilters] = on }
    suspend fun setSavedFilter(filter: LibraryFilter) = store.edit { it[Keys.savedFilter] = AppJson.encodeToString(filter) }
    suspend fun setDefaultEpisodeMinutes(minutes: Int) = store.edit { it[Keys.defaultMinutes] = minutes.coerceIn(1, 180) }
    suspend fun setGridMode(grid: Boolean) = store.edit { it[Keys.gridMode] = grid }
    suspend fun setExternalPlayer(value: String) = store.edit { it[Keys.externalPlayer] = value }
    suspend fun setSeekSeconds(value: Int) = store.edit { it[Keys.seekSeconds] = value }
    suspend fun setProfile(profile: Profile) = store.edit {
        it[Keys.name] = profile.name.trim()
        it[Keys.handle] = profile.handle.trim()
        it[Keys.bio] = profile.bio.trim()
        if (profile.avatarPath != null) it[Keys.avatar] = profile.avatarPath else it.remove(Keys.avatar)
    }
}
