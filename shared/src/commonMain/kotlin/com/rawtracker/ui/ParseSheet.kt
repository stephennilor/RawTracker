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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawtracker.RawTrackerController
import com.rawtracker.data.ParsedFood
import com.rawtracker.design.BrutalButton
import com.rawtracker.design.BrutalTextField
import com.rawtracker.design.MonoText
import com.rawtracker.design.RawColors

@Composable
fun ParseSheet(controller: RawTrackerController) {
    val ui by controller.ui.collectAsState()
    val draft = ui.draft ?: return
    val canvas = RawColors.canvas
    val ink = RawColors.ink

    var name by remember(draft) { mutableStateOf(draft.food_name) }
    var calories by remember(draft) { mutableStateOf(draft.calories.toString()) }
    var protein by remember(draft) { mutableStateOf(draft.protein_g.toString()) }
    var carbs by remember(draft) { mutableStateOf(draft.carbs_g.toString()) }
    var fat by remember(draft) { mutableStateOf(draft.fat_g.toString()) }
    var ts by remember(draft) { mutableStateOf(ui.draftTimestamp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = { controller.dismissSheet() }),
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
                MonoText(if (ui.editingMealId != null) "EDIT ENTRY" else "REVIEW & SAVE", weight = FontWeight.Bold, size = 14.sp)
                MonoText(formatDayLabel(controller.selectedDate.collectAsState().value, controller.isViewingToday()), color = ink.copy(alpha = 0.7f), size = 13.sp)
            }
            Spacer(Modifier.height(14.dp))

            MonoText("FOOD", weight = FontWeight.Bold, size = 11.sp)
            Spacer(Modifier.height(4.dp))
            BrutalTextField(name, { name = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("CAL", calories, { calories = it }, Modifier.weight(1f))
                NumberField("PROTEIN", protein, { protein = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("CARBS", carbs, { carbs = it }, Modifier.weight(1f))
                NumberField("FAT", fat, { fat = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))

            MonoText("TIME", weight = FontWeight.Bold, size = 11.sp)
            Spacer(Modifier.height(4.dp))
            TimeField(ts) { ts = it }
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrutalButton("Cancel", { controller.dismissSheet() }, Modifier.weight(1f), filled = false)
                BrutalButton(
                    label = if (ui.editingMealId != null) "Update" else "Save",
                    onClick = {
                        controller.confirmSave(
                            ParsedFood(
                                food_name = name.ifBlank { "Food" },
                                calories = calories.toIntOrNull() ?: 0,
                                protein_g = protein.toIntOrNull() ?: 0,
                                carbs_g = carbs.toIntOrNull() ?: 0,
                                fat_g = fat.toIntOrNull() ?: 0
                            ),
                            ts
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        MonoText(label, weight = FontWeight.Bold, size = 11.sp)
        Spacer(Modifier.height(4.dp))
        BrutalTextField(
            value = value,
            onValueChange = { onChange(it.filter { c -> c.isDigit() }) },
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number
        )
    }
}
