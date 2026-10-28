package com.animeshadow.app.manga.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animeshadow.app.R
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.manga.data.DownloadState
import com.animeshadow.app.manga.data.DownloadedTitle
import com.animeshadow.app.manga.data.HistoryRow
import com.animeshadow.app.manga.data.MangaEntity
import com.animeshadow.app.manga.data.MangaFeed
import com.animeshadow.app.manga.data.MangaKind
import com.animeshadow.app.manga.data.ReadStatus
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.Poster
import com.animeshadow.app.ui.common.Refreshable
import com.animeshadow.app.ui.common.ScoreLabel
import com.animeshadow.app.ui.common.SelectChip
import com.animeshadow.app.ui.common.StatusBanner
import com.animeshadow.app.ui.common.genreLabel
import com.animeshadow.app.ui.common.label
import com.animeshadow.app.ui.common.latinDateTimeFormat
import com.animeshadow.app.ui.common.scoreText
import com.animeshadow.app.ui.common.statusRes
import com.animeshadow.app.ui.feed.GridToggle
import com.animeshadow.app.ui.theme.Palette
import com.animeshadow.app.ui.theme.RefType
import kotlinx.coroutines.launch

// ================================================================== shared pieces

@Composable
fun kindLabel(kind: MangaKind): String = stringResource(
    when (kind) { MangaKind.MANGA -> R.string.kind_manga; MangaKind.MANHWA -> R.string.kind_manhwa; MangaKind.MANHUA -> R.string.kind_manhua },
)

@Composable
fun readStatusLabel(s: ReadStatus): String = stringResource(
    when (s) {
        ReadStatus.READING -> R.string.read_reading
        ReadStatus.PLAN_TO_READ -> R.string.read_plan
        ReadStatus.COMPLETED -> R.string.read_completed
        ReadStatus.ON_HOLD -> R.string.read_on_hold
        ReadStatus.DROPPED -> R.string.read_dropped
    },
)

/** «الكل | مانجا | مانهوا | مانها» — the main name stays «مانجا»; this only filters by origin. */
@Composable
fun KindChips(kind: MangaKind?, onKind: (MangaKind?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp).testTag("kind_chips"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelectChip(stringResource(R.string.filter_all), kind == null, { onKind(null) })
        MangaKind.entries.forEach { k -> SelectChip(kindLabel(k), kind == k, { onKind(k) }, Modifier.testTag("kind_" + k.name.lowercase())) }
    }
}

/** Ref «المانجات المحملة»: poster, then a dark card with the bold title and «★ 9.0 مستمرة». */
@Composable
fun MangaCard(m: MangaEntity, onClick: () -> Unit, modifier: Modifier = Modifier, corner: String? = null) {
    Column(modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Card).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Poster(m.poster, Modifier.fillMaxSize(), m.displayTitle, m.color)
            if (corner != null) {
                Text(
                    corner, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Palette.AccentDeep).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            m.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFEDEDED),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = RefType.Body, fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
            modifier = Modifier.fillMaxWidth().padding(start = 7.dp, end = 6.dp, top = 6.dp),
        )
        ScoreLabel(m.score, Modifier.padding(start = 7.dp, top = 2.dp, bottom = 7.dp), prefix = label(statusRes(m.status), m.status).ifBlank { null })
    }
}

@Composable
fun MangaListRow(m: MangaEntity, onClick: () -> Unit, line: String? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(m.poster, Modifier.width(64.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)), m.displayTitle, m.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.displayTitle, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = Palette.Accent, maxLines = 1)
            ScoreLabel(m.score, prefix = kindLabel(m.kind))
        }
    }
}

@Composable
fun MangaGrid(
    items: List<MangaEntity>,
    grid: Boolean,
    onClick: (MangaEntity) -> Unit,
    modifier: Modifier = Modifier,
    onEndReached: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    corner: @Composable (MangaEntity) -> String? = { null },
    line: @Composable (MangaEntity) -> String? = { null },
) {
    val state = rememberLazyGridState()
    if (onEndReached != null) {
        val shouldLoad by remember(state, items.size) {
            derivedStateOf { items.isNotEmpty() && (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 7 }
        }
        LaunchedEffect(shouldLoad, items.size) { if (shouldLoad) onEndReached() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 3 else 1), state = state, modifier = modifier.fillMaxSize().testTag("manga_grid"),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(if (grid) 10.dp else 2.dp),
    ) {
        items(items, key = { it.id }) { m -> if (grid) MangaCard(m, { onClick(m) }, corner = corner(m)) else MangaListRow(m, { onClick(m) }, line(m)) }
        if (loadingMore) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Palette.Accent) }
        }
    }
}

