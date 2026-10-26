package com.animeshadow.app.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.R
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.Refresh
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.ScheduleRow
import com.animeshadow.app.domain.Recommendation
import com.animeshadow.app.domain.RecommendationEngine
import com.animeshadow.app.domain.WeekSchedule
import com.animeshadow.app.ui.common.AnimeGridCard
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.Poster
import com.animeshadow.app.ui.common.SectionHeader
import com.animeshadow.app.ui.common.StatusBanner
import com.animeshadow.app.ui.common.dayRes
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

// ------------------------------------------------------------------ Recommendations

@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    library: LibraryRepository,
    private val anime: AnimeRepository,
) : ViewModel() {
    val ui: StateFlow<List<Recommendation>?> = library.observeLibrary().map { items ->
        val liked = items.filter { RecommendationEngine.entryWeight(it) >= 0.8 }.map { it.entry.animeId }
        RecommendationEngine.recommend(items, anime.cachedCandidates(), anime.recommendationsFor(liked))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun RecommendationsContent(items: List<Recommendation>?, onMenu: () -> Unit, onOpen: (Int) -> Unit) {
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_recommendations), onMenu = onMenu) }) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                items == null -> LoadingBox()
                items.isEmpty() -> EmptyState(stringResource(R.string.empty_recommendations))
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items, key = { it.anime.id }) { r -> RecommendationCard(r, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(r: Recommendation, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PairSide(stringResource(R.string.if_you_liked), r.because, Modifier.weight(1f), onOpen)
            Box(Modifier.padding(horizontal = 8.dp).size(36.dp).clip(CircleShape).background(Palette.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Shuffle, null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
            }
            PairSide(stringResource(R.string.you_may_like), r.anime, Modifier.weight(1f), onOpen)
        }
        val reason = when {
            r.fromSource -> stringResource(R.string.because_source)
            r.sharedGenres.isNotEmpty() -> stringResource(R.string.because_genres, r.sharedGenres.take(4).joinToString("، "))
            else -> null
        }
        if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PairSide(label: String, anime: AnimeEntity?, modifier: Modifier, onOpen: (Int) -> Unit) {
    Column(modifier.then(if (anime != null) Modifier.clickable { onOpen(anime.id) } else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        Poster(anime?.gridPoster, Modifier.padding(vertical = 6.dp).fillMaxWidth(0.72f).aspectRatio(0.7f).clip(RoundedCornerShape(10.dp)), anime?.displayTitle, anime?.color)
        Text(anime?.displayTitle ?: "—", style = MaterialTheme.typography.titleSmall, color = Palette.Accent, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun RecommendationsScreen(onMenu: () -> Unit, onOpen: (Int) -> Unit, vm: RecommendationsViewModel = hiltViewModel()) {
    val items by vm.ui.collectAsStateWithLifecycle()
    RecommendationsContent(items, onMenu, onOpen)
}

// ------------------------------------------------------------------ Weekly schedule

data class ScheduleUi(val days: Map<DayOfWeek, List<ScheduleRow>> = emptyMap(), val refresh: Refresh? = null, val today: DayOfWeek = DayOfWeek.SATURDAY)

@HiltViewModel
class ScheduleViewModel @Inject constructor(private val repo: AnimeRepository) : ViewModel() {
    private val refresh = MutableStateFlow<Refresh?>(null)
    private val zone = ZoneId.systemDefault()
    private val now = System.currentTimeMillis()

    val ui: StateFlow<ScheduleUi> = combine(repo.observeSchedule(now - 24 * 3_600_000L, now + 7 * 24 * 3_600_000L), refresh) { rows, r ->
        ScheduleUi(WeekSchedule.group(rows.filter { it.airAt >= startOfToday() }, zone), r, WeekSchedule.dayOf(now, zone))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUi())

    private fun startOfToday(): Long = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    init { load(false) }

    fun load(force: Boolean) = viewModelScope.launch { refresh.value = repo.refreshSchedule(force) }
}

@Composable
fun ScheduleContent(ui: ScheduleUi, onMenu: () -> Unit, onOpen: (Int) -> Unit, onRetry: () -> Unit) {
    val time = com.animeshadow.app.ui.common.latinTimeFormat()
    Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(stringResource(R.string.nav_schedule), onMenu = onMenu) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            (ui.refresh as? Refresh.Failed)?.let { StatusBanner(stringResource(if (it.offline) R.string.offline_cached else R.string.empty_feed), onRetry = onRetry) }
            if (ui.days.values.all { it.isEmpty() }) {
                if (ui.refresh == null) LoadingBox() else EmptyState(stringResource(R.string.empty_feed))
                return@Column
            }
            // Today first, then the rest of the week in Saturday-first order.
            val order = WeekSchedule.ORDER.let { o -> val i = o.indexOf(ui.today); o.drop(i) + o.take(i) }
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "note") {
                    Text(stringResource(R.string.schedule_expected), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                order.forEach { day ->
                    val rows = ui.days[day].orEmpty()
                    item(span = { GridItemSpan(maxLineSpan) }, key = "day-$day") {
                        val title = stringResource(dayRes(day)) + if (day == ui.today) " • " + stringResource(R.string.today) else ""
                        SectionHeader(title)
                    }
                    if (rows.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty-$day") {
                            Text(stringResource(R.string.schedule_empty_day), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                    items(rows, key = { "$day-${it.anime.id}-${it.airEpisode}" }) { r ->
                        AnimeGridCard(
                            r.anime, { onOpen(r.anime.id) },
                            bottomBadge = stringResource(R.string.episode_n, r.airEpisode),
                            cornerBadge = time.format(Date(r.airAt)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleScreen(onMenu: () -> Unit, onOpen: (Int) -> Unit, vm: ScheduleViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    ScheduleContent(ui, onMenu, onOpen) { vm.load(true) }
}
