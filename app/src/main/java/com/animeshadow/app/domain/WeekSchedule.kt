package com.animeshadow.app.domain

import com.animeshadow.app.data.local.ScheduleRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Groups expected airings by local weekday, Saturday first (as the Arabic week is shown). */
object WeekSchedule {
    val ORDER = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    fun group(rows: List<ScheduleRow>, zone: ZoneId): Map<DayOfWeek, List<ScheduleRow>> {
        val byDay = rows.groupBy { Instant.ofEpochMilli(it.airAt).atZone(zone).dayOfWeek }
        return ORDER.associateWith { day ->
            byDay[day].orEmpty().sortedBy { it.airAt }.distinctBy { it.anime.id }
        }
    }

    fun dayOf(epochMs: Long, zone: ZoneId): DayOfWeek = Instant.ofEpochMilli(epochMs).atZone(zone).dayOfWeek
}
