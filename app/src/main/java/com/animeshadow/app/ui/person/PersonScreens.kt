package com.animeshadow.app.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.AppJson
import com.animeshadow.app.data.Appearance
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.Refresh
import com.animeshadow.app.data.VoiceRole
import com.animeshadow.app.data.local.CharacterEntity
import com.animeshadow.app.data.local.StaffEntity
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.Poster
import com.animeshadow.app.ui.common.SectionHeader
import com.animeshadow.app.ui.common.StatusBanner
import com.animeshadow.app.ui.common.compactCount
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

fun decodeAppearances(json: String?): List<Appearance> =
    json?.let { runCatching { AppJson.decodeFromString<List<Appearance>>(it) }.getOrNull() }.orEmpty().distinctBy { it.animeId }

fun decodeRoles(json: String?): List<VoiceRole> =
    json?.let { runCatching { AppJson.decodeFromString<List<VoiceRole>>(it) }.getOrNull() }.orEmpty()

data class CharacterUi(val character: CharacterEntity? = null, val favorite: Boolean = false, val refresh: Refresh? = null) {
    val appearances: List<Appearance> get() = decodeAppearances(character?.appearancesJson)
}

@HiltViewModel
class CharacterViewModel @Inject constructor(
    state: SavedStateHandle,
    private val repo: AnimeRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val id: Int = checkNotNull(state.get<Int>("id"))
    private val refresh = MutableStateFlow<Refresh?>(null)
    val ui: StateFlow<CharacterUi> = combine(repo.observeCharacter(id), library.observeIsFavoriteCharacter(id), refresh) { c, f, r -> CharacterUi(c, f, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CharacterUi())

    init { load(false) }

    fun load(force: Boolean) = viewModelScope.launch { refresh.value = repo.refreshCharacter(id, force) }

    fun toggleFavorite() = viewModelScope.launch {
        val c = ui.value.character ?: return@launch
        val first = ui.value.appearances.firstOrNull()
        library.setFavoriteCharacter(c.id, c.name, c.image, first?.animeId, first?.title, !ui.value.favorite)
    }
}

data class StaffUi(val staff: StaffEntity? = null, val refresh: Refresh? = null) {
    val roles: List<VoiceRole> get() = decodeRoles(staff?.rolesJson)
}

@HiltViewModel
class StaffViewModel @Inject constructor(state: SavedStateHandle, private val repo: AnimeRepository) : ViewModel() {
    private val id: Int = checkNotNull(state.get<Int>("id"))
    private val refresh = MutableStateFlow<Refresh?>(null)
    val ui: StateFlow<StaffUi> = combine(repo.observeStaffMember(id), refresh) { s, r -> StaffUi(s, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffUi())

    init { load(false) }

    fun load(force: Boolean) = viewModelScope.launch { refresh.value = repo.refreshStaff(id, force) }
}

@Composable
private fun PersonHeader(image: String?, name: String, native: String?, extra: String?) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(image, Modifier.width(120.dp).aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)), name)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            native?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary) }
            extra?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Favorite) }
        }
    }
}

@Composable
private fun Bio(text: String?) {
    if (text.isNullOrBlank()) return
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(stringResource(R.string.about))
        Text(
            text, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), color = Palette.TextSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (text.length > 400) Text(
            stringResource(if (expanded) R.string.less else R.string.more), color = Palette.Accent,
            modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MiniCard(title: String, image: String?, sub: String?, onClick: () -> Unit) {
    Column(Modifier.width(104.dp).clickable(onClick = onClick)) {
        Poster(image, Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(10.dp)), title)
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CharacterContent(ui: CharacterUi, onBack: () -> Unit, onFavorite: () -> Unit, onOpenAnime: (Int) -> Unit, onOpenStaff: (Int) -> Unit, onRetry: () -> Unit) {
    val c = ui.character
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            AppTopBar(c?.name.orEmpty(), onBack = onBack) {
                if (c != null) IconButton(onClick = onFavorite) {
                    Icon(
                        if (ui.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        stringResource(if (ui.favorite) R.string.remove_favorite else R.string.add_favorite),
                        tint = if (ui.favorite) Palette.Favorite else Palette.Text,
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            (ui.refresh as? Refresh.Failed)?.let { StatusBanner(stringResource(if (it.offline) R.string.offline_cached else R.string.empty_feed), onRetry = onRetry) }
            when {
                c == null && ui.refresh == null -> LoadingBox()
                c == null -> EmptyState(stringResource(R.string.empty_feed))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { PersonHeader(c.image, c.name, c.nameNative, c.favourites?.let { stringResource(R.string.favourites_count, compactCount(it)) }) }
                    val va = ui.appearances.firstOrNull { it.vaId != null }
                    if (va != null) item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SectionHeader(stringResource(R.string.voice_actor))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Card).clickable { onOpenStaff(va.vaId!!) }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Poster(va.vaImage, Modifier.size(52.dp).clip(CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(va.vaName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.va_other_roles), style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
                                }
                            }
                        }
                    }
                    item { Bio(c.description) }
                    if (ui.appearances.isNotEmpty()) item {
                        Column(Modifier.padding(top = 8.dp)) {
                            SectionHeader(stringResource(R.string.appears_in), Modifier.padding(horizontal = 16.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(ui.appearances, key = { it.animeId }) { a -> MiniCard(a.title, a.cover, null) { onOpenAnime(a.animeId) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaffContent(ui: StaffUi, onBack: () -> Unit, onOpenAnime: (Int) -> Unit, onOpenCharacter: (Int) -> Unit, onRetry: () -> Unit) {
    val s = ui.staff
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(s?.name.orEmpty(), onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            (ui.refresh as? Refresh.Failed)?.let { StatusBanner(stringResource(if (it.offline) R.string.offline_cached else R.string.empty_feed), onRetry = onRetry) }
            when {
                s == null && ui.refresh == null -> LoadingBox()
                s == null -> EmptyState(stringResource(R.string.empty_feed))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { PersonHeader(s.image, s.name, s.nameNative, null) }
                    item { Bio(s.description) }
                    if (ui.roles.isNotEmpty()) item {
                        Column(Modifier.padding(top = 8.dp)) {
                            SectionHeader(stringResource(R.string.va_other_roles), Modifier.padding(horizontal = 16.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(ui.roles, key = { "${it.animeId}-${it.characterId}" }) { r ->
                                    MiniCard(r.characterName ?: r.animeTitle, r.characterImage ?: r.cover, r.animeTitle) {
                                        if (r.characterId != null) onOpenCharacter(r.characterId) else onOpenAnime(r.animeId)
                                    }
                                }
                            }
                        }
                    }
                    if (ui.roles.isNotEmpty()) item {
                        Column(Modifier.padding(top = 8.dp)) {
                            SectionHeader(stringResource(R.string.appears_in), Modifier.padding(horizontal = 16.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(ui.roles.distinctBy { it.animeId }, key = { it.animeId }) { r -> MiniCard(r.animeTitle, r.cover, null) { onOpenAnime(r.animeId) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterScreen(onBack: () -> Unit, onOpenAnime: (Int) -> Unit, onOpenStaff: (Int) -> Unit, vm: CharacterViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    CharacterContent(ui, onBack, { vm.toggleFavorite() }, onOpenAnime, onOpenStaff, { vm.load(true) })
}

@Composable
fun StaffScreen(onBack: () -> Unit, onOpenAnime: (Int) -> Unit, onOpenCharacter: (Int) -> Unit, vm: StaffViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    StaffContent(ui, onBack, onOpenAnime, onOpenCharacter) { vm.load(true) }
}
