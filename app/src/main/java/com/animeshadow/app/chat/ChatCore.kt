package com.animeshadow.app.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.AppMode
import com.animeshadow.app.data.settings.ExtraPrefs
import com.animeshadow.app.manga.data.MangaDatabase
import com.animeshadow.app.manga.data.ReadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The assistant's fixed name — never user-editable (the server enforces it too). */
const val ASSISTANT_NAME = "شادو سينباي"
const val DAILY_LIMIT = 20

@Serializable
data class ChatCard(
    val id: Int,
    val type: String = "ANIME",
    val title: String,
    val native: String? = null,
    val cover: String? = null,
    val score: Int? = null,
    val year: Int? = null,
    val format: String? = null,
) { val isManga: Boolean get() = type == "MANGA" }

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    /** "user" or "assistant". */
    val role: String,
    val text: String = "",
    /** Local copy of the image the user sent (one per message). */
    val imagePath: String? = null,
    val cards: List<ChatCard> = emptyList(),
    val at: Long = System.currentTimeMillis(),
    /** A failed send: shown with its reason; nothing is retried automatically (no retry button). */
    val error: String? = null,
) { val isUser: Boolean get() = role == "user" }

@Serializable
data class ChatConversation(
    val id: String = UUID.randomUUID().toString(),
    /** Short topic summary written by the assistant after the first exchange (never "محادثة جديدة" once known). */
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val preview: String = "",
)

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; isLenient = true }

/** Conversations live on the phone only (filesDir/chat). The server keeps no history. */
class ChatStore(private val dir: File) {
    private val mutex = Mutex()
    private val _list = MutableStateFlow<List<ChatConversation>>(emptyList())
    val conversations: StateFlow<List<ChatConversation>> = _list.asStateFlow()
    private var loaded = false

    private val index get() = File(dir, "index.json")
    private fun convFile(id: String) = File(dir, "c_${id.filter { it.isLetterOrDigit() || it == '-' }}.json")
    val imageDir: File get() = File(dir, "img").apply { mkdirs() }

    suspend fun load() = mutex.withLock { ensure() }

    private suspend fun ensure() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            _list.value = runCatching { json.decodeFromString(ListSerializer(ChatConversation.serializer()), index.readText()) }.getOrDefault(emptyList())
                .sortedByDescending { it.updatedAt }
        }
        loaded = true
    }

    suspend fun messages(id: String): List<ChatMessage> = mutex.withLock {
        ensure()
        withContext(Dispatchers.IO) { runCatching { json.decodeFromString(ListSerializer(ChatMessage.serializer()), convFile(id).readText()) }.getOrDefault(emptyList()) }
    }

    suspend fun save(conv: ChatConversation, messages: List<ChatMessage>) = mutex.withLock {
        ensure()
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            writeAtomic(convFile(conv.id), json.encodeToString(ListSerializer(ChatMessage.serializer()), messages))
            val list = (listOf(conv) + _list.value.filter { it.id != conv.id }).sortedByDescending { it.updatedAt }
            writeAtomic(index, json.encodeToString(ListSerializer(ChatConversation.serializer()), list))
            _list.value = list
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        ensure()
        withContext(Dispatchers.IO) {
            val msgs = runCatching { json.decodeFromString(ListSerializer(ChatMessage.serializer()), convFile(id).readText()) }.getOrDefault(emptyList())
            msgs.mapNotNull { it.imagePath }.forEach { runCatching { File(it).delete() } }
            convFile(id).delete()
            val list = _list.value.filter { it.id != id }
            writeAtomic(index, json.encodeToString(ListSerializer(ChatConversation.serializer()), list))
            _list.value = list
        }
    }

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }
}

// ---------------- network ----------------
@Serializable data class WireHistory(val role: String, val text: String)
@Serializable data class WireImage(val mime: String, val base64: String)
@Serializable data class WireLibItem(val t: String, val k: String? = null, val s: String? = null, val sc: Int? = null)
@Serializable data class WireLibrary(val summary: String? = null, val items: List<WireLibItem> = emptyList())
@Serializable
data class WireRequest(
    val text: String,
    val image: WireImage? = null,
    val history: List<WireHistory> = emptyList(),
    @SerialName("want_title") val wantTitle: Boolean = false,
    @SerialName("call_me") val callMe: String? = null,
    val persona: String? = null,
    val mode: String = "anime",
    val adult: Boolean = false,
    val library: WireLibrary? = null,
)
@Serializable
data class WireReply(
    val reply: String = "",
    val cards: List<ChatCard> = emptyList(),
    val title: String? = null,
    val limit: Int = DAILY_LIMIT,
    val used: Int = 0,
    val remaining: Int = DAILY_LIMIT,
    @SerialName("resets_at") val resetsAt: String? = null,
)
@Serializable data class WireStatus(val ready: Boolean = false, val limit: Int = DAILY_LIMIT, val used: Int = 0, val remaining: Int = DAILY_LIMIT)
@Serializable data class WireErrorBody(val code: String = "", val message: String = "")
@Serializable data class WireError(val error: WireErrorBody? = null)

