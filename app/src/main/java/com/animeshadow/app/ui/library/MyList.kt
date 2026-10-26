package com.animeshadow.app.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.LibraryItem
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.domain.LibraryFilter
import com.animeshadow.app.domain.LibrarySort
import com.animeshadow.app.ui.browse.FilterGroup
import com.animeshadow.app.ui.common.AnimeGrid
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.SelectChip
import com.animeshadow.app.ui.common.formatRes
import com.animeshadow.app.ui.common.label
import com.animeshadow.app.ui.common.statusRes
import com.animeshadow.app.ui.common.watchStatusRes
import com.animeshadow.app.ui.feed.GridToggle
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyListUi(
    val status: WatchStatus = WatchStatus.WATCHING,
    val filter: LibraryFilter = LibraryFilter(),
    val items: List<LibraryItem> = emptyList(),
    val counts: Map<WatchStatus, Int> = emptyMap(),
    val grid: Boolean = true,
    val availableGenres: List<String> = emptyList(),
    val availableYears: List<Int> = emptyList(),
)

@HiltViewModel
class MyListViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val status = MutableStateFlow(WatchStatus.WATCHING)
    private val filter = MutableStateFlow(LibraryFilter())

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            if (s.saveFilters) filter.value = s.savedFilter
        }
    }

    val ui: StateFlow<MyListUi> = combine(library.observeLibrary(), status, filter, settings.settings) { all, st, f, s ->
        MyListUi(
            status = st,
            filter = f,
            items = f.apply(all, st),
            counts = all.groupingBy { it.entry.status }.eachCount(),
            grid = s.gridMode,
            availableGenres = all.flatMap { it.anime?.genreList.orEmpty() }.distinct().sorted(),
            availableYears = all.mapNotNull { it.anime?.seasonYear }.distinct().sortedDescending(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListUi())

    fun selectStatus(s: WatchStatus) { status.value = s }

    fun setFilter(f: LibraryFilter) {
        filter.value = f
        viewModelScope.launch { if (settings.current().saveFilters) settings.setSavedFilter(f) }
    }

    fun toggleGrid() = viewModelScope.launch { settings.setGridMode(!ui.value.grid) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyListContent(
    ui: MyListUi,
    onMenu: () -> Unit,
    onStatus: (WatchStatus) -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onToggleGrid: () -> Unit,
    onOpen: (AnimeEntity) -> Unit,
) {
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            AppTopBar(stringResource(R.string.nav_mylist), onMenu = onMenu) {
                IconButton(onClick = { showSort = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.cd_sort)) }
                IconButton(onClick = { showFilter = true }) {
                    val n = listOfNotNull(ui.filter.year, ui.filter.minScore).size + ui.filter.genres.size
                    BadgedBox(badge = { if (n > 0) Badge { Text(n.toString()) } }) { Icon(Icons.Filled.FilterList, stringResource(R.string.cd_filter)) }
                }
                GridToggle(ui.grid, onToggleGrid)
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val tabs = WatchStatus.entries
            val selected = tabs.indexOf(ui.status)
            ScrollableTabRow(
                selectedTabIndex = selected,
                containerColor = Palette.Black,
                edgePadding = 8.dp,
                indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[selected]), color = Palette.Accent) },
                divider = {},
            ) {
                tabs.forEach { s ->
                    Tab(
                        selected = s == ui.status,
                        onClick = { onStatus(s) },
                        selectedContentColor = Palette.Text,
                        unselectedContentColor = Palette.TextMuted,
                        text = {
                            val c = ui.counts[s] ?: 0
                            Text(stringResource(watchStatusRes(s)) + if (c > 0) " ($c)" else "", maxLines = 1)
                        },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryFilter.FORMATS.forEach { f ->
                    SelectChip(label(formatRes(f), f), f in ui.filter.formats, {
                        onFilter(ui.filter.copy(formats = if (f in ui.filter.formats) ui.filter.formats - f else ui.filter.formats + f))
                    })
                }
                VerticalDivider(Modifier.height(22.dp).padding(horizontal = 2.dp), color = Palette.Divider)
                listOf("RELEASING", "FINISHED").forEach { s ->
                    SelectChip(label(statusRes(s), s), s in ui.filter.airing, {
                        onFilter(ui.filter.copy(airing = if (s in ui.filter.airing) ui.filter.airing - s else ui.filter.airing + s))
                    })
                }
            }
            if (ui.items.isEmpty()) {
                EmptyState(stringResource(R.string.empty_list))
            } else {
                AnimeGrid(
                    items = ui.items.mapNotNull { it.anime },
                    grid = ui.grid,
                    onClick = onOpen,
                    bottomBadge = { null },
                    cornerBadge = { a -> a.seasonYear?.toString() },
                    listAccent = { a ->
                        val e = ui.items.firstOrNull { it.entry.animeId == a.id }?.entry
                        e?.let { stringResource(R.string.progress_value, it.progress, a.episodes?.toString() ?: "?") }
                    },
                )
            }
        }
    }
    if (showSort) LibrarySortDialog(ui.filter, { onFilter(it); showSort = false }, { showSort = false })
    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }, containerColor = Palette.Surface) {
            var f by remember { mutableStateOf(ui.filter) }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)
                FilterGroup(stringResource(R.string.filter_year)) {
                    ui.availableYears.forEach { y -> SelectChip(y.toString(), f.year == y, { f = f.copy(year = if (f.year == y) null else y) }) }
                }
                FilterGroup(stringResource(R.string.filter_min_score)) {
                    listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0).forEach { m ->
                        SelectChip("★ $m+", f.minScore == m, { f = f.copy(minScore = if (f.minScore == m) null else m) })
                    }
                }
                FilterGroup(stringResource(R.string.filter_genre)) {
                    (ui.availableGenres.ifEmpty { BrowseQuery.GENRES }).forEach { g ->
                        SelectChip(g, g in f.genres, { f = f.copy(genres = if (g in f.genres) f.genres - g else f.genres + g) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onFilter(f); showFilter = false }, Modifier.weight(1f)) { Text(stringResource(R.string.apply)) }
                    OutlinedButton(onClick = { f = LibraryFilter(sort = f.sort, ascending = f.ascending) }, Modifier.weight(1f)) { Text(stringResource(R.string.reset)) }
                    TextButton(onClick = { showFilter = false }) { Text(stringResource(R.string.cancel)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun LibrarySortDialog(filter: LibraryFilter, onPick: (LibraryFilter) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Triple(LibrarySort.DATE_ADDED, false, stringResource(R.string.sort_date_added) + " (" + stringResource(R.string.sort_newest) + ")"),
        Triple(LibrarySort.DATE_ADDED, true, stringResource(R.string.sort_date_added) + " (" + stringResource(R.string.sort_oldest) + ")"),
        Triple(LibrarySort.NAME, true, stringResource(R.string.sort_name)),
        Triple(LibrarySort.YEAR, false, stringResource(R.string.sort_year) + " (" + stringResource(R.string.descending) + ")"),
        Triple(LibrarySort.YEAR, true, stringResource(R.string.sort_year) + " (" + stringResource(R.string.ascending) + ")"),
        Triple(LibrarySort.SCORE, false, stringResource(R.string.sort_score)),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        title = { Text(stringResource(R.string.sort_title)) },
        text = {
            Column {
                options.forEach { (sort, asc, text) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = filter.sort == sort && filter.ascending == asc, onClick = { onPick(filter.copy(sort = sort, ascending = asc)) })
                        Spacer(Modifier.width(4.dp))
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun MyListScreen(onMenu: () -> Unit, onOpen: (Int) -> Unit, vm: MyListViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    MyListContent(ui, onMenu, vm::selectStatus, vm::setFilter, { vm.toggleGrid() }, { onOpen(it.id) })
}
