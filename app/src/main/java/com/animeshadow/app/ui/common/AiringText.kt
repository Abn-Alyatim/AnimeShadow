package com.animeshadow.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.animeshadow.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** v1.5.0: «الجمعة 18:30» for the next episode of an airing show ([epochMs] = AniList airingAt × 1000). */
@Composable
fun airingWhen(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(zone)
    val day = stringResource(dayRes(t.dayOfWeek))
    return stringResource(R.string.ep_airs_on, day, t.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)))
}
