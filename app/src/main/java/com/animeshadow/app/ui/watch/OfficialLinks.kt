package com.animeshadow.app.ui.watch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animeshadow.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * v1.4.0 «شاهد» / «اقرأ رسمياً»: LEGAL places to watch or read, as AniList lists them for the title (externalLinks of
 * type STREAMING) plus the official trailer. Every item opens the rights-holder's own app or site (YouTube official
 * channels like Muse Asia / Ani-One, Crunchyroll, Netflix, MANGA Plus, VIZ, K MANGA…). Nothing is scraped or re-hosted.
 * See files/animeshadow/research_1.4.0.md for why no in-app player/reader source is used.
 */
@Serializable data class OfficialLink(val site: String, val url: String, val type: String? = null, val language: String? = null, val isDisabled: Boolean? = null)
@Serializable data class OfficialTrailer(val id: String? = null, val site: String? = null)
@Serializable private data class LinksMedia(val externalLinks: List<OfficialLink> = emptyList(), val trailer: OfficialTrailer? = null)
@Serializable private data class LinksData(val Media: LinksMedia? = null)
@Serializable private data class LinksResponse(val data: LinksData? = null)

data class OfficialLinks(val trailerYoutubeId: String?, val youtube: List<OfficialLink>, val platforms: List<OfficialLink>) {
    val isEmpty: Boolean get() = trailerYoutubeId == null && youtube.isEmpty() && platforms.isEmpty()
}

object OfficialLinksRepository {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val cache = ConcurrentHashMap<Int, Pair<Long, OfficialLinks>>()
    private val client by lazy { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build() }
    /** Tests replace the network. */
    @Volatile var fetcher: (suspend (Int) -> String?)? = null
    private const val Q = "query(\$id:Int){Media(id:\$id){externalLinks{site url type language isDisabled} trailer{id site}}}"

    suspend fun load(id: Int): OfficialLinks? {
        cache[id]?.let { (t, v) -> if (System.currentTimeMillis() - t < 6 * 3600_000L) return v }
        val body = (fetcher ?: ::fetch)(id) ?: return null
        val media = runCatching { json.decodeFromString(LinksResponse.serializer(), body).data?.Media }.getOrNull() ?: return null
        return parse(media.externalLinks, media.trailer).also { cache[id] = System.currentTimeMillis() to it }
    }

    fun parse(links: List<OfficialLink>, trailer: OfficialTrailer?): OfficialLinks {
        val streaming = links.filter { it.type == "STREAMING" && it.isDisabled != true && it.url.startsWith("http") }.distinctBy { it.url }
        val yt = streaming.filter { it.site.equals("YouTube", true) || it.url.contains("youtube.com") || it.url.contains("youtu.be") }
        val others = (streaming - yt.toSet()).sortedWith(compareBy({ rank(it.site) }, { it.site }))
            // one chip per site+language (AniList repeats MANGA Plus for each language)
            .distinctBy { it.site.lowercase() + "|" + (it.language ?: "") }
        return OfficialLinks(trailer?.takeIf { it.site.equals("youtube", true) }?.id, yt, others)
    }

    private fun rank(site: String) = when (site.lowercase()) {
        "crunchyroll" -> 0; "netflix" -> 1; "bilibili tv" -> 2; "amazon prime video" -> 3; "manga plus" -> 0; "viz" -> 1; "k manga" -> 2; "shonen jump plus" -> 3; else -> 9
    }

    private suspend fun fetch(id: Int): String? = withContext(Dispatchers.IO) {
        if (android.os.Build.FINGERPRINT == "robolectric") return@withContext null // unit tests never hit the network
        runCatching {
            val payload = kotlinx.serialization.json.buildJsonObject {
                put("query", kotlinx.serialization.json.JsonPrimitive(Q))
                put("variables", kotlinx.serialization.json.buildJsonObject { put("id", kotlinx.serialization.json.JsonPrimitive(id)) })
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url("https://graphql.anilist.co/").post(body).header("Accept", "application/json").build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull()
    }
}

fun openOfficial(context: Context, url: String) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(i) } catch (_: ActivityNotFoundException) { }
}

fun youtubeWatchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

