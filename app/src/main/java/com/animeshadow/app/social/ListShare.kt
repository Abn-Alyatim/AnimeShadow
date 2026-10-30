package com.animeshadow.app.social

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.FavoriteAnimeEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.manga.data.MangaDatabase
import com.animeshadow.app.manga.data.MangaEntity
import com.animeshadow.app.manga.data.MangaLibraryEntity
import com.animeshadow.app.manga.data.ReadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** v1.5.0: «غير مهتم» — titles the user doesn't want suggested (kept on the phone, exportable). */
class NotInterestedStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<ListItem>> = _items
    fun contains(id: Int, kind: String = "ANIME") = _items.value.any { it.id == id && it.k == kind }
    fun toggle(item: ListItem) { set(if (contains(item.id ?: -1, item.k)) _items.value.filterNot { it.id == item.id && it.k == item.k } else _items.value + item) }
    fun addAll(items: List<ListItem>) = set((_items.value + items.filter { n -> _items.value.none { it.id == n.id && it.k == n.k } }))
    private fun set(l: List<ListItem>) { prefs.edit().putString(K, json.encodeToString(ListSerializer(ListItem.serializer()), l)).apply(); _items.value = l }
    private fun read(): List<ListItem> = prefs.getString(K, null)?.let { runCatching { json.decodeFromString(ListSerializer(ListItem.serializer()), it) }.getOrNull() } ?: emptyList()
    companion object { const val PREFS = "not_interested"; private const val K = "items" }
}

/** v1.5.0: lets screens without their own ViewModel field reach the «غير مهتم» store. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NotInterestedEntryPoint { fun notInterested(): NotInterestedStore }

/** Provided by the details screen; null in previews/tests (the menu item is then hidden). */
val LocalNotInterested = androidx.compose.runtime.staticCompositionLocalOf<NotInterestedStore?> { null }

/** One exportable list: key like «anime:COMPLETED», «anime:FAVORITES», «anime:NOT_INTERESTED», «manga:READING». */
@Serializable data class ExportList(val key: String, val title: String, val items: List<ListItem>)
@Serializable data class ListsFile(val app: String = "ANIME SHADOW", val kind: String = "lists", val version: Int = 1, val exportedAt: Long = 0, val lists: List<ExportList>)
data class ListImport(val added: Int, val skipped: Int)

object ListKeys {
    val ANIME = WatchStatus.entries.map { "anime:${it.name}" } + listOf("anime:FAVORITES", "anime:NOT_INTERESTED")
    val MANGA = ReadStatus.entries.map { "manga:${it.name}" } + listOf("manga:FAVORITES")
    fun title(key: String): String = when (key) {
        "anime:WATCHING" -> "أشاهده"; "anime:PLAN_TO_WATCH" -> "سأشاهده"; "anime:COMPLETED" -> "شاهدته"; "anime:ON_HOLD" -> "متوقف مؤقتاً"
        "anime:DROPPED" -> "لا أريده (تركته)"; "anime:FAVORITES" -> "المفضلة (أنمي)"; "anime:NOT_INTERESTED" -> "غير مهتم"
        "manga:READING" -> "أقرؤه"; "manga:PLAN_TO_READ" -> "سأقرؤه"; "manga:COMPLETED" -> "قرأته"; "manga:ON_HOLD" -> "متوقف مؤقتاً (مانجا)"
        "manga:DROPPED" -> "لا أريده (مانجا)"; "manga:FAVORITES" -> "المفضلة (مانجا)"
        else -> key
    }
}

