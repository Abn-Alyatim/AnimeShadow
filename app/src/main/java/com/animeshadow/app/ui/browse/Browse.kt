package com.animeshadow.app.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.animeshadow.app.ui.common.genreLabel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.domain.BrowseSort
import com.animeshadow.app.domain.LibraryFilter
import com.animeshadow.app.ui.common.AnimeGrid
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.Refreshable
import com.animeshadow.app.ui.ads.ListAdBanner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.animeshadow.app.ui.common.SelectChip
import com.animeshadow.app.ui.common.StatusBanner
import com.animeshadow.app.ui.common.formatRes
import com.animeshadow.app.ui.common.label
import com.animeshadow.app.ui.common.seasonRes
import com.animeshadow.app.ui.common.statusRes
import com.animeshadow.app.ui.feed.GridToggle
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUi(
    val query: BrowseQuery = BrowseQuery(),
    val items: List<AnimeEntity> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null,
    val grid: Boolean = true,
    /** v1.2: pull-to-refresh running; the current results stay on screen. */
    val refreshing: Boolean = false,
    /** v1.4.0: «محتوى +18» is on (Settings) → the age filter and adult genres/tags are offered. */
    val adultAllowed: Boolean = false,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repo: AnimeRepository,
    private val settings: SettingsRepository,
    private val extra: com.animeshadow.app.data.settings.ExtraPrefs,
) : ViewModel() {
    private val _ui = MutableStateFlow(BrowseUi())
    val ui: StateFlow<BrowseUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { settings.settings.collect { s -> _ui.update { it.copy(grid = s.gridMode) } } }
        // v1.4.0: 18+ titles/genres/tags only when «محتوى +18» is on in Settings; switching it off resets the filter.
        viewModelScope.launch {
            extra.adult.collect { on ->
                val q = _ui.value.query
                val safe = if (on) q else q.copy(adult = false, genre = q.genre?.takeUnless { it in com.animeshadow.app.domain.AniTags.ADULT_GENRES }, tag = q.tag?.takeUnless { com.animeshadow.app.domain.AniTags.isAdultTag(it) })
                _ui.update { it.copy(adultAllowed = on, query = safe) }
                if (safe != q) load(reset = true)
            }
        }
        load(reset = true)
    }

    fun setSearch(text: String) {
        _ui.update { it.copy(query = it.query.copy(search = text)) }
        load(reset = true, debounceMs = 450)
    }

    fun setQuery(q: BrowseQuery) {
        _ui.update { it.copy(query = q) }
        load(reset = true)
    }

    /** Keyboard "search" key: search now (v1.1 waited for the 450 ms typing debounce again). */
    fun submit() = load(reset = true)

    /** Pull-to-refresh: the same query again from AniList, page 1. */
    fun refresh() = load(reset = true, pull = true)

    fun loadMore() {
        val s = _ui.value
        if (s.loading || !s.hasNext) return
        load(reset = false)
    }

    fun toggleGrid() = viewModelScope.launch { settings.setGridMode(!_ui.value.grid) }

    private fun load(reset: Boolean, debounceMs: Long = 0, pull: Boolean = false) {
        if (reset) job?.cancel()
        job = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val q = _ui.value.query
            val page = if (reset) 1 else _ui.value.page + 1
            _ui.update { it.copy(loading = true, error = null, refreshing = pull, items = if (reset && !pull) emptyList() else it.items) }
            repo.browse(q, page).fold(
                { r ->
                    _ui.update {
                        it.copy(
                            items = if (reset) r.items else (it.items + r.items).distinctBy { a -> a.id },
                            page = page, hasNext = r.hasNext, loading = false, fromCache = r.fromCache, refreshing = false,
                        )
                    }
                },
                { e -> _ui.update { it.copy(loading = false, error = e.message ?: "error", hasNext = false, refreshing = false) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseContent(
    ui: BrowseUi,
    autoFocus: Boolean,
    onMenu: () -> Unit,
    onBack: (() -> Unit)?,
    onSearch: (String) -> Unit,
    onQuery: (BrowseQuery) -> Unit,
    onToggleGrid: () -> Unit,
    onOpen: (AnimeEntity) -> Unit,
    onLoadMore: () -> Unit,
    onSubmit: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Palette.RefIcon) }
                    else IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.cd_menu)) }
                },
                title = {
                    // Ref 08: no box, no underline — a transparent field inline in the bar, hint at the right edge.
                    BasicTextField(
                        value = ui.query.search,
                        onValueChange = onSearch,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.RefText, textAlign = TextAlign.Right, fontSize = 17.sp),
                        cursorBrush = SolidColor(Palette.Blue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide() }),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                if (ui.query.search.isEmpty()) {
                                    Text(
                                        stringResource(R.string.search_hint), color = Color(0xFFA6A6A6), fontSize = 17.sp,
                                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search_field"),
                    )
                },
                actions = {
                    IconButton(onClick = { showSort = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.cd_sort), tint = Palette.RefIcon) }
                    IconButton(onClick = { showFilter = true }) {
                        BadgedBox(badge = { if (ui.query.activeFilterCount > 0) Badge { Text(ui.query.activeFilterCount.toString()) } }) {
                            Icon(Icons.Outlined.FilterAlt, stringResource(R.string.cd_filter), tint = Palette.RefIcon)
                        }
                    }
                    if (onBack == null) GridToggle(ui.grid, onToggleGrid)
                },
                colors = TopAppBarDefaults.topAppBarColors(navigationIconContentColor = Palette.RefIcon, actionIconContentColor = Palette.RefIcon, containerColor = Palette.Black),
            )
        },
        bottomBar = { ListAdBanner() },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ui.fromCache) StatusBanner(stringResource(R.string.search_offline))
            ui.error?.let { StatusBanner(stringResource(R.string.load_failed)) }
            Refreshable(ui.refreshing && ui.items.isNotEmpty(), onRefresh, Modifier.weight(1f)) {
                when {
                    ui.items.isEmpty() && ui.loading -> LoadingBox()
                    ui.items.isEmpty() -> EmptyState(stringResource(R.string.empty_list))
                    else -> AnimeGrid(ui.items, ui.grid, onOpen, onEndReached = onLoadMore, loadingMore = ui.loading && !ui.refreshing && ui.page > 0)
                }
            }
        }
    }
    if (showSort) SortDialog(ui.query.sort, { onQuery(ui.query.copy(sort = it)); showSort = false }, { showSort = false })
    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }, containerColor = Palette.Surface) {
            FilterSheet(ui.query, onApply = { onQuery(it); showFilter = false }, onCancel = { showFilter = false }, adultAllowed = ui.adultAllowed)
        }
    }
}

