package com.animeshadow.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animeshadow.app.R
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.ui.theme.Palette
import com.animeshadow.app.ui.theme.RefType

@Composable
fun Poster(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null, placeholderColor: String? = null) {
    val bg = remember(placeholderColor) {
        placeholderColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)).copy(alpha = 0.25f) }.getOrNull() } ?: Palette.Card
    }
    Box(modifier.background(bg)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val TightLine = androidx.compose.ui.text.style.LineHeightStyle(
    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
    androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
)

@Composable
fun ScoreLabel(score100: Int?, modifier: Modifier = Modifier, prefix: String? = null, mal: Double? = null) {
    // Ref 05 shows MyAnimeList-style scores ("8.52"); use the MAL score when we have it, else AniList's /10.
    val s = mal?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: scoreText(score100)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Ref 05 (360 dp phone): «مستمر ★ 8.52», 12 sp light grey, 6 dp gaps, orange star.
        if (prefix != null) {
            Text(prefix, style = MaterialTheme.typography.bodySmall.copy(fontSize = RefType.Small, lineHeight = 16.sp, lineHeightStyle = TightLine), color = Color(0xFFCDCDCE), maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        Text("★", color = Color(0xFFEC9A33), fontSize = 10.sp, lineHeight = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(s ?: "—", style = MaterialTheme.typography.bodySmall.copy(fontSize = RefType.Small, lineHeight = 16.sp, lineHeightStyle = TightLine), color = Color(0xFFCACACA), maxLines = 1)
    }
}

/** Badge drawn over the bottom of a poster ("Episode : 3"). */
@Composable
fun PosterBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000))))
            .padding(start = 6.dp, end = 9.dp, top = 4.dp, bottom = 9.dp),
        // Ref 05: the «الحلقة : N» badge sits at the right of the poster's bottom edge.
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontSize = RefType.Small, lineHeight = 16.sp), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun CornerBadge(text: String, modifier: Modifier = Modifier, color: Color = Palette.AccentDeep) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** Poster card for 3-column grids: poster (2:3), title, "status ★ score". */
@Composable
fun AnimeGridCard(
    anime: AnimeEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBadge: String? = null,
    cornerBadge: String? = null,
    subtitle: String? = null,
) {
    Column(modifier.clickable(onClick = onClick)) {
        // Ref 05 (360 dp phone): 2:3 poster, the title 13 dp under it and 7 dp in, 14 sp bold grey; the score line under it.
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(2.dp))) {
            Poster(anime.gridPoster, Modifier.fillMaxSize(), anime.displayTitle, anime.color)
            if (bottomBadge != null) PosterBadge(bottomBadge, Modifier.align(Alignment.BottomCenter))
            if (cornerBadge != null) CornerBadge(cornerBadge, Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            anime.displayTitle,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = RefType.Body, lineHeight = 18.sp, fontWeight = FontWeight.Bold, lineHeightStyle = TightLine),
            color = Color(0xFFCCCCCC),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 7.dp, end = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        ScoreLabel(anime.score, Modifier.padding(start = 7.dp), prefix = subtitle ?: label(statusRes(anime.status), anime.status), mal = anime.malScore)
    }
}

/** List-mode row: poster at the start, title, accent episode line, "type ★ score". */
@Composable
fun AnimeListRow(anime: AnimeEntity, onClick: () -> Unit, modifier: Modifier = Modifier, accentLine: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(anime.gridPoster, Modifier.width(64.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)), anime.displayTitle, anime.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(anime.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (accentLine != null) Text(accentLine, style = MaterialTheme.typography.bodySmall, color = Palette.Accent, maxLines = 1)
            ScoreLabel(anime.score, prefix = listOfNotNull(label(formatRes(anime.format), anime.format).ifBlank { null }, anime.seasonYear?.toString()).joinToString(" • "))
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun episodeBadge(anime: AnimeEntity): String? =
    if (anime.format == "MOVIE") null else anime.latestEpisode?.let { stringResource(R.string.episode_n, it) }

/**
 * Poster grid (3 columns) or list (1 column) with stable keys and an end-of-list callback for paging.
 */
@Composable
fun AnimeGrid(
    items: List<AnimeEntity>,
    grid: Boolean,
    onClick: (AnimeEntity) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, top = 1.dp, bottom = 10.dp),
    onEndReached: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    header: (LazyGridScope.() -> Unit)? = null,
    bottomBadge: @Composable (AnimeEntity) -> String? = { episodeBadge(it) },
    cornerBadge: (AnimeEntity) -> String? = { null },
    listAccent: @Composable (AnimeEntity) -> String? = { episodeBadge(it) },
) {
    if (onEndReached != null) {
        val shouldLoad by remember(state, items.size) {
            derivedStateOf {
                val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                items.isNotEmpty() && last >= state.layoutInfo.totalItemsCount - 7
            }
        }
        LaunchedEffect(shouldLoad, items.size) { if (shouldLoad) onEndReached() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 3 else 1),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 14.5.dp else 4.dp),
    ) {
        header?.invoke(this)
        items(items, key = { it.id }, contentType = { if (grid) 1 else 2 }) { a ->
            if (grid) {
                AnimeGridCard(a, { onClick(a) }, bottomBadge = bottomBadge(a), cornerBadge = cornerBadge(a))
            } else {
                AnimeListRow(a, { onClick(a) }, accentLine = listAccent(a))
            }
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        // Ref 05: the title starts ~78 dp from the left edge (after the drawer/back icon), medium-bold.
        title = { Text(title, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content), color = Color(0xFFF5F5F5), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp)) },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Palette.RefIcon)
                }
                onMenu != null -> IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.cd_menu), tint = Palette.RefIcon) }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Black, scrolledContainerColor = Palette.Black),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = Palette.Accent) }
        }
    }
}

@Composable
fun StatusBanner(text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = Palette.Card,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Palette.Divider),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, null, tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = Palette.Accent) }
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Accent else Palette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = if (selected) Color.White else Palette.TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

@Composable
fun RoundImage(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Poster(url, modifier.size(size).clip(CircleShape))
}
