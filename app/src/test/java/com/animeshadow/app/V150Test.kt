package com.animeshadow.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.auth.GoogleAccount
import com.animeshadow.app.data.auth.GoogleAccountRepository
import com.animeshadow.app.data.auth.GoogleSignInErrors
import com.animeshadow.app.data.auth.GoogleSignInResult
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.domain.Airing
import com.animeshadow.app.manga.data.MangaDatabase
import com.animeshadow.app.manga.data.MangaDexCount
import com.animeshadow.app.social.AppStatusRepository
import com.animeshadow.app.social.ExportList
import com.animeshadow.app.social.ListItem
import com.animeshadow.app.social.ListShare
import com.animeshadow.app.social.MaintenanceBus
import com.animeshadow.app.social.Me
import com.animeshadow.app.social.NotInterestedStore
import com.animeshadow.app.social.Pro
import com.animeshadow.app.social.SharedList
import com.animeshadow.app.social.SocialApi
import com.animeshadow.app.social.SocialException
import com.animeshadow.app.testutil.TestDb
import com.animeshadow.app.ui.status.MaintenanceText
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** v1.5.0 logic: airing rules, sign-in errors, server client (status/maintenance/session), lists export/import, Pro. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V150Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun prefs(name: String) = ctx.getSharedPreferences(name + System.nanoTime(), Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ airing (items 2)

    @Test fun airing_show_lists_announced_episodes_but_only_released_ones_are_markable() {
        val a = AnimeEntity(id = 1, status = "RELEASING", episodes = 12, nextEpisode = 5, nextAiringAt = 1L)
        assertEquals(4, Airing.released(a))
        assertEquals(12, Airing.listed(a))
        assertFalse(Airing.canComplete(a))
        assertTrue(Airing.canRate(a))
    }

    @Test fun airing_show_without_announced_total_lists_the_next_one() {
        val a = AnimeEntity(id = 1, status = "RELEASING", episodes = null, nextEpisode = 8, nextAiringAt = 1L)
        assertEquals(7, Airing.released(a))
        assertEquals(8, Airing.listed(a))
    }

    @Test fun not_yet_aired_cannot_be_rated_or_completed_and_finished_can() {
        val soon = AnimeEntity(id = 2, status = "NOT_YET_RELEASED", episodes = 12)
        assertEquals(0, Airing.released(soon)); assertFalse(Airing.canRate(soon)); assertFalse(Airing.canComplete(soon))
        val done = AnimeEntity(id = 3, status = "FINISHED", episodes = 24)
        assertEquals(24, Airing.released(done)); assertTrue(Airing.canRate(done)); assertTrue(Airing.canComplete(done))
    }

    // ------------------------------------------------------------------ Google sign-in errors (item 1)

    @Test fun google_error_10_is_reported_as_missing_android_client() {
        assertEquals(GoogleSignInResult.Developer, GoogleSignInErrors.classify("GetCredentialUnknownException", "[28444] Developer console is not set up correctly."))
        assertEquals(GoogleSignInResult.Developer, GoogleSignInErrors.classify("NoCredentialException", "10: Developer error"))
        assertEquals(GoogleSignInResult.Cancelled, GoogleSignInErrors.classify("GetCredentialCancellationException", null))
        assertEquals(GoogleSignInResult.NoAccount, GoogleSignInErrors.classify("NoCredentialException", "No credentials available"))
        assertEquals(GoogleSignInResult.Network, GoogleSignInErrors.classify("GetCredentialUnknownException", "7: network error"))
        assertEquals(GoogleSignInResult.PlayServices, GoogleSignInErrors.classify("GetCredentialProviderConfigurationException", "no provider"))
        assertTrue(GoogleSignInErrors.classify("GetCredentialInterruptedException", "x") is GoogleSignInResult.Failed)
    }

    @Test fun pro_from_the_server_shows_on_the_account_and_is_dropped_with_the_session() {
        val repo = GoogleAccountRepository(prefs("acct"), "web")
        repo.save(GoogleAccount("a@gmail.com", "A"))
        assertFalse(repo.account.value.pro)
        repo.saveSession("tok", Me(id = "u1", email = "a@gmail.com", pro = Pro(active = true, plan = "month", until = "2026-12-01T00:00:00Z")))
        assertTrue(repo.account.value.pro)
        assertEquals("tok", repo.session)
        repo.forgetSession()
        assertFalse(repo.account.value.pro); assertNull(repo.session)
        assertTrue(repo.account.value.signedIn) // the Google account itself stays
    }

    // ------------------------------------------------------------------ server: status, maintenance, broadcast

    @Test fun status_maintenance_and_broadcast_parse_and_a_broadcast_shows_once() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"maintenance":{"on":true,"message":"صيانة","until":"2026-10-30T15:30:00Z"},"broadcast":{"id":"b_1","title":"جديد","body":"نسخة 1.5.0","at":"x"},"sign_in_ready":true,"server_time":"x"}"""))
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SocialApi(server.url("/").toString(), { null })
            val repo = AppStatusRepository(api, prefs("st"))
            val s = repo.refresh()!!
            assertTrue(s.maintenance.on); assertEquals("صيانة", s.maintenance.message); assertTrue(s.signInReady)
            assertEquals("b_1", repo.unseenBroadcast(s)?.id)
            repo.markSeen(s.broadcast!!)
            assertNull(repo.unseenBroadcast(s))
            // the panel being down never changes the last answer (never locks the app by itself)
            assertEquals(s, repo.refresh())
            assertEquals("/animeshadow/v1/status", server.takeRequest().path)
        }
    }

    @Test fun a_maintenance_answer_from_any_call_signals_the_app_and_carries_the_message() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"MAINTENANCE","message":"نعود بعد قليل"}}"""))
            val api = SocialApi(server.url("/").toString(), { "tok" })
            val got = async { withTimeout(5_000) { MaintenanceBus.signals.first() } }
            kotlinx.coroutines.yield()
            try { api.friends(); fail("expected maintenance") } catch (e: SocialException) {
                assertEquals("MAINTENANCE", e.code); assertEquals("نعود بعد قليل", e.message); assertEquals(503, e.status)
            }
            got.await()
            assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun missing_route_and_offline_have_clear_arabic() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val api = SocialApi(server.url("/").toString(), { null })
            try { api.me(); fail() } catch (e: SocialException) { assertEquals(SocialApi.NOT_DEPLOYED, e.message) }
        }
        val dead = SocialApi("http://127.0.0.1:9", { null })
        try { dead.me(); fail() } catch (e: SocialException) { assertEquals("OFFLINE", e.code) }
    }

    @Test fun maintenance_until_reads_as_day_and_time() {
        assertEquals("الجمعة 18:30", MaintenanceText.until("2026-10-30T15:30:00Z", ZoneId.of("Asia/Aden")))
        assertNull(MaintenanceText.until(null)); assertNull(MaintenanceText.until("soon"))
    }

    // ------------------------------------------------------------------ lists export / import (item 7)

    private fun share(): ListShare {
        val db = TestDb.inMemory()
        val m = Room.inMemoryDatabaseBuilder(ctx, MangaDatabase::class.java).allowMainThreadQueries().build()
        return ListShare(db, m, NotInterestedStore(prefs("ni")), now = { 1_000L }).also { shareDb = db }
    }
    private var shareDb: com.animeshadow.app.data.local.AppDatabase? = null

    @Test fun export_json_csv_text_then_import_back_into_an_empty_library() = runBlocking {
        val a = share(); val db = shareDb!!
        db.animeDao().upsert(AnimeEntity(id = 21, titleEnglish = "One Piece, Egghead"))
        db.animeDao().upsert(AnimeEntity(id = 5, titleEnglish = "Bleach \"TYBW\""))
        db.libraryDao().upsert(LibraryEntryEntity(21, WatchStatus.COMPLETED, 0, null, 1, 1))
        db.libraryDao().upsert(LibraryEntryEntity(5, WatchStatus.DROPPED, 0, null, 1, 1))
        a.notInterested.toggle(ListItem(99, "Boring Show", "ANIME"))
        val lists = a.build(listOf("anime:COMPLETED", "anime:DROPPED", "anime:NOT_INTERESTED"))
        assertEquals(listOf(1, 1, 1), lists.map { it.items.size })

        val csv = a.toCsv(lists)
        assertTrue(csv.startsWith("\uFEFFlist,type,anilist_id,title,status,score\n"))
        assertTrue("\"One Piece, Egghead\"" in csv)
        assertTrue("\"Bleach \"\"TYBW\"\"\"" in csv)
        val txt = a.toText(lists)
        assertTrue(txt.startsWith("شاهدته (1)\n• One Piece, Egghead"))

        val json = a.toJson(lists)
        // a fresh phone imports it back
        val b = share(); val db2 = shareDb!!
        val r = b.import(b.parse(json))
        assertEquals(3, r.added); assertEquals(0, r.skipped)
        assertEquals(WatchStatus.COMPLETED, db2.libraryDao().get(21)?.status)
        assertEquals(WatchStatus.DROPPED, db2.libraryDao().get(5)?.status)
        assertTrue(b.notInterested.contains(99))
        // importing again never duplicates or overwrites
        val again = b.import(b.parse(json))
        assertEquals(2, again.skipped)
    }

    @Test fun a_list_sent_in_chat_imports_into_the_matching_list() = runBlocking {
        val a = share()
        val shared = SharedList("شاهدته", listOf(ListItem(7, "Naruto", "ANIME", "COMPLETED")))
        assertEquals("anime:COMPLETED", a.guessKey(shared))
        val r = a.import(listOf(ExportList(a.guessKey(shared), shared.title, shared.items)))
        assertEquals(1, r.added)
        assertEquals("manga:READING", a.guessKey(SharedList("أقرؤه", listOf(ListItem(1, "x", "MANGA")))))
        try { a.parse("not json"); fail() } catch (_: IllegalArgumentException) {}
    }

    // ------------------------------------------------------------------ manga chapters count (item 3)

    @Test fun mangadex_aggregate_gives_the_highest_whole_chapter() {
        val j = Json.parseToJsonElement("""{"volumes":{"1":{"chapters":{"1":{},"2":{},"2.5":{}}},"none":{"chapters":{"117":{},"none":{}}}}}""") as JsonObject
        assertEquals(117, MangaDexCount.maxChapter(j))
        val s = Json.parseToJsonElement("""{"data":[{"id":"x","attributes":{"links":{"al":"5"}}},{"id":"y","attributes":{"links":{"al":"30013"}}}]}""") as JsonObject
        assertEquals("y", MangaDexCount.matchId(s, 30013))
    }
}
