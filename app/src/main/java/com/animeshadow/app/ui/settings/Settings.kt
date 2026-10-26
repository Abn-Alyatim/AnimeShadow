package com.animeshadow.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.animeshadow.app.BuildConfig
import com.animeshadow.app.R
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.BackupManager
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.settings.Settings
import com.animeshadow.app.data.auth.AniListAccount
import com.animeshadow.app.data.auth.AniListAuthRepository
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.sync.SyncScheduler
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface SettingsEvent {
    data class Exported(val anime: Int, val episodes: Int) : SettingsEvent
    data class Imported(val anime: Int, val episodes: Int, val favorites: Int) : SettingsEvent
    data object ImportFailed : SettingsEvent
    data object Done : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupManager,
    private val library: LibraryRepository,
    private val anime: AnimeRepository,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
    private val auth: AniListAuthRepository,
) : ViewModel() {
    val ui: StateFlow<Settings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val account: StateFlow<AniListAccount> = auth.account
    fun loginUrl(): String? = auth.authorizeUrl()
    fun logout() = auth.logout()
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setNotifications(on: Boolean) = viewModelScope.launch {
        settings.setNotifications(on)
        runCatching { SyncScheduler.schedule(context, on) }
    }

    fun setSaveFilters(on: Boolean) = viewModelScope.launch { settings.setSaveFilters(on) }
    fun setMinutes(m: Int) = viewModelScope.launch { settings.setDefaultEpisodeMinutes(m) }
    fun setSeek(s: Int) = viewModelScope.launch { settings.setSeekSeconds(s) }

    fun export(uri: Uri) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { backup.export(it, clock.now()) } }.getOrNull()
        }
        _events.send(if (r != null) SettingsEvent.Exported(r.library.size, r.episodes.size) else SettingsEvent.ImportFailed)
    }

    fun import(uri: Uri) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { backup.import(it) } }.getOrNull()
        }
        _events.send(if (r != null) SettingsEvent.Imported(r.library, r.episodes, r.favoriteAnime + r.favoriteCharacters) else SettingsEvent.ImportFailed)
    }

    fun clearImages() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            context.imageLoader.memoryCache?.clear()
            runCatching { context.imageLoader.diskCache?.clear() }
        }
        _events.send(SettingsEvent.Done)
    }

    fun clearCatalogue() = viewModelScope.launch { anime.clearCatalogueCache(); _events.send(SettingsEvent.Done) }
    fun clearHistory() = viewModelScope.launch { library.clearHistory(); _events.send(SettingsEvent.Done) }
    fun clearAll() = viewModelScope.launch { library.clearUserData(); _events.send(SettingsEvent.Done) }
}

class SettingsActions(
    val onNotifications: (Boolean) -> Unit = {},
    val onSaveFilters: (Boolean) -> Unit = {},
    val onMinutes: (Int) -> Unit = {},
    val onSeek: (Int) -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onClearImages: () -> Unit = {},
    val onClearCatalogue: () -> Unit = {},
    val onClearHistory: () -> Unit = {},
    val onClearAll: () -> Unit = {},
    val onShare: () -> Unit = {},
    /** Only called when a client id is configured (otherwise the calm «not activated yet» dialog shows). */
    val onLogin: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onDeveloper: () -> Unit = {},
)

