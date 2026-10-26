package com.animeshadow.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.remote.MalPage
import com.animeshadow.app.data.remote.PoliteInterceptor
import com.animeshadow.app.data.translate.MyMemoryTranslator
import com.animeshadow.app.data.translate.NameGuard
import com.animeshadow.app.data.translate.SynopsisPrefetcher
import com.animeshadow.app.data.translate.SynopsisTranslator
import com.animeshadow.app.data.translate.TranslationQuotaException
import com.animeshadow.app.di.Network
import com.animeshadow.app.testutil.FakeClock
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestDb
import com.animeshadow.app.testutil.TestImages
import com.animeshadow.app.ui.details.DetailsActions
import com.animeshadow.app.ui.details.DetailsContent
import com.animeshadow.app.ui.details.DetailsUi
import com.animeshadow.app.ui.feed.GenreUi
import com.animeshadow.app.ui.feed.StudioContent
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import com.animeshadow.app.ui.theme.Palette
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/** v1.1.1: online translation with names kept, background pre-translation, studio works, list rows with synopses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V111Test {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: AnimeRepository
    private val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val gql: MutableList<String> = Collections.synchronizedList(mutableListOf())
    /** What the fake MyMemory answers: by default "ع(" + q + ")" so tokens survive. */
    @Volatile private var mm: (String) -> String = { q -> ok("ع($q)") }

    private fun ok(text: String, status: Int = 200, quota: Boolean = false) =
        """{"responseData":{"translatedText":${org.json.JSONObject.quote(text)},"match":1},"responseStatus":$status,"quotaFinished":$quota}"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.startsWith("/get")) {
                    val q = request.requestUrl!!.queryParameter("q")!!
                    queries += q
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(mm(q))
                }
                val body = request.body.readUtf8()
                gql += body
                val json = when {
                    body.contains("Studio(search") -> STUDIO_JSON
                    body.contains("Media(id") -> Fixtures.text("anilist_detail_onepiece.json")
                    else -> LIST_JSON
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(json)
            }
        }
        server.start()
        db = TestDb.inMemory()
        val client = Network.client(PoliteInterceptor(1000, 60_000, 0, maxRetries = 0))
        repo = AnimeRepository(db, Network.aniList(server.url("/").toString(), client), null, FakeClock(), null as MalPage?)
        TestImages.install(ApplicationProvider.getApplicationContext<Context>())
    }

    @After fun tearDown() {
        db.close(); server.shutdown()
    }

    private fun translator() = MyMemoryTranslator(OkHttpClient(), baseUrl = server.url("/").toString())

    // ---------- names ----------
    @Test fun name_guard_keeps_titles_and_full_character_names() {
        val g = NameGuard.protect(
            "Enter Monkey D. Luffy, who wants One Piece. When Luffy sails, Zoro follows.",
            titles = listOf("ONE PIECE"), names = listOf("Luffy Monkey", "Zoro Roronoa"),
        )
        assertEquals(setOf("One Piece", "Monkey D. Luffy", "Luffy", "Zoro"), g.tokens.values.toSet())
        assertTrue(g.text.startsWith("Enter {{77"))
        assertTrue(g.text.contains("When {{77")) // "When" itself is still translated
        val back = NameGuard.restore(g.text, g)!!
        assertTrue(back.contains("Monkey D. Luffy"))
        assertNull(NameGuard.restore("lost", g))
    }

    @Test fun mymemory_keeps_names_and_splits_long_text() = runBlocking {
        val long = (1..12).joinToString(" ") { "Sentence number $it about Asta and the Wizard King title, which is long enough." }
        val out = translator().translate(long, listOf("title:Black Clover", "Asta Staria"))!!
        assertTrue(queries.size >= 2)
        assertTrue(queries.all { it.length <= 480 })
        assertTrue(queries.none { it.contains("Asta") }) // the name never reaches the service
        assertTrue(out.contains("Asta"))
        assertTrue(out.startsWith("ع("))
    }

    @Test fun mymemory_retries_another_token_style_when_one_is_lost() = runBlocking {
        mm = { q -> ok("ع(" + q.replace(Regex("""\{\{\d+}}"""), "") + ")") } // drops {{n}} tokens, keeps Xa77nn
        val out = translator().translate("Luffy wants the treasure.", listOf("Luffy Monkey"))!!
        assertTrue(out.contains("Luffy"))
        assertTrue(queries.any { it.contains("Xa7701") })
    }

    @Test fun mymemory_quota_is_reported_and_errors_give_null() {
        mm = { ok("MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY", status = 429, quota = true) }
        var quota = false
        try { runBlocking { translator().translate("Hello there.") } } catch (_: TranslationQuotaException) { quota = true }
        assertTrue(quota)
        mm = { "not json" }
        assertNull(runBlocking { translator().translate("Hello there.") })
    }

    // ---------- lists carry synopsis + names, background pre-translation ----------
    @Test fun list_rows_carry_synopsis_and_names_and_ask_for_background_translation() = runBlocking {
        var kicks = 0
        repo.onSynopsesStored = { kicks++ }
        repo.browse(com.animeshadow.app.domain.BrowseQuery(search = "x"), 1).getOrThrow()
        val a = repo.anime(9001)!!
        assertTrue(a.description!!.startsWith("Asta and Yuno"))
        assertEquals("Asta Staria|Yuno Grinberryall", a.charNames)
        assertTrue(kicks >= 1)
        assertTrue(gql.first().contains("description(asHtml:false)"))
    }

    @Test fun prefetcher_translates_library_first_caches_and_stops_on_quota() = runBlocking {
        repo.browse(com.animeshadow.app.domain.BrowseQuery(search = "x"), 1).getOrThrow() // 9001, 9002 with synopses
        db.libraryDao().upsertAll(listOf(LibraryEntryEntity(9002, WatchStatus.WATCHING, 0, null, 1, 1)))
        val seen = mutableListOf<String>()
        val t = object : SynopsisTranslator {
            override suspend fun translate(english: String, keep: List<String>): String? { seen += english.take(4); return "عربي " + keep.size }
        }
        val out = SynopsisPrefetcher(repo, t).run()
        assertEquals(2, out.translated)
        assertEquals(listOf("Frie", "Asta"), seen) // 9002 (in the list) first
        assertNotNull(repo.anime(9001)!!.arabicSynopsis)
        assertFalse(out.morePending)
        // nothing left: a second pass does no work
        assertEquals(0, SynopsisPrefetcher(repo, t).run().translated)
        // quota: stops at once and says so
        db.animeDao().upsert(repo.anime(9001)!!.copy(descriptionAr = null, descriptionArSrc = null))
        val q = object : SynopsisTranslator {
            override suspend fun translate(english: String, keep: List<String>): String? = throw TranslationQuotaException("q")
        }
        assertTrue(SynopsisPrefetcher(repo, q).run().quotaReached)
    }

    @Test fun failed_translation_is_retried_later_not_in_a_loop() = runBlocking {
        repo.browse(com.animeshadow.app.domain.BrowseQuery(search = "x"), 1).getOrThrow()
        var calls = 0
        val t = object : SynopsisTranslator {
            override suspend fun translate(english: String, keep: List<String>): String? { calls++; return null }
        }
        val out = SynopsisPrefetcher(repo, t).run()
        assertEquals(2, calls)
        assertEquals(2, out.failed)
        assertTrue(repo.pendingTranslations(10).isEmpty()) // parked for TRANSLATE_RETRY
    }

    // ---------- studio works ----------
    @Test fun studio_works_load_from_anilist() = runBlocking {
        val r = repo.studioWorks("Toei Animation", 1).getOrThrow()
        assertEquals(listOf(9001, 9002), r.items.map { it.id })
        assertTrue(r.hasNext)
        assertTrue(gql.single().contains("\"search\":\"Toei Animation\""))
    }

    @Test fun studio_chip_opens_the_studio_list() {
        var opened: String? = null
        val ui = DetailsUi(anime = Fixtures.onePieceTranslated, entry = LibraryEntryEntity(21, WatchStatus.WATCHING, 3, 9, 0, 0), watched = setOf(1))
        compose.setContent {
            AnimeShadowTheme { Surface(color = Palette.Black) { Box(Modifier.fillMaxSize()) { DetailsContent(ui, DetailsActions(onStudio = { opened = it }), 0) } } }
        }
        compose.onNodeWithTag("details_list").performScrollToNode(hasTestTag("studio_chip"))
        compose.onNodeWithTag("studio_chip").performClick()
        assertEquals("Toei Animation", opened)
    }

    @Test fun studio_screen_shows_title_and_works() {
        val items = Fixtures.airing.take(4)
        compose.setContent {
            AnimeShadowTheme { StudioContent(GenreUi(genre = "Toei Animation", items = items, page = 1), {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("أعمال Toei Animation").assertIsDisplayed()
        compose.onNodeWithText(items.first().displayTitle).assertIsDisplayed()
    }

    @Test fun header_lines_read_like_ref_11() {
        val a = Fixtures.onePieceTranslated.copy(status = "RELEASING", nextAiringAt = Fixtures.NOW + 3_600_000L)
        compose.setContent {
            AnimeShadowTheme { Surface(color = Palette.Black) { Box(Modifier.fillMaxSize()) { DetailsContent(DetailsUi(anime = a), DetailsActions(), 0) } } }
        }
        // «مسلسل | 13+»: type and age only, no episode count.
        val type = compose.onNodeWithTag("type_line").fetchSemanticsNode().config
        val text = type[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        assertFalse(text, text.contains("حلقة"))
        // «قائمتي» with the list status right under it, not cut.
        compose.onNodeWithTag("my_list_status", useUnmergedTree = true).assertExists()
    }

    companion object {
        private fun media(id: Int, title: String, desc: String, names: List<String>) = """
            {"id":$id,"idMal":null,"title":{"romaji":"$title","english":"$title","native":null},"coverImage":{"large":null,"extraLarge":null,"color":null},
             "format":"TV","status":"RELEASING","episodes":12,"duration":24,"season":"FALL","seasonYear":2026,"averageScore":80,"meanScore":80,
             "popularity":${100000 - id},"genres":["Action"],"studios":{"nodes":[{"id":18,"name":"Toei Animation"}]},"nextAiringEpisode":null,
             "description":"$desc","characters":{"edges":[${names.mapIndexed { i, n -> """{"node":{"id":${id * 10 + i},"name":{"full":"$n"}}}""" }.joinToString(",")}]}}
        """.trimIndent()

        val M1 = media(9001, "Black Clover", "Asta and Yuno were abandoned at the same church.", listOf("Asta Staria", "Yuno Grinberryall"))
        val M2 = media(9002, "Frieren", "Frieren is an elf mage.", listOf("Frieren"))
        val LIST_JSON = """{"data":{"Page":{"pageInfo":{"hasNextPage":false},"media":[$M1,$M2]}}}"""
        val STUDIO_JSON = """{"data":{"Studio":{"id":18,"name":"Toei Animation","media":{"pageInfo":{"hasNextPage":true},"nodes":[$M1,$M2]}}}}"""
    }
}
