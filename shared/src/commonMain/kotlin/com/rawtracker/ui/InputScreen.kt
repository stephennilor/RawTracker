package com.rawtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawtracker.RawTrackerController
import com.rawtracker.data.HistoryItem
import com.rawtracker.data.Meal
import com.rawtracker.data.WaterLog
import com.rawtracker.design.BrutalButton
import com.rawtracker.design.BrutalIconButton
import com.rawtracker.design.BrutalTextField
import com.rawtracker.design.MonoText
import com.rawtracker.design.RawColors
import com.rawtracker.design.RawIcons
import com.rawtracker.design.inkBorder
import com.rawtracker.design.ParsingOverlay
import kotlinx.coroutines.delay

@Composable
fun InputScreen(controller: RawTrackerController) {
    val ui by controller.ui.collectAsState()
    val totals by controller.totals.collectAsState()
    val goals by controller.goals.collectAsState()
    val history by controller.history.collectAsState()
    val selectedDate by controller.selectedDate.collectAsState()
    val ink = RawColors.ink
    val focusManager = LocalFocusManager.current

    var showCalendar by remember { mutableStateOf(false) }

    // Clearing focus also dismisses the soft keyboard, so the review sheet / feedback
    // toast aren't left hidden behind it after a send.
    val doSend: () -> Unit = {
        focusManager.clearFocus(force = true)
        controller.submit()
    }

    val picker = rememberFoodPicker(onBytes = { controller.attachImage(it) })

    val cameraRequest by controller.cameraRequest.collectAsState()
    LaunchedEffect(cameraRequest) {
        if (cameraRequest > 0) picker.launchCamera()
    }

    val composerFocus = remember { FocusRequester() }
    val focusSignal by controller.focusInput.collectAsState()
    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) runCatching { composerFocus.requestFocus() }
    }

    LaunchedEffect(ui.message) {
        if (ui.message != null) {
            delay(2600)
            controller.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoText("RAWTRACKER", weight = FontWeight.Bold, size = 13.sp)
                if (ui.pendingCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .background(ink, RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        MonoText(
                            "${ui.pendingCount} queued",
                            color = RawColors.canvas,
                            weight = FontWeight.Bold,
                            size = 10.sp
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BrutalIconButton(
                    icon = RawIcons.water,
                    contentDescription = "Log water",
                    onClick = { controller.openWaterSheet() },
                    boxSize = 40.dp
                )
                BrutalIconButton(
                    icon = RawIcons.settings,
                    contentDescription = "Settings",
                    onClick = { controller.openSettings() },
                    boxSize = 40.dp
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DateNav(
            label = formatDayLabel(selectedDate, controller.isViewingToday()),
            onPrev = { controller.previousDay() },
            onNext = { controller.nextDay() },
            onPickDate = { showCalendar = true }
        )
        Spacer(Modifier.height(10.dp))
        MacroHeader(
            calories = totals.calories to goals.calories,
            protein = totals.protein to goals.protein,
            carbs = totals.carbs to goals.carbs,
            fat = totals.fat to goals.fat
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (history.isEmpty()) {
                items(listOf(Unit)) {
                    MonoText(
                        "// nothing logged today",
                        color = ink.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(
                history,
                key = { item ->
                    when (item) {
                        is HistoryItem.MealEntry -> "m${item.meal.id}"
                        is HistoryItem.WaterEntry -> "w${item.water.id}"
                    }
                }
            ) { item ->
                when (item) {
                    is HistoryItem.MealEntry ->
                        MealRow(
                            item.meal,
                            onEdit = { controller.editMeal(item.meal) },
                            onDelete = { controller.deleteMeal(item.meal.id) },
                            onTimeChange = { controller.updateMealTime(item.meal.id, it) }
                        )
                    is HistoryItem.WaterEntry ->
                        WaterRow(
                            item.water,
                            onDelete = { controller.deleteWater(item.water.id) },
                            onTimeChange = { controller.updateWaterTime(item.water.id, it) }
                        )
                }
            }
        }

        ui.message?.let { msg ->
            MonoText(
                text = msg,
                color = if (msg.isErrorMessage()) {
                    Color(0xFFFD4B38)
                } else ink,
                weight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        if (ui.attachedImage != null) {
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(40.dp).background(ink, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        RawIcons.camera, contentDescription = null,
                        tint = RawColors.canvas, modifier = Modifier.size(20.dp)
                    )
                }
                MonoText("photo attached", size = 13.sp)
                BrutalIconButton(RawIcons.close, "Remove photo", { controller.clearAttachment() }, boxSize = 32.dp)
            }
        }

        InputBar(
            value = ui.input,
            onValueChange = controller::onInputChange,
            onCamera = { picker.launchCamera() },
            onGallery = { picker.launchGallery() },
            onSend = doSend,
            isParsing = ui.isParsing,
            focusRequester = composerFocus
        )
        Spacer(Modifier.height(8.dp))
    }

        if (ui.isParsing) {
            ParsingOverlay(onCancel = { controller.cancelParse() })
        }
        if (showCalendar) {
            CalendarOverlay(controller, onDismiss = { showCalendar = false })
        }
        if (ui.showAddChooser) {
            AddFoodChooser(
                onDescribe = { controller.chooseDescribe() },
                onPhoto = { controller.choosePhoto() },
                onPhotoAndDescribe = { controller.choosePhotoAndDescribe() },
                onDismiss = { controller.dismissAddChooser() }
            )
        }
    }
}

private fun String.isErrorMessage(): Boolean =
    contains("fail", ignoreCase = true) ||
        contains("offline", ignoreCase = true) ||
        contains("Gemini", ignoreCase = true) ||
        contains("too long", ignoreCase = true) ||
        contains("not valid", ignoreCase = true) ||
        contains("could not", ignoreCase = true)

/** Bottom sheet presented when "+ FOOD" is tapped on the widget: pick how to add food. */
@Composable
private fun AddFoodChooser(
    onDescribe: () -> Unit,
    onPhoto: () -> Unit,
    onPhotoAndDescribe: () -> Unit,
    onDismiss: () -> Unit
) {
    val canvas = RawColors.canvas
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(canvas, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            MonoText("ADD FOOD", weight = FontWeight.Bold, size = 14.sp)
            Spacer(Modifier.height(14.dp))
            BrutalButton("Describe", onDescribe, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            BrutalButton("Photo", onPhoto, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(10.dp))
            BrutalButton("Photo + describe", onPhotoAndDescribe, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DateNav(label: String, onPrev: () -> Unit, onNext: () -> Unit, onPickDate: () -> Unit) {
    val ink = RawColors.ink
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrutalIconButton(RawIcons.caretLeft, "Previous day", onPrev, boxSize = 40.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .inkBorder(ink)
                .clickable(onClick = onPickDate)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                RawIcons.calendar, contentDescription = null,
                tint = ink, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            MonoText(label, weight = FontWeight.Bold, size = 14.sp)
        }
        BrutalIconButton(RawIcons.caretRight, "Next day", onNext, boxSize = 40.dp)
    }
}

@Composable
private fun MacroHeader(
    calories: Pair<Int, Int>,
    protein: Pair<Int, Int>,
    carbs: Pair<Int, Int>,
    fat: Pair<Int, Int>
) {
    val ink = RawColors.ink
    Column {
        MonoText("CALORIES", weight = FontWeight.Bold, size = 12.sp)
        androidx.compose.material3.Text(
            text = calories.first.toString(),
            color = ink,
            style = androidx.compose.material3.MaterialTheme.typography.displayLarge
        )
        MonoText("/ ${calories.second} kcal", color = ink.copy(alpha = 0.7f), size = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MacroPill("PROTEIN", protein, Modifier.weight(1f))
            MacroPill("CARBS", carbs, Modifier.weight(1f))
            MacroPill("FAT", fat, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroPill(label: String, value: Pair<Int, Int>, modifier: Modifier = Modifier) {
    val ink = RawColors.ink
    Column(modifier = modifier.inkBorder(ink).padding(10.dp)) {
        MonoText(label, weight = FontWeight.Bold, size = 11.sp)
        androidx.compose.material3.Text(
            text = "${value.first}",
            color = ink,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )
        MonoText("/ ${value.second}g", color = ink.copy(alpha = 0.7f), size = 11.sp)
    }
}

@Composable
private fun MealRow(meal: Meal, onEdit: () -> Unit, onDelete: () -> Unit, onTimeChange: (Long) -> Unit) {
    val ink = RawColors.ink
    Row(
        modifier = Modifier.fillMaxWidth().inkBorder(ink).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeChip(meal.eatenAt, onTimeChange)
        Spacer(Modifier.width(10.dp))
        // Tapping the meal body opens the edit sheet; the time chip and delete stay independent.
        Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
            MonoText(meal.foodName, weight = FontWeight.Medium, size = 14.sp)
            MonoText(
                "${meal.calories}kcal  P${meal.proteinG} C${meal.carbsG} F${meal.fatG}",
                color = ink.copy(alpha = 0.75f),
                size = 12.sp
            )
        }
        BrutalIconButton(RawIcons.delete, "Delete", onDelete, boxSize = 32.dp)
    }
}

@Composable
private fun WaterRow(water: WaterLog, onDelete: () -> Unit, onTimeChange: (Long) -> Unit) {
    val ink = RawColors.ink
    Row(
        modifier = Modifier.fillMaxWidth().inkBorder(ink).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeChip(water.loggedAt, onTimeChange)
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Icon(
            RawIcons.water, contentDescription = null,
            tint = ink, modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        MonoText("${water.milliliters} ml water", weight = FontWeight.Medium, size = 14.sp, modifier = Modifier.weight(1f))
        BrutalIconButton(RawIcons.delete, "Delete", onDelete, boxSize = 32.dp)
    }
}

/** Tappable time label — tap to open the native time picker and edit the entry's time. */
@Composable
private fun TimeChip(epochMs: Long, onTimeChange: (Long) -> Unit) {
    val p = TimeUtil.parts(epochMs)
    val picker = rememberSystemTimePicker { h, m -> onTimeChange(TimeUtil.withTime(epochMs, h, m)) }
    MonoText(
        formatClock(epochMs),
        weight = FontWeight.Bold,
        size = 13.sp,
        modifier = Modifier.clickable { picker.show(p.hour, p.minute) }.padding(vertical = 2.dp)
    )
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSend: () -> Unit,
    isParsing: Boolean,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BrutalIconButton(RawIcons.camera, "Camera", onCamera, boxSize = 48.dp)
        BrutalIconButton(RawIcons.gallery, "Pick photo", onGallery, boxSize = 48.dp)
        BrutalTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "describe your food...",
            modifier = Modifier.weight(1f),
            imeAction = ImeAction.Send,
            onImeAction = { if (!isParsing) onSend() },
            focusRequester = focusRequester
        )
        BrutalIconButton(
            icon = RawIcons.send,
            contentDescription = "Send",
            onClick = { if (!isParsing) onSend() },
            filled = true,
            boxSize = 52.dp
        )
    }
}
