package com.animeshadow.app.data

import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.remote.MalPage
import com.animeshadow.app.data.remote.PoliteInterceptor
import com.animeshadow.app.di.Network
import com.animeshadow.app.domain.AgeRating
import com.animeshadow.app.domain.ContentRating
import com.animeshadow.app.testutil.FakeClock
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestDb
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Items 10 + 12: MAL score / scorers / content rating / main picture. Jikan first, the MAL page when Jikan is down,
 * AniList (cover + isAdult) when both are. The MAL page fixture is the real page recorded on 2026-10-26.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MalEnrichmentTest {
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: AnimeRepository
    private val clock = FakeClock()
    @Volatile private var jikanDown = false
    @Volatile private var malPageDown = false
    private val malPageCalls = AtomicInteger()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v4/anime/") ->
                        if (jikanDown) MockResponse().setResponseCode(503)
                        else MockResponse().setHeader("Content-Type", "application/json").setBody(Fixtures.text("jikan_anime_21_SYNTHETIC.json"))
                    path.startsWith("/anime/") -> {
                        malPageCalls.incrementAndGet()
                        if (malPageDown) MockResponse().setResponseCode(503) else MockResponse().setBody(Fixtures.text("mal_page_21_trimmed.html"))
                    }
                    else -> MockResponse().setHeader("Content-Type", "application/json").setBody(Fixtures.text("anilist_detail_onepiece.json"))
                }
            }
        }
        server.start()
        db = TestDb.inMemory()
        val client = Network.client(PoliteInterceptor(1000, 60_000, 0, maxRetries = 0))
        val base = server.url("/").toString()
        repo = AnimeRepository(db, Network.aniList(base, client), Network.jikan(base, client), clock, MalPage(client, base))
    }

    @After fun tearDown() {
        db.close(); server.shutdown()
    }

    @Test fun jikan_up_fills_mal_fields_and_poster() = runTest {
        repo.refreshDetail(21)
        val a = repo.anime(21)!!
        assertEquals("jikan", a.malSource)
        assertEquals(8.72, a.malScore!!, 0.001)
        assertEquals(1564769, a.malScoredBy)
        assertEquals("PG-13 - Teens 13 or older", a.malRating)
        assertEquals("https://cdn.myanimelist.net/images/anime/1244/138851l.jpg", a.malPoster)
        assertEquals(a.malPoster, a.posterUrl)
        assertEquals(AgeRating.TEEN, ContentRating.of(a.malRating, a.isAdult))
        assertEquals(0, malPageCalls.get())
    }

    @Test fun jikan_down_falls_back_to_the_mal_page() = runTest {
        jikanDown = true
        repo.refreshDetail(21)
        val a = repo.anime(21)!!
        assertEquals("mal_page", a.malSource)
        assertEquals(8.72, a.malScore!!, 0.001)
        assertEquals(1564769, a.malScoredBy)
        assertEquals(2739748, a.malMembers)
        assertEquals("PG-13 - Teens 13 or older", a.malRating)
        assertEquals("https://cdn.myanimelist.net/images/anime/1244/138851l.jpg", a.posterUrl)
    }

    @Test fun both_down_keeps_anilist_cover_and_isadult() = runTest {
        jikanDown = true
        malPageDown = true
        repo.refreshDetail(21)
        val a = repo.anime(21)!!
        assertNull(a.malSource)
        assertNull(a.malPoster)
        assertEquals("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-ELSYx3yMPcKM.jpg", a.posterUrl)
        assertNull(ContentRating.of(a.malRating, a.isAdult)) // One Piece is not adult on AniList: no badge rather than a guess
        assertEquals(AgeRating.ADULT, ContentRating.of(null, isAdult = true))
    }

    @Test fun mal_fields_are_cached_and_survive_a_list_refresh() = runTest {
        repo.refreshDetail(21)
        val before = repo.anime(21)!!
        // A card refresh (lists) must not wipe the detail-only MAL fields.
        val card = before.copy(malPoster = null, malRating = null, malSource = null).mergedWith(before)
        assertEquals(before.malPoster, card.malPoster)
        assertEquals(before.malRating, card.malRating)
        // Second open within 12 h: no new MAL request.
        jikanDown = true
        repo.refreshDetail(21)
        assertEquals(0, malPageCalls.get())
    }
}
