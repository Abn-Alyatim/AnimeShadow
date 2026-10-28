package com.animeshadow.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
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
) {
    val signedIn: Boolean get() = email.isNotBlank()
}

sealed interface GoogleSignInResult {
    data class Success(val account: GoogleAccount) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object NoAccount : GoogleSignInResult
    /** No Web client id baked in, or Google says this app (package + SHA-1) is not registered yet. */
    data object NotConfigured : GoogleSignInResult
    data class Failed(val detail: String?) : GoogleSignInResult
}

/** Asks Google (Credential Manager) for the account. Faked in tests. */
fun interface GoogleSignInSource {
    suspend fun signIn(activityContext: Context, webClientId: String): GoogleSignInResult
}

/**
 * Credential Manager bottom sheet with the phone's Google accounts; when it has nothing to offer, the full
 * «Sign in with Google» flow. [webClientId] = the Web client ID of Madyan's Google Cloud project; the project must
 * also have an Android OAuth client for com.animeshadow.app with the signing SHA-1 (see GOOGLE_LOGIN_AR.md).
 */
class CredentialManagerSignIn : GoogleSignInSource {
    override suspend fun signIn(activityContext: Context, webClientId: String): GoogleSignInResult {
        if (webClientId.isBlank()) return GoogleSignInResult.NotConfigured
        val manager = CredentialManager.create(activityContext)
        val quick = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(webClientId).setAutoSelectEnabled(true).build()
        return try {
            parse(manager.getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(quick).build()))
        } catch (c: CancellationException) {
            throw c
        } catch (_: NoCredentialException) {
            try {
                val full = GetSignInWithGoogleOption.Builder(webClientId).build()
                parse(manager.getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(full).build()))
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                map(e)
            }
        } catch (e: Exception) {
            map(e)
        }
    }

    private fun map(e: Exception): GoogleSignInResult = when (e) {
        is GetCredentialCancellationException -> GoogleSignInResult.Cancelled
        is NoCredentialException -> GoogleSignInResult.NoAccount
        // "[28444] Developer console is not set up correctly" = the Android OAuth client (package + SHA-1) is missing.
        is GetCredentialException -> if (e.message.orEmpty().contains("28444") || e.message.orEmpty().contains("not set up", true)) GoogleSignInResult.NotConfigured else GoogleSignInResult.Failed(e.type)
        else -> GoogleSignInResult.Failed(e.javaClass.simpleName)
    }

    private fun parse(response: GetCredentialResponse): GoogleSignInResult {
        val c = response.credential
        if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return try {
                val g = GoogleIdTokenCredential.createFrom(c.data)
                GoogleSignInResult.Success(GoogleAccount(g.id, g.displayName ?: g.givenName ?: g.id.substringBefore('@'), g.profilePictureUri?.toString()))
            } catch (_: GoogleIdTokenParsingException) {
                GoogleSignInResult.Failed("token_parse")
            }
        }
        return GoogleSignInResult.Failed("unexpected_credential")
    }
}

/** The signed-in Google account, stored in private SharedPreferences (no token kept: the app has no server). */
class GoogleAccountRepository(private val prefs: SharedPreferences, val webClientId: String) {
    private val _account = MutableStateFlow(read())
    val account: StateFlow<GoogleAccount> = _account
    val configured: Boolean get() = webClientId.isNotBlank()

    fun save(a: GoogleAccount) {
        prefs.edit().putString(K_EMAIL, a.email).putString(K_NAME, a.name).putString(K_PHOTO, a.photoUrl).apply()
        _account.value = a
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _account.value = GoogleAccount()
    }

    private fun read() = GoogleAccount(prefs.getString(K_EMAIL, "").orEmpty(), prefs.getString(K_NAME, "").orEmpty(), prefs.getString(K_PHOTO, null))

    companion object {
        const val PREFS = "google_account"
        private const val K_EMAIL = "email"
        private const val K_NAME = "name"
        private const val K_PHOTO = "photo"
    }
}
