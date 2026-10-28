package com.animeshadow.app.manga.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.manga.data.DownloadEntity
import com.animeshadow.app.manga.data.DownloadWithManga
import com.animeshadow.app.manga.data.DownloadedTitle
import com.animeshadow.app.manga.data.HistoryRow
import com.animeshadow.app.manga.data.LibraryRow
import com.animeshadow.app.manga.data.MangaEntity
import com.animeshadow.app.manga.data.MangaFeed
import com.animeshadow.app.manga.data.MangaHistoryEntity
import com.animeshadow.app.manga.data.MangaKind
import com.animeshadow.app.manga.data.MangaLibraryEntity
import com.animeshadow.app.manga.data.MangaRepository
import com.animeshadow.app.manga.data.ReadStatus
import com.animeshadow.app.manga.download.MangaDownloads
import com.animeshadow.app.manga.source.ChapterProviders
import com.animeshadow.app.manga.source.ProviderChapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

// ------------------------------------------------------------------ home (trending / popular / top / genres)

data class MangaHomeUi(
    val kind: MangaKind? = null,
    val feeds: Map<MangaFeed, List<MangaEntity>> = emptyMap(),
    val loading: Set<MangaFeed> = emptySet(),
    val failed: Boolean = false,
    val offline: Boolean = false,
    val grid: Boolean = true,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MangaHomeViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val settings: SettingsRepository,
    private val state: SavedStateHandle,
) : ViewModel() {
    private val kind = MutableStateFlow(state.get<String>("kind")?.let { k -> MangaKind.entries.firstOrNull { it.name == k } })
    private val loading = MutableStateFlow<Set<MangaFeed>>(emptySet())
    private val error = MutableStateFlow<Boolean?>(null)
    private val pages = mutableMapOf<String, Int>()
    private val hasMore = mutableMapOf<String, Boolean>()

    private val feeds = kind.flatMapLatest { k ->
        combine(MangaFeed.entries.map { f -> repo.observeFeed(f, k).map { f to it } }) { it.toMap() }
    }

    val ui: StateFlow<MangaHomeUi> = combine(kind, feeds, loading, error, settings.mangaGrid) { k, f, l, e, g ->
        MangaHomeUi(k, f, l, failed = e != null, offline = e == true, grid = g)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaHomeUi())

    init { refreshAll() }

    fun setKind(k: MangaKind?) {
        if (kind.value == k) return
        kind.value = k
        state["kind"] = k?.name
        refreshAll()
    }

    fun refreshAll() = MangaFeed.entries.forEach { load(it, 1) }

    fun loadMore(feed: MangaFeed) {
        val key = feed.key(kind.value)
        if (hasMore[key] == false || feed in loading.value) return
        load(feed, (pages[key] ?: 1) + 1)
    }

    private fun load(feed: MangaFeed, page: Int) {
        val k = kind.value
        viewModelScope.launch {
            loading.value = loading.value + feed
            try {
                val more = repo.refreshFeed(feed, k, page)
                pages[feed.key(k)] = page
                hasMore[feed.key(k)] = more
                error.value = null
            } catch (e: IOException) {
                error.value = true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error.value = false
            } finally {
                loading.value = loading.value - feed
            }
        }
    }

    fun toggleGrid() = viewModelScope.launch { settings.setMangaGrid(!ui.value.grid) }
}

// ------------------------------------------------------------------ search / genre

