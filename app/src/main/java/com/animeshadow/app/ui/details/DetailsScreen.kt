package com.animeshadow.app.ui.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.absolutePadding
import com.animeshadow.app.ui.theme.RefType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.animeshadow.app.R
import com.animeshadow.app.data.AppJson
import com.animeshadow.app.data.RatingParts
import com.animeshadow.app.data.Refresh
import com.animeshadow.app.data.ScoreBucket
import com.animeshadow.app.data.StatusBucket
import com.animeshadow.app.data.local.AnimeCharacterEntity
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.domain.ContentRating
import com.animeshadow.app.domain.WeekSchedule
import com.animeshadow.app.ui.collections.CharacterCard
import com.animeshadow.app.ui.common.BarChart
import com.animeshadow.app.ui.common.ChartSlice
import com.animeshadow.app.ui.common.CornerBadge
import com.animeshadow.app.ui.common.DonutChart
import com.animeshadow.app.ui.common.EmptyState
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.common.Poster
import com.animeshadow.app.ui.common.ScoreLabel
import com.animeshadow.app.ui.common.SectionHeader
import com.animeshadow.app.ui.common.StatusBanner
import com.animeshadow.app.ui.common.dayRes
import com.animeshadow.app.ui.common.formatRes
import com.animeshadow.app.ui.common.genreLabel
import com.animeshadow.app.ui.common.groupedCount
import com.animeshadow.app.ui.common.label
import com.animeshadow.app.ui.common.seasonRes
import com.animeshadow.app.ui.common.sourceRes
import com.animeshadow.app.ui.common.statusRes
import com.animeshadow.app.ui.common.watchStatusColor
import com.animeshadow.app.ui.common.watchStatusRes
import com.animeshadow.app.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

class DetailsActions(
    val onBack: () -> Unit = {},
    val onFavorite: () -> Unit = {},
    val onStatus: (WatchStatus) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRating: (RatingParts) -> Unit = {},
    val onEpisode: (Int, Boolean) -> Unit = { _, _ -> },
    val onMarkUpTo: (Int) -> Unit = {},
    /** Small eye in the episodes bar; the callback gets whether it marked (true) or unmarked (false) everything. */
    val onToggleAll: ((Boolean) -> Unit) -> Unit = {},
    val onUndoAll: () -> Unit = {},
    val onReminder: (Boolean) -> Unit = {},
    val onOpenAnime: (Int) -> Unit = {},
    val onOpenCharacter: (Int) -> Unit = {},
    val onOpenStaff: (Int) -> Unit = {},
    val onGenre: (String) -> Unit = {},
    /** Studio chip in the info grid: opens the list of that studio's works. */
    val onStudio: (String) -> Unit = {},
    val onTrailer: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onOpenStaff: (Int) -> Unit,
    onGenre: (String) -> Unit = {},
    onStudio: (String) -> Unit = {},
    vm: DetailsViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DetailsContent(
        ui,
        actions = DetailsActions(
            onBack = onBack, onFavorite = { vm.toggleFavorite() }, onStatus = { vm.setStatus(it) }, onRemove = { vm.remove() },
            onRating = { vm.setRating(it) },
            onEpisode = { e, w -> vm.setEpisode(e, w) }, onMarkUpTo = { vm.markUpTo(it) },
            onToggleAll = { done -> vm.toggleAllWatched(done) }, onUndoAll = { vm.undoBulk() },
            onReminder = { vm.setReminder(it) },
            onOpenAnime = onOpenAnime, onOpenCharacter = onOpenCharacter, onOpenStaff = onOpenStaff, onGenre = onGenre, onStudio = onStudio,
            onTrailer = { id ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))) }
            },
            onRetry = { vm.refresh(true) },
        ),
    )
}

