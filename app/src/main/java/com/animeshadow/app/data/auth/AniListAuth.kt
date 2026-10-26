package com.animeshadow.app.data.auth

import android.content.SharedPreferences
import android.net.Uri
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.remote.AniListApi
import com.animeshadow.app.data.remote.GqlRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * v1.2 AniList login — OAuth 2 implicit grant (https://docs.anilist.co/guide/auth/implicit):
 * the app opens anilist.co/api/v2/oauth/authorize?client_id=…&response_type=token in the browser; after the user
 * approves, AniList redirects to the client's registered redirect URL, animeshadow://anilist-auth, with
 * #access_token=…&token_type=Bearer&expires_in=… in the fragment. No client secret exists in this flow, so nothing
 * secret is ever in the APK. The client id comes from BuildConfig.ANILIST_CLIENT_ID; empty = not activated yet.
 */
object AniListOAuth {
    const val AUTHORIZE = "https://anilist.co/api/v2/oauth/authorize"
    const val REDIRECT_SCHEME = "animeshadow"
    const val REDIRECT_HOST = "anilist-auth"

    fun authorizeUrl(clientId: String): String = "$AUTHORIZE?client_id=${Uri.encode(clientId)}&response_type=token"

    data class Token(val accessToken: String, val expiresAt: Long)

    /** Parses the redirect; null when it is not ours, carries an error, or has no token. */
    fun parseRedirect(uri: String, nowMs: Long): Token? {
        val u = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        if (u.scheme != REDIRECT_SCHEME || u.host != REDIRECT_HOST) return null
        val params = (u.encodedFragment ?: u.encodedQuery ?: return null).split('&').mapNotNull { kv ->
            val i = kv.indexOf('=')
            if (i <= 0) null else Uri.decode(kv.substring(0, i)) to Uri.decode(kv.substring(i + 1))
        }.toMap()
        if (params["error"] != null) return null
        val token = params["access_token"]?.takeIf { it.isNotBlank() } ?: return null
        val seconds = params["expires_in"]?.toLongOrNull() ?: (365L * 24 * 3600)
        return Token(token, nowMs + seconds * 1000)
    }
}

data class AniListAccount(
    val configured: Boolean = false,
    val loggedIn: Boolean = false,
    val name: String? = null,
    val avatar: String? = null,
)

/** Token + viewer profile, in the app's private preferences (never in backups: allowBackup=false). */
class AniListAuthRepository(
    private val prefs: SharedPreferences,
    private val api: AniListApi,
    private val clock: Clock,
    val clientId: String,
) {
    private val _account = MutableStateFlow(read())
    val account: StateFlow<AniListAccount> = _account

    val configured: Boolean get() = clientId.isNotBlank()

    fun authorizeUrl(): String? = if (configured) AniListOAuth.authorizeUrl(clientId) else null

    fun token(): String? {
        val t = prefs.getString(K_TOKEN, null) ?: return null
        if (clock.now() >= prefs.getLong(K_EXPIRES, 0)) return null
        return t
    }

    private fun read() = AniListAccount(
        configured = clientId.isNotBlank(),
        loggedIn = token() != null,
        name = prefs.getString(K_NAME, null),
        avatar = prefs.getString(K_AVATAR, null),
    )

    /** Handles the browser redirect. True when a token was stored (the profile name is fetched best-effort). */
    suspend fun handleRedirect(uri: String): Boolean {
        val t = AniListOAuth.parseRedirect(uri, clock.now()) ?: return false
        prefs.edit().putString(K_TOKEN, t.accessToken).putLong(K_EXPIRES, t.expiresAt).apply()
        try {
            val r = api.viewer("Bearer ${t.accessToken}", GqlRequest(VIEWER_QUERY, JsonObject(emptyMap())))
            r.data?.viewer?.let { v ->
                prefs.edit().putString(K_NAME, v.name).putString(K_AVATAR, v.avatar?.large ?: v.avatar?.medium).putInt(K_ID, v.id).apply()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Logged in anyway; the name shows after the next successful call.
        }
        _account.value = read()
        return true
    }

    fun logout() {
        prefs.edit().clear().apply()
        _account.value = read()
    }

    companion object {
        const val PREFS = "anilist_auth"
        private const val K_TOKEN = "token"
        private const val K_EXPIRES = "expires_at"
        private const val K_NAME = "name"
        private const val K_AVATAR = "avatar"
        private const val K_ID = "id"
        const val VIEWER_QUERY = "query { Viewer { id name avatar { large medium } } }"
    }
}
