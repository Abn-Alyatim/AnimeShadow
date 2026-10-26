package com.animeshadow.app.data

import com.animeshadow.app.data.remote.MalPage
import com.animeshadow.app.domain.AgeRating
import com.animeshadow.app.domain.ContentRating
import com.animeshadow.app.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MalPageParserTest {
    @Test fun parses_the_recorded_one_piece_page() {
        val info = MalPage.parse(Fixtures.text("mal_page_21_trimmed.html"))!!
        assertEquals(8.72, info.score!!, 0.0001)
        assertEquals(1564769, info.scoredBy)
        assertEquals(2739748, info.members)
        assertEquals("PG-13 - Teens 13 or older", info.rating)
        assertEquals("https://cdn.myanimelist.net/images/anime/1244/138851l.jpg", info.poster)
        assertEquals("mal_page", info.source)
    }

    @Test fun parses_the_mobile_markup_too() {
        val html = """<div itemprop="image" content="https://cdn.myanimelist.net/images/anime/1384/119988.jpg"></div>
            <span itemprop="ratingValue">  <span class="score-label score-8">  8.61 </span></span>
            (scored by <span itemprop="ratingCount" style="display:none">424161</span>)
            <td class="x">Rating</td>
            <td class="y">R+ - Mild Nudity</td>"""
        val info = MalPage.parse(html)!!
        assertEquals(8.61, info.score!!, 0.0001)
        assertEquals(424161, info.scoredBy)
        assertEquals("R+ - Mild Nudity", info.rating)
        assertEquals("https://cdn.myanimelist.net/images/anime/1384/119988l.jpg", info.poster)
    }

    @Test fun unrelated_html_gives_null() {
        assertNull(MalPage.parse("<html><body>Just a moment...</body></html>"))
    }

    @Test fun large_variant_only_for_plain_ids() {
        assertEquals("https://cdn.myanimelist.net/images/anime/1/2l.jpg", MalPage.large("https://cdn.myanimelist.net/images/anime/1/2.jpg"))
        assertEquals("https://cdn.myanimelist.net/images/anime/1/2l.jpg", MalPage.large("https://cdn.myanimelist.net/images/anime/1/2l.jpg"))
    }

    @Test fun content_rating_mapping() {
        assertEquals(AgeRating.ALL, ContentRating.fromMal("G - All Ages"))
        assertEquals(AgeRating.CHILDREN, ContentRating.fromMal("PG - Children"))
        assertEquals(AgeRating.TEEN, ContentRating.fromMal("PG-13 - Teens 13 or older"))
        assertEquals(AgeRating.MATURE, ContentRating.fromMal("R - 17+ (violence & profanity)"))
        assertEquals(AgeRating.MATURE, ContentRating.fromMal("R+ - Mild Nudity"))
        assertEquals(AgeRating.ADULT, ContentRating.fromMal("Rx - Hentai"))
        assertNull(ContentRating.fromMal(null))
        assertEquals("13+", AgeRating.TEEN.label)
        assertEquals("18+", ContentRating.of(null, isAdult = true)!!.label)
        assertEquals(AgeRating.TEEN, ContentRating.of("PG-13 - Teens 13 or older", isAdult = true)) // MAL wins over AniList
    }
}
