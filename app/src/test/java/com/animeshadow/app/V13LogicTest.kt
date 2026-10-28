package com.animeshadow.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.AppMode
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.manga.data.DownloadState
import com.animeshadow.app.manga.data.MangaApi
import com.animeshadow.app.manga.data.MangaDatabase
import com.animeshadow.app.manga.data.MangaHistoryEntity
import com.animeshadow.app.manga.data.MangaKind
import com.animeshadow.app.manga.data.MangaMediaResponse
import com.animeshadow.app.manga.data.MangaPageResponse
import com.animeshadow.app.manga.data.MangaRepository
import com.animeshadow.app.manga.data.ReadStatus
import com.animeshadow.app.manga.download.ChapterDownloader
import com.animeshadow.app.manga.download.DownloadRunner
import com.animeshadow.app.manga.download.MangaDownloads
import com.animeshadow.app.manga.download.PartialDownloadException
import com.animeshadow.app.manga.reader.ControlsTap
import com.animeshadow.app.manga.reader.FitMode
import com.animeshadow.app.manga.reader.ReaderDirection
import com.animeshadow.app.manga.reader.ReaderPrefs
import com.animeshadow.app.manga.reader.ReaderSettings
import com.animeshadow.app.manga.source.ChapterPages
import com.animeshadow.app.manga.source.ChapterProvider
import com.animeshadow.app.manga.source.ChapterProviders
import com.animeshadow.app.manga.source.NoChapterProvider
import com.animeshadow.app.manga.source.ProviderChapter
import com.animeshadow.app.manga.ui.ChapterRow
import com.animeshadow.app.manga.ui.MangaTitleViewModel
import com.animeshadow.app.data.remote.GqlRequest
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.MangaFixtures
import com.animeshadow.app.testutil.TestDb
import com.animeshadow.app.ui.RootViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** v1.3 logic: mode switch persistence, reader defaults/reset, the stub provider, fast resumable downloads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V13LogicTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before fun setUp() { dir = File(ctx.cacheDir, "v13-" + System.nanoTime()).apply { mkdirs() } }
    @After fun tearDown() { scopes.forEach { it.cancel() }; dir.deleteRecursively(); ChapterProviders.active = NoChapterProvider }

    private fun store(name: String) = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }) { File(dir, "$name.preferences_pb") }
    private fun mangaDb() = Room.inMemoryDatabaseBuilder(ctx, MangaDatabase::class.java).allowMainThreadQueries().build()

    private val noApi = object : MangaApi {
        override suspend fun page(request: GqlRequest) = MangaPageResponse()
        override suspend fun media(request: GqlRequest) = MangaMediaResponse()
    }

    // ------------------------------------------------------------------ mode switch

    @Test fun mode_defaults_to_anime_and_the_switch_survives_a_restart() = runBlocking {
        val file = "settings"
        val s1 = SettingsRepository(store(file))
        assertEquals(AppMode.ANIME, s1.mode.first())
        s1.setMode(AppMode.MANGA)
        assertEquals(AppMode.MANGA, s1.mode.first())
        scopes.forEach { it.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() }; scopes.clear()
        // "Restart": a new DataStore on the same file reads the same mode.
        val s2 = SettingsRepository(store(file))
        assertEquals(AppMode.MANGA, s2.mode.first())
        s2.setMode(AppMode.ANIME)
        assertEquals(AppMode.ANIME, s2.mode.first())
    }

    @Test fun each_mode_keeps_its_own_library_and_switching_deletes_nothing() = runBlocking {
        val anime = TestDb.inMemory()
        val manga = mangaDb()
        val settings = SettingsRepository(store("s"))
        val lib = LibraryRepository(anime, Clock { Fixtures.NOW })
        val repo = MangaRepository(manga, noApi, Clock { Fixtures.NOW })
        val a = Fixtures.top.first()
        anime.animeDao().upsertAll(listOf(a))
        lib.setStatus(a.id, WatchStatus.WATCHING)
        val m = MangaFixtures.list.first()
        manga.manga().upsertOne(m)
        repo.setStatus(m.id, ReadStatus.READING)
        repo.toggleFavorite(m.id)
        repeat(3) { settings.setMode(AppMode.MANGA); settings.setMode(AppMode.ANIME) }
        assertEquals(listOf(a.id), lib.observeLibrary().first().map { it.entry.animeId })
        assertEquals(listOf(m.id), repo.observeLibrary().first().map { it.entry.mangaId })
        assertEquals(listOf(m.id), repo.observeFavorites().first().map { it.entry.mangaId })
        // Different files, different tables: the manga library never shows anime and vice versa.
        assertTrue(lib.observeLibrary().first().none { it.entry.animeId == m.id })
        anime.close(); manga.close()
    }

    @Test fun splash_holds_2s_online_and_5s_offline() {
        assertEquals(2_000L, RootViewModel.splashHoldMs(online = true))
        assertEquals(5_000L, RootViewModel.splashHoldMs(online = false))
    }

    // ------------------------------------------------------------------ reader settings

    @Test fun reader_defaults_by_origin_and_reset() = runBlocking {
        val prefs = ReaderPrefs(store("reader"))
        val d = prefs.current()
        assertEquals(ReaderSettings.DEFAULT, d)
        assertEquals(ReaderDirection.VERTICAL, d.effectiveDirection(MangaKind.MANHWA, null))
        assertEquals(ReaderDirection.VERTICAL, d.effectiveDirection(MangaKind.MANHUA, null))
        assertEquals(ReaderDirection.RTL, d.effectiveDirection(MangaKind.MANGA, null))
        assertEquals(FitMode.WIDTH, d.fit)
        assertTrue(d.keepScreenOn && d.cleanScreen && d.autoHide && d.tapZones && d.zoom)
        assertEquals(ControlsTap.CENTER, d.controlsTap)
        assertNull(d.brightness)
        assertFalse(d.autoScroll)

        prefs.update { it.copy(fit = FitMode.HEIGHT, pageGapDp = 8, cleanScreen = false, direction = ReaderDirection.LTR, brightness = 0.4f, controlsTap = ControlsTap.ANYWHERE) }
        prefs.setTitleDirection(42, ReaderDirection.VERTICAL)
        val c = prefs.current()
        assertEquals(FitMode.HEIGHT, c.fit)
        assertEquals(8, c.pageGapDp)
        assertFalse(c.cleanScreen)
        assertEquals(ReaderDirection.LTR, c.effectiveDirection(MangaKind.MANGA, null))
        // A per-title choice wins over the global one.
        assertEquals(ReaderDirection.VERTICAL, c.effectiveDirection(MangaKind.MANGA, prefs.titleDirection(42).first()))
        prefs.reset(42)
        assertEquals(ReaderSettings.DEFAULT, prefs.current())
        assertNull(prefs.titleDirection(42).first())
    }

    // ------------------------------------------------------------------ provider stub

    @Test fun shipped_provider_is_the_empty_stub() = runBlocking {
        assertEquals(NoChapterProvider, ChapterProviders.active)
        assertFalse(NoChapterProvider.isAvailable)
        assertTrue(NoChapterProvider.listChapters(1, listOf("x")).isEmpty())
        assertTrue(NoChapterProvider.pageUrls(ProviderChapter("1", "1")).urls.isEmpty())
        val repo = MangaRepository(mangaDb(), noApi, Clock { 0 })
        assertFalse(repo.providerAvailable)
        assertTrue(repo.chapters(MangaFixtures.list.first()).isEmpty())
    }

    @Test fun continue_button_picks_the_right_chapter() {
        val m = MangaFixtures.list.first()
        val rows = MangaFixtures.downloads(m, listOf(1, 2, 3)).map { ChapterRow.Local(it, false) }
        assertEquals("1", MangaTitleViewModel.continueFrom(rows, emptyList())?.number)
        val h = { n: Int, fin: Boolean, at: Long -> MangaHistoryEntity("demo:${m.id}-$n", m.id, "$n", 0, 17, at, fin) }
        assertEquals("2", MangaTitleViewModel.continueFrom(rows, listOf(h(2, false, 5)))?.number)
        assertEquals("3", MangaTitleViewModel.continueFrom(rows, listOf(h(1, true, 1), h(2, true, 9)))?.number)
        assertEquals("3", MangaTitleViewModel.continueFrom(rows, listOf(h(3, true, 9)))?.number)
    }

    // ------------------------------------------------------------------ downloads

    private fun pageServer(pages: Int, failFrom: Int? = null, concurrent: AtomicInteger? = null, peak: AtomicInteger? = null): MockWebServer {
        val server = MockWebServer()
        val fail = java.util.concurrent.atomic.AtomicBoolean(failFrom != null)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val n = request.path!!.substringAfterLast('/').substringBefore('.').toInt()
                if (fail.get() && failFrom != null && n >= failFrom) return MockResponse().setResponseCode(503)
                concurrent?.let { c -> val now = c.incrementAndGet(); peak?.updateAndGet { maxOf(it, now) }; Thread.sleep(60); c.decrementAndGet() }
                return MockResponse().setBody(Buffer().write(ByteArray(2048) { (n % 127).toByte() }))
            }
        }
        server.start()
        return server
    }

    private fun pagesOf(server: MockWebServer, n: Int) = ChapterPages((0 until n).map { server.url("/ch/$it.jpg").toString() })

    @Test fun pages_download_in_parallel() = runBlocking {
        val concurrent = AtomicInteger(); val peak = AtomicInteger()
        val server = pageServer(12, concurrent = concurrent, peak = peak)
        val out = File(dir, "par")
        val done = ChapterDownloader(OkHttpClient(), parallel = 5).download(pagesOf(server, 12), out)
        assertEquals(12, done)
        assertEquals(12, ChapterDownloader.localPages(out).size)
        assertTrue("peak parallel downloads was ${peak.get()}", peak.get() in 3..5)
        server.shutdown()
    }

    @Test fun a_dropped_download_resumes_from_the_page_it_stopped_at() = runBlocking {
        val server = MockWebServer()
        var failing = true
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val n = request.path!!.substringAfterLast('/').substringBefore('.').toInt()
                return if (failing && n >= 4) MockResponse().setResponseCode(503) else MockResponse().setBody("page-$n")
            }
        }
        server.start()
        val out = File(dir, "resume")
        val dl = ChapterDownloader(OkHttpClient(), parallel = 4, retries = 0, retryDelayMs = 1)
        val progress = mutableListOf<Int>()
        try {
            dl.download(pagesOf(server, 12), out) { d, _ -> progress += d }
            fail("expected a partial download")
        } catch (e: PartialDownloadException) {
            assertEquals(4, e.done)
            assertEquals(12, e.total)
        }
        assertEquals(4, ChapterDownloader.localPages(out).size)
        assertTrue(out.listFiles()!!.none { it.name.endsWith(".part") })
        val before = server.requestCount
        failing = false
        progress.clear()
        assertEquals(12, dl.download(pagesOf(server, 12), out) { d, _ -> progress += d })
        // Only the 8 missing pages were fetched again; progress starts at 4/12.
        assertEquals(8, server.requestCount - before)
        assertEquals(4, progress.first())
        assertEquals(12, progress.last())
        assertEquals("page-7", ChapterDownloader.pageFile(out, 7).readText())
        server.shutdown()
    }

    private class FakeRunner : DownloadRunner { var starts = 0; var stops = 0; override fun start() { starts++ }; override fun stop() { stops++ } }

    private class FakeProvider(private val server: MockWebServer, private val pages: Int) : ChapterProvider {
        override val id = "fake"
        override val displayName = "Fake"
        override val isAvailable = true
        override suspend fun listChapters(aniListId: Int, titles: List<String>) = (1..3).map { ProviderChapter("c$it", "$it") }
        override suspend fun pageUrls(chapter: ProviderChapter) = ChapterPages((0 until pages).map { server.url("/${chapter.id}/$it.jpg").toString() })
    }

    @Test fun queue_downloads_in_order_pauses_resumes_and_cancels() = runBlocking {
        val server = pageServer(6)
        val provider = FakeProvider(server, 6)
        ChapterProviders.active = provider
        val db = mangaDb()
        val runner = FakeRunner()
        val root = File(dir, "manga")
        val downloads = MangaDownloads(db, root, ChapterDownloader(OkHttpClient()), Clock { Fixtures.NOW }, runner) { provider }
        val m = MangaFixtures.list.first()
        val chapters = provider.listChapters(m.id, emptyList())
        chapters.forEach { downloads.enqueue(m, it) }
        assertEquals(3, runner.starts)
        // Drag chapter 3 to the top.
        downloads.reorder(listOf("fake:c3", "fake:c1", "fake:c2"))
        assertEquals("fake:c3", db.downloads().nextQueued()!!.chapterKey)
        downloads.pause("fake:c2")
        val order = mutableListOf<String>()
        downloads.runQueue { d, done, total -> if (done == total) order += d.chapterKey }
        assertEquals(listOf("fake:c3", "fake:c1"), order)
        assertEquals(DownloadState.DONE, db.downloads().get("fake:c1")!!.state)
        assertEquals(6, downloads.localPages(m.id, "fake:c1").size)
        assertEquals(DownloadState.PAUSED, db.downloads().get("fake:c2")!!.state)
        // The downloaded library shows the title with its 2 chapters, details from manga.db.
        val titles = downloads.observeDownloadedTitles().first()
        assertEquals(listOf(m.id to 2), titles.map { it.manga.id to it.chapterCount })
        downloads.resumeAll()
        downloads.runQueue()
        assertEquals(DownloadState.DONE, db.downloads().get("fake:c2")!!.state)
        // Re-enqueueing a finished chapter does nothing.
        downloads.enqueue(m, chapters[0])
        assertEquals(DownloadState.DONE, db.downloads().get("fake:c1")!!.state)
        // Delete a downloaded chapter: row and files go.
        downloads.deleteChapter("fake:c1")
        assertNull(db.downloads().get("fake:c1"))
        assertFalse(downloads.chapterDir(m.id, "fake:c1").exists())
        server.shutdown()
    }

    @Test fun network_drop_requeues_with_finished_pages_kept_and_cancel_all_cleans_up() = runBlocking {
        val server = pageServer(10, failFrom = 6)
        val provider = FakeProvider(server, 10)
        ChapterProviders.active = provider
        val db = mangaDb()
        val downloads = MangaDownloads(db, File(dir, "m2"), ChapterDownloader(OkHttpClient(), retries = 0, retryDelayMs = 1), Clock { 1 }, FakeRunner()) { provider }
        val m = MangaFixtures.list[1]
        downloads.enqueue(m, ProviderChapter("c1", "1"))
        try { downloads.runQueue(); fail("expected IOException") } catch (_: java.io.IOException) { }
        val d = db.downloads().get("fake:c1")!!
        assertEquals(DownloadState.QUEUED, d.state)
        assertEquals("network", d.error)
        assertEquals(6, d.donePages)
        assertEquals(10, d.totalPages)
        assertTrue(d.pagesJson!!.contains("/c1/9.jpg"))
        assertEquals(6, downloads.localPages(m.id, "fake:c1").size)
        downloads.cancelAll()
        assertNull(db.downloads().get("fake:c1"))
        assertFalse(downloads.chapterDir(m.id, "fake:c1").exists())
        server.shutdown()
    }

    @Test fun reading_records_history_and_marks_the_chapter_read() = runBlocking {
        val db = mangaDb()
        val repo = MangaRepository(db, noApi, Clock { 77 })
        val m = MangaFixtures.list.first()
        db.manga().upsertOne(m)
        db.downloads().upsertAll(MangaFixtures.downloads(m, listOf(5)))
        repo.recordRead(m.id, "demo:${m.id}-5", "5", 3, 17)
        assertEquals(3, repo.lastPage("demo:${m.id}-5"))
        assertFalse(db.downloads().get("demo:${m.id}-5")!!.read)
        repo.recordRead(m.id, "demo:${m.id}-5", "5", 16, 17)
        assertTrue(db.downloads().get("demo:${m.id}-5")!!.read)
        assertEquals(ReadStatus.READING, db.manga().entry(m.id)!!.status)
        assertEquals("5", db.manga().entry(m.id)!!.lastChapter)
        assertEquals(1, repo.observeHistory().first().size)
    }
}