sealed class ChatResult {
    data class Ok(val reply: WireReply) : ChatResult()
    /** «الخدمة قيد التجهيز»: the server has no AI key yet. */
    data object NotReady : ChatResult()
    data class Limit(val message: String) : ChatResult()
    data class Failed(val message: String) : ChatResult()
}

interface ChatBackend {
    suspend fun status(installId: String): WireStatus?
    suspend fun send(installId: String, req: WireRequest): ChatResult
}

/** Talks to the AnimeShadow module of Madyan's panel server; the AI key stays on that server. */
class HttpChatBackend(private val baseUrl: String, private val client: OkHttpClient = defaultClient(), private val session: () -> String? = { null }) : ChatBackend {
    /** v1.5.0: a signed-in user is counted per account (Pro = larger daily limit). */
    private fun Request.Builder.auth() = apply { session()?.let { header("Authorization", "Bearer $it") } }
    override suspend fun status(installId: String): WireStatus? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("${baseUrl.trimEnd('/')}/animeshadow/v1/chat/status").header("X-AS-Install", installId).auth().build()).execute().use { r ->
                if (!r.isSuccessful) null else json.decodeFromString(WireStatus.serializer(), r.body!!.string())
            }
        }.getOrNull()
    }

    override suspend fun send(installId: String, req: WireRequest): ChatResult = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(WireRequest.serializer(), req).toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url("${baseUrl.trimEnd('/')}/animeshadow/v1/chat").header("X-AS-Install", installId).auth().post(body).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isSuccessful) return@withContext ChatResult.Ok(json.decodeFromString(WireReply.serializer(), text))
                val err = runCatching { json.decodeFromString(WireError.serializer(), text).error }.getOrNull()
                when {
                    err?.code == "CHAT_NOT_READY" || err?.code == "CHAT_KEY_INVALID" || r.code == 404 -> ChatResult.NotReady
                    err?.code == "MAINTENANCE" -> { com.animeshadow.app.social.MaintenanceBus.signal(); ChatResult.Failed(err.message.ifBlank { "التطبيق في صيانة قصيرة." }) }
                    r.code == 429 -> ChatResult.Limit(err?.message?.takeIf { it.isNotBlank() } ?: "وصلت حد اليوم ($DAILY_LIMIT رسالة). يتجدد بعد منتصف الليل.")
                    else -> ChatResult.Failed(err?.message?.takeIf { it.isNotBlank() } ?: "شادو سينباي مشغول الحين، جرّب بعد شوي.")
                }
            }
        } catch (e: IOException) {
            ChatResult.Failed("ما في اتصال بالإنترنت، تأكد من الشبكة وأرسل مرة ثانية.")
        } catch (e: Exception) {
            ChatResult.Failed("صار خطأ غير متوقع، أرسل مرة ثانية.")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(70, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS).callTimeout(100, TimeUnit.SECONDS).build()
    }
}

/** Shrinks a picked image to ≤1024 px JPEG (fast upload, under the server's limit) and keeps a local copy for the bubble. */
object ChatImages {
    fun prepare(context: Context, uri: Uri, outDir: File): Pair<File, String>? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1024 || bounds.outHeight / (sample * 2) >= 1024) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val scale = minOf(1f, 1024f / maxOf(bmp.width, bmp.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        val bytes = ByteArrayOutputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it); it.toByteArray() }
        val f = File(outDir.apply { mkdirs() }, "${UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }
        f to Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()
}