/** Item 9: long-press copy with the «تم النسخ» toast. */
fun copyWithToast(context: Context, clipboard: ClipboardManager, text: String) {
    clipboard.setText(AnnotatedString(text))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

private enum class EpFilter { ALL, WATCHED, UNWATCHED }

/** Light grey bar like ref 11 / 13: back, title, then the icons of the current tab. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailsContent(ui: DetailsUi, actions: DetailsActions, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var epFilter by rememberSaveable { mutableStateOf(EpFilter.ALL) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var showMore by remember { mutableStateOf(false) }
    var showEpFilter by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val soon = stringResource(R.string.coming_soon)
    val markedText = stringResource(R.string.marked_all)
    val unmarkedText = stringResource(R.string.unmarked_all)
    val undoText = stringResource(R.string.undo)
    val showSoon: () -> Unit = { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(soon) } }
    Scaffold(
        containerColor = Palette.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ui.anime?.displayTitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge, color = Color(0xFFF5F5F5), modifier = Modifier.padding(start = 12.dp),
                    )
                },
                navigationIcon = { IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Palette.RefIcon) } },
                actions = {
                    if (tab == 1) {
                        // Item 14: a SMALL eye replaces v1.0's wide button. Blue when every episode is watched.
                        val count = ui.episodeCount
                        val allWatched = count > 0 && (1..count).all { it in ui.watched }
                        IconButton(
                            onClick = {
                                actions.onToggleAll { marked ->
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val r = snackbar.showSnackbar(if (marked) markedText else unmarkedText, undoText)
                                        if (r == SnackbarResult.ActionPerformed) actions.onUndoAll()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("eye_all"),
                        ) {
                            Icon(
                                if (allWatched) Icons.Filled.Visibility else Icons.Outlined.Visibility,
                                stringResource(if (allWatched) R.string.cd_unmark_all else R.string.cd_mark_all),
                                tint = if (allWatched) Palette.Blue else Palette.RefIcon, modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(onClick = { newestFirst = !newestFirst }, modifier = Modifier.testTag("ep_sort")) {
                            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.cd_sort), tint = Palette.RefIcon)
                        }
                        Box {
                            IconButton(onClick = { showEpFilter = true }) {
                                Icon(Icons.Outlined.FilterAlt, stringResource(R.string.cd_filter), tint = if (epFilter == EpFilter.ALL) Palette.RefIcon else Palette.Blue)
                            }
                            DropdownMenu(expanded = showEpFilter, onDismissRequest = { showEpFilter = false }, containerColor = Palette.Card) {
                                listOf(EpFilter.ALL to R.string.filter_all, EpFilter.WATCHED to R.string.filter_watched, EpFilter.UNWATCHED to R.string.filter_unwatched).forEach { (f, res) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(res), color = if (f == epFilter) Palette.Blue else Palette.Text) },
                                        onClick = { epFilter = f; showEpFilter = false },
                                    )
                                }
                            }
                        }
                    } else {
                        IconButton(onClick = showSoon) { Icon(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.cd_comments), tint = Palette.RefIcon) }
                        IconButton(onClick = actions.onFavorite) {
                            Icon(
                                if (ui.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                stringResource(R.string.cd_favorite),
                                tint = if (ui.favorite) Palette.Favorite else Palette.RefIcon,
                            )
                        }
                        Box {
                            IconButton(onClick = { showMore = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more), tint = Palette.RefIcon) }
                            MoreMenu(ui, showMore, { showMore = false }, actions)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val tabs = listOf(R.string.tab_details, R.string.tab_episodes, R.string.tab_stats, R.string.tab_characters)
            // Ref 11: tabs start at the LEFT, sized to their text (the 4th is cut at the right edge), short white underline.
            RefTabs(tabs.map { stringResource(it) }, tab) { tab = it }
            (ui.refresh as? Refresh.Failed)?.let {
                StatusBanner(if (it.offline) stringResource(R.string.offline_cached) else stringResource(R.string.load_failed, it.message ?: ""), onRetry = actions.onRetry)
            }
            val anime = ui.anime
            if (anime == null) {
                if (ui.refresh is Refresh.Failed) EmptyState(stringResource(R.string.empty_feed)) else LoadingBox()
                return@Column
            }
            when (tab) {
                0 -> InfoTab(ui, anime, actions)
                1 -> EpisodesTab(ui, anime, actions, epFilter, newestFirst, onComments = showSoon)
                2 -> StatsTab(anime)
                else -> CharactersTab(ui, actions)
            }
        }
    }
}

@Composable
private fun RefTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        titles.forEachIndexed { i, t ->
            val on = i == selected
            Column(
                Modifier.width(IntrinsicSize.Max).clickable { onSelect(i) }.padding(horizontal = 12.dp).testTag("tab_$i"),
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

@Composable
private fun MoreMenu(ui: DetailsUi, expanded: Boolean, onDismiss: () -> Unit, actions: DetailsActions) {
    val a = ui.anime
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = Palette.Card) {
        if (a != null && ui.entry != null && a.status == "RELEASING" && a.nextEpisode != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remind_me)) },
                trailingIcon = { Checkbox(checked = ui.entry.remind, onCheckedChange = null) },
                onClick = { actions.onReminder(!ui.entry.remind); onDismiss() },
            )
        }
        if (a?.trailerId != null && a.trailerSite == "youtube") {
            DropdownMenuItem(text = { Text(stringResource(R.string.cd_play_trailer)) }, onClick = { actions.onTrailer(a.trailerId); onDismiss() })
        }
        if (ui.entry != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_list), color = Palette.Dropped) }, onClick = { actions.onRemove(); onDismiss() })
        }
    }
}

// ------------------------------------------------------------------ Details tab (refs 01 + 11)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoTab(ui: DetailsUi, a: AnimeEntity, actions: DetailsActions) {
    var showRating by remember { mutableStateOf(false) }
    var showPoster by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("details_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") { Header(a) { showPoster = true } }
        item(key = "ratings") { RatingRow(ui, a, actions) { showRating = true } }
        item(key = "synopsis") { Synopsis(ui, a) }
        if (a.genreList.isNotEmpty()) item(key = "genres") {
            // Ref 11: chips flow from the RIGHT.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                // Ref 11: 22 dp chips, 12 dp apart, 35 dp from the right edge, 11 dp under the synopsis.
                FlowRow(
                    Modifier.fillMaxWidth().absolutePadding(left = 15.dp, right = 35.dp, top = 8.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    a.genreList.forEach { g ->
                        RefChip(genreLabel(g), Modifier.clickable { actions.onGenre(g) }.testTag("genre_$g"))
                    }
                }
            }
        }
        item(key = "info") { InfoGrid(a, actions.onStudio) }
        if (a.trailerId != null && a.trailerSite == "youtube") item(key = "trailer") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionHeader(stringResource(R.string.trailer))
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).clickable { actions.onTrailer(a.trailerId) },
                    contentAlignment = Alignment.Center,
                ) {
                    Poster(a.trailerThumb, Modifier.fillMaxSize())
                    Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, stringResource(R.string.cd_play_trailer), tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
        if (ui.relations.isNotEmpty()) item(key = "related") {
            AnimeRow(stringResource(R.string.related), ui.relations.map { it.anime to relationLabel(it.relationType) }, actions.onOpenAnime)
        }
        if (ui.recommendations.isNotEmpty()) item(key = "recs") {
            AnimeRow(stringResource(R.string.recommendations_row), ui.recommendations.map { it.anime to null }, actions.onOpenAnime)
        }
        if (ui.similar.isNotEmpty()) item(key = "similar") {
            AnimeRow(stringResource(R.string.similar_row), ui.similar.map { it to null }, actions.onOpenAnime)
        }
    }
    if (showRating) RatingDialog(a.displayTitle, RatingParts.of(ui.entry), { actions.onRating(it); showRating = false }, { showRating = false })
    if (showPoster) PosterViewer(a.posterUrl, a.displayTitle) { showPoster = false }
}

/** Ref 11 header: poster on the LEFT (tap = zoom), beside it English title, «(day) status», «season year», «type | age». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(a: AnimeEntity, onPoster: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Ref 11 at 360 dp: poster 113 x 158 dp, 15 dp from the left and under the tabs; the text column starts 15 dp
    // right of it and its last line sits 17 dp above the poster's bottom edge.
    Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 12.dp, top = 15.dp), verticalAlignment = Alignment.Bottom) {
        Poster(
            a.posterUrl,
            Modifier.width(113.dp).height(158.dp).clip(RoundedCornerShape(2.dp)).clickable(onClick = onPoster).testTag("poster"),
            stringResource(R.string.cd_poster), a.color,
        )
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
            val title = a.englishTitle ?: a.displayTitle
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = RefType.Title, lineHeight = 20.sp, lineHeightStyle = TightCenter),
                color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Left,
                modifier = Modifier.padding(bottom = 1.dp).combinedClickable(onClick = {}, onLongClick = { copyWithToast(context, clipboard, title) }).testTag("title"),
            )
            val day = a.nextAiringAt?.takeIf { a.status == "RELEASING" }?.let { stringResource(dayRes(WeekSchedule.dayOf(it, ZoneId.systemDefault()))) }
            val status = label(statusRes(a.status), a.status)
            // Ref 11 reads «(الاحد) مستمر»: the day first, so «مستمر» ends up on the left.
            HeaderLine(if (day != null) "($day) $status" else status)
            val season = listOfNotNull(seasonRes(a.season)?.let { stringResource(it) }, a.seasonYear?.toString()).joinToString(" ")
            if (season.isNotBlank()) HeaderLine(season)
            val age = ContentRating.of(a.malRating, a.isAdult)?.label
            // Ref 11: «مسلسل | 13+» — type and age only.
            val type = listOfNotNull(
                formatRes(a.format)?.let { stringResource(it) },
                age?.let { "\u2066$it\u2069" },
            ).joinToString(" | ")
            if (type.isNotBlank()) HeaderLine(type, Modifier.testTag("type_line"))
        }
    }
}

private val TightCenter = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

@Composable
private fun HeaderLine(text: String, modifier: Modifier = Modifier) {
    // Ref 11: 14 sp regular light grey lines, 25 dp apart.
    Box(modifier.height(24.5.dp).semantics(mergeDescendants = true) {}, contentAlignment = Alignment.CenterStart) {
        Text(
            // Right-to-left paragraphs like the reference: «مستمر» left of «(الاحد)», «13+» left of «مسلسل».
            text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = RefType.Body, lineHeight = 20.sp, lineHeightStyle = TightCenter, textDirection = TextDirection.Rtl),
            color = Color(0xFFD1D1D1), maxLines = 1, textAlign = TextAlign.Left,
        )
    }
}

/** Ref 11 rating row, LEFT to RIGHT: average ★, MAL, your ★ «تقييمك», «قائمتي» status (inline menu, not a dialog). */
@Composable
private fun RatingRow(ui: DetailsUi, a: AnimeEntity, actions: DetailsActions, onRate: () -> Unit) {
    var statusMenu by remember { mutableStateOf(false) }
    // Ref 11: four equal columns between 14 dp margins, 17 dp under the poster. Each column is a tight stack:
    // 26 dp icon, the score (or «قائمتي»), and right under it the small grey line.
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 0.dp), verticalAlignment = Alignment.Top) {
        RatingCell(Modifier.weight(1f), { Icon(Icons.Filled.Star, null, tint = Palette.Star, modifier = Modifier.size(26.dp)) }) {
            ScoreOutOf10(a.score?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it / 10.0) })
            CellSub(a.popularity?.let { groupedCount(it) } ?: "—")
        }
        RatingCell(Modifier.weight(1f).testTag("mal_cell"), { MalLogo() }) {
            ScoreOutOf10(a.malScore?.let { String.format(Locale.US, "%.2f", it) })
            CellSub((a.malScoredBy ?: a.malMembers)?.let { groupedCount(it) } ?: "—")
        }
        val overall = ui.entry?.overallScore
        RatingCell(Modifier.weight(1f).clickable(onClick = onRate).testTag("your_rating"), { Icon(Icons.Filled.Star, null, tint = Palette.Blue, modifier = Modifier.size(26.dp)) }) {
            ScoreOutOf10(overall?.let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.1f", it) })
            CellSub(stringResource(R.string.your_score))
        }
        Box(Modifier.weight(1f).wrapContentSize(Alignment.TopCenter)) {
            RatingCell(Modifier.fillMaxWidth().clickable { statusMenu = true }.testTag("my_list"), { Icon(Icons.Outlined.PlayCircle, null, tint = Color.White, modifier = Modifier.size(26.dp)) }) {
                Text(
                    stringResource(R.string.my_list),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = RefType.Body, lineHeight = 24.sp, lineHeightStyle = TightCenter),
                    color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                val e = ui.entry
                CellSub(e?.let { stringResource(watchStatusRes(it.status)) } ?: stringResource(R.string.not_in_list), Modifier.testTag("my_list_status"))
            }
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }, containerColor = Palette.Card) {
                WatchStatus.entries.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(watchStatusRes(s)), fontWeight = if (s == ui.entry?.status) FontWeight.Bold else FontWeight.Normal,
                                color = if (s == ui.entry?.status) Palette.Blue else Palette.Text,
                            )
                        },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(watchStatusColor(s))) },
                        onClick = { actions.onStatus(s); statusMenu = false },
                        modifier = Modifier.testTag("status_${s.name}"),
                    )
                }
                if (ui.entry != null) {
                    HorizontalDivider(color = Palette.Divider)
                    DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_list), color = Palette.Dropped) }, onClick = { actions.onRemove(); statusMenu = false })
                }
            }
        }
    }
}

