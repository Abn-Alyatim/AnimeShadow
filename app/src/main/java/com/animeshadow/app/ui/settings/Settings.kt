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
) : ViewModel() {
    val ui: StateFlow<Settings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
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
)

@Composable
fun SettingsContent(s: Settings, onMenu: () -> Unit, actions: SettingsActions, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    var dialog by remember { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar(stringResource(R.string.nav_settings), onMenu = onMenu) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
            item { ClickRow(stringResource(R.string.settings_about), "v" + BuildConfig.VERSION_NAME) { dialog = "about" } }
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
        "disclaimer", "about" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = Palette.Card,
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(if (dialog == "about") stringResource(R.string.about_text, BuildConfig.VERSION_NAME) else stringResource(R.string.disclaimer_text)) },
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
private fun ClickRow(title: String, sub: String?, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) Palette.Dropped else Palette.Text)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
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

@Composable
fun SettingsScreen(onMenu: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
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
        ),
        snackbar,
    )
}
