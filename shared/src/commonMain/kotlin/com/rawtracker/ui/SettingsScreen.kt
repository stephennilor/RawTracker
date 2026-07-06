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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawtracker.RawTrackerController
import com.rawtracker.data.DuotonePrefs
import com.rawtracker.data.Goals
import com.rawtracker.design.BrutalButton
import com.rawtracker.design.BrutalIconButton
import com.rawtracker.design.BrutalTextField
import com.rawtracker.design.HsvColorPicker
import com.rawtracker.design.EditorialSectionLabel
import com.rawtracker.design.MonoText
import com.rawtracker.design.RawColors
import com.rawtracker.design.RawIcons
import com.rawtracker.design.inkBorder
import com.rawtracker.i18n.strings

private fun Color.toArgbLong(): Long = 0xFF000000L or (toArgb().toLong() and 0xFFFFFFL)

private data class Swatch(val canvas: Long, val ink: Long, val name: String)

private val PRESETS = listOf(
    Swatch(0xFFFFE9CE, 0xFF8A53FF, "Violet"),
    Swatch(0xFFFFE9CE, 0xFFFD4B38, "Coral"),
    Swatch(0xFF0E0E0E, 0xFF00FF95, "Mint"),
    Swatch(0xFFF4F1EA, 0xFF1A1A1A, "Mono"),
    Swatch(0xFF101D42, 0xFFFFD23F, "Solar")
)

