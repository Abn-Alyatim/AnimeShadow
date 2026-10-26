package com.animeshadow.app.data.translate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * English -> Arabic synopsis translation. Returns null when it is not possible right now (no network, the free daily
 * quota is used up); the caller keeps the English text and tries again later. Behind an interface so a server-side
 * proxy can replace the public service without touching the UI.
 */
interface SynopsisTranslator {
    /** [keep]: names to leave exactly as written (show titles, main characters). */
    suspend fun translate(english: String, keep: List<String> = emptyList()): String?
}

/** Thrown when the free service says today's quota is used up; the background job then waits until tomorrow. */
class TranslationQuotaException(message: String) : IOException(message)

/**
 * Keeps names out of the machine translation: each name in the text becomes a {{7701}}-style token that the service
 * copies through untouched, and the tokens are put back afterwards. Matches whole capitalised name runs that contain a
 * known name part, so AniList's "Luffy Monkey" also protects the synopsis's "Monkey D. Luffy".
 */
object NameGuard {
    data class Guarded(val text: String, val tokens: Map<String, String>)

    private val NAME_RUN = Regex("""\b[A-Z][\p{L}'’-]*\.?(?:\s+(?:[A-Z][\p{L}'’-]*\.?|[A-Z]\.))*""")

    fun parts(names: List<String>): Set<String> = names.flatMap { n -> n.split(Regex("""[\s.]+""")) }
        .map { it.trim() }.filter { it.length >= 3 && it.first().isUpperCase() }.toSet()

    fun protect(text: String, titles: List<String>, names: List<String>, style: (Int) -> String = { "{{${7700 + it}}}" }): Guarded {
        val tokens = LinkedHashMap<String, String>()
        val byValue = HashMap<String, String>()
        fun tokenFor(value: String): String = byValue.getOrPut(value) {
            style(tokens.size + 1).also { tokens[it] = value }
        }
        var out = text
        // 1) whole titles (longest first), case-insensitive.
        titles.filter { it.length >= 3 }.distinct().sortedByDescending { it.length }.forEach { t ->
            out = Regex(Regex.escape(t), RegexOption.IGNORE_CASE).replace(out) { m -> tokenFor(m.value) }
        }
        // 2) capitalised runs that contain a known character name part; only the span from the first to the last
        // known part is kept (so a sentence-initial "When Luffy" still translates "When").
        val known = parts(names)
        if (known.isNotEmpty()) {
            out = NAME_RUN.replace(out) { m ->
                val words = Regex("""\S+""").findAll(m.value).toList()
                fun bare(w: String) = w.trimEnd('.', '\'', '’', ',')
                val first = words.indexOfFirst { bare(it.value) in known }
                val last = words.indexOfLast { bare(it.value) in known }
                if (first < 0) return@replace m.value
                val from = words[first].range.first
                var span = m.value.substring(from, words[last].range.last + 1)
                var tail = m.value.substring(words[last].range.last + 1)
                if (span.endsWith(".")) { span = span.dropLast(1); tail = "." + tail }
                m.value.substring(0, from) + tokenFor(span) + tail
            }
        }
        return Guarded(out, tokens)
    }

    /** Null when the service dropped or duplicated a token beyond recognition (the caller then tries another way). */
    fun restore(translated: String, g: Guarded): String? {
        var out = translated
        for ((token, value) in g.tokens) {
            if (!out.contains(token)) return null
            out = out.replace(token, value)
        }
        return out
    }
}

/**
 * MyMemory (api.mymemory.translated.net): free, no API key, no Google Play services. Anonymous use is limited to about
 * 5,000 characters a day per phone (50,000 with a contact e-mail in [email], an optional build setting). Requests are
 * kept under 480 characters (the service's documented 500-byte limit), split at sentence ends.
 */
class MyMemoryTranslator(
    private val client: OkHttpClient,
    private val email: String = "",
    private val baseUrl: String = "https://api.mymemory.translated.net/",
) : SynopsisTranslator {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun translate(english: String, keep: List<String>): String? = try {
        withContext(Dispatchers.IO) { translateBlocking(english, keep) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: TranslationQuotaException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun translateBlocking(english: String, keep: List<String>): String? {
        val (titles, names) = keep.partition { it.startsWith(TITLE_PREFIX) }.let { (t, n) -> t.map { it.removePrefix(TITLE_PREFIX) } to n }
        val out = mutableListOf<String>()
        for (p in english.split("\n")) out += if (p.isBlank()) p else paragraph(p, titles, names) ?: return null
        return out.joinToString("\n")
    }

    private fun paragraph(p: String, titles: List<String>, names: List<String>): String? {
        // Names protected with {{77nn}} tokens; if the service loses one, retry with Xa77nn tokens, then unprotected.
        for (style in STYLES) {
            val g = NameGuard.protect(p, titles, names, style)
            val raw = translateChunks(g.text) ?: return null
            NameGuard.restore(raw, g)?.let { return tidy(it) }
            if (g.tokens.isEmpty()) return tidy(raw)
        }
        return translateChunks(p)?.let(::tidy)
    }

    private fun translateChunks(text: String): String? {
        val parts = mutableListOf<String>()
        for (c in chunks(text)) parts += request(c) ?: return null
        return parts.joinToString(" ")
    }

    private fun request(q: String): String? {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("get")
            .addQueryParameter("q", q).addQueryParameter("langpair", "en|ar")
            .apply { if (email.isNotBlank()) addQueryParameter("de", email) }
            .build()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (r.code == 429) throw TranslationQuotaException("HTTP 429")
            if (!r.isSuccessful) return null
            val root = json.parseToJsonElement(body).jsonObject
            val text = root["responseData"]?.jsonObject?.get("translatedText")?.jsonPrimitive?.content ?: return null
            val status = root["responseStatus"]?.jsonPrimitive?.intOrNull ?: 200
            val quota = root["quotaFinished"]?.jsonPrimitive?.booleanOrNull == true
            if (quota || status == 429 || text.startsWith("MYMEMORY WARNING")) throw TranslationQuotaException(text.take(120))
            if (status != 200 || text.isBlank()) return null
            return text
        }
    }

    companion object {
        const val TITLE_PREFIX = "title:"
        private val STYLES: List<(Int) -> String> = listOf({ n -> "{{${7700 + n}}}" }, { n -> "Xa${7700 + n}" })
        private const val MAX = 480

        /** Sentence-sized pieces of at most [MAX] characters. */
        fun chunks(text: String): List<String> {
            if (text.length <= MAX) return listOf(text)
            val sentences = Regex("""(?<=[.!?…])\s+""").split(text)
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            for (s in sentences) {
                if (cur.isNotEmpty() && cur.length + 1 + s.length > MAX) { out += cur.toString(); cur.clear() }
                if (s.length > MAX) {
                    s.chunked(MAX).forEach { out += it }
                } else {
                    if (cur.isNotEmpty()) cur.append(' ')
                    cur.append(s)
                }
            }
            if (cur.isNotEmpty()) out += cur.toString()
            return out
        }

        fun tidy(s: String): String = s
            .replace("&#39;", "'").replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("""\s{2,}"""), " ").trim()
    }
}
