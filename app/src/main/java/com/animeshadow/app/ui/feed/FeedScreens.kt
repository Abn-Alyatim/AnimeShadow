package com.animeshadow.app.ui.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animeshadow.app.R
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.ui.common.AnimeGrid
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.Refreshable
import com.animeshadow.app.ui.ads.ListAdBanner
import com.animeshadow.app.ui.common.SelectChip
import com.animeshadow.app.ui.common.StatusBanner
import com.animeshadow.app.ui.common.label
import com.animeshadow.app.ui.common.seasonRes
import com.animeshadow.app.ui.theme.Palette

@Composable
fun GridToggle(grid: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        if (grid) Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.cd_list), tint = Palette.RefIcon)
        else Icon(Icons.Filled.GridView, stringResource(R.string.cd_grid), tint = Palette.RefIcon)
    }
}

@Composable
fun refreshMessage(ui: FeedUi): String? = ui.error?.let {
    // v1.2: never the raw (English) exception text; the banner says what to do (pull), and there is no retry button.
    if (it.offline) stringResource(R.string.offline_cached) else stringResource(R.string.load_failed)
}

/** Shared body of Latest Updates / Top Rated / Seasons. */
@Composable
fun FeedContent(
    title: String,
    ui: FeedUi,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onToggleGrid: () -> Unit,
    onOpen: (AnimeEntity) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    cornerBadge: (AnimeEntity) -> String? = { null },
) {
    Scaffold(
        containerColor = Palette.Black,
        topBar = {
            AppTopBar(title, onMenu = onMenu) {
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.cd_search), tint = Palette.RefIcon) }
                GridToggle(ui.grid, onToggleGrid)
            }
        },
        bottomBar = { ListAdBanner() },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            refreshMessage(ui)?.let { StatusBanner(it) }
            Refreshable(refreshing = ui.refreshing && ui.items.isNotEmpty(), onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                when {
                    ui.items.isEmpty() && (ui.refreshing || !ui.loaded) && ui.error == null -> Column {
                        header?.invoke()
                        LoadingBox()
                    }
                    ui.items.isEmpty() -> Column {
                        header?.invoke()
                        EmptyState(stringResource(R.string.empty_feed))
                    }
                    else -> AnimeGrid(
                        items = ui.items,
                        grid = ui.grid,
                        onClick = onOpen,
                        onEndReached = onLoadMore,
                        loadingMore = ui.loadingMore,
                        cornerBadge = cornerBadge,
                        header = header?.let { h -> { item(span = { GridItemSpan(maxLineSpan) }, key = "header") { h() } } },
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onMenu: () -> Unit, onSearch: () -> Unit, onOpen: (Int) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    FeedContent(stringResource(R.string.nav_latest), ui, onMenu, onSearch, vm::toggleGrid, { onOpen(it.id) }, vm::loadMore, { vm.refresh(true) })
}

@Composable
fun TopScreen(onMenu: () -> Unit, onSearch: () -> Unit, onOpen: (Int) -> Unit, vm: TopViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    FeedContent(
        stringResource(R.string.nav_top), ui, onMenu, onSearch, vm::toggleGrid, { onOpen(it.id) }, vm::loadMore, { vm.refresh(true) },
        cornerBadge = { a -> ui.items.indexOf(a).takeIf { it >= 0 }?.let { "#${it + 1}" } },
    )
}

@Composable
fun SeasonPicker(pick: SeasonPick, onPick: (SeasonPick) -> Unit, thisYear: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (thisYear + 1 downTo 1990).forEach { y -> SelectChip(y.toString(), y == pick.year, { onPick(pick.copy(year = y)) }) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrowseQuery.SEASONS.forEach { s -> SelectChip(label(seasonRes(s), s), s == pick.season, { onPick(pick.copy(season = s)) }) }
        }
    }
}

@Composable
fun SeasonsContent(
    ui: FeedUi, pick: SeasonPick, thisYear: Int, onMenu: () -> Unit, onSearch: () -> Unit, onToggleGrid: () -> Unit,
    onPick: (SeasonPick) -> Unit, onOpen: (AnimeEntity) -> Unit, onLoadMore: () -> Unit, onRefresh: () -> Unit,
) {
    FeedContent(
        stringResource(R.string.nav_seasons), ui, onMenu, onSearch, onToggleGrid, onOpen, onLoadMore, onRefresh,
        header = { SeasonPicker(pick, onPick, thisYear) },
    )
}

@Composable
fun SeasonsScreen(onMenu: () -> Unit, onSearch: () -> Unit, onOpen: (Int) -> Unit, vm: SeasonsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pick by vm.pick.collectAsStateWithLifecycle()
    SeasonsContent(ui, pick, java.time.LocalDate.now().year, onMenu, onSearch, vm::toggleGrid, vm::select, { onOpen(it.id) }, vm::loadMore, { vm.refresh(true) })
}
