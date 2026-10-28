package com.animeshadow.app.manga

import com.animeshadow.app.data.translate.MyMemoryTranslator
import com.animeshadow.app.manga.data.MangaMediaDto
import com.animeshadow.app.manga.data.MangaRelation
import com.animeshadow.app.ui.common.titlePair
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.3.1 manga details data (plain JVM): AniList detail parsing, titles, translation fallback. */
class V131MangaDataTest {
    private val parser = Json { ignoreUnknownKeys = true }

    @Test fun detail_parses_mal_id_authors_and_relations() {
        val json = """{"id":106130,"idMal":114745,"title":{"english":"Blue Lock","romaji":"Blue Lock","native":"ブルーロック"},
            "staff":{"edges":[{"role":"Story","node":{"name":{"full":"Muneyuki Kaneshiro"}}},{"role":"Art","node":{"name":{"full":"Yuusuke Nomura"}}},{"role":"Assistant","node":{"name":{"full":"Tatsuya Endou"}}}]},
            "relations":{"edges":[{"relationType":"ADAPTATION","node":{"id":137822,"type":"ANIME","format":"TV","title":{"english":"BLUE LOCK"}}},
              {"relationType":"SEQUEL","node":{"id":2,"type":"MANGA","format":"MANGA","title":{"romaji":"Blue Lock 2"},"coverImage":{"large":"https://x/2.jpg"}}},
              {"relationType":"SIDE_STORY","node":{"id":3,"type":"MANGA","format":"MANGA","title":{"english":"Episode Nagi"}}},
              {"relationType":"PREQUEL","node":{"id":1,"type":"MANGA","format":"ONE_SHOT","title":{"english":"Blue Lock 0"}}}]}}"""
        val dto = parser.decodeFromString(MangaMediaDto.serializer(), json)
        val e = dto.toEntity(0, detail = true)
        assertEquals(114745, e.idMal)
        assertEquals(listOf("Muneyuki Kaneshiro", "Yuusuke Nomura"), e.authorList)
        val page = MangaRelation.forPage(e.relationList)
        assertEquals(listOf("PREQUEL", "SEQUEL", "SIDE_STORY"), page.map { it.relationType }) // anime adaptation not shown in manga mode
        assertEquals("https://x/2.jpg", page[1].cover)
    }

    @Test fun titles_english_on_top_japanese_below_once_when_same() {
        assertEquals("One Piece" to null, titlePair("One Piece", "ONE PIECE", "ワンピース"))
        assertEquals("Attack on Titan" to "Shingeki no Kyojin", titlePair("Attack on Titan", "Shingeki no Kyojin", "進撃の巨人"))
        assertEquals("Shingeki no Kyojin" to null, titlePair(null, "Shingeki no Kyojin", "進撃の巨人"))
        assertEquals("ワンピース" to null, titlePair(null, null, "ワンピース"))
    }

    @Test fun translation_falls_back_when_mymemory_quota_is_used_up() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/get") -> MockResponse().setBody("""{"responseData":{"translatedText":"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY"},"quotaFinished":true,"responseStatus":429}""")
                request.path!!.startsWith("/translate_a/single") -> MockResponse().setBody("""[[["كان إيساغي قريباً من الهدف.","Isagi was close to the goal.",null,null,10]],null,"en"]""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val base = server.url("/").toString()
            val t = MyMemoryTranslator(OkHttpClient(), baseUrl = base, fallbackUrl = base)
            assertEquals("كان إيساغي قريباً من الهدف.", t.translate("Isagi was close to the goal."))
        } finally { server.shutdown() }
    }
}