@Composable
private fun RatingCell(modifier: Modifier, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(5.dp))
        content()
    }
}

@Composable
private fun ScoreOutOf10(value: String?) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = RefType.Score, color = Color.White)) { append(value ?: "—") }
            withStyle(SpanStyle(fontSize = RefType.OutOf, color = Color.White)) { append("/10") }
        },
        style = TextStyle(lineHeight = 24.sp, lineHeightStyle = TightCenter),
        maxLines = 1,
    )
}

@Composable
private fun CellSub(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.bodySmall.copy(fontSize = RefType.Small, lineHeight = 16.sp, lineHeightStyle = TightCenter),
        color = Color(0xFFCACACA), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.padding(top = 2.dp),
    )
}

@Composable
private fun MalLogo() {
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF2E51A2)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(stringResource(R.string.mal_short), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
    }
}

/** Small dark chip of ref 11 (genres, studio): 22 dp high, 4 dp corners, 11.5 sp text. */
@Composable
private fun RefChip(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = RefType.Chip, pill: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1A1A1A)).then(modifier).height(22.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFFE6E6E6), style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize, lineHeight = 15.sp, lineHeightStyle = TightCenter), maxLines = 1)
    }
}

/**
 * Ref 11 synopsis: Arabic, right-aligned, 14 sp, 5 lines then «…» (tap to expand). The Arabic is an online machine
 * translation made in the background and cached (see SynopsisPrefetcher); English only while offline and never
 * translated, with a small «جارٍ الترجمة…» while a translation is on its way.
 */
