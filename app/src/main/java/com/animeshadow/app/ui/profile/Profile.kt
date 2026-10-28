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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.animeshadow.app.data.auth.GoogleAccount
import com.animeshadow.app.data.auth.GoogleAccountRepository

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
    private val google: GoogleAccountRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val ui: StateFlow<ProfileUi> = combine(library.observeLibrary(), library.observeFavoriteAnimeCount(), settings.settings) { items, favs, s ->
        val cover = items.filter { it.anime?.banner != null }.maxByOrNull { it.entry.userScore ?: 0 }?.anime
            ?: items.firstOrNull { it.anime != null }?.anime
        ProfileUi(s.profile, StatsCalculator.compute(items, s.defaultEpisodeMinutes), favs, cover)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi())

    val account: StateFlow<GoogleAccount> = google.account

    fun signOut() = google.signOut()

    fun save(profile: Profile) = viewModelScope.launch { settings.setProfile(profile.copy(avatarPath = ui.value.profile.avatarPath)) }

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

    /** v1.3 item 5: «حذف الصورة» inside Edit Profile. */
    fun deleteAvatar() = viewModelScope.launch {
        withContext(Dispatchers.IO) { context.filesDir.listFiles { x -> x.name.startsWith("avatar_") }?.forEach { it.delete() } }
        settings.setProfile(ui.value.profile.copy(avatarPath = null))
    }
}

/**
 * v1.3: the account section moved here from Settings (item 1); the avatar is NOT a button any more (item 5: the
 * photo is changed / deleted inside «تعديل الملف الشخصي»); the stats show counts only, no percentages (item 4).
 */
@Composable
fun ProfileContent(
    ui: ProfileUi,
    onMenu: () -> Unit,
    onEdit: () -> Unit,
    account: GoogleAccount = GoogleAccount(),
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val stats = ui.stats
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_stats), onMenu = onMenu) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("profile_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    Poster(ui.cover?.banner ?: ui.cover?.coverXL, Modifier.fillMaxWidth().height(150.dp), placeholderColor = ui.cover?.color)
                    Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xFF000000)))))
                    Avatar(ui.profile.avatarPath, Modifier.align(Alignment.BottomCenter).testTag("profile_avatar"))
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.profile.name.ifBlank { account.name }.ifBlank { stringResource(R.string.profile_default_name) }, style = MaterialTheme.typography.titleLarge)
                    if (ui.profile.handle.isNotBlank()) Text("\u2068@" + ui.profile.handle + "\u2069", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    if (ui.profile.bio.isNotBlank()) Text("\u2068" + ui.profile.bio + "\u2069", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onEdit, modifier = Modifier.padding(top = 8.dp).testTag("edit_profile")) { Text(stringResource(R.string.edit_profile)) }
                }
            }
            item { AccountSection(account, onLogin, onLogout) }
            if (stats != null) {
                item {
                    Card {
                        DonutChart(
                            listOf(ChartSlice(stringResource(R.string.nav_fav_anime), ui.favoriteCount, Palette.Favorite)) +
                                WatchStatus.entries.map { ChartSlice(stringResource(watchStatusRes(it)), stats.count(it), watchStatusColor(it)) },
                            centerText = stats.total.toString(),
                            showPercent = false,
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
}

/** «الحساب»: Google only. Signed in → photo, name, email and «تسجيل الخروج»; otherwise one row that opens the login. */
@Composable
fun AccountSection(account: GoogleAccount, onLogin: () -> Unit, onLogout: () -> Unit) {
    Card {
        Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
        Spacer(Modifier.height(8.dp))
        if (account.signedIn) {
            Row(Modifier.fillMaxWidth().testTag("account_signed_in"), verticalAlignment = Alignment.CenterVertically) {
                val m = Modifier.size(44.dp).clip(CircleShape).background(Palette.Card)
                if (account.photoUrl != null) AsyncImage(account.photoUrl, null, m, contentScale = ContentScale.Crop)
                else Box(m, contentAlignment = Alignment.Center) { GoogleG(Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("\u2066" + account.email + "\u2069", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(vertical = 10.dp))
            Text(
                stringResource(R.string.google_logout), color = Palette.Dropped, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(vertical = 4.dp).testTag("google_logout"),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onLogin).padding(vertical = 6.dp).testTag("account_login"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { GoogleG(Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.google_login), style = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl))
                    Text(stringResource(R.string.anilist_login_sub), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
        }
    }
}

/** Google's "G" (drawn from the vector resource). */
@Composable
fun GoogleG(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_google_g), null, modifier)
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface).padding(16.dp)) { content() }
}

@Composable
private fun Avatar(path: String?, modifier: Modifier, size: androidx.compose.ui.unit.Dp = 104.dp) {
    val m = modifier.size(size).clip(CircleShape).border(3.dp, Palette.Accent, CircleShape).background(Palette.Card)
    if (path != null) AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = m)
    else Image(painterResource(R.drawable.as_logo), null, m, contentScale = ContentScale.Crop)
}

/** v1.3 item 5: Edit Profile is a screen; the photo is changed or deleted here (and only here). */
@Composable
fun EditProfileContent(
    initial: Profile,
    onBack: () -> Unit,
    onSave: (Profile) -> Unit,
    onPickAvatar: () -> Unit,
    onDeleteAvatar: () -> Unit,
) {
    var name by remember(initial.name) { mutableStateOf(initial.name) }
    var handle by remember(initial.handle) { mutableStateOf(initial.handle) }
    var bio by remember(initial.bio) { mutableStateOf(initial.bio) }
    val save = { onSave(initial.copy(name = name, handle = handle, bio = bio)) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            AppTopBar(stringResource(R.string.edit_profile), onBack = onBack) {
                IconButton(onClick = save, modifier = Modifier.testTag("edit_save")) { Icon(Icons.Filled.Check, stringResource(R.string.save), tint = Palette.Accent) }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Avatar(initial.avatarPath, Modifier.testTag("edit_avatar"), size = 120.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPickAvatar,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.White),
                    modifier = Modifier.testTag("avatar_change"),
                ) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.avatar_change))
                }
                if (initial.avatarPath != null) {
                    OutlinedButton(onClick = onDeleteAvatar, modifier = Modifier.testTag("avatar_delete")) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = Palette.Dropped); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.avatar_delete), color = Palette.Dropped)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, focusedLabelColor = Palette.Accent, cursorColor = Palette.Accent)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(handle, { handle = it.filter { c -> !c.isWhitespace() }.take(30) }, label = { Text(stringResource(R.string.profile_handle)) }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(bio, { bio = it.take(160) }, label = { Text(stringResource(R.string.profile_bio)) }, maxLines = 3, colors = colors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = save, modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.White),
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ProfileScreen(onMenu: () -> Unit, onEdit: () -> Unit, onLogin: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    ProfileContent(ui, onMenu, onEdit, account, onLogin, { vm.signOut() })
}

@Composable
fun EditProfileScreen(onBack: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setAvatar(uri) }
    EditProfileContent(
        ui.profile, onBack, { vm.save(it); onBack() },
        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        { vm.deleteAvatar() },
    )
}