@Composable
fun SettingsContent(
    s: Settings,
    onMenu: () -> Unit,
    actions: SettingsActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    account: AniListAccount = AniListAccount(configured = BuildConfig.ANILIST_CLIENT_ID.isNotBlank()),
) {
    var dialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar(stringResource(R.string.nav_settings), onMenu = onMenu) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("settings_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            // v1.2: AniList login (optional; behind config).
            item { Header(stringResource(R.string.settings_account)) }
            item {
                if (account.loggedIn) {
                    AccountRow(account, actions.onLogout)
                } else {
                    ClickRow(stringResource(R.string.anilist_login), stringResource(R.string.anilist_login_sub), tag = "anilist_login") {
                        if (account.configured) actions.onLogin() else dialog = "anilist_off"
                    }
                }
            }

            item { Header(stringResource(R.string.settings_general)) }
            item { SwitchRow(stringResource(R.string.settings_notify_all), stringResource(R.string.settings_notify_all_sub), s.notifications, actions.onNotifications) }
            item { SwitchRow(stringResource(R.string.settings_save_filter), stringResource(R.string.settings_save_filter_sub), s.saveFilters, actions.onSaveFilters) }
            item { ClickRow(stringResource(R.string.settings_episode_minutes), stringResource(R.string.minutes_short, s.defaultEpisodeMinutes) + " — " + stringResource(R.string.settings_episode_minutes_sub)) { dialog = "minutes" } }

            item { Header(stringResource(R.string.settings_player)) }
            item { ClickRow(stringResource(R.string.settings_default_player), stringResource(R.string.settings_player_none) + "\n" + stringResource(R.string.settings_player_note)) { } }
            item { ClickRow(stringResource(R.string.settings_seek), stringResource(R.string.settings_seek_value, s.seekSeconds)) { dialog = "seek" } }

            item { Header(stringResource(R.string.settings_appearance)) }
            item { ClickRow(stringResource(R.string.settings_dark), stringResource(R.string.settings_amoled)) { } }

            item { Header(stringResource(R.string.settings_backup)) }
            item { ClickRow(stringResource(R.string.settings_export), null, onClick = actions.onExport) }
            item { ClickRow(stringResource(R.string.settings_import), null, onClick = actions.onImport) }

            item { Header(stringResource(R.string.settings_data)) }
            item { ClickRow(stringResource(R.string.settings_clear_images), null, onClick = actions.onClearImages) }
            item { ClickRow(stringResource(R.string.settings_clear_catalogue), null, onClick = actions.onClearCatalogue) }
            item { ClickRow(stringResource(R.string.settings_clear_history), null, onClick = actions.onClearHistory) }
            item { ClickRow(stringResource(R.string.settings_clear_all), null, danger = true) { dialog = "delete" } }

            item { Header(stringResource(R.string.settings_other)) }
            item { ClickRow(stringResource(R.string.settings_share), null, onClick = actions.onShare) }
            item { ClickRow(stringResource(R.string.settings_disclaimer), null) { dialog = "disclaimer" } }

            // v1.2: «عن التطبيق» with the developer row (taps open WhatsApp).
            item { Header(stringResource(R.string.settings_about_header)) }
            item { ClickRow(stringResource(R.string.settings_version), "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", tag = "about_row") { dialog = "about" } }
            item { DeveloperRow(actions.onDeveloper) }
        }
    }
    when (dialog) {
        "minutes" -> ChoiceDialog(
            stringResource(R.string.settings_episode_minutes),
            listOf(5, 12, 20, 22, 24, 25, 30, 45, 60).map { it to stringResource(R.string.minutes_short, it) },
            s.defaultEpisodeMinutes, { actions.onMinutes(it); dialog = null }, { dialog = null },
        )
        "seek" -> ChoiceDialog(
            stringResource(R.string.settings_seek),
            listOf(5, 10, 15, 30, 85).map { it to stringResource(R.string.settings_seek_value, it) },
            s.seekSeconds, { actions.onSeek(it); dialog = null }, { dialog = null },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = Palette.Card,
            text = { Text(stringResource(R.string.confirm_delete_all)) },
            confirmButton = { TextButton(onClick = { actions.onClearAll(); dialog = null }) { Text(stringResource(R.string.delete), color = Palette.Dropped) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        "anilist_off" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = Palette.Card,
            modifier = Modifier.testTag("anilist_off_dialog"),
            title = { Text(stringResource(R.string.anilist_not_active_title), style = MaterialTheme.typography.headlineSmall.mixed()) },
            text = { Text(stringResource(R.string.anilist_not_active_text), style = MaterialTheme.typography.bodyMedium.mixed()) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.ok)) } },
        )
        "disclaimer", "about" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = Palette.Card,
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(if (dialog == "about") stringResource(R.string.about_text, BuildConfig.VERSION_NAME) else stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodyMedium.mixed()) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Accent, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
    }
    HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ClickRow(title: String, sub: String?, danger: Boolean = false, tag: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge.mixed(), color = if (danger) Palette.Dropped else Palette.Text)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall.mixed(), color = Palette.TextMuted)
    }
    HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
}

