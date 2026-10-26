package com.animeshadow.app.ui.common

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.animeshadow.app.R
import com.animeshadow.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v1.2: false draws the Shadow loop's still frame instead of the animation (screenshot tests, where frames must
 * not depend on timing). The app always animates.
 */
val LocalAnimatedLoader = staticCompositionLocalOf { true }

/**
 * Madyan's 3.4 s Shadow loop (res/raw/shadow_loop.webp: 640×360 animated WebP, 103 frames at 30 fps, ~490 KB,
 * re-encoded from his shadow_loop3.mp4 at 37–44 dB PSNR; the clip's edges are feathered into pure #000 so no box shows
 * on the black screen). An animated WebP loops with no gap or black flash (a video player seeks back to 0, which
 * can stutter), and Android animates it on the render thread, so it stays smooth even while the main thread is busy.
 * Android 8.0/8.1 (no AnimatedImageDrawable) show the still frame.
 */
@Composable
fun ShadowLoop(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val animated = LocalAnimatedLoader.current && Build.VERSION.SDK_INT >= 28
    var drawable by remember { mutableStateOf<Drawable?>(null) }
    if (animated) {
        LaunchedEffect(Unit) { drawable = decodeLoop(ctx) }
        DisposableEffect(Unit) { onDispose { (drawable as? AnimatedImageDrawable)?.stop(); drawable = null } }
    }
    Box(modifier.aspectRatio(16f / 9f).background(Palette.Black).testTag("shadow_loop")) {
        val d = drawable
        when {
            d != null -> AndroidView(
                factory = { c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                modifier = Modifier.fillMaxSize(),
                update = { v -> if (v.drawable !== d) v.setImageDrawable(d) },
            )
            // Decoding takes a few ms; black (the clip's own background) until then, so there is no jump.
            !animated -> Image(painterResource(R.drawable.shadow_still), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

private suspend fun decodeLoop(ctx: Context): Drawable? =
    if (Build.VERSION.SDK_INT >= 28) decodeLoop28(ctx) else null

@RequiresApi(28)
private suspend fun decodeLoop28(ctx: Context): Drawable? = withContext(Dispatchers.IO) {
    runCatching {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.resources, R.raw.shadow_loop)).also { d ->
            if (d is AnimatedImageDrawable) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                d.start()
            }
        }
    }.getOrNull()
}

/** Full-area loading state: the small Shadow loop on black (replaces the v1.1 spinner). */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    ScrollableCenter(modifier.testTag("loading")) { ShadowLoop(Modifier.width(184.dp)) }
}

/**
 * Centred content that still scrolls vertically, so pull-to-refresh also works on empty / loading / error screens
 * (a pull needs a scrollable child: v1.1's EmptyState was not, so pulling an empty list did nothing).
 */
@Composable
fun ScrollableCenter(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().heightIn(min = h), contentAlignment = Alignment.Center, content = content)
        }
    }
}

/**
 * v1.2 pull-to-refresh for every list screen (v1.1 had none at all, which is why pulling did nothing).
 * [refreshing] is the screen's real network state, so the indicator stays until the new data is in Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().testTag("pull_refresh"),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).testTag(if (refreshing) "refresh_indicator_on" else "refresh_indicator"),
                containerColor = Palette.Chip,
                color = Palette.Accent,
            )
        },
        content = content,
    )
}
