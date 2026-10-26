package com.animeshadow.app.data

import androidx.room.withTransaction
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.local.AiringEntity
import com.animeshadow.app.data.local.AnimeDao
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.CharacterEntity
import com.animeshadow.app.data.local.FeedItemEntity
import com.animeshadow.app.data.local.FeedMetaEntity
import com.animeshadow.app.data.local.RecommendationEntity
import com.animeshadow.app.data.local.RelationEntity
import com.animeshadow.app.data.local.StaffEntity
import com.animeshadow.app.data.remote.AniListApi
import com.animeshadow.app.data.remote.AniListQueries
import com.animeshadow.app.data.remote.GqlError
import com.animeshadow.app.data.remote.GqlRequest
import com.animeshadow.app.data.remote.JikanApi
import com.animeshadow.app.data.remote.MalInfo
import com.animeshadow.app.data.remote.MalPage
import com.animeshadow.app.data.remote.toMalInfo
import com.animeshadow.app.data.remote.MediaDto
import com.animeshadow.app.data.remote.PageDto
import com.animeshadow.app.domain.BrowseQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** Outcome of a network refresh. Cached data stays visible whatever happens. */
sealed interface Refresh {
    data object Updated : Refresh
    data object Fresh : Refresh
    data class Failed(val offline: Boolean, val message: String?) : Refresh
}

class ApiException(message: String) : IOException(message)

data class PageResult(val items: List<AnimeEntity>, val hasNext: Boolean, val fromCache: Boolean)

/**
 * Offline-first catalogue: every screen observes Room; network calls only refresh Room (with TTLs) and never
 * block reading. AniList is the source for lists, details, characters, staff and schedules; Jikan (MyAnimeList)
 * only adds the MAL score/members on the details page when it answers.
 */
