package com.animeshadow.app.domain

import com.animeshadow.app.data.local.LibraryItem
import com.animeshadow.app.data.local.WatchStatus
import kotlinx.serialization.Serializable

enum class LibrarySort { DATE_ADDED, NAME, YEAR, SCORE }

/** Local (offline) filtering and sorting of the user's library. */
@Serializable
data class LibraryFilter(
    /** AniList formats: TV, MOVIE, OVA, ONA, SPECIAL (TV_SHORT is treated as TV). Empty = all. */
    val formats: Set<String> = emptySet(),
    /** RELEASING, FINISHED, NOT_YET_RELEASED. Empty = all. */
    val airing: Set<String> = emptySet(),
    val year: Int? = null,
    /** Minimum global score, 0..10 (one decimal). */
    val minScore: Double? = null,
    /** Every selected genre must be present. */
    val genres: Set<String> = emptySet(),
    val sort: LibrarySort = LibrarySort.DATE_ADDED,
    val ascending: Boolean = false,
) {
    val isActive: Boolean get() = formats.isNotEmpty() || airing.isNotEmpty() || year != null || minScore != null || genres.isNotEmpty()

    fun apply(items: List<LibraryItem>, status: WatchStatus?): List<LibraryItem> {
        val filtered = items.filter { item ->
            val a = item.anime
            (status == null || item.entry.status == status) &&
                (formats.isEmpty() || normalizeFormat(a?.format) in formats) &&
                (airing.isEmpty() || a?.status in airing) &&
                (year == null || a?.seasonYear == year) &&
                (minScore == null || ((a?.score ?: 0) / 10.0) >= minScore) &&
                (genres.isEmpty() || (a != null && a.genreList.containsAll(genres)))
        }
        val comparator: Comparator<LibraryItem> = when (sort) {
            LibrarySort.DATE_ADDED -> compareBy { it.entry.addedAt }
            LibrarySort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.anime?.displayTitle ?: "" }
            LibrarySort.YEAR -> compareBy<LibraryItem> { it.anime?.seasonYear ?: 0 }.thenBy { it.anime?.displayTitle ?: "" }
            LibrarySort.SCORE -> compareBy<LibraryItem> { it.anime?.score ?: 0 }.thenBy { it.anime?.displayTitle ?: "" }
        }
        return filtered.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    companion object {
        val FORMATS = listOf("TV", "ONA", "OVA", "MOVIE", "SPECIAL")
        val AIRING = listOf("RELEASING", "FINISHED", "NOT_YET_RELEASED")
        fun normalizeFormat(format: String?): String? = when (format) {
            "TV_SHORT" -> "TV"
            "MUSIC" -> "SPECIAL"
            else -> format
        }
    }
}
