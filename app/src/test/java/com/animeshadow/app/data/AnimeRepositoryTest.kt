package com.animeshadow.app.data

import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.remote.PoliteInterceptor
import com.animeshadow.app.di.Network
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.testutil.FakeClock
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Offline-first behaviour against a local server that replays real AniList responses recorded on 2026-10-26. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimeRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: AnimeRepository
    private val clock = FakeClock()
    private val aniCalls = AtomicInteger()
    /** Going "offline" = the server disappears (connection refused), like a phone with no network. */
    private var offline: Boolean = false
        set(value) {
            field = value
            if (value) server.shutdown()
        }
    @Volatile private var jikanDown = false
    @Volatile private var graphQlError = false

    private fun json(name: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(Fixtures.text(name))

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.startsWith("/v4/anime/")) {
                    return if (jikanDown) MockResponse().setResponseCode(503) else json("jikan_anime_SYNTHETIC.json")
                }
                aniCalls.incrementAndGet()
                if (graphQlError) return MockResponse().setBody("""{"data":null,"errors":[{"message":"Too Many Requests.","status":429}]}""")
                val body = request.body.readUtf8()
                val page2 = body.contains("\"page\":2")
                return when {
                    body.contains("Media(id") -> json("anilist_detail.json")
                    body.contains("Character(id") -> json("anilist_character.json")
                    body.contains("Staff(id") -> json("anilist_staff.json")
                    body.contains("FAVOURITES_DESC") -> json("anilist_popular_characters.json")
                    body.contains("airingSchedules") -> json("anilist_schedule.json")
                    body.contains("id_in") -> json("anilist_batch.json")
                    body.contains("status:RELEASING") -> if (page2) json("anilist_season.json") else json("anilist_airing.json")
                    body.contains("\"search\"") -> json("anilist_search.json")
                    else -> json("anilist_top.json")
                }
            }
        }
        server.start()
        db = TestDb.inMemory()
        val client = Network.client(PoliteInterceptor(1000, 60_000, 0, maxRetries = 0))
        val base = server.url("/").toString()
        repo = AnimeRepository(db, Network.aniList(base, client), Network.jikan(base, client), clock)
    }

    @After fun tearDown() {
        db.close()
        runCatching { server.shutdown() }
    }

    @Test fun airing_refresh_caches_feed_in_order() = runTest {
        assertEquals(Refresh.Updated, repo.refreshAiring())
        val items = repo.observeFeed(AnimeRepository.FEED_AIRING).first()
        assertEquals(30, items.size)
        assertEquals(Fixtures.airing.map { it.id }, items.map { it.id })
        assertEquals("One Piece", items.first().displayTitle) // AniList "ONE PIECE", shown like MAL
    }

    @Test fun fresh_cache_is_not_refetched_until_ttl() = runTest {
        repo.refreshAiring()
        assertEquals(1, aniCalls.get())
        assertEquals(Refresh.Fresh, repo.refreshAiring())
        assertEquals(1, aniCalls.get())
        clock.advance(AnimeRepository.AIRING_TTL + 1)
        assertEquals(Refresh.Updated, repo.refreshAiring())
        assertEquals(2, aniCalls.get())
        assertEquals(Refresh.Updated, repo.refreshAiring(force = true))
        assertEquals(3, aniCalls.get())
    }

    @Test fun offline_keeps_showing_cached_data() = runTest {
        repo.refreshAiring()
        offline = true
        val r = repo.refreshAiring(force = true)
        assertTrue(r.toString(), r is Refresh.Failed && r.offline)
        assertEquals(30, repo.observeFeed(AnimeRepository.FEED_AIRING).first().size)
    }

    @Test fun offline_with_empty_cache_fails_without_crashing() = runTest {
        offline = true
        val r = repo.refreshAiring()
        assertTrue(r.toString(), r is Refresh.Failed && r.offline)
        assertTrue(repo.observeFeed(AnimeRepository.FEED_AIRING).first().isEmpty())
    }

    @Test fun graphql_error_is_reported_not_offline() = runTest {
        graphQlError = true
        val r = repo.refreshAiring()
        assertTrue(r is Refresh.Failed && !r.offline && r.message!!.contains("Too Many"))
    }

    @Test fun load_more_appends_next_page() = runTest {
        repo.refreshAiring()
        assertEquals(Refresh.Updated, repo.loadMoreAiring())
        val items = repo.observeFeed(AnimeRepository.FEED_AIRING).first()
        assertTrue(items.size > 30)
        assertEquals(Fixtures.airing.map { it.id }, items.take(30).map { it.id })
    }

    @Test fun detail_refresh_stores_everything_and_enriches_from_jikan() = runTest {
        assertEquals(Refresh.Updated, repo.refreshDetail(151807))
        val a = repo.observeAnime(151807).first()!!
        assertEquals("Solo Leveling", a.titleEnglish)
        assertEquals("俺だけレベルアップな件", a.titleNative)
        assertNotNull(a.description)
        assertEquals("HkIKAnwLZCw", a.trailerId)
        assertEquals(8.23, a.malScore!!, 0.0)
        assertEquals(25, repo.observeCharacters(151807).first().size)
        assertTrue(repo.observeStaff(151807).first().isNotEmpty())
        val animeRelations = Fixtures.detailDto.relations!!.edges.count { it.node?.type == "ANIME" }
        assertEquals(animeRelations, repo.observeRelations(151807).first().size)
        assertEquals(12, repo.observeRecommendations(151807).first().size)
        val calls = aniCalls.get()
        assertEquals(Refresh.Fresh, repo.refreshDetail(151807))
        assertEquals(calls, aniCalls.get())
    }

    @Test fun jikan_outage_does_not_break_details() = runTest {
        jikanDown = true
        assertEquals(Refresh.Updated, repo.refreshDetail(151807))
        val a = repo.observeAnime(151807).first()!!
        assertNull(a.malScore)
        assertNotNull(a.description)
    }

    @Test fun list_refresh_keeps_detail_fields() = runTest {
        repo.refreshDetail(151807)
        repo.browse(BrowseQuery(search = "solo leveling"), 1)
        val a = repo.observeAnime(151807).first()!!
        assertNotNull(a.description)
        assertNotNull(a.detailFetchedAt)
    }

    @Test fun search_online_then_offline_falls_back_to_local_cache() = runTest {
        val online = repo.browse(BrowseQuery(search = "solo leveling"), 1).getOrThrow()
        assertFalse(online.fromCache)
        assertEquals("Solo Leveling", online.items.first().displayTitle)
        offline = true
        val cached = repo.browse(BrowseQuery(search = "leveling"), 1).getOrThrow()
        assertTrue(cached.fromCache)
        assertTrue(cached.items.any { it.id == online.items.first().id })
        assertTrue(repo.browse(BrowseQuery(search = "leveling"), 2).isFailure)
    }

    @Test fun schedule_is_cached_with_airing_times() = runTest {
        assertEquals(Refresh.Updated, repo.refreshSchedule())
        val raw = Fixtures.schedule.filter { it.media?.isAdult != true }
        val rows = repo.observeSchedule(0, Long.MAX_VALUE).first()
        assertEquals(raw.size, rows.size)
        assertEquals(raw.first().airingAt * 1000, rows.first().airAt)
        assertEquals(Refresh.Fresh, repo.refreshSchedule())
    }

    @Test fun character_and_voice_actor_pages() = runTest {
        assertEquals(Refresh.Updated, repo.refreshCharacter(129928))
        val c = repo.observeCharacter(129928).first()!!
        assertEquals("Jin-U Seong", c.name)
        assertTrue(c.appearancesJson.contains("Taito Ban"))
        assertEquals(Refresh.Updated, repo.refreshStaff(151336))
        val s = repo.observeStaffMember(151336).first()!!
        assertEquals("Taito Ban", s.name)
        assertTrue(s.rolesJson.length > 10)
    }

    @Test fun popular_characters_are_cached_in_order() = runTest {
        repo.refreshPopularCharacters()
        val list = repo.observePopularCharacters().first()
        assertEquals(Fixtures.popularCharacters.map { it.id }, list.map { it.id })
    }

    @Test fun background_card_refresh_uses_one_request_per_50_ids() = runTest {
        val r = repo.refreshCards((1..60).toList())
        assertTrue(r.isSuccess)
        assertEquals(2, aniCalls.get())
    }
}
