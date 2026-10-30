package com.animeshadow.app.domain

import com.animeshadow.app.data.local.AnimeEntity

/**
 * v1.5.0 (Madyan, Oct 29): an airing show lists every announced episode; the ones not out yet are shown as
 * «لم تُعرض بعد» and can't be marked, and the show can't be «شاهدته» (completed) until it has finished airing.
 * Rating is allowed while airing and only blocked for titles that haven't started.
 */
object Airing {
    /** Episodes known to be out (markable). */
    fun released(a: AnimeEntity?, progress: Int = 0, watchedMax: Int = 0): Int {
        a ?: return 0
        return when (a.status) {
            "FINISHED", "CANCELLED" -> a.episodes ?: maxOf(progress, watchedMax)
            "NOT_YET_RELEASED" -> 0
            else -> a.latestEpisode
                // airing without a schedule on AniList: trust what is listed/what the user already marked
                ?: if (a.nextEpisode == null) (a.episodes ?: maxOf(progress, watchedMax)) else 0
        }
    }

    /** Rows to list: the announced total, else what is out plus the next announced one. */
    fun listed(a: AnimeEntity?, progress: Int = 0, watchedMax: Int = 0): Int {
        a ?: return 0
        val out = released(a, progress, watchedMax)
        val next = a.nextEpisode ?: 0
        return maxOf(a.episodes ?: 0, out, next, progress, watchedMax)
    }

    fun canComplete(a: AnimeEntity?): Boolean = a == null || a.status == "FINISHED" || a.status == "CANCELLED" ||
        (a.status == null && a.nextEpisode == null)

    fun canRate(a: AnimeEntity?): Boolean = a?.status != "NOT_YET_RELEASED"
}
