package com.rawtracker.ui

import com.rawtracker.i18n.strings
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalTime::class)
fun formatClock(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val h = dt.hour.toString().padStart(2, '0')
    val m = dt.minute.toString().padStart(2, '0')
    return "$h:$m"
}

/** Localized "today" when [isToday], else e.g. "WED 17 JUN". */
fun formatDayLabel(date: LocalDate, isToday: Boolean): String =
    if (isToday) strings.todayLabel()
    else "${strings.weekdayAbbrev(date.dayOfWeek)} ${date.dayOfMonth} ${strings.monthAbbrev(date.month)}"
