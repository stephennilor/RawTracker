package com.rawtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawtracker.RawTrackerController
import com.rawtracker.design.BrutalButton
import com.rawtracker.design.BrutalIconButton
import com.rawtracker.design.MonoText
import com.rawtracker.design.RawColors
import com.rawtracker.design.RawIcons
import com.rawtracker.design.inkBorder
import com.rawtracker.i18n.strings
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalTime::class)
@Composable
fun CalendarOverlay(controller: RawTrackerController, onDismiss: () -> Unit) {
    val canvas = RawColors.canvas
    val ink = RawColors.ink
    val selected by controller.selectedDate.collectAsState()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }

    // Month being displayed in the grid (1st of that month).
    var monthAnchor by remember { mutableStateOf(LocalDate(selected.year, selected.monthNumber, 1)) }

    val firstDow = monthAnchor.dayOfWeek.isoDayNumber // Mon=1..Sun=7
    val leadingBlanks = firstDow - 1
    val daysInMonth = monthAnchor.daysUntil(monthAnchor.plus(DatePeriod(months = 1)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .background(canvas, RoundedCornerShape(6.dp))
                .inkBorder(ink, 3.dp)
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrutalIconButton(RawIcons.caretLeft, strings.previousMonth, {
                    monthAnchor = monthAnchor.minus(DatePeriod(months = 1))
                }, boxSize = 38.dp)
                MonoText("${strings.monthFull(monthAnchor.month)} ${monthAnchor.year}", weight = FontWeight.Bold, size = 14.sp)
                BrutalIconButton(RawIcons.caretRight, strings.nextMonth, {
                    monthAnchor = monthAnchor.plus(DatePeriod(months = 1))
                }, boxSize = 38.dp)
            }
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                strings.weekdayInitials().forEach { d ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        MonoText(d, color = ink.copy(alpha = 0.6f), weight = FontWeight.Bold, size = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            val cells = leadingBlanks + daysInMonth
            val rows = (cells + 6) / 7
            var dayCounter = 1
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val index = r * 7 + c
                        if (index < leadingBlanks || dayCounter > daysInMonth) {
                            Box(Modifier.weight(1f).aspectRatio(1f)) {}
                        } else {
                            val day = dayCounter
                            val date = LocalDate(monthAnchor.year, monthAnchor.monthNumber, day)
                            val isSelected = date == selected
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .background(if (isSelected) ink else canvas, RoundedCornerShape(5.dp))
                                    .inkBorder(ink, if (isToday) 2.dp else 1.dp)
                                    .clickable {
                                        controller.selectDate(date)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                MonoText(
                                    day.toString(),
                                    color = if (isSelected) canvas else ink,
                                    weight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    size = 13.sp
                                )
                            }
                            dayCounter++
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(10.dp))
            BrutalButton(strings.jumpToday, {
                controller.goToday()
                onDismiss()
            }, Modifier.fillMaxWidth(), filled = false)
        }
    }
}