class ListShare(private val db: AppDatabase, private val mangaDb: MangaDatabase, val notInterested: NotInterestedStore, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; explicitNulls = false }

    suspend fun build(keys: Collection<String>): List<ExportList> {
        val anime = if (keys.any { it.startsWith("anime:") }) db.libraryDao().all() else emptyList()
        val favIds = if ("anime:FAVORITES" in keys) db.libraryDao().allFavoriteAnime().map { it.animeId } else emptyList()
        val favAnime = if (favIds.isEmpty()) emptyList() else db.animeDao().getByIds(favIds)
        val manga = if (keys.any { it.startsWith("manga:") }) mangaDb.manga().allLibrary() else emptyList()
        fun a(e: AnimeEntity?, id: Int, status: String?, score: Double?) = ListItem(id, e?.titleEnglish ?: e?.titleRomaji ?: "#$id", "ANIME", status, score)
        return keys.mapNotNull { key ->
            val items = when {
                key == "anime:FAVORITES" -> favIds.map { id -> a(favAnime.firstOrNull { it.id == id }, id, null, null) }
                key == "anime:NOT_INTERESTED" -> notInterested.items.value.filter { it.k == "ANIME" }
                key.startsWith("anime:") -> anime.filter { it.entry.status.name == key.removePrefix("anime:") }
                    .map { a(it.anime, it.entry.animeId, it.entry.status.name, it.entry.overallScore) }
                key == "manga:FAVORITES" -> manga.filter { it.entry.favorite }.map { m(it.manga, it.entry) }
                key.startsWith("manga:") -> manga.filter { it.entry.status?.name == key.removePrefix("manga:") }.map { m(it.manga, it.entry) }
                else -> return@mapNotNull null
            }
            ExportList(key, ListKeys.title(key), items)
        }
    }

    private fun m(e: MangaEntity?, l: MangaLibraryEntity) = ListItem(l.mangaId, e?.titleEnglish ?: e?.titleRomaji ?: "#${l.mangaId}", "MANGA", l.status?.name, l.userScore?.toDouble())

    fun toJson(lists: List<ExportList>): String = json.encodeToString(ListsFile.serializer(), ListsFile(exportedAt = now(), lists = lists))

    /** Readable in any spreadsheet app (UTF-8 BOM so Excel shows Arabic). */
    fun toCsv(lists: List<ExportList>): String = buildString {
        append('\uFEFF').append("list,type,anilist_id,title,status,score\n")
        for (l in lists) for (i in l.items) append(listOf(l.title, i.k, i.id?.toString() ?: "", i.t, i.s ?: "", i.sc?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "").joinToString(",") { csv(it) }).append('\n')
    }

    /** Plain text for chat apps / notes: «شاهدته (12)» then one title per line. */
    fun toText(lists: List<ExportList>): String = lists.joinToString("\n\n") { l -> "${l.title} (${l.items.size})\n" + l.items.joinToString("\n") { "• ${it.t}" + (it.sc?.let { s -> "  ★${if (s % 1.0 == 0.0) s.toInt() else s}" } ?: "") } }

    private fun csv(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun parse(text: String): List<ExportList> {
        val t = text.trim().removePrefix("\uFEFF")
        runCatching { return json.decodeFromString(ListsFile.serializer(), t).lists }
        runCatching { val s = json.decodeFromString(SharedList.serializer(), t); return listOf(ExportList(guessKey(s), s.title, s.items)) }
        throw IllegalArgumentException("not a lists file")
    }

    fun guessKey(s: SharedList): String {
        val kind = if (s.items.firstOrNull()?.k == "MANGA") "manga" else "anime"
        return (if (kind == "anime") ListKeys.ANIME else ListKeys.MANGA).firstOrNull { ListKeys.title(it) == s.title }
            ?: s.items.firstOrNull()?.s?.let { st -> "$kind:$st" } ?: "$kind:PLAN_TO_WATCH".replace("manga:PLAN_TO_WATCH", "manga:PLAN_TO_READ")
    }

    /** Adds what isn't in the library yet (never overwrites the user's own entries). */
    suspend fun import(lists: List<ExportList>): ListImport {
        var added = 0; var skipped = 0
        val t = now()
        db.withTransaction {
            for (l in lists.filter { it.key.startsWith("anime:") }) for (i in l.items) {
                val id = i.id ?: run { skipped++; null } ?: continue
                if (db.animeDao().get(id) == null) db.animeDao().upsert(AnimeEntity(id = id, titleEnglish = i.t))
                when (l.key) {
                    "anime:FAVORITES" -> { db.libraryDao().insertFavoriteAnime(FavoriteAnimeEntity(id, t)); added++ }
                    "anime:NOT_INTERESTED" -> { notInterested.addAll(listOf(i.copy(k = "ANIME"))); added++ }
                    else -> {
                        val st = runCatching { WatchStatus.valueOf(i.s ?: l.key.removePrefix("anime:")) }.getOrNull() ?: WatchStatus.PLAN_TO_WATCH
                        if (db.libraryDao().get(id) != null) skipped++
                        else { db.libraryDao().upsert(LibraryEntryEntity(id, st, 0, null, t, t)); added++ }
                    }
                }
            }
        }
        mangaDb.withTransaction {
            val dao = mangaDb.manga()
            for (l in lists.filter { it.key.startsWith("manga:") }) for (i in l.items) {
                val id = i.id ?: run { skipped++; null } ?: continue
                if (dao.get(id) == null) dao.upsertOne(MangaEntity(id = id, titleEnglish = i.t))
                val old = dao.entry(id)
                if (l.key == "manga:FAVORITES") { dao.upsertEntry((old ?: MangaLibraryEntity(id)).copy(favorite = true, updatedAt = t)); added++; continue }
                val st = runCatching { ReadStatus.valueOf(i.s ?: l.key.removePrefix("manga:")) }.getOrNull() ?: ReadStatus.PLAN_TO_READ
                if (old?.status != null) skipped++ else { dao.upsertEntry((old ?: MangaLibraryEntity(id)).copy(status = st, updatedAt = t)); added++ }
            }
        }
        return ListImport(added, skipped)
    }
}