/** Swipeable tabs: tap a tab or swipe the pages (v1.3 item 2). [equal] = full-width halves like the manga refs. */
@Composable
fun SwipeTabs(titles: List<String>, pager: PagerState, equal: Boolean = false) {
    val scope = rememberCoroutineScope()
    val selected = pager.currentPage
    Row(Modifier.fillMaxWidth().then(if (equal) Modifier else Modifier.horizontalScroll(rememberScrollState())).testTag("swipe_tabs")) {
        titles.forEachIndexed { i, t ->
            val on = i == selected
            Column(
                Modifier.then(if (equal) Modifier.weight(1f) else Modifier.width(IntrinsicSize.Max))
                    .clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 12.dp).testTag("mtab_$i"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    t, maxLines = 1, color = if (on) Color.White else Color(0xFFACADB0),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = RefType.Body, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    modifier = Modifier.padding(top = 6.dp, bottom = 9.dp),
                )
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (on) Color.White else Color.Transparent))
            }
        }
    }
}

// ================================================================== home

@Composable
fun MangaHomeContent(
    ui: MangaHomeUi,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onKind: (MangaKind?) -> Unit,
    onToggleGrid: () -> Unit,
    onOpen: (MangaEntity) -> Unit,
    onGenre: (String) -> Unit,
    onLoadMore: (MangaFeed) -> Unit,
    onRefresh: () -> Unit,
    toggle: (@Composable () -> Unit)? = null,
    initialPage: Int = 0,
) {
    val pager = rememberPagerState(initialPage = initialPage) { 4 }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            AppTopBar(stringResource(R.string.manga_home), onMenu = onMenu) {
                toggle?.invoke()
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.cd_search), tint = Palette.RefIcon) }
                GridToggle(ui.grid, onToggleGrid)
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            KindChips(ui.kind, onKind)
            SwipeTabs(listOf(stringResource(R.string.manga_trending), stringResource(R.string.manga_popular), stringResource(R.string.nav_top), stringResource(R.string.manga_genres)), pager)
            if (ui.failed && ui.feeds.values.all { it.isEmpty() }) StatusBanner(stringResource(if (ui.offline) R.string.offline_cached else R.string.load_failed))
            HorizontalPager(pager, Modifier.fillMaxSize().testTag("manga_home_pager"), key = { it }) { page ->
                if (page == 3) {
                    GenreList(onGenre)
                } else {
                    val feed = MangaFeed.entries[page]
                    val items = ui.feeds[feed].orEmpty()
                    Refreshable(feed in ui.loading && items.isNotEmpty(), onRefresh, Modifier.fillMaxSize()) {
                        when {
                            items.isEmpty() && feed in ui.loading -> LoadingBox()
                            items.isEmpty() -> EmptyState(stringResource(R.string.empty_feed))
                            else -> MangaGrid(items, ui.grid, onOpen, onEndReached = { onLoadMore(feed) }, corner = { m -> if (ui.kind == null && m.kind != MangaKind.MANGA) kindLabel(m.kind) else null })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreList(onGenre: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BrowseQuery.GENRES.filter { it != "Ecchi" && it != "Hentai" }.forEach { g ->
                Text(
                    genreLabel(g), style = MaterialTheme.typography.bodyMedium, color = Palette.Text,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Chip).clickable { onGenre(g) }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("genre_$g"),
                )
            }
        }
    }
}

@Composable
fun MangaHomeScreen(onMenu: () -> Unit, onSearch: () -> Unit, onOpen: (Int) -> Unit, onGenre: (String) -> Unit, toggle: @Composable () -> Unit, vm: MangaHomeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    MangaHomeContent(ui, onMenu, onSearch, vm::setKind, { vm.toggleGrid() }, { onOpen(it.id) }, onGenre, vm::loadMore, { vm.refreshAll() }, toggle)
}

// ================================================================== search / genre list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaSearchContent(ui: MangaSearchUi, onBack: (() -> Unit)?, onMenu: () -> Unit, onQuery: (String) -> Unit, onKind: (MangaKind?) -> Unit, onToggleGrid: () -> Unit, onOpen: (MangaEntity) -> Unit, onMore: () -> Unit) {
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            if (ui.genre != null) {
                AppTopBar(genreLabel(ui.genre), onBack = onBack) { GridToggle(ui.grid, onToggleGrid) }
            } else {
                TopAppBar(
                    title = {
                        TextField(
                            ui.query, onQuery, singleLine = true, placeholder = { Text(stringResource(R.string.manga_search_hint), color = Palette.TextMuted) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Palette.Accent,
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("manga_search_field"),
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Palette.RefIcon) }
                        else IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.cd_menu), tint = Palette.RefIcon) }
                    },
                    actions = { GridToggle(ui.grid, onToggleGrid) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            KindChips(ui.kind, onKind)
            if (ui.failed && ui.items.isEmpty()) StatusBanner(stringResource(R.string.load_failed))
            when {
                ui.items.isEmpty() && ui.loading -> LoadingBox()
                ui.items.isEmpty() -> EmptyState(stringResource(R.string.empty_feed))
                else -> MangaGrid(ui.items, ui.grid, onOpen, onEndReached = onMore, loadingMore = ui.loading)
            }
        }
    }
}

