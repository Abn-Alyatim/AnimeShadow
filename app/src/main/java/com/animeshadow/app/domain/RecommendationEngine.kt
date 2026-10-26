package com.animeshadow.app.domain

import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.LibraryItem
import com.animeshadow.app.data.local.RecommendationEntity
import com.animeshadow.app.data.local.WatchStatus
import kotlin.math.sqrt

data class Recommendation(
    val anime: AnimeEntity,
    val score: Double,
    /** The library title that led to this suggestion ("if you liked ..."). */
    val because: AnimeEntity?,
    val sharedGenres: List<String>,
    val fromSource: Boolean,
)

/**
 * Local, explainable recommendations (no AI, no network):
 *  - every library entry gets a weight from its status and the user's own rating;
 *  - genre affinity = weighted sum over the library, normalised;
 *  - a candidate scores by genre affinity (60%), being recommended by AniList for a title the user liked (30%)
 *    and its global score (10%). Titles already in the library are never suggested.
 */
object RecommendationEngine {
    fun entryWeight(item: LibraryItem): Double {
        val base = when (item.entry.status) {
            WatchStatus.COMPLETED -> 1.0
            WatchStatus.WATCHING -> 0.8
            WatchStatus.PLAN_TO_WATCH -> 0.4
            WatchStatus.ON_HOLD -> 0.3
            WatchStatus.DROPPED -> -0.6
        }
        val rating = item.entry.userScore ?: return base
        val factor = 1.0 + (rating.coerceIn(1, 10) - 5) / 5.0 // 1 -> 0.2, 5 -> 1.0, 10 -> 2.0
        return if (base < 0) base * (2.0 - factor).coerceAtLeast(0.2) else base * factor
    }

    fun genreAffinity(library: List<LibraryItem>): Map<String, Double> {
        val raw = HashMap<String, Double>()
        for (item in library) {
            val genres = item.anime?.genreList.orEmpty()
            if (genres.isEmpty()) continue
            val w = entryWeight(item) / sqrt(genres.size.toDouble())
            genres.forEach { g -> raw[g] = (raw[g] ?: 0.0) + w }
        }
        val max = raw.values.maxOfOrNull { kotlin.math.abs(it) } ?: return emptyMap()
        if (max == 0.0) return emptyMap()
        return raw.mapValues { it.value / max }
    }

    fun recommend(
        library: List<LibraryItem>,
        candidates: List<AnimeEntity>,
        sourceRecs: List<RecommendationEntity>,
        limit: Int = 30,
    ): List<Recommendation> {
        if (library.isEmpty()) return emptyList()
        val inLibrary = library.map { it.entry.animeId }.toHashSet()
        val affinity = genreAffinity(library)
        val liked = library.filter { entryWeight(it) >= 0.8 && it.anime != null }
        val likedById = liked.associateBy { it.entry.animeId }
        // candidate id -> best (source weight, liked anime)
        val sourceBoost = HashMap<Int, Pair<Double, AnimeEntity>>()
        for (rec in sourceRecs) {
            val from = likedById[rec.animeId] ?: continue
            val v = entryWeight(from) / 2.0 * (rec.rating.coerceIn(0, 100) / 100.0).coerceAtLeast(0.2)
            val cur = sourceBoost[rec.recId]
            if (cur == null || v > cur.first) sourceBoost[rec.recId] = v to from.anime!!
        }
        return candidates.asSequence()
            .filter { it.id !in inLibrary && !it.isAdult }
            .distinctBy { it.id }
            .map { a ->
                val genres = a.genreList
                val genreScore = if (genres.isEmpty()) 0.0 else genres.sumOf { affinity[it] ?: 0.0 } / sqrt(genres.size.toDouble())
                val src = sourceBoost[a.id]
                val global = (a.score ?: 0) / 100.0
                val total = genreScore.coerceAtLeast(-1.0) * 0.6 + (src?.first ?: 0.0).coerceAtMost(1.0) * 0.3 + global * 0.1
                val because = src?.second ?: liked.maxByOrNull { l -> l.anime!!.genreList.count { it in genres } * entryWeight(l) }
                    ?.takeIf { l -> l.anime!!.genreList.any { it in genres } }?.anime
                val shared = because?.genreList?.filter { it in genres }.orEmpty()
                Recommendation(a, total, because, shared, src != null)
            }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    /** Local "similar anime": Jaccard similarity of genres, ties broken by global score. */
    fun similar(target: AnimeEntity, candidates: List<AnimeEntity>, limit: Int = 12): List<AnimeEntity> {
        val tg = target.genreList.toSet()
        if (tg.isEmpty()) return emptyList()
        return candidates.asSequence()
            .filter { it.id != target.id && !it.isAdult }
            .map { c ->
                val cg = c.genreList.toSet()
                val inter = cg.intersect(tg).size
                val union = (cg union tg).size
                c to if (union == 0) 0.0 else inter.toDouble() / union
            }
            .filter { it.second >= 0.34 }
            .sortedWith(compareByDescending<Pair<AnimeEntity, Double>> { it.second }.thenByDescending { it.first.score ?: 0 })
            .take(limit)
            .map { it.first }
            .toList()
    }
}