@Composable
private fun Synopsis(ui: DetailsUi, a: AnimeEntity) {
    var expanded by remember { mutableStateOf(false) }
    val arabic = a.arabicSynopsis
    val text = arabic ?: a.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_synopsis)
    // Ref 11: the text block runs from 15 dp on the left to 31 dp from the right edge, 33 dp under the rating row.
    Column(Modifier.fillMaxWidth().absolutePadding(left = 15.dp, right = 31.dp, top = 27.5.dp, bottom = 0.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = RefType.Body, lineHeight = 19.2.sp),
            color = Color(0xFFDFDFDF),
            textAlign = if (arabic != null) TextAlign.Right else TextAlign.Left,
            maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.testTag("synopsis"),
        )
        if (arabic == null && ui.translating) {
            Text(stringResource(R.string.translating), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(top = 2.dp).testTag("synopsis_note"))
        }
    }
}

/**
 * Ref 11 info grid, laid out RIGHT to LEFT (المصدر on the right, مدة الحلقة on its left …): two 137 dp columns whose
 * text ends 31 dp from the right edge, label bold white over a regular grey value, rows 56.5 dp apart. Last cell is the
 * English title like the reference (long-press copies it). The studio chip opens its works.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoGrid(a: AnimeEntity, onStudio: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    data class Cell(val label: String, val value: String, val chip: Boolean = false, val copy: Boolean = false)
    val cells = buildList {
        sourceRes(a.source)?.let { add(Cell(stringResource(R.string.info_source), stringResource(it))) }
        a.duration?.let { add(Cell(stringResource(R.string.info_duration), stringResource(R.string.minutes_short, it))) }
        a.startDate?.let { add(Cell(stringResource(R.string.info_from), it)) }
        add(Cell(stringResource(R.string.info_to), a.endDate ?: "?"))
        a.studio?.let { add(Cell(stringResource(R.string.info_studio), it, chip = true)) }
        // Ref 11's last cell: «العنوان الانجليزي» (long-press copies it).
        a.englishTitle?.let { add(Cell(stringResource(R.string.info_english), it, copy = true)) }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxWidth().absolutePadding(left = 54.dp, right = 31.dp, top = 19.dp, bottom = 0.dp)) {
            cells.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(56.5.dp)) {
                    pair.forEach { c ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(
                                c.label, style = MaterialTheme.typography.titleSmall.copy(fontSize = RefType.Body, lineHeight = 20.sp, lineHeightStyle = TightCenter),
                                color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1,
                            )
                            Spacer(Modifier.height(1.dp))
                            if (c.chip) {
                                RefChip(c.value, Modifier.clickable { onStudio(c.value) }.testTag("studio_chip"), fontSize = 12.sp, pill = true)
                            } else {
                                val m = if (c.copy) {
                                    Modifier.combinedClickable(onClick = {}, onLongClick = { copyWithToast(context, clipboard, c.value) }).testTag("english_title")
                                } else Modifier
                                Text(
                                    c.value, style = MaterialTheme.typography.bodyMedium.copy(fontSize = RefType.Body, lineHeight = 20.sp, lineHeightStyle = TightCenter),
                                    color = Color(0xFFC7C7C7), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = m,
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Item 9: full-screen, pinch-to-zoom poster. Double-tap resets, tap the X or back to close. */
@Composable
fun PosterViewer(url: String?, title: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("poster_viewer")) {
            AsyncImage(
                model = url, contentDescription = title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                    .transformable(state)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.cd_close), tint = Color.White)
            }
        }
    }
}

