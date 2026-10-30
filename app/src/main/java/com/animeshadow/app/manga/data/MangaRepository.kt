package com.animeshadow.app.manga.data

import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.translate.SynopsisTranslator
import com.animeshadow.app.manga.source.ChapterProvider
import com.animeshadow.app.manga.source.ChapterProviders
import com.animeshadow.app.manga.source.ProviderChapter
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/** Feeds of the manga home tabs, per kind filter (null = all three). */
enum class MangaFeed(val sort: String) {
    TRENDING("TRENDING_DESC"), POPULAR("POPULARITY_DESC"), TOP("SCORE_DESC");

    fun key(kind: MangaKind?) = name + ":" + (kind?.name ?: "ALL")
}

sealed interface MangaLoad {
    data object Idle : MangaLoad
    data object Loading : MangaLoad
    data object Done : MangaLoad
    data class Failed(val offline: Boolean) : MangaLoad
}

class MangaRepository(
    private val db: MangaDatabase,
    private val api: MangaApi,
    private val clock: Clock,
    private val translator: SynopsisTranslator? = null,
    private val provider: () -> ChapterProvider = { ChapterProviders.active },
    private val jikan: com.animeshadow.app.data.remote.JikanApi? = null,
) {
    private val dao get() = db.manga()

    fun observeFeed(feed: MangaFeed, kind: MangaKind?): Flow<List<MangaEntity>> = dao.observeFeed(feed.key(kind))
    fun observe(id: Int): Flow<MangaEntity?> = dao.observe(id)
    fun observeEntry(id: Int): Flow<MangaLibraryEntity?> = dao.observeEntry(id)

    /** One page of a feed from AniList, stored in manga.db (page 1 replaces, later pages append). Returns hasNext. */
    suspend fun refreshFeed(feed: MangaFeed, kind: MangaKind?, page: Int = 1): Boolean {
        val r = api.page(MangaQueries.page(page, feed.sort, kind?.countries?.first()))
        r.errors?.firstOrNull()?.let { throw IOException(it.message ?: "AniList error") }
        val p = r.data?.page ?: return false
        dao.replaceFeed(feed.key(kind), p.media.map { it.toEntity(clock.now(), detail = false) }, clock.now(), append = page > 1)
        return p.pageInfo?.hasNextPage == true
    }

    /** Search or genre list (not cached as a feed; the cards are cached for offline details). */
    suspend fun search(query: String?, genre: String?, kind: MangaKind?, page: Int = 1, tag: String? = null, adult: Boolean = false): Pair<List<MangaEntity>, Boolean> {
        val sort = if (!query.isNullOrBlank()) "SEARCH_MATCH" else "POPULARITY_DESC"
        val r = api.page(MangaQueries.page(page, sort, kind?.countries?.first(), genre, query, tag = tag, adult = if (adult) null else false))
        r.errors?.firstOrNull()?.let { throw IOException(it.message ?: "AniList error") }
        val items = r.data?.page?.media.orEmpty().map { it.toEntity(clock.now(), detail = false) }
        dao.upsertKeepingDetails(items)
        return items to (r.data?.page?.pageInfo?.hasNextPage == true)
    }

    /** Full details (synopsis, dates, source); kept in manga.db so a downloaded title opens offline. */
    suspend fun refreshDetails(id: Int) {
        val r = api.media(MangaQueries.detail(id))
        r.errors?.firstOrNull()?.let { throw IOException(it.message ?: "AniList error") }
        val m = r.data?.media ?: return
        val old = dao.get(id)
        val e = m.toEntity(clock.now(), detail = true)
        // v1.3.1: MAL score for the MAL cell (optional: Jikan down → the last known value, or no MAL cell).
        val mal = e.idMal?.let { mid -> runCatching { jikan?.manga(mid)?.data }.getOrNull() }
        dao.upsertOne(
            e.copy(
                descriptionAr = if (old?.description == e.description) old?.descriptionAr else null,
                malScore = mal?.score ?: old?.malScore,
                malScoredBy = (mal?.scored_by ?: mal?.members) ?: old?.malScoredBy,
            ),
        )
    }

    /** MyMemory translation of the synopsis (titles protected); stored once. */
    suspend fun translate(id: Int) {
        val t = translator ?: return
        val m = dao.get(id) ?: return
        val en = m.description?.takeIf { it.isNotBlank() } ?: return
        if (m.descriptionAr != null) return
        // Same call as the anime page: titles marked as titles so they stay untranslated.
        val keep = listOfNotNull(m.titleEnglish, m.titleRomaji).distinct().map { com.animeshadow.app.data.translate.MyMemoryTranslator.TITLE_PREFIX + it } + m.authorList
        val ar = runCatching { t.translate(en, keep) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        dao.setArabic(id, ar)
    }

    // ------------------------------------------------------------------ library (manga only)
    suspend fun setStatus(id: Int, status: ReadStatus?) {
        val e = dao.entry(id) ?: MangaLibraryEntity(id)
        dao.upsertEntry(e.copy(status = status, updatedAt = clock.now()))
        dao.pruneEntries()
    }

    suspend fun toggleFavorite(id: Int) {
        val e = dao.entry(id) ?: MangaLibraryEntity(id)
        dao.upsertEntry(e.copy(favorite = !e.favorite, updatedAt = clock.now()))
        dao.pruneEntries()
    }

    suspend fun setUserScore(id: Int, score: Int?) {
        val e = dao.entry(id) ?: MangaLibraryEntity(id)
        dao.upsertEntry(e.copy(userScore = score?.coerceIn(0, 10), updatedAt = clock.now()))
        dao.pruneEntries()
    }

    fun observeLibrary() = dao.observeLibrary()
    fun observeFavorites() = dao.observeFavorites()
    fun observeHistory() = dao.observeHistory()
    fun observeFinishedChapterCount() = dao.observeFinishedChapterCount()
    fun observeTitleHistory(id: Int) = dao.observeTitleHistory(id)

    suspend fun recordRead(mangaId: Int, chapterKey: String, number: String, page: Int, pageCount: Int) {
        val finished = pageCount > 0 && page >= pageCount - 1
        val old = dao.history(chapterKey)
        dao.upsertHistory(MangaHistoryEntity(chapterKey, mangaId, number, page, pageCount, clock.now(), finished || old?.finished == true))
        if (finished) {
            db.downloads().setRead(chapterKey, true)
            val e = dao.entry(mangaId) ?: MangaLibraryEntity(mangaId)
            dao.upsertEntry(e.copy(lastChapter = number, status = e.status ?: ReadStatus.READING, updatedAt = clock.now()))
        }
    }

    // ------------------------------------------------------------------ v1.5.0 chapter tracking list
    fun observeReadChapters(id: Int) = dao.observeReadChapters(id)

    /** Ticks/unticks one chapter; the title joins «أقرأه» and its «آخر فصل» follows the highest tick. */
    suspend fun setChapterRead(mangaId: Int, number: Int, read: Boolean) {
        if (read) dao.insertRead(listOf(MangaReadEntity(mangaId, number, clock.now()))) else dao.deleteRead(mangaId, number)
        afterTick(mangaId)
    }

    /** Long-press: every chapter from 1 to [number] is read. */
    suspend fun markReadUpTo(mangaId: Int, number: Int) {
        if (number < 1) return
        val now = clock.now()
        dao.insertRead((1..number).map { MangaReadEntity(mangaId, it, now) })
        afterTick(mangaId)
    }

    private suspend fun afterTick(mangaId: Int) {
        val max = dao.maxRead(mangaId)
        val e = dao.entry(mangaId) ?: MangaLibraryEntity(mangaId)
        val m = dao.get(mangaId)
        val total = m?.chapters
        val status = when {
            max == null -> e.status
            total != null && max >= total && m.status == "FINISHED" -> ReadStatus.COMPLETED
            e.status == null || e.status == ReadStatus.PLAN_TO_READ -> ReadStatus.READING
            else -> e.status
        }
        dao.upsertEntry(e.copy(lastChapter = max?.toString() ?: e.lastChapter, status = status, updatedAt = clock.now()))
        dao.pruneEntries()
    }

    /** Chapters to list: AniList's count, else the latest English chapter MangaDex knows (metadata only), else 0. */
    suspend fun trackedChapterCount(m: MangaEntity): Int = m.chapters?.takeIf { it > 0 }
        ?: runCatching { MangaDexCount.latest(m.id, listOfNotNull(m.titleEnglish, m.titleRomaji)) }.getOrNull() ?: 0

    suspend fun lastPage(chapterKey: String): Int = dao.history(chapterKey)?.page ?: 0

    // ------------------------------------------------------------------ chapters (pluggable provider)
    val providerAvailable: Boolean get() = provider().isAvailable

    suspend fun chapters(m: MangaEntity): List<ProviderChapter> {
        val p = provider()
        if (!p.isAvailable) return emptyList()
        return p.listChapters(m.id, listOfNotNull(m.titleEnglish, m.titleRomaji, m.titleNative)).sortedByDescending { it.numberValue }
    }
}
