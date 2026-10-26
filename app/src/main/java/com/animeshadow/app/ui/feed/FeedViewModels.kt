package com.animeshadow.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.Refresh
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class FeedUi(
    val items: List<AnimeEntity> = emptyList(),
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Refresh.Failed? = null,
    val grid: Boolean = true,
    val loaded: Boolean = false,
)

/** A cached, paged AniList list: Room is the source of truth, the network only refreshes it. */
abstract class PagedFeedViewModel(
    protected val repo: AnimeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    protected val feedKey = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val error = MutableStateFlow<Refresh.Failed?>(null)
    private val loaded = MutableStateFlow(false)

    protected abstract suspend fun refreshCall(force: Boolean): Refresh
    protected abstract suspend fun loadMoreCall(): Refresh

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<FeedUi> = combine(
        feedKey.flatMapLatest { repo.observeFeed(it) },
        combine(refreshing, loadingMore, error, loaded) { r, l, e, d -> listOf(r, l, e, d) },
        settings.settings,
    ) { items, flags, s ->
        FeedUi(items, flags[0] as Boolean, flags[1] as Boolean, flags[2] as Refresh.Failed?, s.gridMode, flags[3] as Boolean)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUi())

    private var job: Job? = null
    private var jobForced = false
    private var generation = 0

    /**
     * [force] = a pull-to-refresh: always goes to the network (ignores the cache TTL). v1.1 bug: a pull that came
     * while the automatic (TTL-checked) load was still running was dropped; now the pull replaces it.
     */
    fun refresh(force: Boolean = false) {
        if (job?.isActive == true) {
            if (!force || jobForced) return
            job?.cancel()
        }
        jobForced = force
        val gen = ++generation
        job = viewModelScope.launch {
            refreshing.value = true
            try {
                val r = refreshCall(force)
                error.value = r as? Refresh.Failed
                loaded.value = true
            } finally {
                // A cancelled older run must not switch off the indicator of the run that replaced it.
                if (gen == generation) refreshing.value = false
            }
        }
    }

    /** Switches to another feed: cancels the running refresh of the previous one. */
    protected fun restart() {
        generation++
        job?.cancel()
        moreJob?.cancel()
        refreshing.value = false
        loadingMore.value = false
        error.value = null
        loaded.value = false
        refresh()
    }

    private var moreJob: Job? = null

    fun loadMore() {
        if (loadingMore.value || refreshing.value || error.value != null) return
        moreJob = viewModelScope.launch {
            loadingMore.value = true
            try {
                val r = loadMoreCall()
                if (r is Refresh.Failed) error.value = r
            } finally {
                loadingMore.value = false
            }
        }
    }

    fun toggleGrid() {
        viewModelScope.launch { settings.setGridMode(!ui.value.grid) }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(repo: AnimeRepository, settings: SettingsRepository) : PagedFeedViewModel(repo, settings) {
    init {
        feedKey.value = AnimeRepository.FEED_AIRING
        refresh()
    }
    override suspend fun refreshCall(force: Boolean) = repo.refreshAiring(force)
    override suspend fun loadMoreCall() = repo.loadMoreAiring()
}

@HiltViewModel
class TopViewModel @Inject constructor(repo: AnimeRepository, settings: SettingsRepository) : PagedFeedViewModel(repo, settings) {
    init {
        feedKey.value = AnimeRepository.FEED_TOP
        refresh()
    }
    override suspend fun refreshCall(force: Boolean) = repo.refreshTop(force)
    override suspend fun loadMoreCall() = repo.loadMoreTop()
}

data class SeasonPick(val year: Int, val season: String)

fun currentSeason(today: LocalDate = LocalDate.now(ZoneId.systemDefault())): SeasonPick = SeasonPick(
    today.year,
    when (today.monthValue) {
        12, 1, 2 -> "WINTER"
        3, 4, 5 -> "SPRING"
        6, 7, 8 -> "SUMMER"
        else -> "FALL"
    },
).let { if (today.monthValue == 12) it.copy(year = today.year + 1) else it }

@HiltViewModel
class SeasonsViewModel @Inject constructor(repo: AnimeRepository, settings: SettingsRepository) : PagedFeedViewModel(repo, settings) {
    private val _pick = MutableStateFlow(currentSeason())
    val pick: StateFlow<SeasonPick> = _pick

    init {
        select(_pick.value)
    }

    fun select(p: SeasonPick) {
        _pick.value = p
        feedKey.value = AnimeRepository.seasonFeed(p.year, p.season)
        restart()
    }

    override suspend fun refreshCall(force: Boolean) = _pick.value.let { repo.refreshSeason(it.year, it.season, force) }
    override suspend fun loadMoreCall() = _pick.value.let { repo.loadMoreSeason(it.year, it.season) }
}
