package com.animeshadow.app.manga.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.animeshadow.app.R
import com.animeshadow.app.manga.data.DownloadState
import com.animeshadow.app.manga.data.MangaDatabase
import com.animeshadow.app.manga.data.MangaEntity
import com.animeshadow.app.manga.data.MangaKind
import com.animeshadow.app.manga.data.MangaRepository
import com.animeshadow.app.manga.download.MangaDownloads
import com.animeshadow.app.manga.source.ChapterProviders
import com.animeshadow.app.manga.source.ProviderChapter
import com.animeshadow.app.ui.common.SelectChip
import com.animeshadow.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** One chapter as the reader sees it: page sources are files on the phone (offline) or the provider's URLs. */
data class ReaderChapter(
    val key: String,
    val number: String,
    val pages: List<Any>,
    val offline: Boolean,
)

data class ReaderUi(
    val manga: MangaEntity? = null,
    val chapter: ReaderChapter? = null,
    val startPage: Int = 0,
    val prevKey: String? = null,
    val nextKey: String? = null,
    val settings: ReaderSettings = ReaderSettings.DEFAULT,
    val titleDirection: ReaderDirection? = null,
    val loading: Boolean = true,
) {
    val direction: ReaderDirection get() = settings.effectiveDirection(manga?.kind ?: MangaKind.MANGA, titleDirection)
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repo: MangaRepository,
    private val downloads: MangaDownloads,
    private val prefs: ReaderPrefs,
    private val db: MangaDatabase,
    state: SavedStateHandle,
) : ViewModel() {
    val mangaId: Int = state.get<Int>("id") ?: 0
    private val key = MutableStateFlow(state.get<String>("key").orEmpty())
    private val chapterState = MutableStateFlow(ReaderUi())

    val ui: StateFlow<ReaderUi> = combine(chapterState, prefs.settings, prefs.titleDirection(mangaId)) { c, s, d -> c.copy(settings = s, titleDirection = d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUi())

    private var remote: List<ProviderChapter> = emptyList()
    private var saveJob: Job? = null

    init { viewModelScope.launch { open(key.value) } }

    private suspend fun open(k: String) {
        chapterState.value = chapterState.value.copy(loading = true)
        val m = repo.observe(mangaId).first()
        val local = downloads.localPages(mangaId, k)
        val dl = db.downloads().get(k)
        if (remote.isEmpty() && m != null && repo.providerAvailable) remote = runCatching { repo.chapters(m) }.getOrDefault(emptyList())
        val chapter: ReaderChapter? = if (dl?.state == DownloadState.DONE && local.isNotEmpty()) {
            ReaderChapter(k, dl.chapterNumber, local, offline = true)
        } else {
            val rc = remote.firstOrNull { ChapterProviders.chapterKey(ChapterProviders.active.id, it.id) == k }
            if (rc != null) {
                val pages = runCatching { ChapterProviders.active.pageUrls(rc) }.getOrNull()
                pages?.let { ReaderChapter(k, rc.number, it.urls, offline = false) }
            } else if (local.isNotEmpty()) ReaderChapter(k, dl?.chapterNumber ?: "", local, offline = true) else null
        }
        // Neighbours: the provider's list when there is one, else the downloaded chapters of this title.
        val keys: List<Pair<String, Double>> = if (remote.isNotEmpty()) remote.map { ChapterProviders.chapterKey(ChapterProviders.active.id, it.id) to it.numberValue }
        else db.downloads().observeDownloadedChapters(mangaId).first().map { it.chapterKey to it.numberValue }
        val asc = keys.sortedBy { it.second }.map { it.first }
        val i = asc.indexOf(k)
        chapterState.value = ReaderUi(
            manga = m, chapter = chapter, startPage = repo.lastPage(k).coerceAtMost(((chapter?.pages?.size ?: 1) - 1).coerceAtLeast(0)),
            prevKey = asc.getOrNull(i - 1)?.takeIf { i > 0 }, nextKey = asc.getOrNull(i + 1)?.takeIf { i >= 0 }, loading = false,
        )
        val current = remote.firstOrNull { ChapterProviders.chapterKey(ChapterProviders.active.id, it.id) == k }
        if (m != null && current != null && prefs.current().prefetchNext) runCatching { downloads.prefetchNext(m, remote, current) }
    }

    fun goTo(k: String?) { if (k != null) { key.value = k; viewModelScope.launch { open(k) } } }

    fun onPage(page: Int) {
        val c = ui.value.chapter ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(300)
            repo.recordRead(mangaId, c.key, c.number, page, c.pages.size)
        }
    }

    fun update(transform: (ReaderSettings) -> ReaderSettings) = viewModelScope.launch { prefs.update(transform) }
    fun setTitleDirection(d: ReaderDirection?) = viewModelScope.launch { prefs.setTitleDirection(mangaId, d) }
    fun reset() = viewModelScope.launch { prefs.reset(mangaId) }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

class ReaderActions(
    val onBack: () -> Unit = {},
    val onPage: (Int) -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSettings: ((ReaderSettings) -> ReaderSettings) -> Unit = {},
    val onTitleDirection: (ReaderDirection?) -> Unit = {},
    val onReset: () -> Unit = {},
)

/**
 * The built-in reader. Clean screen by default (Madyan 3:23 AM): no status bar, no navigation bar, no app bars; a
 * tap in the middle shows the controls (title + ⚙️ on top, page slider and previous / next chapter at the bottom),
 * which hide again after 3 s. Everything is adjustable in ⚙️ and «استعادة الافتراضي» brings the defaults back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderContent(ui: ReaderUi, actions: ReaderActions, initialControls: Boolean = false, initialSheet: Boolean = false) {
    val s = ui.settings
    val chapter = ui.chapter
    val view = LocalView.current
    val context = LocalContext.current
    var controls by remember { mutableStateOf(initialControls || !s.cleanScreen) }
    var sheet by remember { mutableStateOf(initialSheet) }
    var touch by remember { mutableIntStateOf(0) }
    val showControls = controls || sheet || !s.cleanScreen
    // Immersive: hide the system bars while the screen is clean; give them back when leaving.
    DisposableEffect(showControls, s.cleanScreen) {
        val window = context.activity()?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (s.cleanScreen && !showControls) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { context.activity()?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    DisposableEffect(s.keepScreenOn) { view.keepScreenOn = s.keepScreenOn; onDispose { view.keepScreenOn = false } }
    DisposableEffect(s.brightness) {
        val w = context.activity()?.window
        w?.attributes = w?.attributes?.apply { screenBrightness = s.brightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        onDispose { w?.attributes = w?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }
    LaunchedEffect(controls, touch, s.autoHide, sheet) {
        if (controls && s.autoHide && s.cleanScreen && !sheet) { delay(ReaderSettings.AUTO_HIDE_MS); controls = false }
    }

    val pages = chapter?.pages.orEmpty()
    val direction = ui.direction
    var current by remember(chapter?.key) { mutableIntStateOf(ui.startPage) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.startPage)
    val pagerState = rememberPagerState(initialPage = ui.startPage) { pages.size.coerceAtLeast(1) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(chapter?.key, direction) {
        if (direction == ReaderDirection.VERTICAL) snapshotFlow { listState.firstVisibleItemIndex }.collect { current = it; actions.onPage(it) }
        else snapshotFlow { pagerState.currentPage }.collect { current = it; actions.onPage(it) }
    }
    val step: (Int) -> Unit = { d ->
        scope.launch {
            if (direction == ReaderDirection.VERTICAL) listState.animateScrollBy(view.height * 0.8f * d)
            else {
                val t = pagerState.currentPage + d
                if (t in 0 until pages.size) pagerState.animateScrollToPage(t) else if (t >= pages.size) actions.onNext() else actions.onPrev()
            }
        }
    }
    // Auto-scroll (off by default): smooth scroll for the vertical strip, a page every few seconds when paged.
    LaunchedEffect(s.autoScroll, s.autoScrollSpeed, direction, showControls, chapter?.key) {
        if (!s.autoScroll || showControls || pages.isEmpty()) return@LaunchedEffect
        if (direction == ReaderDirection.VERTICAL) {
            while (isActive) { androidx.compose.runtime.withFrameNanos { }; listState.scrollBy(s.autoScrollSpeed * 0.9f) }
        } else {
            while (isActive) { delay((11 - s.autoScrollSpeed) * 1_000L); step(1) }
        }
    }
    LaunchedEffect(s.volumeKeys) { if (s.volumeKeys) runCatching { focus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("reader")
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (!s.volumeKeys || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> { step(1); true }
                    AndroidKeyEvent.KEYCODE_VOLUME_UP -> { step(-1); true }
                    else -> false
                }
            }
            .pointerInput(s.tapZones, s.controlsTap, direction, pages.size) {
                detectTapGestures { o: Offset ->
                    val w = size.width
                    val zone = when {
                        s.controlsTap == ControlsTap.ANYWHERE || !s.tapZones -> 0
                        o.x < w * 0.3f -> -1
                        o.x > w * 0.7f -> 1
                        else -> 0
                    }
                    if (zone == 0) { controls = !controls; touch++ }
                    else {
                        // RTL manga: the left side is the NEXT page; LTR and vertical: the right side is next.
                        val forward = if (direction == ReaderDirection.RTL) zone == -1 else zone == 1
                        step(if (forward) 1 else -1)
                    }
                }
            },
    ) {
        when {
            ui.loading -> Unit
            chapter == null || pages.isEmpty() -> Text(stringResource(R.string.reader_no_pages), color = Palette.TextSecondary, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            direction == ReaderDirection.VERTICAL -> {
                ZoomBox(s.zoom) {
                    LazyColumn(Modifier.fillMaxSize().testTag("reader_vertical"), state = listState, verticalArrangement = Arrangement.spacedBy(s.pageGapDp.dp)) {
                        itemsIndexed(pages, key = { i, _ -> i }) { _, p -> PageImage(p, s.fit, vertical = true) }
                    }
                }
            }
            else -> HorizontalPager(
                pagerState, Modifier.fillMaxSize().testTag("reader_paged"), reverseLayout = direction == ReaderDirection.RTL,
                pageSpacing = s.pageGapDp.dp, beyondViewportPageCount = 1, key = { it },
            ) { i -> ZoomBox(s.zoom) { PageImage(pages[i], s.fit, vertical = false) } }
        }

        AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xE6000000)).statusBarsPadding().height(56.dp).testTag("reader_top"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(ui.manga?.displayTitle.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content))
                    if (chapter != null) Text(stringResource(R.string.manga_chapter_n, chapter.number) + if (chapter.offline) "  •  " + stringResource(R.string.reader_offline) else "", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { sheet = true }, modifier = Modifier.testTag("reader_settings_button")) { Icon(Icons.Filled.Settings, stringResource(R.string.reader_settings), tint = Color.White) }
            }
        }
        AnimatedVisibility(showControls && pages.isNotEmpty(), enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            // RTL manga: the slider and prev/next run right-to-left too (page 1 on the right).
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (direction == ReaderDirection.RTL) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xE6000000)).navigationBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp).testTag("reader_bottom"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onPrev, enabled = ui.prevKey != null) { Icon(Icons.Filled.SkipPrevious, stringResource(R.string.reader_prev_chapter), tint = if (ui.prevKey != null) Color.White else Palette.TextMuted) }
                Text("\u2066${current + 1}\u2069", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = current.toFloat(), onValueChange = { v ->
                        touch++
                        val t = v.toInt()
                        scope.launch { if (direction == ReaderDirection.VERTICAL) listState.scrollToItem(t) else pagerState.scrollToPage(t) }
                    },
                    valueRange = 0f..(pages.size - 1).coerceAtLeast(1).toFloat(), modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.CardHigh),
                )
                Text("\u2066${pages.size}\u2069", color = Color.White, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = actions.onNext, enabled = ui.nextKey != null) { Icon(Icons.Filled.SkipNext, stringResource(R.string.reader_next_chapter), tint = if (ui.nextKey != null) Color.White else Palette.TextMuted) }
            }
            }
        }
    }
    if (sheet) ReaderSettingsSheet(ui, actions) { sheet = false; touch++ }
}

@Composable
private fun PageImage(source: Any, fit: FitMode, vertical: Boolean) {
    val ctx = LocalContext.current
    val model = remember(source) { ImageRequest.Builder(ctx).data(source).crossfade(false).build() }
    val scale = when (fit) { FitMode.WIDTH -> ContentScale.FillWidth; FitMode.HEIGHT -> ContentScale.FillHeight; FitMode.SCREEN -> ContentScale.Fit }
    if (vertical) {
        AsyncImage(model, null, Modifier.fillMaxWidth(), contentScale = if (fit == FitMode.WIDTH) ContentScale.FillWidth else ContentScale.Fit)
    } else {
        Box(Modifier.fillMaxSize().then(if (fit == FitMode.WIDTH) Modifier.verticalScroll(rememberScrollState()) else Modifier), contentAlignment = Alignment.Center) {
            AsyncImage(model, null, if (fit == FitMode.HEIGHT) Modifier.fillMaxHeight().horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth(), contentScale = scale)
        }
    }
}

/** Pinch to zoom (1×–4×), pan when zoomed; at 1× swipes go to the pager / list as usual. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomBox(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { Box(Modifier.fillMaxSize()) { content() }; return }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, offset, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        pan = if (scale <= 1f) Offset.Zero else pan + offset
    }
    Box(
        Modifier.fillMaxSize()
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y },
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(ui: ReaderUi, actions: ReaderActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Surface) {
        ReaderSettingsPanel(ui, actions)
    }
}

/** The ⚙️ panel (also used as-is by the screenshot test). */
@Composable
fun ReaderSettingsPanel(ui: ReaderUi, actions: ReaderActions) {
    val s = ui.settings
    val set = actions.onSettings
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().testTag("reader_settings")) {
        Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Group(stringResource(R.string.reader_direction)) {
            listOf(ReaderDirection.VERTICAL to R.string.reader_dir_vertical, ReaderDirection.RTL to R.string.reader_dir_rtl, ReaderDirection.LTR to R.string.reader_dir_ltr).forEach { (d, res) ->
                SelectChip(stringResource(res), ui.direction == d, {
                    if (ui.titleDirection != null) actions.onTitleDirection(d) else set { it.copy(direction = d) }
                }, Modifier.testTag("dir_" + d.name.lowercase()))
            }
        }
        SwitchLine(stringResource(R.string.reader_this_title_only), ui.titleDirection != null, "title_only") { on -> actions.onTitleDirection(if (on) ui.direction else null) }
        Group(stringResource(R.string.reader_fit)) {
            listOf(FitMode.WIDTH to R.string.reader_fit_width, FitMode.HEIGHT to R.string.reader_fit_height, FitMode.SCREEN to R.string.reader_fit_screen).forEach { (f, res) ->
                SelectChip(stringResource(res), s.fit == f, { set { it.copy(fit = f) } })
            }
        }
        SwitchLine(stringResource(R.string.reader_zoom), s.zoom) { on -> set { it.copy(zoom = on) } }
        Divider()
        SwitchLine(stringResource(R.string.reader_brightness_auto), s.brightness == null) { on -> set { it.copy(brightness = if (on) null else 0.6f) } }
        if (s.brightness != null) LabeledSlider(stringResource(R.string.reader_brightness), s.brightness, 0.05f..1f) { v -> set { it.copy(brightness = v) } }
        LabeledSlider(stringResource(R.string.reader_page_gap) + "  \u2066" + s.pageGapDp + "dp\u2069", s.pageGapDp.toFloat(), 0f..32f, steps = 7) { v -> set { it.copy(pageGapDp = v.toInt()) } }
        Divider()
        SwitchLine(stringResource(R.string.reader_clean_screen), s.cleanScreen, "clean_screen") { on -> set { it.copy(cleanScreen = on) } }
        Group(stringResource(R.string.reader_controls_how)) {
            SelectChip(stringResource(R.string.reader_controls_center), s.controlsTap == ControlsTap.CENTER, { set { it.copy(controlsTap = ControlsTap.CENTER) } })
            SelectChip(stringResource(R.string.reader_controls_anywhere), s.controlsTap == ControlsTap.ANYWHERE, { set { it.copy(controlsTap = ControlsTap.ANYWHERE) } })
        }
        SwitchLine(stringResource(R.string.reader_auto_hide), s.autoHide) { on -> set { it.copy(autoHide = on) } }
        SwitchLine(stringResource(R.string.reader_tap_zones), s.tapZones) { on -> set { it.copy(tapZones = on) } }
        SwitchLine(stringResource(R.string.reader_volume_keys), s.volumeKeys) { on -> set { it.copy(volumeKeys = on) } }
        SwitchLine(stringResource(R.string.reader_auto_scroll), s.autoScroll) { on -> set { it.copy(autoScroll = on) } }
        if (s.autoScroll) LabeledSlider(stringResource(R.string.reader_auto_scroll_speed) + "  \u2066" + s.autoScrollSpeed + "\u2069", s.autoScrollSpeed.toFloat(), 1f..10f, steps = 8) { v -> set { it.copy(autoScrollSpeed = v.toInt()) } }
        SwitchLine(stringResource(R.string.reader_keep_on), s.keepScreenOn) { on -> set { it.copy(keepScreenOn = on) } }
        SwitchLine(stringResource(R.string.reader_prefetch), s.prefetchNext) { on -> set { it.copy(prefetchNext = on) } }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = actions.onReset, modifier = Modifier.fillMaxWidth().testTag("reader_reset")) {
            Text(stringResource(R.string.reader_reset), color = Palette.Accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(vertical = 6.dp))

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Accent, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun SwitchLine(title: String, checked: Boolean, tag: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
    }
}

@Composable
private fun LabeledSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Text(title, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.padding(top = 6.dp))
    Slider(value, onChange, valueRange = range, steps = steps, colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.CardHigh))
}

@Composable
fun ReaderScreen(onBack: () -> Unit, vm: ReaderViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    androidx.compose.runtime.key(ui.chapter?.key, ui.loading) {
    ReaderContent(
        ui,
        ReaderActions(
            onBack = onBack, onPage = vm::onPage, onPrev = { vm.goTo(ui.prevKey) }, onNext = { vm.goTo(ui.nextKey) },
            onSettings = { t -> vm.update(t) }, onTitleDirection = { vm.setTitleDirection(it) }, onReset = { vm.reset() },
        ),
    )
    }
}

@Suppress("unused") private val keepFile: Class<File> = File::class.java
@Suppress("unused") private val keepRound = RoundedCornerShape(0.dp)
@Suppress("unused") private val keepWidth = Modifier.width(0.dp).size(0.dp)