/** Ref 03: four 0–10 sliders, overall X/10 under them, «حسنا» / «الغاء» at the bottom right. */
@Composable
fun RatingDialog(title: String, initial: RatingParts, onSave: (RatingParts) -> Unit, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF080808),
        shape = RoundedCornerShape(4.dp),
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(), maxLines = 2)
        },
        text = {
            Column(Modifier.fillMaxWidth().testTag("rating_dialog")) {
                RatingSlider(stringResource(R.string.rate_story), r.story, "slider_story") { r = r.copy(story = it) }
                RatingSlider(stringResource(R.string.rate_characters), r.characters, "slider_characters") { r = r.copy(characters = it) }
                RatingSlider(stringResource(R.string.rate_animation), r.animation, "slider_animation") { r = r.copy(animation = it) }
                RatingSlider(stringResource(R.string.rate_music), r.music, "slider_music") { r = r.copy(music = it) }
                val o = r.overall
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)) {
                            append(if (o % 1.0 == 0.0) o.toInt().toString() else String.format(Locale.US, "%.2f", o).trimEnd('0').trimEnd('.'))
                        }
                        withStyle(SpanStyle(fontSize = 13.sp, color = Palette.TextSecondary)) { append("/10") }
                    },
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("rating_overall"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(r) }, modifier = Modifier.testTag("rating_ok")) { Text(stringResource(R.string.ok_ref), color = Palette.RefText, fontSize = 13.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Palette.Blue, fontSize = 13.sp) }
        },
    )
}

