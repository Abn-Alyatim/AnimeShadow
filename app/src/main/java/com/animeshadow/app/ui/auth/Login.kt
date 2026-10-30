package com.animeshadow.app.ui.auth

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.data.auth.CredentialManagerSignIn
import com.animeshadow.app.data.auth.GoogleAccountRepository
import com.animeshadow.app.data.auth.GoogleSignInResult
import com.animeshadow.app.data.auth.GoogleSignInSource
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.ShadowLoop
import com.animeshadow.app.ui.profile.GoogleG
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the login screen tells the user (calm Arabic, no retry button). */
enum class LoginMessage { NOT_CONFIGURED, NO_ACCOUNT, FAILED, DONE, DEVELOPER, NETWORK, PLAY_SERVICES, DONE_OFFLINE }

/** v1.5.0: clear Arabic for each failure (error 10 says exactly what is missing in Google Cloud). */
@Composable
fun loginMessageText(m: LoginMessage): String? = when (m) {
    LoginMessage.NOT_CONFIGURED -> stringResource(R.string.login_not_ready)
    LoginMessage.DEVELOPER -> stringResource(R.string.login_error_10)
    LoginMessage.NO_ACCOUNT -> stringResource(R.string.google_no_account)
    LoginMessage.NETWORK -> stringResource(R.string.login_network)
    LoginMessage.PLAY_SERVICES -> stringResource(R.string.login_play_services)
    LoginMessage.FAILED -> stringResource(R.string.login_failed_generic)
    LoginMessage.DONE_OFFLINE -> stringResource(R.string.login_done_offline)
    LoginMessage.DONE -> null
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val repo: GoogleAccountRepository, private val api: com.animeshadow.app.social.SocialApi? = null) : ViewModel() {
    /** Swapped in tests. */
    var source: GoogleSignInSource = CredentialManagerSignIn()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _events = Channel<LoginMessage>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun signIn(activity: Context) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val r = try { source.signIn(activity, repo.webClientId) } finally { _busy.value = false }
            when (r) {
                is GoogleSignInResult.Success -> {
                    repo.save(r.account)
                    // v1.5.0: link to the AnimeShadow server (friends, chat, Pro). The library works without it.
                    val linked = if (api != null && r.idToken.isNotBlank()) {
                        _busy.value = true
                        try { runCatching { api.signIn(r.idToken) }.getOrNull()?.also { repo.saveSession(it.token, it.me) } } finally { _busy.value = false }
                    } else null
                    _events.send(if (linked != null || api == null) LoginMessage.DONE else LoginMessage.DONE_OFFLINE)
                }
                GoogleSignInResult.NotConfigured -> _events.send(LoginMessage.NOT_CONFIGURED)
                GoogleSignInResult.Developer -> _events.send(LoginMessage.DEVELOPER)
                GoogleSignInResult.NoAccount -> _events.send(LoginMessage.NO_ACCOUNT)
                GoogleSignInResult.Network -> _events.send(LoginMessage.NETWORK)
                GoogleSignInResult.PlayServices -> _events.send(LoginMessage.PLAY_SERVICES)
                is GoogleSignInResult.Failed -> _events.send(LoginMessage.FAILED)
                GoogleSignInResult.Cancelled -> Unit
            }
        }
    }
}

/**
 * v1.3 item 8 — the login screen, AnimeSlayer style: back arrow + «تسجيل الدخول», the splash video at the top with
 * the app name under it, and ONE way in: Google (round button). Nothing else (no email, no password).
 */
@Composable
fun LoginContent(onBack: () -> Unit, onGoogle: () -> Unit, busy: Boolean = false, snackbar: SnackbarHostState = remember { SnackbarHostState() }, message: String? = null) {
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar(stringResource(R.string.login_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).testTag("login_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.testTag("shadow_icon")) { ShadowLoop(Modifier.width(132.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.app_name), color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, letterSpacing = 3.sp), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(56.dp))
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Color.White).border(2.dp, Palette.Accent, CircleShape)
                    .clickable(enabled = !busy, onClick = onGoogle).testTag("google_button"),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(30.dp), color = Palette.Accent, strokeWidth = 3.dp)
                else GoogleG(Modifier.size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.google_login), style = MaterialTheme.typography.titleSmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), color = Palette.RefText)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.anilist_login_sub), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
            )
            // v1.5.0: the reason stays on screen (a snackbar vanished before error 10's explanation could be read).
            androidx.compose.animation.AnimatedVisibility(visible = message != null, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()) {
                Text(
                    message.orEmpty(), style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp, textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
                    color = Palette.RefText, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .border(1.dp, Palette.Divider, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(14.dp).testTag("login_message"),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun LoginScreen(onBack: () -> Unit, vm: LoginViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<LoginMessage?>(null) }
    LaunchedEffect(Unit) {
        vm.events.collect { m ->
            when (m) {
                LoginMessage.DONE, LoginMessage.DONE_OFFLINE -> {
                    android.widget.Toast.makeText(context, if (m == LoginMessage.DONE) R.string.google_login_done else R.string.login_done_offline, android.widget.Toast.LENGTH_LONG).show()
                    onBack()
                }
                else -> shown = m
            }
        }
    }
    LoginContent(onBack, { shown = null; vm.signIn(context) }, busy, snackbar, shown?.let { loginMessageText(it) })
}
