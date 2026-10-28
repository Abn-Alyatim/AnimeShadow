package com.animeshadow.app.manga.data

/**
 * v1.3.1: the profile in MANGA mode shows manga numbers (1.3.0 showed the anime ones there): titles per status,
 * favourites, chapters watched to the end, your mean rating and top genres — all from manga.db only.
 */
data class MangaStats(
    val total: Int = 0,
    val byStatus: Map<ReadStatus, Int> = emptyMap(),
    val favorites: Int = 0,
    val chaptersWatched: Int = 0,
    val meanUserScore: Double? = null,
    val topGenres: List<Pair<String, Int>> = emptyList(),
    val coverUrl: String? = null,
) {
    fun count(s: ReadStatus): Int = byStatus[s] ?: 0
}

object MangaStatsCalculator {
    fun compute(library: List<LibraryRow>, favorites: List<LibraryRow>, chaptersWatched: Int): MangaStats {
        val listed = library.filter { it.entry.status != null }
        val scores = (listed + favorites).distinctBy { it.entry.mangaId }.mapNotNull { it.entry.userScore }
        val genres = listed.flatMap { it.manga?.genreList.orEmpty() }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5).map { it.key to it.value }
        val cover = (listed + favorites).mapNotNull { it.manga }.let { ms -> ms.firstOrNull { it.banner != null }?.banner ?: ms.firstOrNull()?.poster }
        return MangaStats(
            total = listed.size,
            byStatus = ReadStatus.entries.associateWith { s -> listed.count { it.entry.status == s } },
            favorites = favorites.size,
            chaptersWatched = chaptersWatched,
            meanUserScore = if (scores.isEmpty()) null else scores.average(),
            topGenres = genres,
            coverUrl = cover,
        )
    }
}
