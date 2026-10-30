package com.animeshadow.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** v1.3: the app's only login is Google. The account lives on this phone (name, email, photo); no password ever. */
data class GoogleAccount(
    val email: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    /** v1.5.0: Pro granted in Madyan Panel for this Gmail (from the AnimeShadow server). */
    val pro: Boolean = false,
) {
    val signedIn: Boolean get() = email.isNotBlank()
}

sealed interface GoogleSignInResult {
    data class Success(val account: GoogleAccount, val idToken: String = "") : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object NoAccount : GoogleSignInResult
    /** No Web client id baked into this build: «تسجيل الدخول غير مفعّل بعد». */
    data object NotConfigured : GoogleSignInResult
    /** Google's error 10: the Android OAuth client (package + SHA-1) isn't registered in the Cloud project yet. */
    data object Developer : GoogleSignInResult
    data object Network : GoogleSignInResult
    data object PlayServices : GoogleSignInResult
    data class Failed(val detail: String?) : GoogleSignInResult
}

/** Asks Google (Credential Manager) for the account. Faked in tests. */
fun interface GoogleSignInSource {
    suspend fun signIn(activityContext: Context, webClientId: String): GoogleSignInResult
}

/** v1.5.0: why Credential Manager failed, from the exception class + message (pure, unit-tested). */
object GoogleSignInErrors {
    private val devRx = Regex("(^|[^0-9])10:|developer console|developer_error|\\[28444]|not set up correctly")
    fun classify(type: String, message: String?): GoogleSignInResult {
        val m = message.orEmpty().lowercase()
        return when {
            type == "GetCredentialCancellationException" -> GoogleSignInResult.Cancelled
            devRx.containsMatchIn(m) -> GoogleSignInResult.Developer
            type == "GetCredentialProviderConfigurationException" || type == "GetCredentialUnsupportedException" -> GoogleSignInResult.PlayServices
            type == "NoCredentialException" -> GoogleSignInResult.NoAccount
            Regex("(^|[^0-9])7:").containsMatchIn(m) || "network" in m || "unable to resolve host" in m -> GoogleSignInResult.Network
            Regex("(^|[^0-9])(16|12501):").containsMatchIn(m) || "cancel" in m -> GoogleSignInResult.Cancelled
            else -> GoogleSignInResult.Failed(type.removePrefix("GetCredential").removeSuffix("Exception").ifBlank { null })
        }
    }
}

/**
 * Same order as دفتري 1.1.4: an account that already approved the app (silent) → any Google account on the phone
 * (first sign-in) → the full «Sign in with Google» sheet, which can also add an account. [webClientId] = the Web client ID
 * of AnimeShadow's own Google Cloud project (serverClientId); that project must also hold an Android OAuth client for
 * com.animeshadow.app + the signing SHA-1, otherwise Google answers error 10 (shown as such).
 */
class CredentialManagerSignIn : GoogleSignInSource {
    override suspend fun signIn(activityContext: Context, webClientId: String): GoogleSignInResult {
        if (webClientId.isBlank()) return GoogleSignInResult.NotConfigured
        val manager = CredentialManager.create(activityContext)
        val options = listOf(
            GetGoogleIdOption.Builder().setServerClientId(webClientId).setFilterByAuthorizedAccounts(true).setAutoSelectEnabled(true).build(),
            GetGoogleIdOption.Builder().setServerClientId(webClientId).setFilterByAuthorizedAccounts(false).build(),
            GetSignInWithGoogleOption.Builder(webClientId).build(),
        )
        var last: GoogleSignInResult = GoogleSignInResult.NoAccount
        for (o in options) {
            try {
                return parse(manager.getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(o).build()))
            } catch (c: CancellationException) {
                throw c
            } catch (e: NoCredentialException) {
                last = GoogleSignInErrors.classify(e.javaClass.simpleName, e.message)
                if (last == GoogleSignInResult.Developer) break // error 10 rides NoCredentialException too; other options won't help
            } catch (e: GetCredentialException) {
                return GoogleSignInErrors.classify(e.javaClass.simpleName, e.message)
            } catch (e: Exception) {
                return GoogleSignInResult.Failed(e.javaClass.simpleName)
            }
        }
        return last
    }

    private fun parse(response: GetCredentialResponse): GoogleSignInResult {
        val c = response.credential
        if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return try {
                val g = GoogleIdTokenCredential.createFrom(c.data)
                GoogleSignInResult.Success(GoogleAccount(g.id, g.displayName ?: g.givenName ?: g.id.substringBefore('@'), g.profilePictureUri?.toString()), g.idToken)
            } catch (_: GoogleIdTokenParsingException) {
                GoogleSignInResult.Failed("token_parse")
            }
        }
        return GoogleSignInResult.Failed("unexpected_credential")
    }

    /** Forgets the chosen account so the next sign-in shows the chooser again. */
    suspend fun clear(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
    }
}

/**
 * The signed-in Google account, stored in private SharedPreferences. v1.5.0: plus the AnimeShadow server session
 * (an opaque token the server made after checking Google's ID token — never the Google token itself) and the
 * last known server profile (username, friend code, Pro).
 */
class GoogleAccountRepository(private val prefs: SharedPreferences, val webClientId: String) {
    private val _me = MutableStateFlow(readMe())
    private val _account = MutableStateFlow(read())
    val account: StateFlow<GoogleAccount> = _account
    val configured: Boolean get() = webClientId.isNotBlank()
    /** Server profile (null = not linked to the server yet). */
    val me: StateFlow<com.animeshadow.app.social.Me?> = _me
    val session: String? get() = prefs.getString(K_SESSION, null)

    fun save(a: GoogleAccount) {
        prefs.edit().putString(K_EMAIL, a.email).putString(K_NAME, a.name).putString(K_PHOTO, a.photoUrl).apply()
        _account.value = a.copy(pro = _me.value?.pro?.active == true)
    }

    fun saveSession(token: String, me: com.animeshadow.app.social.Me) {
        prefs.edit().putString(K_SESSION, token).apply()
        saveMe(me)
    }

    fun saveMe(me: com.animeshadow.app.social.Me) {
        prefs.edit().putString(K_ME, meJson.encodeToString(com.animeshadow.app.social.Me.serializer(), me)).apply()
        _me.value = me
        _account.value = _account.value.copy(pro = me.pro.active)
    }

    /** The server forgot this session (signed out elsewhere / reset): stay signed in locally, re-link on next sign-in. */
    fun forgetSession() {
        prefs.edit().remove(K_SESSION).remove(K_ME).apply()
        _me.value = null
        _account.value = _account.value.copy(pro = false)
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _account.value = GoogleAccount()
        _me.value = null
    }

    private fun readMe(): com.animeshadow.app.social.Me? = prefs.getString(K_ME, null)?.let { runCatching { meJson.decodeFromString(com.animeshadow.app.social.Me.serializer(), it) }.getOrNull() }

    private fun read() = GoogleAccount(prefs.getString(K_EMAIL, "").orEmpty(), prefs.getString(K_NAME, "").orEmpty(), prefs.getString(K_PHOTO, null), _me.value?.pro?.active == true)

    companion object {
        const val PREFS = "google_account"
        private const val K_EMAIL = "email"
        private const val K_NAME = "name"
        private const val K_PHOTO = "photo"
        private const val K_SESSION = "as_session"
        private const val K_ME = "as_me"
        private val meJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