@Composable
fun MangaSearchScreen(onBack: (() -> Unit)?, onMenu: () -> Unit, onOpen: (Int) -> Unit, vm: MangaSearchViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    MangaSearchContent(ui, onBack, onMenu, vm::setQuery, vm::setKind, { vm.toggleGrid() }, { onOpen(it.id) }, vm::loadMore)
}

// ================================================================== title page: «التفاصيل» | «الفصول»

class MangaTitleActions(
    val onBack: () -> Unit = {},
    val onFavorite: () -> Unit = {},
    val onStatus: (ReadStatus?) -> Unit = {},
    val onUserScore: (Int?) -> Unit = {},
    val onGenre: (String) -> Unit = {},
    val onSort: () -> Unit = {},
    val onRead: (ChapterRow) -> Unit = {},
    val onToggleRead: (ChapterRow) -> Unit = {},
    val onDownload: (ChapterRow.Remote) -> Unit = {},
    val onDownloadAll: () -> Unit = {},
    val onDelete: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MangaTitleContent(ui: MangaTitleUi, actions: MangaTitleActions, initialTab: Int = 0) {
    val pager = rememberPagerState(initialPage = initialTab) { 2 }
    val tab = pager.currentPage
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val soon = stringResource(R.string.coming_soon)
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Palette.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(ui.manga?.displayTitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, color = Color(0xFFF5F5F5), modifier = Modifier.padding(start = 12.dp)) },
                navigationIcon = { IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Palette.RefIcon) } },
                actions = {
                    if (tab == 1) {
                        IconButton(onClick = actions.onSort, modifier = Modifier.testTag("ch_sort")) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.cd_sort), tint = Palette.RefIcon) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more), tint = Palette.RefIcon) }
                            DropdownMenu(menu, { menu = false }, containerColor = Palette.Card) {
                                if (!ui.downloadedOnly && ui.providerAvailable) DropdownMenuItem(text = { Text(stringResource(R.string.manga_download_all)) }, onClick = { menu = false; actions.onDownloadAll() })
                                DropdownMenuItem(text = { Text(stringResource(if (ui.newestFirst) R.string.sort_oldest_first else R.string.sort_newest_first)) }, onClick = { menu = false; actions.onSort() })
                            }
                        }
                    } else {
                        IconButton(onClick = { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(soon) } }) { Icon(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.cd_comments), tint = Palette.RefIcon) }
                        IconButton(onClick = actions.onFavorite, modifier = Modifier.testTag("m_fav")) {
                            val fav = ui.entry?.favorite == true
                            Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, stringResource(R.string.cd_favorite), tint = if (fav) Palette.Favorite else Palette.RefIcon)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
        floatingActionButton = {
            val c = ui.continueRow
            if (tab == 1 && c != null && (ui.downloadedOnly || ui.providerAvailable)) {
                Row(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(Palette.Accent).clickable { actions.onRead(c) }.padding(horizontal = 18.dp, vertical = 12.dp).testTag("continue_fab"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.manga_continue, c.number), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SwipeTabs(listOf(stringResource(R.string.tab_details), stringResource(R.string.manga_tab_chapters)), pager, equal = true)
            if (ui.failed && ui.manga?.detailLoaded != true) StatusBanner(stringResource(R.string.offline_cached))
            val m = ui.manga
            if (m == null) {
                if (ui.failed) EmptyState(stringResource(R.string.empty_feed)) else LoadingBox()
                return@Column
            }
            HorizontalPager(pager, Modifier.fillMaxSize().testTag("manga_title_pager"), key = { it }) { page ->
                if (page == 0) MangaDetailsTab(ui, m, actions) else ChaptersTab(ui, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaDetailsTab(ui: MangaTitleUi, m: MangaEntity, actions: MangaTitleActions) {
    var statusMenu by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("manga_details")) {
        // Ref: poster on the LEFT over a darkened cover, title and status beside it.
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Poster(m.banner ?: m.poster, Modifier.fillMaxSize(), placeholderColor = m.color)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color(0xEE000000), Color.Black))))
            Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Poster(m.poster, Modifier.width(118.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)), m.displayTitle, m.color)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.displayTitle, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, textDirection = TextDirection.Content), fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(label(statusRes(m.status), m.status), style = MaterialTheme.typography.bodyMedium, color = Palette.RefText)
                    Text(kindLabel(m.kind) + (m.chapters?.let { " • " + stringResource(R.string.manga_chapters_count, it) } ?: ""), style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                }
            }
        }
        // Ref row of three: ★ score/10 | ☆ «إضافة تقييم» | + «قائمتي».
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = Color(0xFFEC9A33), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("\u2066" + (scoreText(m.score) ?: "-") + "/10\u2069", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            Row(Modifier.weight(1f).clickable { rate = true }.testTag("m_rate"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val us = ui.entry?.userScore
                Icon(if (us != null) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = if (us != null) Palette.Accent else Palette.RefIcon, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (us != null) "\u2066$us/10\u2069" else stringResource(R.string.manga_add_rating), color = Palette.RefText, style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(Modifier.clickable { statusMenu = true }.testTag("m_status"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = Palette.RefIcon, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ui.entry?.status?.let { readStatusLabel(it) } ?: stringResource(R.string.my_list_short), color = if (ui.entry?.status != null) Palette.Accent else Palette.RefText, style = MaterialTheme.typography.bodyMedium)
                }
                DropdownMenu(statusMenu, { statusMenu = false }, containerColor = Palette.Card) {
                    ReadStatus.entries.forEach { s -> DropdownMenuItem(text = { Text(readStatusLabel(s), color = if (ui.entry?.status == s) Palette.Accent else Palette.Text) }, onClick = { statusMenu = false; actions.onStatus(s) }) }
                    if (ui.entry?.status != null) DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_list), color = Palette.Dropped) }, onClick = { statusMenu = false; actions.onStatus(null) })
                }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.padding(horizontal = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Surface).padding(14.dp)) {
                val text = m.descriptionAr ?: m.description
                if (!text.isNullOrBlank()) {
                    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = RefType.Body, lineHeight = 22.sp, textDirection = TextDirection.Content), color = Palette.RefText)
                    if (m.descriptionAr != null) Text(stringResource(R.string.machine_translation), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                } else if (ui.translating) {
                    Text(stringResource(R.string.translating), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                if (m.genreList.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        m.genreList.forEach { g ->
                            Text(genreLabel(g), style = MaterialTheme.typography.bodySmall.copy(fontSize = RefType.Chip), color = Palette.Text,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Chip).clickable { actions.onGenre(g) }.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                InfoPair(stringResource(R.string.info_source), label(com.animeshadow.app.ui.common.sourceRes(m.source), m.source).ifBlank { stringResource(R.string.unknown) }, stringResource(R.string.manga_origin), kindLabel(m.kind))
                InfoPair(stringResource(R.string.info_aired_from), m.startDate ?: "?", stringResource(R.string.info_aired_to), m.endDate ?: "?")
                InfoPair(stringResource(R.string.manga_chapters), m.chapters?.toString() ?: "?", stringResource(R.string.manga_volumes), m.volumes?.toString() ?: "?")
            }
        }
        Spacer(Modifier.height(80.dp))
    }
    if (rate) RatingDialog(ui.entry?.userScore, { actions.onUserScore(it); rate = false }, { rate = false })
}

