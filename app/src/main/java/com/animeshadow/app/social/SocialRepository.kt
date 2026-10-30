package com.animeshadow.app.social

import android.content.SharedPreferences
import com.animeshadow.app.data.auth.GoogleAccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * v1.5.0: Madyan Panel controls seen by the app — «إيقاف مؤقت» (full-screen maintenance) and «رسالة جماعية»
 * (an in-app card once per message). Checked on launch, on resume, and every 30 s while paused.
 */
class AppStatusRepository(private val api: SocialApi, private val prefs: SharedPreferences) {
    private val _status = MutableStateFlow<AppStatus?>(null)
    val status: StateFlow<AppStatus?> = _status
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    /** Unreachable server = keep the last answer (never lock the app because the panel is down). */
    suspend fun refresh(): AppStatus? {
        _checking.value = true
        try {
            val s = runCatching { api.status() }.getOrNull() ?: return _status.value
            _status.value = s
            return s
        } finally { _checking.value = false }
    }

    fun unseenBroadcast(s: AppStatus?): Broadcast? = s?.broadcast?.takeIf { it.id != prefs.getString(K_SEEN, null) && (it.title.isNotBlank() || it.body.isNotBlank()) }
    fun markSeen(b: Broadcast) { prefs.edit().putString(K_SEEN, b.id).apply() }

    companion object {
        const val PREFS = "as_status"
        private const val K_SEEN = "seen_broadcast"
    }
}

/** Keeps the server profile fresh (username, code, Pro) and drops a session the server no longer knows. */
class SocialRepository(val api: SocialApi, val account: GoogleAccountRepository) {
    val linked: Boolean get() = account.session != null

    suspend fun refreshMe(): Me? {
        if (!linked) return null
        return try { api.me().also { account.saveMe(it) } } catch (e: SocialException) {
            if (e.status == 401) account.forgetSession()
            null
        }
    }
}
