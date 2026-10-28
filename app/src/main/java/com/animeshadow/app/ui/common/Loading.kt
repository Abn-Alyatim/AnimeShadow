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
 * The Shadow loader (res/raw/shadow_loop.webp: 528×360 animated WebP, 110 frames at 30 fps, a seamless 3.7 s loop).
 * v1.3.1: it is cut from the SAME clip as the splash/login video (Madyan's 1.3 video), with the same pure-black,
 * feathered edges — 1.3.0 still played the old v1.2 clip here, which is the «old video» seen when switching to Manga
 * (Manga's first screen loads from the network, so the loader is the first thing it shows).
 * The still frame (shadow_still = the loop's first frame) is ALWAYS drawn underneath, so the loader can never be an
 * empty black box: not while the WebP decodes, not if decoding fails, not on Android 8.0/8.1 (no
 * AnimatedImageDrawable). The animation is (re)started on the main thread each time its view is attached.
 * An animated WebP loops with no gap or black flash, and Android animates it on the render thread.
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
    Box(modifier.aspectRatio(SHADOW_CLIP_ASPECT).background(Palette.Black).testTag("shadow_loop")) {
        Image(painterResource(R.drawable.shadow_still), null, Modifier.fillMaxSize().testTag("shadow_still"), contentScale = ContentScale.Fit)
        val d = drawable
        if (d != null) AndroidView(
            factory = { c ->
                ImageView(c).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) { startLoop((v as ImageView).drawable) }
                        override fun onViewDetachedFromWindow(v: android.view.View) = Unit
                    })
                }
            },
            modifier = Modifier.fillMaxSize().testTag("shadow_loop_anim"),
            update = { v -> if (v.drawable !== d) v.setImageDrawable(d); startLoop(d) },
        )
    }
}

private fun startLoop(d: Drawable?) {
    if (Build.VERSION.SDK_INT >= 28 && d is AnimatedImageDrawable && !d.isRunning) {
        d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        d.start()
    }
}

private suspend fun decodeLoop(ctx: Context): Drawable? =
    if (Build.VERSION.SDK_INT >= 28) decodeLoop28(ctx) else null

@RequiresApi(28)
private suspend fun decodeLoop28(ctx: Context): Drawable? = withContext(Dispatchers.IO) {
    runCatching {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.resources, R.raw.shadow_loop)).also { d ->
            if (d is AnimatedImageDrawable) d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
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
