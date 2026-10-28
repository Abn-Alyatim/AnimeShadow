package com.animeshadow.app.ui.ads

import android.app.Activity
import android.content.Context
import com.animeshadow.app.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * v1.4.0: ONE interstitial at most every [EVERY] screen changes and never more often than every [MIN_GAP_MS], only on
 * ordinary browsing screens. Never on the splash, login, the manga reader or the chat (callers simply don't count
 * those). Unit id from BuildConfig (Google's TEST unit in this build; the real one is a Gradle property, see ADMOB_AR.md).
 */
object Interstitials {
    const val EVERY = 6
    const val MIN_GAP_MS = 3 * 60_000L

    /** Routes where a full-screen ad must never appear. */
    private val NEVER = listOf("splash", "login", "reader", "chat", "settings", "edit_profile")

    private var count = 0
    private var lastShown = 0L
    private var ad: InterstitialAd? = null
    private var loading = false

    /** Pure decision (unit-tested): is this screen change the one that may show an ad? */
    fun shouldShow(route: String?, screensSinceLast: Int, now: Long, lastShownAt: Long): Boolean {
        if (isBlocked(route)) return false
        return screensSinceLast >= EVERY && now - lastShownAt >= MIN_GAP_MS
    }

    fun isBlocked(route: String?): Boolean = route == null || NEVER.any { route.contains(it, ignoreCase = true) }

    private fun enabled() = BuildConfig.ADS_ENABLED && android.os.Build.FINGERPRINT != "robolectric"

    fun preload(context: Context) {
        if (!enabled() || ad != null || loading) return
        loading = true
        runCatching {
            InterstitialAd.load(context.applicationContext, BuildConfig.ADS_INTERSTITIAL_UNIT, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(a: InterstitialAd) { ad = a; loading = false }
                override fun onAdFailedToLoad(e: LoadAdError) { ad = null; loading = false }
            })
        }.onFailure { loading = false }
    }

    /** Called on every screen change of the main navigation. */
    fun onScreen(activity: Activity?, route: String?) {
        if (!enabled() || activity == null || isBlocked(route)) return
        count++
        val now = System.currentTimeMillis()
        if (count >= EVERY - 1) preload(activity)
        if (!shouldShow(route, count, now, lastShown)) return
        val a = ad ?: return
        ad = null
        count = 0
        lastShown = now
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { preload(activity) }
        }
        runCatching { a.show(activity) }
    }
}
