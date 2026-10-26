package com.animeshadow.app.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.ui.common.AnimeGrid
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.StatusBanner
import com.animeshadow.app.ui.common.genreLabel
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenreUi(
    val genre: String = "",
    val items: List<AnimeEntity> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null,
    val grid: Boolean = true,
)

/** v1.1 item 11: tapping a genre chip lists every anime of that genre (AniList, most popular first, paged). */
@HiltViewModel
class GenreViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: AnimeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val genre: String = checkNotNull(savedState.get<String>("name"))
    private val _ui = MutableStateFlow(GenreUi(genre = genre))
    val ui: StateFlow<GenreUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { settings.settings.collect { s -> _ui.update { it.copy(grid = s.gridMode) } } }
        load(reset = true)
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || !s.hasNext) return
        load(reset = false)
    }

    fun retry() = load(reset = true)

    fun toggleGrid() = viewModelScope.launch { settings.setGridMode(!_ui.value.grid) }

    private fun load(reset: Boolean) {
        if (reset) job?.cancel()
        job = viewModelScope.launch {
            val page = if (reset) 1 else _ui.value.page + 1
            _ui.update { it.copy(loading = true, error = null) }
            repo.browse(BrowseQuery(genre = genre), page).fold(
                { r ->
                    _ui.update {
                        it.copy(
                            items = if (reset) r.items else (it.items + r.items).distinctBy { a -> a.id },
                            page = page, hasNext = r.hasNext, loading = false, fromCache = r.fromCache,
                        )
                    }
                },
                { e -> _ui.update { it.copy(loading = false, error = e.message ?: "error", hasNext = false) } },
            )
        }
    }
}

@Composable
fun GenreContent(ui: GenreUi, onBack: () -> Unit, onToggleGrid: () -> Unit, onOpen: (AnimeEntity) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar(stringResource(R.string.genre_title, genreLabel(ui.genre)), onBack = onBack) { GridToggle(ui.grid, onToggleGrid) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ui.fromCache) StatusBanner(stringResource(R.string.search_offline))
            ui.error?.let { StatusBanner(stringResource(R.string.load_failed, it), onRetry = onRetry) }
            when {
                ui.items.isEmpty() && ui.loading -> LoadingBox()
                ui.items.isEmpty() -> EmptyState(stringResource(R.string.empty_list))
                else -> AnimeGrid(ui.items, ui.grid, onOpen, onEndReached = onLoadMore, loadingMore = ui.loading && ui.page > 0)
            }
        }
    }
}

@Composable
fun GenreScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, vm: GenreViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    GenreContent(ui, onBack, { vm.toggleGrid() }, { onOpen(it.id) }, vm::loadMore, vm::retry)
}

/** v1.1.1: the studio chip on the details page lists that studio's works (AniList, most popular first, paged). */
@HiltViewModel
class StudioViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: AnimeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val studio: String = checkNotNull(savedState.get<String>("name"))
    private val _ui = MutableStateFlow(GenreUi(genre = studio))
    val ui: StateFlow<GenreUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { settings.settings.collect { s -> _ui.update { it.copy(grid = s.gridMode) } } }
        load(reset = true)
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || !s.hasNext) return
        load(reset = false)
    }

    fun retry() = load(reset = true)

    fun toggleGrid() = viewModelScope.launch { settings.setGridMode(!_ui.value.grid) }

    private fun load(reset: Boolean) {
        if (reset) job?.cancel()
        job = viewModelScope.launch {
            val page = if (reset) 1 else _ui.value.page + 1
            _ui.update { it.copy(loading = true, error = null) }
            repo.studioWorks(studio, page).fold(
                { r ->
                    _ui.update {
                        it.copy(
                            items = if (reset) r.items else (it.items + r.items).distinctBy { a -> a.id },
                            page = page, hasNext = r.hasNext, loading = false, fromCache = r.fromCache,
                        )
                    }
                },
                { e -> _ui.update { it.copy(loading = false, error = e.message ?: "error", hasNext = false) } },
            )
        }
    }
}

@Composable
fun StudioContent(ui: GenreUi, onBack: () -> Unit, onToggleGrid: () -> Unit, onOpen: (AnimeEntity) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    Scaffold(
        containerColor = Palette.Black,
        topBar = { AppTopBar(stringResource(R.string.studio_title, ui.genre), onBack = onBack) { GridToggle(ui.grid, onToggleGrid) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().testTag("studio_screen")) {
            ui.error?.let { StatusBanner(stringResource(R.string.load_failed, it), onRetry = onRetry) }
            when {
                ui.items.isEmpty() && ui.loading -> LoadingBox()
                ui.items.isEmpty() -> EmptyState(stringResource(R.string.empty_list))
                else -> AnimeGrid(ui.items, ui.grid, onOpen, onEndReached = onLoadMore, loadingMore = ui.loading && ui.page > 0)
            }
        }
    }
}

@Composable
fun StudioScreen(onBack: () -> Unit, onOpen: (Int) -> Unit, vm: StudioViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    StudioContent(ui, onBack, { vm.toggleGrid() }, { onOpen(it.id) }, vm::loadMore, vm::retry)
}
