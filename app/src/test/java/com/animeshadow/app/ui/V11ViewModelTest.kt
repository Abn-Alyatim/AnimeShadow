package com.animeshadow.app.ui

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.remote.MalPage
import com.animeshadow.app.data.remote.PoliteInterceptor
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.data.translate.SynopsisTranslator
import com.animeshadow.app.di.Network
import com.animeshadow.app.testutil.FakeClock
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestDb
import com.animeshadow.app.ui.details.DetailsViewModel
import com.animeshadow.app.ui.feed.GenreViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections

/** The real ViewModels with a local server: Arabic synopsis wiring (item 2), genre list (item 11), mark-all (item 14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V11ViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: AnimeRepository
    private lateinit var library: LibraryRepository
    private lateinit var settings: SettingsRepository
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bodies: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private class FakeTranslator(private val answer: String?) : SynopsisTranslator {
        var calls = 0
        override suspend fun translate(english: String, keep: List<String>): String? { calls++; return answer }
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                bodies += body
                val json = if (body.contains("Media(id")) "anilist_detail_onepiece.json" else "anilist_search.json"
                return MockResponse().setHeader("Content-Type", "application/json").setBody(Fixtures.text(json))
            }
        }
        server.start()
        db = TestDb.inMemory()
        val client = Network.client(PoliteInterceptor(1000, 60_000, 0, maxRetries = 0))
        repo = AnimeRepository(db, Network.aniList(server.url("/").toString(), client), null, FakeClock(), null as MalPage?)
        library = LibraryRepository(db, FakeClock())
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("s.preferences_pb") })
    }

    @After fun tearDown() {
        db.close(); server.shutdown(); scope.cancel()
    }

    private fun waitFor(timeoutMs: Long = 10_000, cond: suspend () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return@runBlocking
            Thread.sleep(50)
        }
        throw AssertionError("condition not met in $timeoutMs ms")
    }

    private fun details(translator: SynopsisTranslator) =
        DetailsViewModel(SavedStateHandle(mapOf("id" to 21)), repo, library, FakeClock(), ctx, translator)

    @Test fun details_translates_the_synopsis_once_and_caches_it() {
        val t = FakeTranslator("ترجمة تجريبية")
        details(t)
        waitFor { repo.anime(21)?.arabicSynopsis != null }
        assertEquals("ترجمة تجريبية", runBlocking { repo.anime(21) }!!.arabicSynopsis)
        assertEquals(1, t.calls)
        // Opening it again: cached, no second translation.
        val t2 = FakeTranslator("x")
        details(t2)
        waitFor { bodies.isNotEmpty() }
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, t2.calls)
    }

    @Test fun details_keeps_english_when_translation_is_unavailable() {
        val t = FakeTranslator(null)
        details(t)
        waitFor { t.calls == 1 && repo.anime(21) != null }
        val a = runBlocking { repo.anime(21) }!!
        assertNull(a.arabicSynopsis)
        assertTrue(a.description!!.startsWith("Gold Roger"))
    }

    @Test fun mark_all_toggle_and_undo_through_the_viewmodel() {
        val vm = details(FakeTranslator(null))
        waitFor { repo.anime(21) != null }
        val collector = CoroutineScope(Dispatchers.Main).launch { vm.ui.collect { } }
        waitFor { vm.ui.value.anime != null }
        val count = vm.ui.value.episodeCount
        assertTrue(count > 1000) // One Piece: next episode 1181 -> 1180 out
        var marked: Boolean? = null
        vm.toggleAllWatched { marked = it }
        waitFor { marked != null && vm.ui.value.watched.size == count }
        assertEquals(true, marked)
        vm.undoBulk()
        waitFor { vm.ui.value.watched.isEmpty() }
        assertNull(runBlocking { library.entry(21) })
        collector.cancel()
    }

    @Test fun genre_screen_asks_anilist_for_that_genre() {
        val vm = GenreViewModel(SavedStateHandle(mapOf("name" to "Adventure")), repo, settings)
        waitFor { vm.ui.value.items.isNotEmpty() }
        assertTrue(bodies.any { it.contains("\"genre\":\"Adventure\"") })
        assertTrue(bodies.any { it.contains("\"adult\":false") })
    }
}