/**
 * v1.2 bidi fix: Arabic text that contains Latin words («تسجيل الدخول بحساب AniList») came out in the wrong word
 * order, because an unspecified text direction follows the (left-to-right) layout. Content direction orders the
 * words correctly; the alignment stays left like every other row.
 */
private fun androidx.compose.ui.text.TextStyle.mixed() =
    copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content, textAlign = androidx.compose.ui.text.style.TextAlign.Left)

/** «المطوّر»: name (Arabic / English) and the WhatsApp number; tapping opens wa.me. */
@Composable
private fun DeveloperRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("developer_row").clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_developer), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.developer_name), style = MaterialTheme.typography.bodyMedium.mixed(), color = Palette.TextSecondary)
            // Latin digits, isolated so "+967…" never flips inside Arabic text.
            Text("\u2066" + stringResource(R.string.developer_phone) + "\u2069", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Icon(
            Icons.AutoMirrored.Filled.Chat, stringResource(R.string.cd_whatsapp),
            tint = Color(0xFF25D366), modifier = Modifier.size(24.dp),
        )
    }
    HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun AccountRow(account: AniListAccount, onLogout: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("anilist_account").padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (account.avatar != null) {
            coil.compose.AsyncImage(account.avatar, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            stringResource(R.string.anilist_logged_in_as, "\u2068" + (account.name ?: "AniList") + "\u2069"),
            style = MaterialTheme.typography.bodyLarge.mixed(), modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onLogout) { Text(stringResource(R.string.anilist_logout), color = Palette.Accent) }
    }
    HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Palette.Card,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (v, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(v) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = v == selected, onClick = { onPick(v) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Opens a link in the browser / WhatsApp; a calm toast when no app can open it. */
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, R.string.no_app_to_open, android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsScreen(onMenu: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setNotifications(granted) }
    val msgExport = stringResource(R.string.done)
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            val text = when (e) {
                is SettingsEvent.Exported -> context.getString(R.string.export_done, e.anime.toString(), e.episodes.toString())
                is SettingsEvent.Imported -> context.getString(R.string.import_done, e.anime.toString(), e.episodes.toString(), e.favorites.toString())
                SettingsEvent.ImportFailed -> context.getString(R.string.import_failed)
                SettingsEvent.Done -> context.getString(R.string.done)
            }
            snackbar.showSnackbar(text.ifBlank { msgExport })
        }
    }
    SettingsContent(
        s, onMenu,
        SettingsActions(
            onNotifications = { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setNotifications(on)
            },
            onSaveFilters = { vm.setSaveFilters(it) },
            onMinutes = { vm.setMinutes(it) },
            onSeek = { vm.setSeek(it) },
            onExport = { exportLauncher.launch("anime-shadow-backup.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onClearImages = { vm.clearImages() },
            onClearCatalogue = { vm.clearCatalogue() },
            onClearHistory = { vm.clearHistory() },
            onClearAll = { vm.clearAll() },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text)) }
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            },
            onLogin = { vm.loginUrl()?.let { openUrl(context, it) } },
            onLogout = { vm.logout() },
            onDeveloper = { openUrl(context, context.getString(R.string.developer_whatsapp_url)) },
        ),
        snackbar,
        account,
    )
}
