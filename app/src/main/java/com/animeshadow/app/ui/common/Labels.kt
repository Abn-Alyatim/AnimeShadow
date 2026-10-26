package com.animeshadow.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.animeshadow.app.R
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.ui.theme.Palette
import java.time.DayOfWeek
import java.util.Locale

@StringRes fun statusRes(status: String?): Int? = when (status) {
    "RELEASING" -> R.string.status_RELEASING
    "FINISHED" -> R.string.status_FINISHED
    "NOT_YET_RELEASED" -> R.string.status_NOT_YET_RELEASED
    "CANCELLED" -> R.string.status_CANCELLED
    "HIATUS" -> R.string.status_HIATUS
    else -> null
}

@StringRes fun formatRes(format: String?): Int? = when (format) {
    "TV" -> R.string.format_TV
    "TV_SHORT" -> R.string.format_TV_SHORT
    "MOVIE" -> R.string.format_MOVIE
    "OVA" -> R.string.format_OVA
    "ONA" -> R.string.format_ONA
    "SPECIAL" -> R.string.format_SPECIAL
    "MUSIC" -> R.string.format_MUSIC
    else -> null
}

@StringRes fun seasonRes(season: String?): Int? = when (season) {
    "WINTER" -> R.string.season_WINTER
    "SPRING" -> R.string.season_SPRING
    "SUMMER" -> R.string.season_SUMMER
    "FALL" -> R.string.season_FALL
    else -> null
}

@StringRes fun watchStatusRes(status: WatchStatus): Int = when (status) {
    WatchStatus.WATCHING -> R.string.ws_WATCHING
    WatchStatus.PLAN_TO_WATCH -> R.string.ws_PLAN_TO_WATCH
    WatchStatus.COMPLETED -> R.string.ws_COMPLETED
    WatchStatus.ON_HOLD -> R.string.ws_ON_HOLD
    WatchStatus.DROPPED -> R.string.ws_DROPPED
}

fun watchStatusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.WATCHING -> Palette.Watching
    WatchStatus.PLAN_TO_WATCH -> Palette.Plan
    WatchStatus.COMPLETED -> Palette.Completed
    WatchStatus.ON_HOLD -> Palette.OnHold
    WatchStatus.DROPPED -> Palette.Dropped
}

@StringRes fun dayRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.SATURDAY -> R.string.day_SATURDAY
    DayOfWeek.SUNDAY -> R.string.day_SUNDAY
    DayOfWeek.MONDAY -> R.string.day_MONDAY
    DayOfWeek.TUESDAY -> R.string.day_TUESDAY
    DayOfWeek.WEDNESDAY -> R.string.day_WEDNESDAY
    DayOfWeek.THURSDAY -> R.string.day_THURSDAY
    DayOfWeek.FRIDAY -> R.string.day_FRIDAY
}

@StringRes fun sourceRes(source: String?): Int? = when (source) {
    "ORIGINAL" -> R.string.source_ORIGINAL
    "MANGA" -> R.string.source_MANGA
    "LIGHT_NOVEL" -> R.string.source_LIGHT_NOVEL
    "WEB_NOVEL" -> R.string.source_WEB_NOVEL
    "NOVEL" -> R.string.source_NOVEL
    "VIDEO_GAME" -> R.string.source_VIDEO_GAME
    "VISUAL_NOVEL" -> R.string.source_VISUAL_NOVEL
    "WEB_MANGA" -> R.string.source_WEB_MANGA
    "MANHWA" -> R.string.source_MANHWA
    null -> null
    else -> R.string.source_OTHER
}

@Composable fun label(@StringRes res: Int?, fallback: String?): String = res?.let { stringResource(it) } ?: fallback.orEmpty()

/** "8.7" from AniList's 0..100 score. Western digits on purpose: scores read the same in both languages. */
fun scoreText(score100: Int?): String? = score100?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it / 10.0) }

fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}

/** v1.1: genres in Arabic (ref 11 chips). Unknown AniList genres fall back to their English name. */
@StringRes fun genreRes(genre: String): Int? = when (genre) {
    "Action" -> R.string.genre_Action
    "Adventure" -> R.string.genre_Adventure
    "Comedy" -> R.string.genre_Comedy
    "Drama" -> R.string.genre_Drama
    "Ecchi" -> R.string.genre_Ecchi
    "Fantasy" -> R.string.genre_Fantasy
    "Hentai" -> R.string.genre_Hentai
    "Horror" -> R.string.genre_Horror
    "Mahou Shoujo" -> R.string.genre_Mahou_Shoujo
    "Mecha" -> R.string.genre_Mecha
    "Music" -> R.string.genre_Music
    "Mystery" -> R.string.genre_Mystery
    "Psychological" -> R.string.genre_Psychological
    "Romance" -> R.string.genre_Romance
    "Sci-Fi" -> R.string.genre_Sci_Fi
    "Slice of Life" -> R.string.genre_Slice_of_Life
    "Sports" -> R.string.genre_Sports
    "Supernatural" -> R.string.genre_Supernatural
    "Thriller" -> R.string.genre_Thriller
    else -> null
}

@Composable fun genreLabel(genre: String): String = label(genreRes(genre), genre)

/** "1,562,923" (Western digits, thousands separators) as the reference rating row shows counts. */
fun groupedCount(n: Int): String = String.format(Locale.US, "%,d", n)

/** The user's locale but with Western (Latin) digits, as the reference app shows numbers. */
fun latinLocale(base: Locale = Locale.getDefault()): Locale = Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build()

fun latinDateTimeFormat(): java.text.DateFormat = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, latinLocale())
fun latinTimeFormat(): java.text.DateFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, latinLocale())