@Composable
fun SortDialog(current: BrowseSort, onPick: (BrowseSort) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        BrowseSort.POPULARITY to stringResource(R.string.sort_popularity),
        BrowseSort.SCORE to stringResource(R.string.sort_score),
        BrowseSort.TRENDING to stringResource(R.string.sort_trending),
        BrowseSort.NEWEST to stringResource(R.string.sort_date_added) + " (" + stringResource(R.string.sort_newest) + ")",
        BrowseSort.OLDEST to stringResource(R.string.sort_date_added) + " (" + stringResource(R.string.sort_oldest) + ")",
        BrowseSort.NAME to stringResource(R.string.sort_name),
        BrowseSort.YEAR_DESC to stringResource(R.string.sort_year) + " (" + stringResource(R.string.descending) + ")",
        BrowseSort.YEAR_ASC to stringResource(R.string.sort_year) + " (" + stringResource(R.string.ascending) + ")",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        title = { Text(stringResource(R.string.sort_title)) },
        text = {
            Column {
                options.forEach { (sort, text) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sort == current, onClick = { onPick(sort) })
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(initial: BrowseQuery, onApply: (BrowseQuery) -> Unit, onCancel: () -> Unit, adultAllowed: Boolean = false) {
    var q by remember { mutableStateOf(initial) }
    val thisYear = java.time.LocalDate.now().year
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
        Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)
        FilterGroup(stringResource(R.string.filter_status)) {
            LibraryFilter.AIRING.forEach { s -> SelectChip(label(statusRes(s), s), q.status == s, { q = q.copy(status = if (q.status == s) null else s) }) }
        }
        FilterGroup(stringResource(R.string.filter_type)) {
            LibraryFilter.FORMATS.forEach { f -> SelectChip(label(formatRes(f), f), q.format == f, { q = q.copy(format = if (q.format == f) null else f) }) }
        }
        FilterGroup(stringResource(R.string.filter_season)) {
            BrowseQuery.SEASONS.forEach { s -> SelectChip(label(seasonRes(s), s), q.season == s, { q = q.copy(season = if (q.season == s) null else s) }) }
        }
        FilterGroup(stringResource(R.string.filter_year)) {
            (thisYear + 1 downTo thisYear - 14).forEach { y -> SelectChip(y.toString(), q.year == y, { q = q.copy(year = if (q.year == y) null else y) }) }
        }
        FilterGroup(stringResource(R.string.filter_genre)) {
            com.animeshadow.app.domain.AniTags.genres(adultAllowed).forEach { g -> SelectChip(genreLabel(g), q.genre == g, { q = q.copy(genre = if (q.genre == g) null else g) }, Modifier.testTag("fgenre_$g")) }
        }
        // v1.4.0: AniList tags (the most used ones here; every tag is in «التصنيفات» from the menu).
        FilterGroup("وسوم") {
            val shown = (com.animeshadow.app.domain.AniTags.POPULAR + listOfNotNull(q.tag)).distinct()
                .filter { adultAllowed || !com.animeshadow.app.domain.AniTags.isAdultTag(it) }
            shown.forEach { t -> SelectChip(com.animeshadow.app.domain.AniTags.ar(t), q.tag == t, { q = q.copy(tag = if (q.tag == t) null else t) }, Modifier.testTag("ftag_$t")) }
        }
        if (adultAllowed) FilterGroup(stringResource(R.string.filter_age)) {
            listOf(false to R.string.age_safe, null to R.string.age_any, true to R.string.age_adult).forEach { (v, res) ->
                SelectChip(stringResource(res), q.adult == v, { q = q.copy(adult = v) }, Modifier.testTag("age_$v"))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onApply(q) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.apply)) }
            OutlinedButton(onClick = { q = BrowseQuery(search = q.search, sort = q.sort) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.reset)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun BrowseScreen(autoFocus: Boolean, onMenu: () -> Unit, onBack: (() -> Unit)?, onOpen: (Int) -> Unit, vm: BrowseViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    BrowseContent(ui, autoFocus, onMenu, onBack, vm::setSearch, vm::setQuery, { vm.toggleGrid() }, { onOpen(it.id) }, vm::loadMore, vm::submit, vm::refresh)
}