class AnimeRepository(
    private val db: AppDatabase,
    private val aniList: AniListApi,
    private val jikan: JikanApi?,
    private val clock: Clock,
    private val malPage: MalPage? = null,
) {
    private val dao: AnimeDao = db.animeDao()

    companion object {
        const val FEED_AIRING = "airing"
        const val FEED_TOP = "top"
        const val FEED_POP_CHARACTERS = "popular_characters"
        const val AIRING_TTL = 30 * 60_000L
        const val LIST_TTL = 6 * 60 * 60_000L
        const val SCHEDULE_TTL = 60 * 60_000L
        const val DETAIL_TTL = 12 * 60 * 60_000L
        const val PERSON_TTL = 24 * 60 * 60_000L
        /** A failed background translation is retried after this long. */
        const val TRANSLATE_RETRY = 2 * 60 * 60_000L
        fun seasonFeed(year: Int, season: String) = "season|$year|$season"
    }

    // ---------- observing (never touches the network) ----------
    fun observeFeed(feed: String): Flow<List<AnimeEntity>> = dao.observeFeed(feed)
    fun observeFeedMeta(feed: String) = dao.observeFeedMeta(feed)
    fun observeAnime(id: Int) = dao.observe(id)
    fun observeCharacters(id: Int) = dao.observeCharacters(id)
    fun observeStaff(id: Int) = dao.observeStaff(id)
    fun observeRelations(id: Int) = dao.observeRelations(id)
    fun observeRecommendations(id: Int) = dao.observeRecommendations(id)
    fun observeCharacter(id: Int) = dao.observeCharacter(id)
    fun observeStaffMember(id: Int) = dao.observeStaffMember(id)
    fun observeSchedule(from: Long, to: Long) = dao.observeSchedule(from, to)
    fun observePopularCharacters(): Flow<List<CharacterEntity>> = dao.observeCharacterFeed(FEED_POP_CHARACTERS)

    suspend fun anime(id: Int) = dao.get(id)
    suspend fun cachedCandidates(limit: Int = 600) = dao.topCached(limit)
    suspend fun recommendationsFor(ids: List<Int>) = if (ids.isEmpty()) emptyList() else dao.recommendationsFor(ids)

    // ---------- helpers ----------
    private fun vars(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) when (v) {
            null -> Unit
            is Int -> put(k, v)
            is Long -> put(k, v)
            is Boolean -> put(k, v)
            is String -> put(k, v)
            is List<*> -> put(k, JsonArray(v.map { e -> if (e is Int) JsonPrimitive(e) else JsonPrimitive(e.toString()) }))
            else -> put(k, v.toString())
        }
    }

    private fun check(errors: List<GqlError>?) {
        if (!errors.isNullOrEmpty()) throw ApiException(errors.first().message ?: "AniList error")
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Throwable.toFailed() = Refresh.Failed(offline = this is IOException && this !is ApiException, message = message)

    /** Upserts card rows without wiping detail fields of rows already cached. */
    private suspend fun storeCards(media: List<MediaDto>): List<AnimeEntity> {
        if (media.isEmpty()) return emptyList()
        val now = clock.now()
        val old = dao.getByIds(media.map { it.id }).associateBy { it.id }
        val rows = media.distinctBy { it.id }.map { it.toCardEntity(now).mergedWith(old[it.id]) }
        dao.upsertAll(rows)
        notifySynopses(rows)
        return rows
    }

    private suspend fun fetchPage(query: String, variables: JsonObject): PageDto {
        val r = aniList.page(GqlRequest(query, variables))
        check(r.errors)
        return r.data?.page ?: throw ApiException("empty response")
    }

    private suspend fun isFresh(feed: String, ttl: Long): Boolean {
        val meta = dao.feedMeta(feed) ?: return false
        return clock.now() - meta.fetchedAt < ttl && dao.feedSize(feed) > 0
    }

    /** Refreshes page 1 of a paged feed (replacing it) unless it is still fresh. */
    private suspend fun refreshPagedFeed(feed: String, ttl: Long, force: Boolean, fetch: suspend (Int) -> PageDto): Refresh {
        if (!force && isFresh(feed, ttl)) return Refresh.Fresh
        return guarded {
            val page = fetch(1)
            db.withTransaction {
                val rows = storeCards(page.media)
                dao.clearFeed(feed)
                dao.insertFeedItems(rows.mapIndexed { i, a -> FeedItemEntity(feed, a.id, i) })
                dao.upsertFeedMeta(FeedMetaEntity(feed, clock.now(), 1, page.pageInfo?.hasNextPage ?: false))
            }
        }.fold({ Refresh.Updated }, { it.toFailed() })
    }

    private suspend fun loadMoreFeed(feed: String, fetch: suspend (Int) -> PageDto): Refresh {
        val meta = dao.feedMeta(feed) ?: return refreshPagedFeed(feed, 0, true, fetch)
        if (!meta.hasNext) return Refresh.Fresh
        return guarded {
            val next = meta.pages + 1
            val page = fetch(next)
            db.withTransaction {
                val start = dao.feedSize(feed)
                val rows = storeCards(page.media)
                dao.insertFeedItems(rows.mapIndexed { i, a -> FeedItemEntity(feed, a.id, start + i) })
                dao.upsertFeedMeta(meta.copy(pages = next, hasNext = page.pageInfo?.hasNextPage ?: false))
            }
        }.fold({ Refresh.Updated }, { it.toFailed() })
    }

    // ---------- feeds ----------
    private val airingFetch: suspend (Int) -> PageDto = { p -> fetchPage(AniListQueries.AIRING, vars("page" to p, "perPage" to 30)) }
    suspend fun refreshAiring(force: Boolean = false) = refreshPagedFeed(FEED_AIRING, AIRING_TTL, force, airingFetch)
    suspend fun loadMoreAiring() = loadMoreFeed(FEED_AIRING, airingFetch)

    private val topFetch: suspend (Int) -> PageDto = { p -> fetchPage(AniListQueries.BROWSE, vars("page" to p, "sort" to listOf("SCORE_DESC"))) }
    suspend fun refreshTop(force: Boolean = false) = refreshPagedFeed(FEED_TOP, LIST_TTL, force, topFetch)
    suspend fun loadMoreTop() = loadMoreFeed(FEED_TOP, topFetch)

    private fun seasonFetch(year: Int, season: String): suspend (Int) -> PageDto = { p ->
        fetchPage(AniListQueries.BROWSE, vars("page" to p, "sort" to listOf("POPULARITY_DESC"), "season" to season, "year" to year))
    }
    suspend fun refreshSeason(year: Int, season: String, force: Boolean = false) =
        refreshPagedFeed(seasonFeed(year, season), LIST_TTL, force, seasonFetch(year, season))
    suspend fun loadMoreSeason(year: Int, season: String) = loadMoreFeed(seasonFeed(year, season), seasonFetch(year, season))

    /**
     * One page of the catalogue (search + filters). Network first; when offline, page 1 falls back to a local title
     * search over everything cached so search still works without internet.
     */
    suspend fun browse(query: BrowseQuery, page: Int): Result<PageResult> {
        val net = guarded {
            val p = fetchPage(
                AniListQueries.BROWSE,
                vars(
                    "page" to page, "search" to query.search.trim().ifBlank { null },
                    "sort" to listOf(if (query.search.isNotBlank() && query.sort == com.animeshadow.app.domain.BrowseSort.POPULARITY) "SEARCH_MATCH" else query.sort.anilist),
                    "status" to query.status, "format" to query.format, "season" to query.season,
                    "year" to query.year, "genre" to query.genre, "adult" to query.adult,
                ),
            )
            PageResult(storeCards(p.media), p.pageInfo?.hasNextPage ?: false, fromCache = false)
        }
        if (net.isSuccess || page > 1) return net
        val local = if (query.search.isBlank()) dao.topCached(60) else dao.searchLocal(query.search.trim())
        val filtered = local.filter { a ->
            (query.status == null || a.status == query.status) && (query.format == null || a.format == query.format) &&
                (query.season == null || a.season == query.season) && (query.year == null || a.seasonYear == query.year) &&
                (query.genre == null || query.genre in a.genreList) && (query.adult == null || a.isAdult == query.adult)
        }
        return Result.success(PageResult(filtered, hasNext = false, fromCache = true))
    }

    // ---------- schedule ----------
    suspend fun refreshSchedule(force: Boolean = false): Refresh {
        val key = "schedule"
        if (!force) {
            val meta = dao.feedMeta(key)
            if (meta != null && clock.now() - meta.fetchedAt < SCHEDULE_TTL) return Refresh.Fresh
        }
        return guarded {
            val nowSec = clock.now() / 1000
            val from = nowSec - 24 * 3600
            val to = nowSec + 7 * 24 * 3600
            val all = mutableListOf<com.animeshadow.app.data.remote.AiringScheduleDto>()
            var page = 1
            do {
                val p = fetchPage(AniListQueries.SCHEDULE, vars("page" to page, "from" to from.toInt(), "to" to to.toInt()))
                all += p.airingSchedules
                page++
            } while (p.pageInfo?.hasNextPage == true && page <= 6)
            val visible = all.filter { it.media != null && it.media.isAdult != true }
            db.withTransaction {
                storeCards(visible.mapNotNull { it.media })
                dao.deleteAiringBefore((nowSec - 2 * 24 * 3600) * 1000)
                dao.upsertAiring(visible.map { AiringEntity(it.media!!.id, it.episode, it.airingAt * 1000) })
                dao.upsertFeedMeta(FeedMetaEntity(key, clock.now(), page - 1, false))
            }
        }.fold({ Refresh.Updated }, { it.toFailed() })
    }

    // ---------- details ----------
    suspend fun refreshDetail(id: Int, force: Boolean = false): Refresh {
        val old = dao.get(id)
        if (!force && old?.detailFetchedAt != null && clock.now() - old.detailFetchedAt < DETAIL_TTL) {
            enrichFromMal(old)
            return Refresh.Fresh
        }
        val result = guarded {
            val r = aniList.media(GqlRequest(AniListQueries.DETAIL, vars("id" to id)))
            check(r.errors)
            val m = r.data?.media ?: throw ApiException("not found")
            db.withTransaction {
                val now = clock.now()
                val related = m.relations?.edges.orEmpty().filter { it.node != null && (it.node.type == null || it.node.type == "ANIME") }
                val recs = m.recommendations?.nodes.orEmpty().filter { it.mediaRecommendation != null }
                storeCards(related.map { it.node!! } + recs.map { it.mediaRecommendation!! })
                dao.upsert(m.toDetailEntity(now, dao.get(id)))
                dao.get(id)?.let { notifySynopses(listOf(it)) }
                dao.clearCharacters(id); dao.upsertCharacters(m.characterRows())
                dao.clearStaff(id); dao.upsertStaff(m.staffRows())
                dao.clearRelations(id)
                dao.upsertRelations(related.mapIndexed { i, e -> RelationEntity(id, e.node!!.id, e.relationType ?: "OTHER", i) })
                dao.clearRecommendations(id)
                dao.upsertRecommendations(recs.mapIndexed { i, n -> RecommendationEntity(id, n.mediaRecommendation!!.id, n.rating ?: 0, i) })
            }
        }.fold({ Refresh.Updated }, { it.toFailed() })
        dao.get(id)?.let { enrichFromMal(it) }
        return result
    }

    /**
     * Best effort MyAnimeList enrichment (score, scorers, members, content rating, main picture):
     * Jikan first, then the MAL page itself. Failures are ignored and AniList data stays (cover + isAdult).
     * Returns the source that answered ("jikan" / "mal_page"), or null when neither did.
     */
    suspend fun enrichFromMal(anime: AnimeEntity, force: Boolean = false): String? {
        val mal = anime.idMal ?: return null
        val fetched = anime.malFetchedAt
        if (!force && fetched != null && anime.malSource != null && clock.now() - fetched < DETAIL_TTL) return anime.malSource
        val info: MalInfo = jikan?.let { api -> guarded { api.anime(mal).data?.toMalInfo() }.getOrNull() }
            ?: malPage?.let { page -> guarded { page.fetch(mal) }.getOrNull() }
            ?: return null
        dao.get(anime.id)?.let { cur ->
            dao.upsert(
                cur.copy(
                    malScore = info.score ?: cur.malScore,
                    malMembers = info.members ?: cur.malMembers,
                    malScoredBy = info.scoredBy ?: cur.malScoredBy,
                    malRating = info.rating ?: cur.malRating,
                    malPoster = info.poster ?: cur.malPoster,
                    malSource = info.source,
                    malFetchedAt = clock.now(),
                ),
            )
        }
        return info.source
    }

    /** Stores the on-device Arabic translation of the current English synopsis. */
    suspend fun saveArabicSynopsis(id: Int, source: String, arabic: String) {
        dao.get(id)?.takeIf { it.description == source }?.let { dao.upsert(it.copy(descriptionAr = arabic, descriptionArSrc = source.hashCode(), arFailedAt = null)) }
    }

    /** Synopses still waiting for their Arabic (see [AnimeDao.pendingTranslations]). */
    suspend fun pendingTranslations(limit: Int): List<AnimeEntity> = dao.pendingTranslations(clock.now() - TRANSLATE_RETRY, limit)

    suspend fun markTranslationFailed(id: Int) = dao.markTranslationFailed(id, clock.now())

    /**
     * Called whenever rows with an English synopsis were stored, so the background translator can have the Arabic
     * ready before the details page opens. Set by the app (WorkManager); null in tests.
     */
    @Volatile var onSynopsesStored: (() -> Unit)? = null

    private fun notifySynopses(rows: List<AnimeEntity>) {
        if (rows.any { !it.description.isNullOrBlank() && it.arabicSynopsis == null }) runCatching { onSynopsesStored?.invoke() }
    }

    // ---------- studio works (v1.1.1: the studio chip on the details page) ----------
    suspend fun studioWorks(name: String, page: Int): Result<PageResult> = guarded {
        val r = aniList.studio(GqlRequest(AniListQueries.STUDIO, vars("search" to name, "page" to page)))
        check(r.errors)
        val media = r.data?.studio?.media ?: throw ApiException("studio not found")
        PageResult(storeCards(media.nodes), media.pageInfo?.hasNextPage ?: false, fromCache = false)
    }

    suspend fun refreshCharacter(id: Int, force: Boolean = false): Refresh {
        val old = dao.getCharacter(id)
        if (!force && old != null && old.appearancesJson != "[]" && clock.now() - old.fetchedAt < PERSON_TTL) return Refresh.Fresh
        return guarded {
            val r = aniList.character(GqlRequest(AniListQueries.CHARACTER, vars("id" to id)))
            check(r.errors)
            val c = r.data?.character ?: throw ApiException("not found")
            storeCards(c.media?.edges.orEmpty().mapNotNull { it.node })
            dao.upsertCharacter(c.toEntity(clock.now()))
        }.fold({ Refresh.Updated }, { it.toFailed() })
    }

    suspend fun refreshStaff(id: Int, force: Boolean = false): Refresh {
        val old = dao.getStaffMember(id)
        if (!force && old != null && clock.now() - old.fetchedAt < PERSON_TTL) return Refresh.Fresh
        return guarded {
            val r = aniList.staff(GqlRequest(AniListQueries.STAFF, vars("id" to id)))
            check(r.errors)
            val s = r.data?.staff ?: throw ApiException("not found")
            dao.upsertStaffMember(s.toEntity(clock.now()))
        }.fold({ Refresh.Updated }, { it.toFailed() })
    }

    // ---------- popular characters ----------
    suspend fun refreshPopularCharacters(force: Boolean = false): Refresh {
        val feed = FEED_POP_CHARACTERS
        if (!force && isFresh(feed, LIST_TTL)) return Refresh.Fresh
        return guarded {
            val p = fetchPage(AniListQueries.POPULAR_CHARACTERS, vars("page" to 1))
            val now = clock.now()
            db.withTransaction {
                p.characters.forEach { c ->
                    val existing = dao.getCharacter(c.id)
                    val fresh = c.toEntity(now)
                    dao.upsertCharacter(
                        if (existing != null && existing.appearancesJson.length > fresh.appearancesJson.length) {
                            existing.copy(favourites = c.favourites, image = fresh.image ?: existing.image)
                        } else fresh.copy(fetchedAt = 0),
                    )
                }
                dao.clearFeed(feed)
                dao.insertFeedItems(p.characters.mapIndexed { i, c -> FeedItemEntity(feed, c.id, i) })
                dao.upsertFeedMeta(FeedMetaEntity(feed, now, 1, false))
            }
        }.fold({ Refresh.Updated }, { it.toFailed() })
    }


    // ---------- background sync ----------
    /** Re-reads the card fields (next episode etc.) of the given anime in one request per 50 ids. */
    suspend fun refreshCards(ids: List<Int>): Result<List<AnimeEntity>> = guarded {
        ids.distinct().chunked(50).flatMap { chunk ->
            val p = fetchPage(AniListQueries.BY_IDS, vars("ids" to chunk))
            storeCards(p.media)
        }
    }

    suspend fun clearCatalogueCache(): Int {
        dao.clearAllFeedMeta()
        return dao.pruneUnreferenced()
    }
}