@Composable
private fun InfoPair(k1: String, v1: String, k2: String, v2: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.weight(1f)) {
            Text(k1, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            Text("\u2068$v1\u2069", style = MaterialTheme.typography.bodyMedium, color = Palette.RefText)
        }
        Column(Modifier.weight(1f)) {
            Text(k2, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            Text("\u2068$v2\u2069", style = MaterialTheme.typography.bodyMedium, color = Palette.RefText)
        }
    }
}

@Composable
private fun RatingDialog(initial: Int?, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableFloatStateOf((initial ?: 7).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Palette.Card,
        title = { Text(stringResource(R.string.manga_add_rating)) },
        text = {
            Column {
                Text("\u2066${v.toInt()}/10\u2069", style = MaterialTheme.typography.headlineSmall, color = Palette.Accent, modifier = Modifier.align(Alignment.CenterHorizontally))
                Slider(v, { v = it }, valueRange = 0f..10f, steps = 9, colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v.toInt()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.delete), color = Palette.Dropped) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Ref «الفصول»: «الفصل-N» on the left; eye (read) and trash (delete) on the right. */
@Composable
private fun ChaptersTab(ui: MangaTitleUi, actions: MangaTitleActions) {
    when {
        !ui.downloadedOnly && !ui.providerAvailable -> {
            // No legal chapter source yet: «قريباً», never an error and never a retry button.
            Column(Modifier.fillMaxSize().padding(32.dp).testTag("chapters_soon"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.headlineSmall, color = Palette.Accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.manga_chapters_soon), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, textAlign = TextAlign.Center)
            }
        }
        ui.chapters.isEmpty() && ui.chaptersLoading -> LoadingBox()
        ui.chapters.isEmpty() -> EmptyState(stringResource(if (ui.downloadedOnly) R.string.manga_no_downloaded_chapters else R.string.manga_no_chapters))
        else -> LazyColumn(Modifier.fillMaxSize().testTag("chapter_list"), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(ui.chapters, key = { it.key }) { row -> ChapterItem(row, actions) }
        }
    }
}

@Composable
private fun ChapterItem(row: ChapterRow, actions: MangaTitleActions) {
    val read = when (row) { is ChapterRow.Local -> row.read; is ChapterRow.Remote -> row.read }
    Row(Modifier.fillMaxWidth().clickable { actions.onRead(row) }.padding(start = 16.dp, end = 4.dp).height(56.dp).testTag("chapter_${row.number}"), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.manga_chapter_dash, row.number), style = MaterialTheme.typography.bodyLarge, color = if (read) Palette.TextMuted else Palette.Text, modifier = Modifier.weight(1f))
        when (row) {
            is ChapterRow.Remote -> {
                val d = row.download
                when {
                    d == null -> IconButton(onClick = { actions.onDownload(row) }) { Icon(Icons.Filled.Download, stringResource(R.string.cd_download), tint = Palette.RefIcon) }
                    d.state == DownloadState.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = Palette.Completed, modifier = Modifier.padding(12.dp).size(22.dp))
                    else -> Text("\u2066${d.donePages}/${d.totalPages}\u2069", style = MaterialTheme.typography.bodySmall, color = Palette.Accent, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
            is ChapterRow.Local -> Unit
        }
        IconButton(onClick = { actions.onToggleRead(row) }, modifier = Modifier.testTag("eye_${row.number}")) {
            Icon(if (read) Icons.Filled.Visibility else Icons.Outlined.Visibility, stringResource(R.string.cd_mark_read), tint = if (read) Palette.Accent else Palette.RefIcon)
        }
        val canDelete = row is ChapterRow.Local || (row as ChapterRow.Remote).download != null
        if (canDelete) IconButton(onClick = { actions.onDelete(row.key) }, modifier = Modifier.testTag("trash_${row.number}")) {
            Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Palette.RefIcon)
        }
    }
}

@Composable
fun MangaTitleScreen(onBack: () -> Unit, onGenre: (String) -> Unit, onRead: (Int, String) -> Unit, vm: MangaTitleViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val start = rememberSaveable { if (vm.ui.value.downloadedOnly) 1 else 0 }
    val snackbar = remember { SnackbarHostState() }
    MangaTitleContent(
        ui,
        MangaTitleActions(
            onBack = onBack, onFavorite = { vm.toggleFavorite() }, onStatus = { vm.setStatus(it) }, onUserScore = { vm.setUserScore(it) },
            onGenre = onGenre, onSort = vm::toggleSort,
            onRead = { row ->
                val ok = row is ChapterRow.Local || ui.providerAvailable
                if (ok) onRead(vm.id, row.key)
            },
            onToggleRead = { vm.toggleRead(it) }, onDownload = { vm.download(it) }, onDownloadAll = { vm.downloadAll() }, onDelete = { vm.delete(it) },
        ),
        initialTab = start,
    )
    @Suppress("UNUSED_EXPRESSION") snackbar
}

// ================================================================== library / favorites / history (manga only)

@Composable
fun MangaLibraryContent(ui: MangaLibraryUi, onMenu: () -> Unit, onToggleGrid: () -> Unit, onOpen: (MangaEntity) -> Unit) {
    val tabs = ReadStatus.entries
    val pager = rememberPagerState { tabs.size }
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_mylist), onMenu = onMenu) { GridToggle(ui.grid, onToggleGrid) } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SwipeTabs(tabs.map { s -> readStatusLabel(s) + (ui.byStatus[s]?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: "") }, pager)
            HorizontalPager(pager, Modifier.fillMaxSize().testTag("manga_library_pager"), key = { it }) { page ->
                val items = ui.byStatus[tabs[page]].orEmpty()
                if (items.isEmpty()) EmptyState(stringResource(R.string.manga_empty_list)) else MangaGrid(items, ui.grid, onOpen)
            }
        }
    }
}

