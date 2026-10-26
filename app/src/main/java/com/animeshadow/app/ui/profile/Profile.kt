package com.animeshadow.app.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.animeshadow.app.R
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.Profile
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.domain.LibraryStats
import com.animeshadow.app.domain.StatsCalculator
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.ChartSlice
import com.animeshadow.app.ui.common.DonutChart
import com.animeshadow.app.ui.common.HorizontalBars
import com.animeshadow.app.ui.common.Poster
import com.animeshadow.app.ui.common.SectionHeader
import com.animeshadow.app.ui.common.watchStatusColor
import com.animeshadow.app.ui.common.watchStatusRes
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class ProfileUi(
    val profile: Profile = Profile(),
    val stats: LibraryStats? = null,
    val favoriteCount: Int = 0,
    val cover: AnimeEntity? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    library: LibraryRepository,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val ui: StateFlow<ProfileUi> = combine(library.observeLibrary(), library.observeFavoriteAnimeCount(), settings.settings) { items, favs, s ->
        val cover = items.filter { it.anime?.banner != null }.maxByOrNull { it.entry.userScore ?: 0 }?.anime
            ?: items.firstOrNull { it.anime != null }?.anime
        ProfileUi(s.profile, StatsCalculator.compute(items, s.defaultEpisodeMinutes), favs, cover)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi())

    fun save(profile: Profile) = viewModelScope.launch { settings.setProfile(profile) }

    /** Copies the picked photo into app storage so it survives the gallery item being deleted. */
    fun setAvatar(uri: Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                context.filesDir.listFiles { x -> x.name.startsWith("avatar_") && x.name != f.name }?.forEach { it.delete() }
                f.absolutePath
            }.getOrNull()
        } ?: return@launch
        settings.setProfile(ui.value.profile.copy(avatarPath = path))
    }
}

@Composable
fun ProfileContent(ui: ProfileUi, onMenu: () -> Unit, onSave: (Profile) -> Unit, onPickAvatar: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val stats = ui.stats
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_stats), onMenu = onMenu) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    Poster(ui.cover?.banner ?: ui.cover?.coverXL, Modifier.fillMaxWidth().height(150.dp), placeholderColor = ui.cover?.color)
                    Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xFF000000)))))
                    Avatar(ui.profile.avatarPath, Modifier.align(Alignment.BottomCenter).clickable(onClick = onPickAvatar))
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.profile.name.ifBlank { stringResource(R.string.profile_default_name) }, style = MaterialTheme.typography.titleLarge)
                    if (ui.profile.handle.isNotBlank()) Text("\u2068@" + ui.profile.handle + "\u2069", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    if (ui.profile.bio.isNotBlank()) Text("\u2068" + ui.profile.bio + "\u2069", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.edit_profile)) }
                }
            }
            if (stats != null) {
                item {
                    Card {
                        DonutChart(
                            listOf(ChartSlice(stringResource(R.string.nav_fav_anime), ui.favoriteCount, Palette.Favorite)) +
                                WatchStatus.entries.map { ChartSlice(stringResource(watchStatusRes(it)), stats.count(it), watchStatusColor(it)) },
                            centerText = stats.total.toString(),
                        )
                        Text(
                            stringResource(R.string.stats_episodes, stats.episodesWatched),
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                item {
                    Card {
                        SectionHeader(stringResource(R.string.stats_watch_time))
                        val hours = String.format(Locale.US, "%.1f", stats.totalHours)
                        Text(stringResource(R.string.stats_hours, hours), style = MaterialTheme.typography.headlineSmall, color = Palette.Accent)
                        Text(
                            if (stats.usesDefaultDuration) stringResource(R.string.stats_estimate_note, stats.episodesWithoutDuration, stats.defaultMinutes)
                            else stringResource(R.string.stats_estimate_plain),
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                        )
                        stats.meanUserScore?.let {
                            Text(stringResource(R.string.stats_mean_score) + ": ★ " + String.format(Locale.US, "%.1f", it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                if (stats.topGenres.isNotEmpty()) item {
                    Card {
                        SectionHeader(stringResource(R.string.stats_top_genres))
                        HorizontalBars(stats.topGenres.map { ChartSlice(it.first, it.second, Palette.Accent) })
                    }
                }
            }
        }
    }
    if (editing) EditProfileDialog(ui.profile, { onSave(it); editing = false }, { editing = false })
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface).padding(16.dp)) { content() }
}

@Composable
private fun Avatar(path: String?, modifier: Modifier) {
    val m = modifier.size(104.dp).clip(CircleShape).border(3.dp, Palette.Accent, CircleShape).background(Palette.Card)
    if (path != null) AsyncImage(model = File(path), contentDescription = stringResource(R.string.change_avatar), contentScale = ContentScale.Crop, modifier = m)
    else Image(painterResource(R.drawable.as_logo), stringResource(R.string.change_avatar), m, contentScale = ContentScale.Crop)
}

@Composable
private fun EditProfileDialog(initial: Profile, onSave: (Profile) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var handle by remember { mutableStateOf(initial.handle) }
    var bio by remember { mutableStateOf(initial.bio) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        title = { Text(stringResource(R.string.edit_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true)
                OutlinedTextField(handle, { handle = it.filter { c -> !c.isWhitespace() }.take(30) }, label = { Text(stringResource(R.string.profile_handle)) }, singleLine = true)
                OutlinedTextField(bio, { bio = it.take(160) }, label = { Text(stringResource(R.string.profile_bio)) }, maxLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(initial.copy(name = name, handle = handle, bio = bio)) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ProfileScreen(onMenu: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setAvatar(uri) }
    ProfileContent(ui, onMenu, { vm.save(it) }) {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
