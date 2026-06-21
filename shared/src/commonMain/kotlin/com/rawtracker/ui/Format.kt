package com.rawtracker.ui

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
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

private fun monthAbbrev(m: Month): String = when (m) {
    Month.JANUARY -> "JAN"; Month.FEBRUARY -> "FEB"; Month.MARCH -> "MAR"
    Month.APRIL -> "APR"; Month.MAY -> "MAY"; Month.JUNE -> "JUN"
    Month.JULY -> "JUL"; Month.AUGUST -> "AUG"; Month.SEPTEMBER -> "SEP"
    Month.OCTOBER -> "OCT"; Month.NOVEMBER -> "NOV"; Month.DECEMBER -> "DEC"
    else -> m.name.take(3)
}

fun monthFull(m: Month): String = when (m) {
    Month.JANUARY -> "JANUARY"; Month.FEBRUARY -> "FEBRUARY"; Month.MARCH -> "MARCH"
    Month.APRIL -> "APRIL"; Month.MAY -> "MAY"; Month.JUNE -> "JUNE"
    Month.JULY -> "JULY"; Month.AUGUST -> "AUGUST"; Month.SEPTEMBER -> "SEPTEMBER"
    Month.OCTOBER -> "OCTOBER"; Month.NOVEMBER -> "NOVEMBER"; Month.DECEMBER -> "DECEMBER"
    else -> m.name
}

fun weekdayAbbrev(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"; DayOfWeek.WEDNESDAY -> "WED"
    DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"; DayOfWeek.SATURDAY -> "SAT"
    DayOfWeek.SUNDAY -> "SUN"
    else -> d.name.take(3)
}

/** "TODAY" when [isToday], else e.g. "WED 17 JUN". */
fun formatDayLabel(date: LocalDate, isToday: Boolean): String =
    if (isToday) "TODAY"
    else "${weekdayAbbrev(date.dayOfWeek)} ${date.dayOfMonth} ${monthAbbrev(date.month)}"
