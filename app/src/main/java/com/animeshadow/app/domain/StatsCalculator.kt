package com.animeshadow.app.domain

import com.animeshadow.app.data.local.LibraryItem
import com.animeshadow.app.data.local.WatchStatus

data class LibraryStats(
    val total: Int,
    val byStatus: Map<WatchStatus, Int>,
    val episodesWatched: Int,
    /** Minutes from episodes whose duration is known. */
    val knownMinutes: Long,
    /** Watched episodes whose duration is unknown (counted with the default duration). */
    val episodesWithoutDuration: Int,
    val defaultMinutes: Int,
    val topGenres: List<Pair<String, Int>>,
    val meanUserScore: Double?,
) {
    val totalMinutes: Long get() = knownMinutes + episodesWithoutDuration.toLong() * defaultMinutes
    val usesDefaultDuration: Boolean get() = episodesWithoutDuration > 0
    val totalHours: Double get() = totalMinutes / 60.0
    fun count(status: WatchStatus) = byStatus[status] ?: 0
}

object StatsCalculator {
    fun compute(items: List<LibraryItem>, defaultMinutes: Int, topN: Int = 6): LibraryStats {
        val byStatus = WatchStatus.entries.associateWith { s -> items.count { it.entry.status == s } }
        var known = 0L
        var unknownEps = 0
        var episodes = 0
        for (item in items) {
            val p = item.entry.progress.coerceAtLeast(0)
            episodes += p
            val d = item.anime?.duration
            if (d != null && d > 0) known += p.toLong() * d else unknownEps += p
        }
        val genreCounts = HashMap<String, Int>()
        items.forEach { item -> item.anime?.genreList?.forEach { g -> genreCounts[g] = (genreCounts[g] ?: 0) + 1 } }
        val top = genreCounts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(topN).map { it.key to it.value }
        val scores = items.mapNotNull { it.entry.userScore }
        return LibraryStats(
            total = items.size,
            byStatus = byStatus,
            episodesWatched = episodes,
            knownMinutes = known,
            episodesWithoutDuration = unknownEps,
            defaultMinutes = defaultMinutes,
            topGenres = top,
            meanUserScore = if (scores.isEmpty()) null else scores.average(),
        )
    }
}
