package com.animeshadow.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.animeshadow.app.chat.ChatBackend
import com.animeshadow.app.chat.ChatCard
import com.animeshadow.app.chat.ChatConversation
import com.animeshadow.app.chat.ChatMessage
import com.animeshadow.app.chat.ChatRepository
import com.animeshadow.app.chat.ChatResult
import com.animeshadow.app.chat.ChatStore
import com.animeshadow.app.chat.WireLibrary
import com.animeshadow.app.chat.WireReply
import com.animeshadow.app.chat.WireRequest
import com.animeshadow.app.chat.WireStatus
import com.animeshadow.app.data.settings.AppMode
import com.animeshadow.app.data.settings.ChatPrefs
import com.animeshadow.app.data.settings.ExtraPrefs
import com.animeshadow.app.domain.AniTags
import com.animeshadow.app.ui.ads.Interstitials
import com.animeshadow.app.ui.watch.OfficialLink
import com.animeshadow.app.ui.watch.OfficialLinksRepository
import com.animeshadow.app.ui.watch.OfficialTrailer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V140LogicTest {
    private fun prefs(): ExtraPrefs {
        val dir = Files.createTempDirectory("xp").toFile()
        return ExtraPrefs(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(dir, "x.preferences_pb") })
    }

    @Test fun every_anilist_genre_and_tag_is_listed_and_adult_ones_are_hidden_by_default() {
        assertEquals(19, AniTags.GENRES.size)
        assertTrue("Ecchi" in AniTags.genres(false))
        assertFalse("Hentai" in AniTags.genres(false))
        assertTrue("Hentai" in AniTags.genres(true))
        assertTrue(AniTags.TAGS.size >= 400)
        val safe = AniTags.tags(false)
        assertTrue(safe.none { it.adult || it.category == AniTags.SEXUAL })
        assertEquals(AniTags.TAGS.size, AniTags.tags(true).size)
        assertTrue(AniTags.isAdultTag(AniTags.TAGS.first { it.adult }.name))
        assertFalse(AniTags.isAdultTag("Isekai"))
        assertEquals("إيسيكاي", AniTags.ar("Isekai"))
        assertEquals("هنتاي", AniTags.ar("Hentai"))
        assertEquals("NotATag", AniTags.ar("NotATag"))
    }

    @Test fun adult_switch_needs_the_one_time_18_confirmation() = runBlocking {
        val p = prefs()
        assertFalse(p.adult.first())
        p.setAdult(true) // no confirmation yet → stays off
        assertFalse(p.adult.first())
        p.setAdult(true, confirmed18 = true)
        assertTrue(p.adult.first())
        p.setAdult(false)
        assertFalse(p.adult.first())
        p.setAdult(true) // confirmed once → no second confirmation needed
        assertTrue(p.adult.first())
        assertTrue(p.adultConfirmed.first())
    }

    @Test fun chat_prefs_are_capped_and_install_id_is_stable() = runBlocking {
        val p = prefs()
        p.setChat(ChatPrefs("x".repeat(80), "y".repeat(900)))
        val c = p.chat.first()
        assertEquals(ExtraPrefs.MAX_CALL_ME, c.callMe.length)
        assertEquals(ExtraPrefs.MAX_PERSONA, c.persona.length)
        val id = p.installId()
        assertEquals(id, p.installId())
        assertTrue(id.length >= 16)
    }

    @Test fun interstitial_at_most_every_six_screens_and_three_minutes_never_on_reader_login_splash_chat() {
        assertFalse(Interstitials.shouldShow("anime/1", 5, 1_000_000, 0))
        assertTrue(Interstitials.shouldShow("anime/1", 6, 1_000_000, 0))
        assertFalse(Interstitials.shouldShow("anime/1", 10, 1_000_000, 1_000_000 - 60_000))
        listOf("splash", "login", "m_reader/5", "manga/reader/3", "chat", "chat/abc", "settings").forEach { assertFalse(it, Interstitials.shouldShow(it, 99, 10_000_000, 0)) }
    }

    @Test fun official_links_keep_only_legal_streaming_pages_and_split_youtube() {
        val l = OfficialLinksRepository.parse(
            listOf(
                OfficialLink("Official Site", "https://frieren-anime.jp/", "INFO"),
                OfficialLink("Crunchyroll", "https://www.crunchyroll.com/series/GG5H5XQX4", "STREAMING"),
                OfficialLink("YouTube", "https://www.youtube.com/playlist?list=PLwLSw1_eDZl10YPPR7qDsVf10wZfYnIMK", "STREAMING"),
                OfficialLink("Netflix", "https://www.netflix.com/title/81726714", "STREAMING"),
                OfficialLink("Twitter", "https://twitter.com/anime_frieren", "SOCIAL"),
                OfficialLink("Hulu", "https://www.hulu.com/x", "STREAMING", isDisabled = true),
            ),
            OfficialTrailer("tR8YH0G67Rk", "youtube"),
        )
        assertEquals("tR8YH0G67Rk", l.trailerYoutubeId)
        assertEquals(1, l.youtube.size)
        assertEquals(listOf("Crunchyroll", "Netflix"), l.platforms.map { it.site })
        val manga = OfficialLinksRepository.parse(
            listOf(
                OfficialLink("MANGA Plus", "https://mangaplus.shueisha.co.jp/titles/100020", "STREAMING", "English"),
                OfficialLink("MANGA Plus", "https://mangaplus.shueisha.co.jp/titles/100079", "STREAMING", "Thai"),
                OfficialLink("VIZ", "https://www.viz.com/one-piece", "STREAMING", "English"),
            ), OfficialTrailer("x", "dailymotion"),
        )
        assertNull(manga.trailerYoutubeId)
        assertEquals(listOf("MANGA Plus", "MANGA Plus", "VIZ"), manga.platforms.map { it.site })
        assertTrue(OfficialLinksRepository.parse(emptyList(), null).isEmpty)
    }

    private class FakeBackend(var result: ChatResult) : ChatBackend {
        val sent = mutableListOf<WireRequest>()
        override suspend fun status(installId: String) = WireStatus(ready = result !is ChatResult.NotReady, remaining = 20)
        override suspend fun send(installId: String, req: WireRequest): ChatResult { sent += req; return result }
    }

    @Test fun chat_saves_titled_history_on_the_phone_and_deletes_it() = runBlocking {
        val dir = Files.createTempDirectory("chat").toFile()
        val store = ChatStore(dir)
        val p = prefs().also { it.setChat(ChatPrefs("مدين", "تكلم يمني")) }
        val backend = FakeBackend(ChatResult.Ok(WireReply("جرّب Frieren 👌", listOf(ChatCard(154587, "ANIME", "Frieren")), title = "ترشيح هادئ", remaining = 19)))
        val repo = ChatRepository(store, backend, p) { WireLibrary("أنمي: مكتمل 3") }
        val (conv, msgs, res) = repo.send(ChatConversation(id = "c1"), emptyList(), "رشح لي", null, AppMode.MANGA)
        assertTrue(res is ChatResult.Ok)
        assertEquals("ترشيح هادئ", conv.title)
        assertEquals(2, msgs.size)
        assertEquals(154587, msgs[1].cards.single().id)
        val req = backend.sent.single()
        assertTrue(req.wantTitle)
        assertEquals("manga", req.mode)
        assertEquals("مدين", req.callMe)
        assertEquals("أنمي: مكتمل 3", req.library?.summary)
        // second message: no title request, history carries the first exchange
        backend.result = ChatResult.Ok(WireReply("تمام", title = "عنوان آخر"))
        val (conv2, msgs2, _) = repo.send(conv, msgs, "وبعدين؟", null, AppMode.ANIME)
        assertEquals("ترشيح هادئ", conv2.title)
        assertFalse(backend.sent.last().wantTitle)
        assertEquals(2, backend.sent.last().history.size)
        // reload from disk
        val again = ChatStore(dir)
        again.load()
        assertEquals(listOf("c1"), again.conversations.value.map { it.id })
        assertEquals(msgs2.size, again.messages("c1").size)
        again.delete("c1")
        assertTrue(again.conversations.value.isEmpty())
        assertTrue(again.messages("c1").isEmpty())
    }

    @Test fun not_ready_and_limit_are_explained_and_a_first_not_ready_message_leaves_no_empty_chat() = runBlocking {
        val store = ChatStore(Files.createTempDirectory("chat2").toFile())
        val backend = FakeBackend(ChatResult.NotReady)
        val repo = ChatRepository(store, backend, prefs()) { null }
        val (_, msgs, _) = repo.send(ChatConversation(id = "n"), emptyList(), "هلا", null, AppMode.ANIME)
        assertEquals(ChatRepository.NOT_READY, msgs.last().error)
        store.load()
        assertTrue(store.conversations.value.isEmpty())
        backend.result = ChatResult.Limit("وصلت حد اليوم (20 رسالة).")
        val history = listOf(ChatMessage(role = "user", text = "a"), ChatMessage(role = "assistant", text = "", error = "x"))
        val (_, m2, _) = repo.send(ChatConversation(id = "l"), history, "b", null, AppMode.ANIME)
        assertEquals("وصلت حد اليوم (20 رسالة).", m2.last().error)
        // failed bubbles never go back to the model as history
        assertEquals(1, backend.sent.last().history.size)
    }

    @Test fun genre_and_tag_routes_build_safe_queries() {
        val g = com.animeshadow.app.domain.BrowseQuery.forGenreArg("Action", adult = false)
        assertEquals("Action", g.genre); assertNull(g.tag); assertEquals(false, g.adult)
        val t = com.animeshadow.app.domain.BrowseQuery.forGenreArg("tag:Isekai", adult = false)
        assertEquals("Isekai", t.tag); assertNull(t.genre); assertEquals(false, t.adult)
        assertNull(com.animeshadow.app.domain.BrowseQuery.forGenreArg("tag:Isekai", adult = true).adult)
        assertEquals(1, t.activeFilterCount)
        assertTrue(com.animeshadow.app.domain.AniTags.POPULAR.all { p -> AniTags.TAGS.any { it.name == p } })
    }
}