/** Ref 03 slider: a thin blue line with a dot per step (0–10), a blue thumb, the numbers under the dots. */
@Composable
private fun RatingSlider(label: String, value: Int, tag: String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Palette.RefText, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
        DotSlider(value, onChange, Modifier.fillMaxWidth().height(22.dp).testTag(tag).semantics { contentDescription = "$label $value" })
        Row(Modifier.fillMaxWidth()) {
            (0..10).forEach { n ->
                Text(n.toString(), fontSize = 13.sp, lineHeight = 16.sp, color = Palette.RefText, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DotSlider(value: Int, onChange: (Int) -> Unit, modifier: Modifier) {
    val latest by rememberUpdatedState(onChange)
    fun pick(x: Float, width: Int): Int {
        val cell = width / 11f
        return ((x / cell).toInt()).coerceIn(0, 10)
    }
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { latest(pick(it.x, size.width)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> latest(pick(change.position.x, size.width)) } }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..10f, steps = 9)
                setProgress { target -> latest(Math.round(target).coerceIn(0, 10)); true }
            },
    ) {
        val cell = size.width / 11f
        val y = size.height / 2
        val x0 = cell / 2
        val x10 = size.width - cell / 2
        drawLine(Color(0xFF5C9DD5), Offset(x0, y), Offset(x10, y), strokeWidth = 2.dp.toPx())
        for (n in 0..10) drawCircle(Palette.Blue, radius = 3.dp.toPx(), center = Offset(x0 + n * cell, y))
        drawCircle(Palette.Blue, radius = 8.dp.toPx(), center = Offset(x0 + value * cell, y))
    }
}

// ------------------------------------------------------------------ Episodes tab (ref 13)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodesTab(ui: DetailsUi, a: AnimeEntity, actions: DetailsActions, filter: EpFilter, newestFirst: Boolean, onComments: () -> Unit) {
    val count = ui.episodeCount
    val episodes = remember(count, ui.watched, filter, newestFirst) {
        val all = (1..count).filter { e ->
            when (filter) {
                EpFilter.ALL -> true
                EpFilter.WATCHED -> e in ui.watched
                EpFilter.UNWATCHED -> e !in ui.watched
            }
        }
        if (newestFirst) all.reversed() else all
    }
    if (count == 0) {
        EmptyState(stringResource(R.string.episodes_unknown_total))
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("episodes_list"), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(episodes, key = { it }) { e ->
            val watched = e in ui.watched
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { actions.onEpisode(e, !watched) }, onLongClick = { actions.onMarkUpTo(e) })
                    .height(46.dp)
                    .padding(start = 14.dp, end = 4.dp)
                    .testTag("ep_$e"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.episode_label, e), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold),
                    color = Palette.RefText, textAlign = TextAlign.Left, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.onEpisode(e, !watched) }, modifier = Modifier.size(40.dp).testTag("eye_$e")) {
                    Icon(
                        Icons.Filled.Visibility,
                        stringResource(R.string.cd_mark_watched, e),
                        tint = if (watched) Palette.Blue else Color(0xFF616161), modifier = Modifier.size(20.dp),
                    )
                }
                
                IconButton(onClick = onComments, modifier = Modifier.size(40.dp).testTag("comments_$e")) {
                    Icon(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.cd_episode_comments, e), tint = Palette.RefIcon, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun relationLabel(type: String): String = when (type) {
    "PREQUEL" -> stringResource(R.string.rel_PREQUEL)
    "SEQUEL" -> stringResource(R.string.rel_SEQUEL)
    else -> stringResource(R.string.rel_OTHER)
}

@Composable
fun AnimeRow(title: String, items: List<Pair<AnimeEntity, String?>>, onOpen: (Int) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionHeader(title, Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.first.id }) { (a, badge) ->
                Column(Modifier.width(108.dp).clickable { onOpen(a.id) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(10.dp))) {
                        Poster(a.gridPoster, Modifier.fillMaxSize(), a.displayTitle, a.color)
                        if (badge != null) CornerBadge(badge, Modifier.align(Alignment.TopStart).padding(4.dp))
                    }
                    Text(a.displayTitle, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    ScoreLabel(a.score, prefix = label(formatRes(a.format), a.format))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Stats tab

@Composable
private fun StatsTab(a: AnimeEntity) {
    val scores = remember(a.scoreDist) { a.scoreDist?.let { runCatching { AppJson.decodeFromString<List<ScoreBucket>>(it) }.getOrNull() }.orEmpty() }
    val statuses = remember(a.statusDist) { a.statusDist?.let { runCatching { AppJson.decodeFromString<List<StatusBucket>>(it) }.getOrNull() }.orEmpty() }
    if (scores.isEmpty() && statuses.isEmpty()) {
        EmptyState(stringResource(R.string.no_stats))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (scores.isNotEmpty()) item {
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Card).padding(14.dp)) {
                SectionHeader(stringResource(R.string.score_distribution))
                BarChart(
                    scores.sortedBy { it.score }.map { b ->
                        val t = b.score / 100f
                        ChartSlice((b.score / 10).toString(), b.amount, androidx.compose.ui.graphics.lerp(Palette.Dropped, Palette.Accent, t))
                    },
                )
            }
        }
        if (statuses.isNotEmpty()) item {
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Card).padding(14.dp)) {
                SectionHeader(stringResource(R.string.status_distribution))
                DonutChart(statuses.map { ChartSlice(distLabel(it.status), it.amount, distColor(it.status)) })
            }
        }
    }
}