/** Builds the short picture of the user's own lists the assistant reads to suggest and rate. */
class LibrarySnapshot(private val db: AppDatabase, private val mangaDb: MangaDatabase) {
    suspend fun build(): WireLibrary? = runCatching {
        val anime = db.libraryDao().all()
        val manga = mangaDb.manga().observeLibrary().first()
        if (anime.isEmpty() && manga.isEmpty()) return null
        val aStat = WatchStatus.entries.associateWith { s -> anime.count { it.entry.status == s } }
        val mStat = ReadStatus.entries.associateWith { s -> manga.count { it.entry.status == s } }
        val summary = buildString {
            if (anime.isNotEmpty()) append("أنمي: أشاهد ${aStat[WatchStatus.WATCHING]}، مكتمل ${aStat[WatchStatus.COMPLETED]}، أخطط ${aStat[WatchStatus.PLAN_TO_WATCH]}، متوقف ${aStat[WatchStatus.ON_HOLD]}، تركته ${aStat[WatchStatus.DROPPED]}. ")
            if (manga.isNotEmpty()) append("مانجا: أقرأ ${mStat[ReadStatus.READING]}، مكتملة ${mStat[ReadStatus.COMPLETED]}، أخطط ${mStat[ReadStatus.PLAN_TO_READ]}.")
        }
        val aItems = anime.sortedWith(compareByDescending<com.animeshadow.app.data.local.LibraryItem> { it.entry.userScore ?: -1 }.thenByDescending { it.entry.updatedAt })
            .take(40).mapNotNull { li ->
                val t = li.anime?.let { it.titleEnglish ?: it.titleRomaji } ?: return@mapNotNull null
                WireLibItem(t, "ANIME", animeStatus(li.entry.status), li.entry.userScore)
            }
        val mItems = manga.take(25).mapNotNull { r ->
            val t = r.manga?.let { it.titleEnglish ?: it.titleRomaji } ?: return@mapNotNull null
            WireLibItem(t, "MANGA", r.entry.status?.let(::mangaStatus) ?: "مفضلة", r.entry.userScore)
        }
        WireLibrary(summary.trim(), aItems + mItems)
    }.getOrNull()

    private fun animeStatus(s: WatchStatus) = when (s) {
        WatchStatus.WATCHING -> "أشاهده"; WatchStatus.PLAN_TO_WATCH -> "أخطط"; WatchStatus.COMPLETED -> "مكتمل"; WatchStatus.ON_HOLD -> "متوقف"; WatchStatus.DROPPED -> "تركته"
    }
    private fun mangaStatus(s: ReadStatus) = when (s) {
        ReadStatus.READING -> "أقرأها"; ReadStatus.PLAN_TO_READ -> "أخطط"; ReadStatus.COMPLETED -> "مكتملة"; ReadStatus.ON_HOLD -> "متوقفة"; ReadStatus.DROPPED -> "تركتها"
    }
}

/** Everything the chat screens need, in one place (Hilt singleton). */
class ChatRepository(
    val store: ChatStore,
    private val backend: ChatBackend,
    private val prefs: ExtraPrefs,
    private val library: suspend () -> WireLibrary?,
) {
    suspend fun status(): WireStatus? = backend.status(prefs.installId())

    /**
     * Sends one user message (text and/or ONE image) in [conv]; returns the updated conversation + messages.
     * The title is asked for on the first exchange and whenever the conversation still has none.
     */
    suspend fun send(conv: ChatConversation, history: List<ChatMessage>, text: String, image: Pair<File, String>?, mode: AppMode): Triple<ChatConversation, List<ChatMessage>, ChatResult> {
        val userMsg = ChatMessage(role = "user", text = text.trim(), imagePath = image?.first?.absolutePath)
        val chatPrefs = prefs.chat.first()
        val req = WireRequest(
            text = text.trim(),
            image = image?.let { WireImage("image/jpeg", it.second) },
            history = history.filter { it.error == null && (it.text.isNotBlank()) }.takeLast(12).map { WireHistory(if (it.isUser) "user" else "assistant", it.text.take(2000)) },
            wantTitle = conv.title.isBlank(),
            callMe = chatPrefs.callMe.ifBlank { null },
            persona = chatPrefs.persona.ifBlank { null },
            mode = if (mode == AppMode.MANGA) "manga" else "anime",
            adult = prefs.adultNow(),
            library = library(),
        )
        val result = backend.send(prefs.installId(), req)
        val now = System.currentTimeMillis()
        val msgs = history + userMsg + when (result) {
            is ChatResult.Ok -> ChatMessage(role = "assistant", text = result.reply.reply, cards = result.reply.cards, at = now)
            ChatResult.NotReady -> ChatMessage(role = "assistant", text = "", error = NOT_READY, at = now)
            is ChatResult.Limit -> ChatMessage(role = "assistant", text = "", error = result.message, at = now)
            is ChatResult.Failed -> ChatMessage(role = "assistant", text = "", error = result.message, at = now)
        }
        val title = when {
            conv.title.isNotBlank() -> conv.title
            result is ChatResult.Ok && !result.reply.title.isNullOrBlank() -> result.reply.title!!.take(40)
            else -> ""
        }
        val preview = (msgs.lastOrNull { it.error == null && it.text.isNotBlank() }?.text ?: text).replace('\n', ' ').take(80)
        val updated = conv.copy(title = title, updatedAt = now, preview = preview)
        // A first message the server could not take at all («قيد التجهيز») does not leave an empty conversation behind.
        if (!(result is ChatResult.NotReady && history.isEmpty())) store.save(updated, msgs)
        return Triple(updated, msgs, result)
    }

    companion object { const val NOT_READY = "المساعد غير مفعّل بعد" }
}
