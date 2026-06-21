package com.rawtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawtracker.RawTrackerController
import com.rawtracker.design.BrutalButton
import com.rawtracker.design.BrutalTextField
import com.rawtracker.design.MonoText
import com.rawtracker.design.RawColors
import com.rawtracker.design.inkBorder

private val WATER_PRESETS = listOf(250, 500, 750)

@Composable
fun WaterSheet(controller: RawTrackerController) {
    val canvas = RawColors.canvas
    val ink = RawColors.ink
    val total by controller.waterTotal.collectAsState()
    val focusManager = LocalFocusManager.current
    var custom by remember { mutableStateOf("") }
    var ts by remember { mutableStateOf(controller.newEntryTimestamp()) }
    val addCustom: () -> Unit = {
        custom.toIntOrNull()?.let {
            focusManager.clearFocus(force = true)
            controller.logWaterAt(it, ts)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = { controller.closeWaterSheet() }),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(canvas, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoText("ADD WATER", weight = FontWeight.Bold, size = 14.sp)
                MonoText("today: $total ml", color = ink.copy(alpha = 0.7f), size = 13.sp)
            }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WATER_PRESETS.forEach { ml ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .inkBorder(ink)
                            .clickable { controller.logWaterAt(ml, ts) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MonoText("$ml ml", weight = FontWeight.Bold, size = 15.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            MonoText("TIME", weight = FontWeight.Bold, size = 11.sp)
            Spacer(Modifier.height(4.dp))
            TimeField(ts) { ts = it }
            Spacer(Modifier.height(16.dp))

            MonoText("CUSTOM (ml)", weight = FontWeight.Bold, size = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrutalTextField(
                    value = custom,
                    onValueChange = { custom = it.filter { c -> c.isDigit() }.take(5) },
                    modifier = Modifier.weight(1f),
                    placeholder = "e.g. 330",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onImeAction = addCustom
                )
                BrutalButton("Add", addCustom, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
