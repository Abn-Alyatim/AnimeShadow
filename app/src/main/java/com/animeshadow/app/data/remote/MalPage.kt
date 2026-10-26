package com.animeshadow.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The MAL fields the details page shows, whichever source produced them. */
data class MalInfo(
    val score: Double?,
    val scoredBy: Int?,
    val members: Int?,
    val rating: String?,
    val poster: String?,
    val source: String,
)

fun JikanAnime.toMalInfo() = MalInfo(
    score = score,
    scoredBy = scored_by,
    members = members,
    rating = rating,
    poster = images?.jpg?.large_image_url ?: images?.jpg?.image_url ?: images?.webp?.large_image_url,
    source = MalPage.SOURCE_JIKAN,
)

/**
 * Second source for the same MAL data when Jikan is unreachable: the public anime page on myanimelist.net
 * (score, scorers, members, content rating, main picture). Parsing is tolerant: any field that is not found is null.
 */
class MalPage(private val client: OkHttpClient, private val baseUrl: String = "https://myanimelist.net/") {
    suspend fun fetch(malId: Int): MalInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl}anime/$malId")
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
            .header("Accept-Language", "en")
            .build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("MAL page HTTP ${r.code}")
            parse(r.body?.string().orEmpty())
        }
    }

    companion object {
        const val SOURCE_JIKAN = "jikan"
        const val SOURCE_PAGE = "mal_page"

        private val score = Regex("""itemprop="ratingValue"[^>]*>\s*(?:<span[^>]*>\s*)?([0-9]+\.[0-9]+)""")
        private val scoredBy = Regex("""itemprop="ratingCount"[^>]*>\s*([0-9,]+)""")
        private val members = Regex("""Members:</span>\s*([0-9,]+)""")
        private val rating = Regex("""Rating:</span>\s*([^<\n]+?)\s*<""")
        private val ratingMobile = Regex(""">Rating</td>\s*<td[^>]*>\s*([^<]+?)\s*<""")
        private val posterDesktop = Regex("""data-src="(https://cdn\.myanimelist\.net/images/anime/[^"]+)"[^>]*itemprop="image"""")
        private val posterAny = Regex("""itemprop="image" content="(https://cdn\.myanimelist\.net/images/anime/[^"]+)"""")

        /** "…/1244/138851.jpg" -> "…/1244/138851l.jpg" (MAL's large variant, same as Jikan's large_image_url). */
        fun large(url: String): String = if (Regex("""/\d+\.(jpg|webp)$""").containsMatchIn(url)) url.replace(Regex("""\.(jpg|webp)$"""), "l.$1") else url

        fun parse(html: String): MalInfo? {
            fun num(r: Regex) = r.find(html)?.groupValues?.get(1)?.replace(",", "")
            val info = MalInfo(
                score = num(score)?.toDoubleOrNull(),
                scoredBy = num(scoredBy)?.toIntOrNull(),
                members = num(members)?.toIntOrNull(),
                rating = (rating.find(html) ?: ratingMobile.find(html))?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it != "None" },
                poster = (posterDesktop.find(html) ?: posterAny.find(html))?.groupValues?.get(1)?.let(::large),
                source = SOURCE_PAGE,
            )
            return info.takeIf { it.score != null || it.rating != null || it.poster != null }
        }
    }
}