@Composable
fun SettingsScreen(controller: RawTrackerController) {
    val goals by controller.goals.collectAsState()
    val duotone by controller.duotone.collectAsState()
    val apiKey by controller.apiKey.collectAsState()
    val ink = RawColors.ink

    var cal by remember(goals) { mutableStateOf(goals.calories.toString()) }
    var protein by remember(goals) { mutableStateOf(goals.protein.toString()) }
    var carbs by remember(goals) { mutableStateOf(goals.carbs.toString()) }
    var fat by remember(goals) { mutableStateOf(goals.fat.toString()) }
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoText(strings.settingsTitle, weight = FontWeight.Bold, size = 16.sp)
            BrutalIconButton(RawIcons.close, strings.back, { controller.openInput() }, boxSize = 40.dp)
        }

        EditorialSectionLabel(strings.dailyTargets)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledNumber(strings.calories, cal, { cal = it }, Modifier.weight(1f))
            LabeledNumber(strings.protein, protein, { protein = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledNumber(strings.carbs, carbs, { carbs = it }, Modifier.weight(1f))
            LabeledNumber(strings.fat, fat, { fat = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        BrutalButton(
            strings.saveTargets,
            {
                controller.saveGoals(
                    Goals(
                        calories = cal.toIntOrNull() ?: 2500,
                        protein = protein.toIntOrNull() ?: 165,
                        carbs = carbs.toIntOrNull() ?: 250,
                        fat = fat.toIntOrNull() ?: 80
                    )
                )
            },
            Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        EditorialSectionLabel(strings.duotone)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PRESETS.forEach { s ->
                val selected = s.canvas == duotone.canvas && s.ink == duotone.ink
                Box(
                    Modifier
                        .size(48.dp)
                        .background(Color(s.canvas), RoundedCornerShape(5.dp))
                        .inkBorder(if (selected) ink else Color(s.ink), if (selected) 3.dp else 2.dp)
                        .clickable { controller.saveDuotone(DuotonePrefs(s.canvas, s.ink)) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(20.dp).background(Color(s.ink), RoundedCornerShape(3.dp)))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        EditorialSectionLabel(strings.customColours)
        var inkColor by remember(duotone.ink) { mutableStateOf(Color(duotone.ink)) }
        var canvasColor by remember(duotone.canvas) { mutableStateOf(Color(duotone.canvas)) }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).background(canvasColor, RoundedCornerShape(5.dp)).inkBorder(inkColor),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(18.dp).background(inkColor, RoundedCornerShape(3.dp)))
            }
            MonoText(strings.livePreview, color = ink.copy(alpha = 0.6f), size = 11.sp)
        }
        MonoText(strings.primaryInk, weight = FontWeight.Bold, size = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        HsvColorPicker(inkColor) { inkColor = it }
        Spacer(Modifier.height(14.dp))
        MonoText(strings.secondaryCanvas, weight = FontWeight.Bold, size = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        HsvColorPicker(canvasColor) { canvasColor = it }
        Spacer(Modifier.height(12.dp))
        BrutalButton(
            strings.applyColours,
            {
                controller.saveDuotone(
                    DuotonePrefs(
                        canvas = canvasColor.toArgbLong(),
                        ink = inkColor.toArgbLong()
                    )
                )
            },
            Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        EditorialSectionLabel(strings.widget)
        MonoText(
            strings.widgetHelp,
            color = ink.copy(alpha = 0.6f),
            size = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val widgetPrefs by controller.widgetPrefs.collectAsState()
        ToggleRow(strings.calorieGoal, widgetPrefs.showGoal) {
            controller.saveWidgetPrefs(widgetPrefs.copy(showGoal = it))
        }
        ToggleRow(strings.macrosPcf, widgetPrefs.showMacros) {
            controller.saveWidgetPrefs(widgetPrefs.copy(showMacros = it))
        }
        ToggleRow(strings.foodButton, widgetPrefs.showFood) {
            controller.saveWidgetPrefs(widgetPrefs.copy(showFood = it))
        }
        ToggleRow(strings.waterButton, widgetPrefs.showWater) {
            controller.saveWidgetPrefs(widgetPrefs.copy(showWater = it))
        }

        Spacer(Modifier.height(24.dp))
        EditorialSectionLabel(strings.healthSync)
        val ui by controller.ui.collectAsState()
        MonoText(
            if (ui.healthConnected) strings.healthConnectedHelp
            else strings.healthWriteOnlyHelp,
            color = ink.copy(alpha = 0.6f),
            size = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        BrutalButton(
            if (ui.healthConnected) strings.healthConnected else strings.connectHealth,
            { controller.connectHealth() },
            Modifier.fillMaxWidth(),
            filled = ui.healthConnected
        )
        Spacer(Modifier.height(8.dp))
        MonoText(
            strings.healthResyncHelp,
            color = ink.copy(alpha = 0.6f),
            size = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrutalButton(strings.resyncToday, { controller.resyncHealthToday() }, Modifier.weight(1f), filled = false)
            BrutalButton(strings.resyncAll, { controller.resyncHealthAll() }, Modifier.weight(1f), filled = false)
        }

        Spacer(Modifier.height(24.dp))
        EditorialSectionLabel(strings.geminiApiKey)
        MonoText(
            if (apiKey.isBlank()) strings.builtInKeyHelp
            else strings.customKeyActive(apiKey.takeLast(4)),
            color = ink.copy(alpha = 0.6f),
            size = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        BrutalTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "AIza... or AQ...",
            keyboardType = KeyboardType.Password
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrutalButton(strings.saveKey, { controller.saveApiKey(keyInput) }, Modifier.weight(1f))
            BrutalButton(
                strings.clear,
                { keyInput = ""; controller.saveApiKey("") },
                Modifier.weight(1f),
                filled = false
            )
        }
        MonoText(
            strings.getFreeKeyHelp,
            color = ink.copy(alpha = 0.6f),
            size = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(24.dp))
        EditorialSectionLabel(strings.data)
        BrutalButton(strings.exportCsv, { controller.exportCsv() }, Modifier.fillMaxWidth(), filled = false)

        Spacer(Modifier.height(24.dp))
        MonoText(
            "${strings.build} ${controller.appVersion}",
            color = ink.copy(alpha = 0.5f),
            size = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ink = RawColors.ink
    val canvas = RawColors.canvas
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonoText(label, weight = FontWeight.Medium, size = 14.sp)
        Box(
            Modifier
                .size(26.dp)
                .background(if (checked) ink else canvas, RoundedCornerShape(5.dp))
                .inkBorder(ink),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                MonoText("x", color = canvas, weight = FontWeight.Bold, size = 15.sp)
            }
        }
    }
}

@Composable
private fun LabeledNumber(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
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
