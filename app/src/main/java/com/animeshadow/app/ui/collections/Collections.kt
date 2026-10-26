package com.animeshadow.app.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.Refresh
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.CharacterEntity
import com.animeshadow.app.data.local.FavoriteCharacterEntity
import com.animeshadow.app.data.local.HistoryRow
import com.animeshadow.app.ui.common.AnimeGrid
import com.animeshadow.app.ui.common.AnimeListRow
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.Poster
import com.animeshadow.app.ui.common.StatusBanner
import com.animeshadow.app.ui.common.compactCount
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** Character card: rounded image with the name over a dark gradient at the bottom (as in the reference UI). */
@Composable
fun CharacterCard(name: String, image: String?, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Poster(image, Modifier.fillMaxSize(), name)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000))))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    library: LibraryRepository,
    private val anime: AnimeRepository,
) : ViewModel() {
    val favoriteAnime: StateFlow<List<AnimeEntity>?> = library.observeFavoriteAnime().map { rows -> rows.map { it.anime } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val favoriteCharacters: StateFlow<List<FavoriteCharacterEntity>?> = library.observeFavoriteCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history: StateFlow<List<HistoryRow>?> = library.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val popularState = MutableStateFlow<Refresh?>(null)
    val popular: StateFlow<Pair<List<CharacterEntity>, Refresh?>> = combine(anime.observePopularCharacters(), popularState) { a, b -> a to b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<CharacterEntity>() to null)

    fun loadPopular(force: Boolean = false) = viewModelScope.launch { popularState.value = anime.refreshPopularCharacters(force) }
}

@Composable
fun FavoriteAnimeContent(items: List<AnimeEntity>?, onMenu: () -> Unit, onOpen: (AnimeEntity) -> Unit) {
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_fav_anime), onMenu = onMenu) }) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                items == null -> LoadingBox()
                items.isEmpty() -> EmptyState(stringResource(R.string.empty_favorites))
                else -> AnimeGrid(items, grid = true, onClick = onOpen, cornerBadge = { it.seasonYear?.toString() })
            }
        }
    }
}

@Composable
fun FavoriteCharactersContent(items: List<FavoriteCharacterEntity>?, onMenu: () -> Unit, onOpen: (Int) -> Unit) {
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_fav_characters), onMenu = onMenu) }) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                items == null -> LoadingBox()
                items.isEmpty() -> EmptyState(stringResource(R.string.empty_favorites))
                else -> LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.characterId }) { c -> CharacterCard(c.name, c.image, c.animeTitle, { onOpen(c.characterId) }) }
                }
            }
        }
    }
}

@Composable
fun PopularCharactersContent(items: List<CharacterEntity>, state: Refresh?, onMenu: () -> Unit, onRetry: () -> Unit, onOpen: (Int) -> Unit) {
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_popular_characters), onMenu = onMenu) }) { pad ->
        Column(Modifier.padding(pad)) {
            (state as? Refresh.Failed)?.let { StatusBanner(stringResource(if (it.offline) R.string.offline_cached else R.string.empty_feed), onRetry = onRetry) }
            when {
                items.isEmpty() && state == null -> LoadingBox()
                items.isEmpty() -> EmptyState(stringResource(R.string.empty_feed))
                else -> LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.id }) { c ->
                        CharacterCard(c.name, c.image, c.favourites?.let { stringResource(R.string.favourites_count, compactCount(it)) }, { onOpen(c.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryContent(items: List<HistoryRow>?, onMenu: () -> Unit, onOpen: (Int) -> Unit) {
    val fmt = com.animeshadow.app.ui.common.latinDateTimeFormat()
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_history), onMenu = onMenu) }) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                items == null -> LoadingBox()
                items.isEmpty() -> EmptyState(stringResource(R.string.empty_history))
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    items(items, key = { it.anime.id }) { row ->
                        AnimeListRow(
                            row.anime, { onOpen(row.anime.id) },
                            accentLine = stringResource(R.string.last_watched_episode, row.lastEpisode) + "  •  " + fmt.format(Date(row.lastWatchedAt)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteAnimeScreen(onMenu: () -> Unit, onOpen: (Int) -> Unit, vm: CollectionsViewModel = hiltViewModel()) {
    val items by vm.favoriteAnime.collectAsStateWithLifecycle()
    FavoriteAnimeContent(items, onMenu) { onOpen(it.id) }
}

@Composable
fun FavoriteCharactersScreen(onMenu: () -> Unit, onOpen: (Int) -> Unit, vm: CollectionsViewModel = hiltViewModel()) {
    val items by vm.favoriteCharacters.collectAsStateWithLifecycle()
    FavoriteCharactersContent(items, onMenu, onOpen)
}

@Composable
fun HistoryScreen(onMenu: () -> Unit, onOpen: (Int) -> Unit, vm: CollectionsViewModel = hiltViewModel()) {
    val items by vm.history.collectAsStateWithLifecycle()
    HistoryContent(items, onMenu, onOpen)
}

@Composable
fun PopularCharactersScreen(onMenu: () -> Unit, onOpen: (Int) -> Unit, vm: CollectionsViewModel = hiltViewModel()) {
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadPopular() }
    val state by vm.popular.collectAsStateWithLifecycle()
    PopularCharactersContent(state.first, state.second, onMenu, { vm.loadPopular(true) }, onOpen)
}
