package com.animeshadow.app.ui.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animeshadow.app.BuildConfig
import com.animeshadow.app.R
import com.animeshadow.app.ui.theme.Palette
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v1.2 ads: one small banner (OFF unless real AdMob ids were set at build time, v1.5.0) (320×50) under the list screens only. No interstitials, pop-ups, video or
 * sound. The SDK is initialised lazily on a background thread a moment after the first screen is drawn (manifest flags
 * OPTIMIZE_INITIALIZATION / OPTIMIZE_AD_LOADING keep Google's own start-up work off the main thread too), so start-up
 * speed is untouched. Until it is ready the slot stays empty black at its final height, so nothing jumps.
 */
object Ads {
    /** Real ids present at build time (see app/build.gradle.kts). False = the ads SDK is never touched. */
    val enabled: Boolean get() = BuildConfig.ADS_ENABLED && BuildConfig.ADS_BANNER_UNIT.isNotBlank()

    private val started = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    fun init(context: Context, delayMs: Long = 1_500) {
        // Robolectric (unit/screenshot tests) has no Play services: never start the SDK there.
        if (!enabled || android.os.Build.FINGERPRINT == "robolectric" || !started.compareAndSet(false, true)) return
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            delay(delayMs)
            runCatching {
                MobileAds.initialize(app) { _ready.value = true }
            }.onFailure { started.set(false) }
        }
    }
}

/** Banner slot height: AdSize.BANNER is 320×50 dp. */
val AdSlotHeight = 50.dp

/** Screenshot / unit tests swap the real AdView for a static strip (no SDK, no network). */
val LocalAdPreview = staticCompositionLocalOf { false }

/**
 * v1.5.0: whether the banner slot exists at all. BuildConfig.ADS_ENABLED is true only when real AdMob ids were given at
 * build time (ADMOB_APP_ID + ADMOB_BANNER_ID); otherwise the slot renders nothing — no ad, no empty gap. Tests override it.
 */
val LocalAdsEnabled = staticCompositionLocalOf { Ads.enabled }

@Composable
fun ListAdBanner(modifier: Modifier = Modifier) {
    if (!LocalAdsEnabled.current) return
    Box(
        modifier.fillMaxWidth().background(Palette.Black).navigationBarsPadding().height(AdSlotHeight).testTag("ad_banner"),
        contentAlignment = Alignment.Center,
    ) {
        if (LocalAdPreview.current) {
            TestAdStrip()
        } else {
            val ready by Ads.ready.collectAsStateWithLifecycle()
            if (ready) RealBanner()
        }
    }
}

@Composable
private fun RealBanner() {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADS_BANNER_UNIT
            loadAd(AdRequest.Builder().build())
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, adView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            adView.destroy()
        }
    }
    AndroidView({ adView }, Modifier.width(320.dp).height(AdSlotHeight))
}

/** What the test banner occupies, drawn statically for screenshots. */
@Composable
private fun TestAdStrip() {
    Box(
        Modifier.width(320.dp).height(AdSlotHeight).background(Color(0xFF1B1B1F)).border(1.dp, Color(0xFF2E2E36)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Test Ad 320×50 · " + stringResource(R.string.ad_label), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A9AA6))
    }
}