@Composable
fun MangaFavoritesContent(ui: MangaLibraryUi, onMenu: () -> Unit, onToggleGrid: () -> Unit, onOpen: (MangaEntity) -> Unit) {
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.manga_favorites), onMenu = onMenu) { GridToggle(ui.grid, onToggleGrid) } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (ui.favorites.isEmpty()) EmptyState(stringResource(R.string.manga_empty_list)) else MangaGrid(ui.favorites, ui.grid, onOpen)
        }
    }
}

@Composable
fun MangaHistoryContent(rows: List<HistoryRow>, onMenu: () -> Unit, onOpen: (Int) -> Unit) {
    val fmt = remember { latinDateTimeFormat() }
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.manga_history), onMenu = onMenu) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (rows.isEmpty()) EmptyState(stringResource(R.string.manga_empty_list))
            else LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.history.chapterKey }) { r ->
                    val m = r.manga ?: return@items
                    MangaListRow(m, { onOpen(m.id) }, stringResource(R.string.manga_chapter_n, r.history.chapterNumber) + "  •  " + "\u2066" + fmt.format(java.util.Date(r.history.readAt)) + "\u2069")
                }
            }
        }
    }
}

// ================================================================== «المانجات المحملة» (ref grid)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedContent(ui: DownloadedUi, onMenu: () -> Unit, onQuery: (String) -> Unit, onToggleGrid: () -> Unit, onOpen: (Int) -> Unit) {
    var searching by rememberSaveable { mutableStateOf(ui.query.isNotEmpty()) }
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            ui.query, onQuery, singleLine = true, placeholder = { Text(stringResource(R.string.manga_search_hint), color = Palette.TextMuted) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Palette.Accent),
                            modifier = Modifier.fillMaxWidth().testTag("downloaded_search"),
                        )
                    },
                    navigationIcon = { IconButton(onClick = { searching = false; onQuery("") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Palette.RefIcon) } },
                    actions = { GridToggle(ui.grid, onToggleGrid) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
                )
            } else {
                AppTopBar(stringResource(R.string.manga_downloaded), onMenu = onMenu) {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, stringResource(R.string.cd_search), tint = Palette.RefIcon) }
                    GridToggle(ui.grid, onToggleGrid)
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (ui.titles.isEmpty()) EmptyState(stringResource(if (ui.query.isBlank()) R.string.manga_no_downloads else R.string.empty_feed))
            else {
                val counts = remember(ui.titles) { ui.titles.associate { it.manga.id to it.chapterCount } }
                MangaGrid(ui.titles.map(DownloadedTitle::manga), ui.grid, { onOpen(it.id) }, line = { m -> stringResource(R.string.manga_downloaded_count, counts[m.id] ?: 0) })
            }
        }
    }
}