private fun languageAr(l: String?): String? = when (l?.lowercase()) {
    null, "" -> null; "english" -> "إنجليزي"; "japanese" -> "ياباني"; "spanish" -> "إسباني"; "french" -> "فرنسي"; "german" -> "ألماني"; "thai" -> "تايلندي"
    "indonesian" -> "إندونيسي"; "portuguese" -> "برتغالي"; "korean" -> "كوري"; "chinese" -> "صيني"; "arabic" -> "عربي"; "vietnamese" -> "فيتنامي"; "italian" -> "إيطالي"; "russian" -> "روسي"
    else -> l
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.width(6.dp))
        Icon(icon, null, tint = Palette.RefIcon, modifier = Modifier.size(20.dp))
    }
}

/** Anime page «شاهد»: official trailer + official free YouTube episodes + official platforms. Hidden when AniList has none. */
@Composable
fun WatchSection(animeId: Int, fallbackTrailerId: String? = null, modifier: Modifier = Modifier, preview: OfficialLinks? = null, showTrailer: Boolean = true) {
    var links by remember(animeId) { mutableStateOf(preview) }
    if (preview == null) LaunchedEffect(animeId) { links = OfficialLinksRepository.load(animeId) }
    val l0 = links ?: fallbackTrailerId?.let { OfficialLinks(it, emptyList(), emptyList()) } ?: return
    // the details page already has its own trailer block: then this section shows only the episode/platform links
    val l = if (showTrailer) l0 else l0.copy(trailerYoutubeId = null)
    if (l.isEmpty) return
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().testTag("watch_section")) {
        SectionTitle("شاهد", Icons.Filled.SmartDisplay)
        l.trailerYoutubeId?.let { id ->
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Palette.Card).clickable { openOfficial(ctx, youtubeWatchUrl(id)) }.testTag("watch_trailer"), contentAlignment = Alignment.Center) {
                AsyncImage("https://img.youtube.com/vi/$id/hqdefault.jpg", "العرض الرسمي", Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xCC000000)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                Text("العرض الرسمي", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomEnd).background(Color(0x99000000), RoundedCornerShape(topStart = 8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        if (l.youtube.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            l.youtube.forEach { y ->
                LinkRow(if (y.url.contains("playlist")) "حلقات مجانية رسمية على يوتيوب" else "القناة الرسمية على يوتيوب", "YouTube", Color(0xFFFF0033), Modifier.testTag("watch_youtube")) { openOfficial(ctx, y.url) }
            }
        }
        if (l.platforms.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PlatformChips(l.platforms) { openOfficial(ctx, it.url) }
        }
        Text("روابط رسمية فقط. بعض الحلقات المجانية متاحة في دول معيّنة حسب الترخيص.", color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrRtl))
    }
}

/** Manga page «اقرأ رسمياً»: the official readers AniList lists (MANGA Plus, VIZ, K MANGA…). Hidden when there are none. */
@Composable
fun ReadOfficialSection(mangaId: Int, modifier: Modifier = Modifier, preview: OfficialLinks? = null) {
    var links by remember(mangaId) { mutableStateOf(preview) }
    if (preview == null) LaunchedEffect(mangaId) { links = OfficialLinksRepository.load(mangaId) }
    val l = links ?: return
    val all = l.platforms + l.youtube
    if (all.isEmpty()) return
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().testTag("read_official_section")) {
        SectionTitle("اقرأ رسمياً", Icons.AutoMirrored.Filled.MenuBook)
        PlatformChips(all) { openOfficial(ctx, it.url) }
        Text("تفتح التطبيق أو الموقع الرسمي للناشر.", color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrRtl))
    }
}

@Composable
private fun LinkRow(title: String, site: String, dot: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Card).border(1.dp, Palette.Divider, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Palette.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrRtl))
            Text(site, color = Palette.TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).background(dot), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun PlatformChips(links: List<OfficialLink>, onClick: (OfficialLink) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), reverseLayout = true, modifier = Modifier.fillMaxWidth()) {
        items(links, key = { it.url }) { p ->
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Chip).clickable { onClick(p) }.padding(horizontal = 12.dp, vertical = 8.dp).testTag("official_chip"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(p.site, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                languageAr(p.language)?.let { Text(it, color = Palette.TextMuted, fontSize = 10.5.sp) }
            }
        }
    }
}
