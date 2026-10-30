package com.animeshadow.app.manga.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * v1.5.0: for an ongoing manga AniList has no chapter count, so the chapter list asks MangaDex's public API for the
 * highest chapter NUMBER published (titles/numbers only — no pages are read or shown). The title must link back to the
 * same AniList id, so a namesake is never used. Cached for 6 h; any failure → null (the list falls back to the user's ticks).
 */
object MangaDexCount {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<Int, Pair<Long, Int?>>()
    private const val TTL = 6 * 3600_000L

    suspend fun latest(aniListId: Int, titles: List<String>, now: Long = System.currentTimeMillis()): Int? {
        cache[aniListId]?.let { (at, v) -> if (now - at < TTL) return v }
        val v = withContext(Dispatchers.IO) { runCatching { fetch(aniListId, titles) }.getOrNull() }
        cache[aniListId] = now to v
        return v
    }

    private fun get(url: String): JsonObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "AnimeShadow/1.5 (Android)")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject
        } finally { c.disconnect() }
    }

    internal fun matchId(search: JsonObject, aniListId: Int): String? = search["data"]?.jsonArray?.firstOrNull { item ->
        item.jsonObject["attributes"]?.jsonObject?.get("links")?.let { l -> runCatching { l.jsonObject["al"]?.jsonPrimitive?.contentOrNull }.getOrNull() } == aniListId.toString()
    }?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull

    /** Highest whole chapter number in an /aggregate answer. */
    internal fun maxChapter(aggregate: JsonObject): Int? {
        val volumes = aggregate["volumes"] as? JsonObject ?: return null
        return volumes.values.flatMap { v -> (v.jsonObject["chapters"] as? JsonObject)?.keys.orEmpty() }
            .mapNotNull { it.toDoubleOrNull()?.toInt() }.filter { it in 1..20000 }.maxOrNull()
    }

    private fun fetch(aniListId: Int, titles: List<String>): Int? {
        for (t in titles.distinct().take(2)) {
            val q = URLEncoder.encode(t, "UTF-8")
            val id = matchId(get("https://api.mangadex.org/manga?title=$q&limit=10"), aniListId) ?: continue
            return maxChapter(get("https://api.mangadex.org/manga/$id/aggregate?translatedLanguage[]=en"))
        }
        return null
    }
}
