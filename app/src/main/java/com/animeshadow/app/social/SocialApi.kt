package com.animeshadow.app.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** v1.5.0: the AnimeShadow backend on Madyan's panel server (/animeshadow/v1). */
@Serializable data class Maintenance(val on: Boolean = false, val message: String? = null, val until: String? = null)
@Serializable data class Broadcast(val id: String, val title: String = "", val body: String = "", val at: String = "")
@Serializable data class AppStatus(
    val maintenance: Maintenance = Maintenance(),
    val broadcast: Broadcast? = null,
    @SerialName("sign_in_ready") val signInReady: Boolean = false,
)
@Serializable data class Pro(val active: Boolean = false, val plan: String? = null, val until: String? = null)
@Serializable data class Me(
    val id: String, val email: String = "", val name: String = "", val picture: String? = null, val username: String? = null,
    val code: String = "", val pro: Pro = Pro(), val banned: Boolean = false,
)
@Serializable data class SignInReply(val token: String, val me: Me)
@Serializable data class PublicUser(val id: String, val username: String? = null, val name: String = "", val picture: String? = null, val pro: Boolean = false) {
    val handle: String get() = username?.let { "@$it" } ?: name
}
@Serializable data class Friends(
    val friends: List<PublicUser> = emptyList(), val incoming: List<PublicUser> = emptyList(),
    val outgoing: List<PublicUser> = emptyList(), val blocked: List<PublicUser> = emptyList(),
)
/** One title in a shared list (anime or manga). */
@Serializable data class ListItem(val id: Int? = null, val t: String, val k: String = "ANIME", val s: String? = null, val sc: Double? = null)
@Serializable data class SharedList(val title: String, val items: List<ListItem>)
@Serializable data class Msg(
    val id: Long, val from: String, val to: String, val kind: String = "text", val text: String = "", val list: SharedList? = null,
    val at: String = "", val read: Boolean = false,
)
@Serializable data class Conversation(val user: PublicUser, val last: Msg, val unread: Int = 0)
@Serializable data class Conversations(val items: List<Conversation> = emptyList(), @SerialName("unread_total") val unreadTotal: Int = 0)
@Serializable data class Thread(val user: PublicUser, val items: List<Msg> = emptyList(), val blocked: Boolean = false, val friends: Boolean = false)
@Serializable data class RequestReply(val status: String, val user: PublicUser? = null)
@Serializable private data class ErrBody(val code: String = "", val message: String = "")
@Serializable private data class Err(val error: ErrBody? = null)

/** v1.5.0: any server call that answers MAINTENANCE asks the app to re-check its status (full-screen maintenance). */
object MaintenanceBus {
    private val _signals = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val signals: kotlinx.coroutines.flow.SharedFlow<Unit> = _signals
    fun signal() { _signals.tryEmit(Unit) }
}

/** A failed call, with the server's Arabic message (or a network one). */
class SocialException(val code: String, override val message: String, val status: Int = 0) : Exception(message)

class SocialApi(private val baseUrl: String, private val token: () -> String?, private val client: OkHttpClient = defaultClient()) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private suspend fun call(method: String, path: String, body: JsonElement? = null, auth: Boolean = true): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + "/animeshadow/v1" + path)
        if (auth) token()?.let { b.header("Authorization", "Bearer $it") }
        val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
        when (method) {
            "GET" -> b.get()
            "DELETE" -> b.delete(rb)
            "PUT" -> b.put(rb ?: "{}".toRequestBody("application/json".toMediaType()))
            else -> b.post(rb ?: "{}".toRequestBody("application/json".toMediaType()))
        }
        try {
            client.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isSuccessful) return@withContext text
                val e = runCatching { json.decodeFromString(Err.serializer(), text).error }.getOrNull()
                if (e?.code == "MAINTENANCE") MaintenanceBus.signal()
                throw SocialException(e?.code?.ifBlank { null } ?: "HTTP_${r.code}", e?.message?.ifBlank { null } ?: if (r.code == 404) NOT_DEPLOYED else BUSY, r.code)
            }
        } catch (e: IOException) {
            throw SocialException("OFFLINE", OFFLINE)
        }
    }

    suspend fun status(): AppStatus = json.decodeFromString(AppStatus.serializer(), call("GET", "/status", auth = false))
    suspend fun signIn(idToken: String): SignInReply = json.decodeFromString(SignInReply.serializer(), call("POST", "/auth/google", buildJsonObject { put("id_token", idToken) }, auth = false))
    suspend fun signOut() { runCatching { call("POST", "/auth/signout") } }
    suspend fun me(): Me = json.decodeFromString(Me.serializer(), call("GET", "/me"))
    suspend fun setUsername(u: String): Me = json.decodeFromString(Me.serializer(), call("PUT", "/me/username", buildJsonObject { put("username", u) }))
    suspend fun friends(): Friends = json.decodeFromString(Friends.serializer(), call("GET", "/friends"))
    suspend fun request(query: String): RequestReply = json.decodeFromString(RequestReply.serializer(), call("POST", "/friends/request", buildJsonObject { put("query", query) }))
    suspend fun accept(id: String) { call("POST", "/friends/$id/accept") }
    suspend fun remove(id: String) { call("DELETE", "/friends/$id") }
    suspend fun block(id: String) { call("POST", "/blocks/$id") }
    suspend fun unblock(id: String) { call("DELETE", "/blocks/$id") }
    suspend fun report(id: String, reason: String, messageId: Long? = null) {
        call("POST", "/reports", buildJsonObject { put("user_id", id); put("reason", reason); messageId?.let { put("message_id", it) } })
    }
    suspend fun conversations(): Conversations = json.decodeFromString(Conversations.serializer(), call("GET", "/conversations"))
    suspend fun thread(id: String, after: Long = 0): Thread = json.decodeFromString(Thread.serializer(), call("GET", "/messages/$id?after=$after"))
    suspend fun sendText(id: String, text: String): Msg = json.decodeFromString(Msg.serializer(), call("POST", "/messages/$id", buildJsonObject { put("text", text) }))
    suspend fun sendList(id: String, list: SharedList, text: String = ""): Msg {
        val body = buildJsonObject { put("text", text); put("list", json.encodeToJsonElement(SharedList.serializer(), list)) }
        return json.decodeFromString(Msg.serializer(), call("POST", "/messages/$id", body))
    }
    suspend fun markRead(id: String, upto: Long) { runCatching { call("POST", "/messages/$id/read", buildJsonObject { put("upto", upto) }) } }

    companion object {
        const val OFFLINE = "لا يوجد اتصال بالإنترنت."
        const val BUSY = "الخادم مشغول الآن، حاول بعد قليل."
        const val NOT_DEPLOYED = "الخدمة غير متاحة بعد على الخادم."
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    }
}

@Suppress("unused") private val keepJsonObject: Class<JsonObject> = JsonObject::class.java
