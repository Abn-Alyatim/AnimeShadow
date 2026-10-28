package com.animeshadow.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.extraStore: DataStore<Preferences> by preferencesDataStore(name = "extra_prefs")

/** v1.4.0 «شادو سينباي» customisation. The assistant's NAME is not here on purpose: it is fixed («شادو سينباي»). */
data class ChatPrefs(val callMe: String = "", val persona: String = "")

/**
 * v1.4.0 settings kept apart from [SettingsRepository]: the «محتوى +18» switch (OFF by default, needs a one-time
 * "I am 18+" confirmation) and the chat's customisation + anonymous install id (used only for the daily message limit).
 */
class ExtraPrefs(private val store: DataStore<Preferences>) {
    private object Keys {
        val adult = booleanPreferencesKey("adult_content")
        val adultConfirmed = booleanPreferencesKey("adult_confirmed_18")
        val installId = stringPreferencesKey("chat_install_id")
        val callMe = stringPreferencesKey("chat_call_me")
        val persona = stringPreferencesKey("chat_persona")
    }

    /** True only when the user switched it on AND confirmed being 18+. */
    val adult: Flow<Boolean> = store.data.map { (it[Keys.adult] ?: false) && (it[Keys.adultConfirmed] ?: false) }
    val adultConfirmed: Flow<Boolean> = store.data.map { it[Keys.adultConfirmed] ?: false }
    suspend fun adultNow(): Boolean = adult.first()

    suspend fun setAdult(on: Boolean, confirmed18: Boolean = false) = store.edit {
        if (confirmed18) it[Keys.adultConfirmed] = true
        it[Keys.adult] = on && (it[Keys.adultConfirmed] ?: false)
    }

    val chat: Flow<ChatPrefs> = store.data.map { ChatPrefs(it[Keys.callMe].orEmpty(), it[Keys.persona].orEmpty()) }
    suspend fun setChat(p: ChatPrefs) = store.edit {
        it[Keys.callMe] = p.callMe.trim().take(MAX_CALL_ME)
        it[Keys.persona] = p.persona.trim().take(MAX_PERSONA)
    }

    /** Random, made once; identifies this install to the chat server's daily limit (no personal data). */
    suspend fun installId(): String {
        store.data.first()[Keys.installId]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { p -> if (p[Keys.installId] == null) p[Keys.installId] = id }
        return store.data.first()[Keys.installId] ?: id
    }

    companion object {
        const val MAX_CALL_ME = 30
        const val MAX_PERSONA = 400
    }
}