data class MangaSearchUi(
    val query: String = "",
    val genre: String? = null,
    val kind: MangaKind? = null,
    val items: List<MangaEntity> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val grid: Boolean = true,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class MangaSearchViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val settings: SettingsRepository,
    state: SavedStateHandle,
) : ViewModel() {
    private val genre: String? = state.get<String>("name")
    private val _ui = MutableStateFlow(MangaSearchUi(genre = genre))
    private var page = 1
    private var more = true
    private var job: Job? = null
    val ui: StateFlow<MangaSearchUi> = combine(_ui, settings.mangaGrid) { u, g -> u.copy(grid = g) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaSearchUi(genre = genre))

    init {
        viewModelScope.launch {
            _ui.map { it.query.trim() to it.kind }.distinctUntilChanged().debounce(350).collect { search(reset = true) }
        }
    }

    fun setQuery(q: String) { _ui.value = _ui.value.copy(query = q) }
    fun setKind(k: MangaKind?) { _ui.value = _ui.value.copy(kind = k) }
    fun toggleGrid() = viewModelScope.launch { settings.setMangaGrid(!ui.value.grid) }

    fun loadMore() { if (more && !_ui.value.loading) search(reset = false) }

    fun search(reset: Boolean) {
        val u = _ui.value
        if (u.query.isBlank() && u.genre == null) {
            _ui.value = u.copy(items = emptyList(), loading = false, failed = false)
            if (reset) { job?.cancel(); job = viewModelScope.launch { searchPage(1, true, popular = true) } }
            return
        }
        job?.cancel()
        job = viewModelScope.launch { searchPage(if (reset) 1 else page + 1, reset, popular = false) }
    }

    private suspend fun searchPage(p: Int, reset: Boolean, popular: Boolean) {
        val u = _ui.value
        _ui.value = u.copy(loading = true, failed = false)
        try {
            val (items, hasNext) = repo.search(if (popular) null else u.query, u.genre, u.kind, p)
            page = p
            more = hasNext
            _ui.value = _ui.value.copy(items = if (reset) items else (_ui.value.items + items).distinctBy { it.id }, loading = false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _ui.value = _ui.value.copy(loading = false, failed = true)
        }
    }
}

// ------------------------------------------------------------------ title page (details + chapters)

sealed interface ChapterRow {
    val key: String
    val number: String
    data class Remote(val chapter: ProviderChapter, override val key: String, val download: DownloadEntity?, val read: Boolean) : ChapterRow {
        override val number: String get() = chapter.number
    }
    data class Local(val download: DownloadEntity, val read: Boolean) : ChapterRow {
        override val key: String get() = download.chapterKey
        override val number: String get() = download.chapterNumber
    }
}

data class MangaTitleUi(
    val manga: MangaEntity? = null,
    val entry: MangaLibraryEntity? = null,
    val downloadedOnly: Boolean = false,
    val providerAvailable: Boolean = false,
    val chapters: List<ChapterRow> = emptyList(),
    val chaptersLoading: Boolean = false,
    val newestFirst: Boolean = true,
    val continueRow: ChapterRow? = null,
    val failed: Boolean = false,
    val translating: Boolean = false,
)

@HiltViewModel
class MangaTitleViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val downloads: MangaDownloads,
    state: SavedStateHandle,
) : ViewModel() {
    val id: Int = state.get<Int>("id") ?: 0
    private val downloadedOnly: Boolean = state.get<Boolean>("downloaded") ?: false
    private val remote = MutableStateFlow<List<ProviderChapter>>(emptyList())
    private val flags = MutableStateFlow(Triple(false, true, false)) // chaptersLoading, newestFirst, failed

    val ui: StateFlow<MangaTitleUi> = combine(
        repo.observe(id), repo.observeEntry(id), downloads.observeTitleDownloads(id), repo.observeTitleHistory(id),
        combine(remote, flags) { r, f -> r to f },
    ) { m, e, dls, hist, (rem, f) ->
        build(m, e, dls, hist, rem, f.first, f.second, f.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaTitleUi(downloadedOnly = downloadedOnly))

    private fun build(
        m: MangaEntity?, e: MangaLibraryEntity?, dls: List<DownloadEntity>, hist: List<MangaHistoryEntity>,
        rem: List<ProviderChapter>, loading: Boolean, newest: Boolean, failed: Boolean,
    ): MangaTitleUi {
        val byKey = dls.associateBy { it.chapterKey }
        val readKeys = hist.filter { it.finished }.map { it.chapterKey }.toSet() + dls.filter { it.read }.map { it.chapterKey }
        val rows: List<ChapterRow> = if (downloadedOnly || !repo.providerAvailable) {
            if (downloadedOnly) dls.filter { it.state == com.animeshadow.app.manga.data.DownloadState.DONE }.map { ChapterRow.Local(it, it.chapterKey in readKeys) } else emptyList()
        } else {
            rem.map { c ->
                val key = ChapterProviders.chapterKey(ChapterProviders.active.id, c.id)
                ChapterRow.Remote(c, key, byKey[key], key in readKeys)
            }
        }
        val sorted = rows.sortedBy { it.number.toDoubleOrNull() ?: 0.0 }.let { if (newest) it.reversed() else it }
        return MangaTitleUi(
            manga = m, entry = e, downloadedOnly = downloadedOnly, providerAvailable = repo.providerAvailable,
            chapters = sorted, chaptersLoading = loading, newestFirst = newest,
            continueRow = continueFrom(rows, hist), failed = failed,
            translating = m != null && m.description != null && m.descriptionAr == null,
        )
    }

    init {
        viewModelScope.launch {
            try { repo.refreshDetails(id) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; flags.value = flags.value.copy(third = true) }
            runCatching { repo.translate(id) }
            loadChapters()
        }
    }

    private suspend fun loadChapters() {
        if (downloadedOnly || !repo.providerAvailable) return
        val m = repo.observe(id).first() ?: return
        flags.value = flags.value.copy(first = true)
        try { remote.value = repo.chapters(m) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } finally { flags.value = flags.value.copy(first = false) }
    }

    fun toggleSort() { flags.value = flags.value.copy(second = !flags.value.second) }
    fun setStatus(s: ReadStatus?) = viewModelScope.launch { repo.setStatus(id, s) }
    fun toggleFavorite() = viewModelScope.launch { repo.toggleFavorite(id) }
    fun setUserScore(s: Int?) = viewModelScope.launch { repo.setUserScore(id, s) }
    fun download(row: ChapterRow.Remote) = viewModelScope.launch { ui.value.manga?.let { downloads.enqueue(it, row.chapter) } }
    fun downloadAll() = viewModelScope.launch {
        val m = ui.value.manga ?: return@launch
        ui.value.chapters.filterIsInstance<ChapterRow.Remote>().filter { it.download == null }.sortedBy { it.chapter.numberValue }.forEach { downloads.enqueue(m, it.chapter) }
    }
    fun delete(key: String) = viewModelScope.launch { downloads.deleteChapter(key) }
    fun toggleRead(row: ChapterRow) = viewModelScope.launch { downloads.setRead(row.key, !rowRead(row)) }
    private fun rowRead(row: ChapterRow) = when (row) { is ChapterRow.Local -> row.read; is ChapterRow.Remote -> row.read }

    companion object {
        /** «تابع الفصل N»: the last chapter opened if unfinished, else the one after it, else the first one. */
        fun continueFrom(rows: List<ChapterRow>, hist: List<MangaHistoryEntity>): ChapterRow? {
            if (rows.isEmpty()) return null
            val asc = rows.sortedBy { it.number.toDoubleOrNull() ?: 0.0 }
            val last = hist.maxByOrNull { it.readAt } ?: return asc.first()
            val i = asc.indexOfFirst { it.key == last.chapterKey }
            return when {
                i < 0 -> asc.first()
                !last.finished -> asc[i]
                else -> asc.getOrNull(i + 1) ?: asc[i]
            }
        }
    }
}

// ------------------------------------------------------------------ library / favorites / history (manga only)

data class MangaLibraryUi(
    val status: ReadStatus = ReadStatus.READING,
    val byStatus: Map<ReadStatus, List<MangaEntity>> = emptyMap(),
    val favorites: List<MangaEntity> = emptyList(),
    val history: List<HistoryRow> = emptyList(),
    val grid: Boolean = true,
)

@HiltViewModel
class MangaLibraryViewModel @Inject constructor(private val repo: MangaRepository, private val settings: SettingsRepository) : ViewModel() {
    val ui: StateFlow<MangaLibraryUi> = combine(repo.observeLibrary(), repo.observeFavorites(), repo.observeHistory(), settings.mangaGrid) { lib: List<LibraryRow>, fav: List<LibraryRow>, hist: List<HistoryRow>, grid: Boolean ->
        MangaLibraryUi(
            byStatus = ReadStatus.entries.associateWith { s -> lib.filter { it.entry.status == s }.mapNotNull { it.manga } },
            favorites = fav.mapNotNull { it.manga },
            history = hist,
            grid = grid,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaLibraryUi())

    fun toggleGrid() = viewModelScope.launch { settings.setMangaGrid(!ui.value.grid) }
}

// ------------------------------------------------------------------ downloads

data class DownloadedUi(val titles: List<DownloadedTitle> = emptyList(), val grid: Boolean = true, val query: String = "")

@HiltViewModel
class DownloadedViewModel @Inject constructor(downloads: MangaDownloads, private val settings: SettingsRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    val ui: StateFlow<DownloadedUi> = combine(downloads.observeDownloadedTitles(), settings.mangaGrid, query) { t, g, q ->
        DownloadedUi(if (q.isBlank()) t else t.filter { it.manga.displayTitle.contains(q.trim(), ignoreCase = true) || it.manga.titleRomaji.orEmpty().contains(q.trim(), true) }, g, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadedUi())

    fun setQuery(q: String) { query.value = q }
    fun toggleGrid() = viewModelScope.launch { settings.setMangaGrid(!ui.value.grid) }
}

@HiltViewModel
class DownloadQueueViewModel @Inject constructor(private val downloads: MangaDownloads) : ViewModel() {
    val rows: StateFlow<List<DownloadWithManga>> = downloads.observeQueue().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun pauseAll() = viewModelScope.launch { downloads.pauseAll() }
    fun resumeAll() = viewModelScope.launch { downloads.resumeAll() }
    fun cancelAll() = viewModelScope.launch { downloads.cancelAll() }
    fun pause(key: String) = viewModelScope.launch { downloads.pause(key) }
    fun resume(key: String) = viewModelScope.launch { downloads.resume(key) }
    fun cancel(key: String) = viewModelScope.launch { downloads.cancel(key) }
    fun toTop(key: String) = viewModelScope.launch { downloads.moveToTop(key) }
    fun reorder(keys: List<String>) = viewModelScope.launch { downloads.reorder(keys) }
}