@Composable
private fun distLabel(s: String) = when (s) {
    "CURRENT" -> stringResource(R.string.dist_CURRENT)
    "PLANNING" -> stringResource(R.string.dist_PLANNING)
    "COMPLETED" -> stringResource(R.string.dist_COMPLETED)
    "DROPPED" -> stringResource(R.string.dist_DROPPED)
    "PAUSED" -> stringResource(R.string.dist_PAUSED)
    else -> s
}

private fun distColor(s: String) = when (s) {
    "CURRENT" -> Palette.Watching
    "PLANNING" -> Palette.Plan
    "COMPLETED" -> Palette.Completed
    "DROPPED" -> Palette.Dropped
    else -> Palette.OnHold
}

// ------------------------------------------------------------------ Characters tab

@Composable
private fun CharactersTab(ui: DetailsUi, actions: DetailsActions) {
    if (ui.characters.isEmpty() && ui.staff.isEmpty()) {
        EmptyState(stringResource(R.string.no_characters))
        return
    }
    val main = ui.characters.filter { it.role == "MAIN" }
    val support = ui.characters.filter { it.role != "MAIN" }
    val mainTitle = stringResource(R.string.main_characters)
    val supportTitle = stringResource(R.string.supporting_characters)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
        if (main.isNotEmpty()) characterSection(mainTitle, main, actions)
        if (support.isNotEmpty()) characterSection(supportTitle, support, actions)
        if (ui.staff.isNotEmpty()) item(key = "staff") {
            Column {
                SectionHeader(stringResource(R.string.staff), Modifier.padding(horizontal = 16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ui.staff, key = { "${it.staffId}-${it.role}" }) { s ->
                        CharacterCard(s.name, s.image, s.role, { actions.onOpenStaff(s.staffId) }, Modifier.width(112.dp))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.characterSection(title: String, list: List<AnimeCharacterEntity>, actions: DetailsActions) {
    item(key = "section-$title") {
        Column(Modifier.padding(bottom = 12.dp)) {
            SectionHeader(title, Modifier.padding(horizontal = 16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.characterId }) { c ->
                    Column(Modifier.width(112.dp)) {
                        CharacterCard(c.name, c.image, null, { actions.onOpenCharacter(c.characterId) })
                        if (c.vaName != null && c.vaId != null) {
                            Row(
                                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { actions.onOpenStaff(c.vaId) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Poster(c.vaImage, Modifier.size(26.dp).clip(CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(c.vaName, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
