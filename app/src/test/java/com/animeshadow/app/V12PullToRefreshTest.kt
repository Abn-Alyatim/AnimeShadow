package com.animeshadow.app

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.Refresh
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.remote.MalPage
import com.animeshadow.app.data.remote.PoliteInterceptor
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.di.Network
import com.animeshadow.app.testutil.FakeClock
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestDb
import com.animeshadow.app.ui.feed.HomeViewModel
import com.animeshadow.app.ui.feed.TopViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * v1.2 item 1: pull-to-refresh must really go to AniList and the screen must show the new data.
 * A local server answers the airing feed with list A first and list B on every later call, so a refresh that
 * does nothing (v1.1) and a refresh that shows stale data both fail here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V12PullToRefreshTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: AnimeRepository
    private lateinit var settings: SettingsRepository
    private val clock = FakeClock()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val calls = AtomicInteger(0)
    @Volatile private var firstDelayMs = 0L
    @Volatile private var offline = false

    private val listA = Fixtures.text("anilist_airing.json")
    private val listB = Fixtures.text("anilist_top.json")

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val n = calls.incrementAndGet()
                if (offline) return MockResponse().setResponseCode(503)
                val r = MockResponse().setHeader("Content-Type", "application/json").setBody(if (n == 1) listA else listB)
                return if (n == 1 && firstDelayMs > 0) r.setBodyDelay(firstDelayMs, TimeUnit.MILLISECONDS) else r
            }
        }
        server.start()
        db = TestDb.inMemory()
        val client = Network.client(PoliteInterceptor(1000, 60_000, 0, maxRetries = 0))
        repo = AnimeRepository(db, Network.aniList(server.url("/").toString(), client), null, clock, null as MalPage?)
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("s.preferences_pb") })
    }

    @After fun tearDown() {
        db.close(); server.shutdown(); scope.cancel()
    }

    private var describe: () -> String = { "" }

    private fun waitFor(timeoutMs: Long = 10_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(30)
        }
        throw AssertionError("condition not met in $timeoutMs ms; calls=${calls.get()} ${describe()}")
    }

    private val idsA by lazy { Fixtures.airing.map { it.id } }
    private val idsB by lazy { Fixtures.top.map { it.id } }

    @Test fun pull_refetches_and_the_screen_shows_the_new_list() {
        val vm = HomeViewModel(repo, settings)
        val collector = CoroutineScope(Dispatchers.Main).launch { vm.ui.collect { } }
        waitFor { vm.ui.value.items.isNotEmpty() && !vm.ui.value.refreshing }
        assertEquals(idsA, vm.ui.value.items.map { it.id })
        assertEquals(1, calls.get())

        // The automatic load inside the cache TTL does not hit the network again…
        vm.refresh(false)
        waitFor { !vm.ui.value.refreshing }
        assertEquals(1, calls.get())

        // …but a pull always does, and the list on screen becomes the server's new list.
        vm.refresh(true)
        waitFor { vm.ui.value.items.map { it.id } == idsB && !vm.ui.value.refreshing }
        assertEquals(2, calls.get())
        assertNotEquals(idsA, idsB)
        assertNull(vm.ui.value.error)
        collector.cancel()
    }

    @Test fun pull_during_the_first_automatic_load_is_not_dropped() {
        firstDelayMs = 1_500 // slow network on the first load
        val vm = TopViewModel(repo, settings)
        describe = { vm.ui.value.let { "refreshing=${it.refreshing} n=${it.items.size} first=${it.items.firstOrNull()?.id} err=${it.error}" } }
        val collector = CoroutineScope(Dispatchers.Main).launch { vm.ui.collect { } }
        // The automatic load has reached the (slow) server and is waiting for its answer when the user pulls.
        waitFor { calls.get() == 1 && vm.ui.value.refreshing }
        vm.refresh(true) // v1.1 returned here without doing anything
        waitFor { vm.ui.value.items.map { it.id } == idsB && !vm.ui.value.refreshing }
        assertTrue(calls.get() >= 2)
        collector.cancel()
    }

    @Test fun failed_pull_keeps_the_cached_list_and_stops_the_indicator() {
        val vm = HomeViewModel(repo, settings)
        val collector = CoroutineScope(Dispatchers.Main).launch { vm.ui.collect { } }
        waitFor { vm.ui.value.items.isNotEmpty() && !vm.ui.value.refreshing }
        offline = true
        vm.refresh(true)
        waitFor { vm.ui.value.error != null && !vm.ui.value.refreshing }
        assertEquals(idsA, vm.ui.value.items.map { it.id })
        assertTrue(vm.ui.value.error is Refresh.Failed)
        assertFalse(vm.ui.value.refreshing)
        collector.cancel()
    }

    @Test fun my_list_pull_rereads_the_titles_from_anilist() = runBlocking {
        // Seed the library with one title from list A, then a pull re-reads its card in one batched request.
        val a = Fixtures.airing.first()
        db.animeDao().upsertAll(listOf(a))
        val lib = com.animeshadow.app.data.LibraryRepository(db, clock)
        lib.setStatus(a.id, com.animeshadow.app.data.local.WatchStatus.WATCHING)
        val vm = com.animeshadow.app.ui.library.MyListViewModel(lib, settings, repo)
        vm.refresh()
        waitFor { calls.get() == 1 && !vm.refreshing.value }
        val body = server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
        assertTrue(body.contains(a.id.toString()))
    }
}
