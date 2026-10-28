package com.animeshadow.app.manga.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.animeshadow.app.manga.data.MangaKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ReaderDirection { VERTICAL, RTL, LTR }
enum class FitMode { WIDTH, HEIGHT, SCREEN }

/** How the hidden controls come back on the clean screen. */
enum class ControlsTap { CENTER, ANYWHERE }

/**
 * The built-in reader's settings. The defaults are what most people want, so nobody has to touch anything:
 * manhwa / manhua scroll top-to-bottom, manga turns pages right-to-left, black background, fit width, the screen
 * stays on, and the screen is CLEAN while reading (no bars; a tap in the middle shows the controls for 3 s).
 * [direction] null = automatic by origin.
 */
data class ReaderSettings(
    val direction: ReaderDirection? = null,
    val fit: FitMode = FitMode.WIDTH,
    val zoom: Boolean = true,
    /** 0..1, null = the phone's own brightness. */
    val brightness: Float? = null,
    val pageGapDp: Int = 0,
    val tapZones: Boolean = true,
    val volumeKeys: Boolean = false,
    val autoScroll: Boolean = false,
    /** 1..10. */
    val autoScrollSpeed: Int = 3,
    val keepScreenOn: Boolean = true,
    val cleanScreen: Boolean = true,
    val controlsTap: ControlsTap = ControlsTap.CENTER,
    val autoHide: Boolean = true,
    val prefetchNext: Boolean = true,
) {
    companion object {
        val DEFAULT = ReaderSettings()
        const val AUTO_HIDE_MS = 3_000L

        /** Manhwa (KR) and manhua (CN) are long vertical strips; Japanese manga reads right-to-left. */
        fun defaultDirection(kind: MangaKind): ReaderDirection = when (kind) {
            MangaKind.MANGA -> ReaderDirection.RTL
            MangaKind.MANHWA, MangaKind.MANHUA -> ReaderDirection.VERTICAL
        }
    }

    fun effectiveDirection(kind: MangaKind, titleOverride: ReaderDirection?): ReaderDirection =
        titleOverride ?: direction ?: defaultDirection(kind)
}

/** Stored in its own DataStore file ("manga_reader"), apart from the anime settings. */
class ReaderPrefs(private val store: DataStore<Preferences>) {
    private object K {
        val direction = stringPreferencesKey("direction")
        val fit = stringPreferencesKey("fit")
        val zoom = booleanPreferencesKey("zoom")
        val brightness = floatPreferencesKey("brightness")
        val gap = intPreferencesKey("page_gap")
        val tapZones = booleanPreferencesKey("tap_zones")
        val volume = booleanPreferencesKey("volume_keys")
        val autoScroll = booleanPreferencesKey("auto_scroll")
        val speed = intPreferencesKey("auto_scroll_speed")
        val keepOn = booleanPreferencesKey("keep_screen_on")
        val clean = booleanPreferencesKey("clean_screen")
        val controlsTap = stringPreferencesKey("controls_tap")
        val autoHide = booleanPreferencesKey("auto_hide")
        val prefetch = booleanPreferencesKey("prefetch_next")
        fun title(id: Int) = stringPreferencesKey("dir_title_$id")
    }

    val settings: Flow<ReaderSettings> = store.data.map { p -> read(p) }

    fun titleDirection(mangaId: Int): Flow<ReaderDirection?> =
        store.data.map { p -> p[K.title(mangaId)]?.let { v -> ReaderDirection.entries.firstOrNull { it.name == v } } }

    suspend fun current(): ReaderSettings = settings.first()

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        store.edit { p ->
            val now = transform(read(p))
            write(p, now)
        }
    }

    /** Per-title direction (optional): null removes the override. */
    suspend fun setTitleDirection(mangaId: Int, direction: ReaderDirection?) = store.edit { p ->
        if (direction == null) p.remove(K.title(mangaId)) else p[K.title(mangaId)] = direction.name
    }

    /** «استعادة الافتراضي»: every reader setting back to the defaults (and this title's override removed). */
    suspend fun reset(mangaId: Int? = null) = store.edit { p ->
        listOf(K.direction, K.fit, K.zoom, K.brightness, K.gap, K.tapZones, K.volume, K.autoScroll, K.speed, K.keepOn, K.clean, K.controlsTap, K.autoHide, K.prefetch)
            .forEach { p.remove(it) }
        if (mangaId != null) p.remove(K.title(mangaId))
    }

    private fun read(p: Preferences): ReaderSettings {
        val d = ReaderSettings.DEFAULT
        return ReaderSettings(
            direction = p[K.direction]?.let { v -> ReaderDirection.entries.firstOrNull { it.name == v } },
            fit = p[K.fit]?.let { v -> FitMode.entries.firstOrNull { it.name == v } } ?: d.fit,
            zoom = p[K.zoom] ?: d.zoom,
            brightness = p[K.brightness]?.takeIf { it in 0f..1f },
            pageGapDp = p[K.gap] ?: d.pageGapDp,
            tapZones = p[K.tapZones] ?: d.tapZones,
            volumeKeys = p[K.volume] ?: d.volumeKeys,
            autoScroll = p[K.autoScroll] ?: d.autoScroll,
            autoScrollSpeed = p[K.speed] ?: d.autoScrollSpeed,
            keepScreenOn = p[K.keepOn] ?: d.keepScreenOn,
            cleanScreen = p[K.clean] ?: d.cleanScreen,
            controlsTap = p[K.controlsTap]?.let { v -> ControlsTap.entries.firstOrNull { it.name == v } } ?: d.controlsTap,
            autoHide = p[K.autoHide] ?: d.autoHide,
            prefetchNext = p[K.prefetch] ?: d.prefetchNext,
        )
    }

    private fun write(p: MutablePreferences, s: ReaderSettings) {
        if (s.direction == null) p.remove(K.direction) else p[K.direction] = s.direction.name
        p[K.fit] = s.fit.name
        p[K.zoom] = s.zoom
        if (s.brightness == null) p.remove(K.brightness) else p[K.brightness] = s.brightness.coerceIn(0f, 1f)
        p[K.gap] = s.pageGapDp.coerceIn(0, 48)
        p[K.tapZones] = s.tapZones
        p[K.volume] = s.volumeKeys
        p[K.autoScroll] = s.autoScroll
        p[K.speed] = s.autoScrollSpeed.coerceIn(1, 10)
        p[K.keepOn] = s.keepScreenOn
        p[K.clean] = s.cleanScreen
        p[K.controlsTap] = s.controlsTap.name
        p[K.autoHide] = s.autoHide
        p[K.prefetch] = s.prefetchNext
    }
}
