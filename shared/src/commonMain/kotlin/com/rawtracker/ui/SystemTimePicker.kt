package com.rawtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawtracker.design.MonoText
import com.rawtracker.design.RawColors
import com.rawtracker.design.RawIcons
import com.rawtracker.design.inkBorder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Launches the host platform's native time picker (Android clock dialog / iOS wheels). */
interface SystemTimePicker {
    fun show(hour: Int, minute: Int)
}

@Composable
expect fun rememberSystemTimePicker(onTimePicked: (hour: Int, minute: Int) -> Unit): SystemTimePicker

@OptIn(ExperimentalTime::class)
object TimeUtil {
    private val tz get() = TimeZone.currentSystemDefault()

    fun parts(epochMs: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)

    /** Returns a new epoch on the same calendar day as [epochMs] but at [hour]:[minute]. */
    fun withTime(epochMs: Long, hour: Int, minute: Int): Long {
        val d = parts(epochMs)
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        return LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, h, m, 0)
            .toInstant(tz).toEpochMilliseconds()
    }
}

/**
 * A brutalist time chip. Tapping it opens the native system time picker; the chosen
 * time is applied to [epochMs]'s calendar day and reported via [onChange].
 */
@Composable
fun TimeField(epochMs: Long, modifier: Modifier = Modifier, onChange: (Long) -> Unit) {
    val ink = RawColors.ink
    val parts = TimeUtil.parts(epochMs)
    val picker = rememberSystemTimePicker { h, m -> onChange(TimeUtil.withTime(epochMs, h, m)) }
    Row(
        modifier = modifier
            .inkBorder(ink)
            .clickable { picker.show(parts.hour, parts.minute) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(RawIcons.clock, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        MonoText(formatClock(epochMs), weight = FontWeight.Bold, size = 16.sp)
    }
}
