package com.rawtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.rawtracker.design.EditorialHeroNumber
import com.rawtracker.design.MonoText
import com.rawtracker.design.RawColors
import com.rawtracker.design.RawIcons
import com.rawtracker.design.inkBorder
import com.rawtracker.i18n.strings
import com.rawtracker.design.ParsingOverlay
import kotlinx.coroutines.delay

@Composable
fun InputScreen(controller: RawTrackerController) {
    val ui by controller.ui.collectAsState()
    val totals by controller.totals.collectAsState()
    val waterTotal by controller.waterTotal.collectAsState()
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

    val picker = rememberFoodPicker(onImages = { controller.attachImages(it) })
    val composerFocus = remember { FocusRequester() }
    val dictation = rememberDictationLauncher(
        onResult = {
            controller.appendDictation(it)
            runCatching { composerFocus.requestFocus() }
        },
        onError = controller::showMessage
    )

    val cameraRequest by controller.cameraRequest.collectAsState()
    LaunchedEffect(cameraRequest) {
        if (cameraRequest > 0) picker.launchCamera()
    }

    val galleryRequest by controller.galleryRequest.collectAsState()
    LaunchedEffect(galleryRequest) {
        if (galleryRequest > 0) picker.launchGallery()
    }

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
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoText(strings.rawtracker, weight = FontWeight.Bold, size = 13.sp)
                if (ui.pendingCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .background(ink, RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        MonoText(
                            strings.queued(ui.pendingCount),
                            color = RawColors.canvas,
                            weight = FontWeight.Bold,
                            size = 10.sp
                        )
                    }
                }
            }
            BrutalIconButton(
                icon = RawIcons.settings,
                contentDescription = strings.settings,
                onClick = { controller.openSettings() },
                boxSize = 40.dp
            )
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
            fat = totals.fat to goals.fat,
            water = waterTotal to goals.waterMl,
            onWaterClick = { controller.openWaterSheet() }
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (history.isEmpty()) {
                items(listOf(Unit)) {
                    MonoText(
                        strings.nothingLoggedToday,
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

        Column(
            // safeDrawing's bottom is max(navigationBars, ime), so it rests on the nav bar when
            // the keyboard is closed and sits flush on the keyboard when open. Do NOT also apply
            // imePadding() here — that double-counts the keyboard height and leaves a giant gap.
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
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

            if (ui.attachedImages.isNotEmpty()) {
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
                            RawIcons.gallery, contentDescription = null,
                            tint = RawColors.canvas, modifier = Modifier.size(20.dp)
                        )
                    }
                    MonoText(strings.photosAttached(ui.attachedImages.size), size = 13.sp)
                    BrutalIconButton(RawIcons.close, strings.removePhoto, { controller.clearAttachment() }, boxSize = 32.dp)
                }
            }

            InputBar(
                value = ui.input,
                onValueChange = controller::onInputChange,
                onAdd = { controller.openAddChooser() },
                onDictate = { dictation.launch() },
                onSend = doSend,
                isParsing = ui.isParsing,
                focusRequester = composerFocus
            )
        }
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
                onGallery = { controller.chooseGallery() },
                onWater = {
                    controller.dismissAddChooser()
                    controller.openWaterSheet()
                },
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
        contains("isn't valid", ignoreCase = true) ||
        contains("could not", ignoreCase = true) ||
        contains("couldn't", ignoreCase = true) ||
        contains("Can't reach", ignoreCase = true) ||
        contains("garbled", ignoreCase = true) ||
        contains("rate limit", ignoreCase = true) ||
        contains("busy", ignoreCase = true) ||
        contains("rejected", ignoreCase = true) ||
        contains("Add your Gemini", ignoreCase = true) ||
        contains("nie powiod", ignoreCase = true) ||
        contains("nie mog", ignoreCase = true) ||
        contains("odrzuci", ignoreCase = true) ||
        contains("niedost", ignoreCase = true) ||
        contains("sprawd", ignoreCase = true) ||
        contains("limit", ignoreCase = true) ||
        contains("zaj", ignoreCase = true)

/** Bottom sheet presented when "+ FOOD" is tapped on the widget: pick how to add food. */
@Composable
private fun AddFoodChooser(
    onDescribe: () -> Unit,
    onPhoto: () -> Unit,
    onGallery: () -> Unit,
    onWater: () -> Unit,
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
            MonoText(strings.add.uppercase(), weight = FontWeight.Bold, size = 14.sp)
            Spacer(Modifier.height(14.dp))
            BrutalButton(strings.describe, onDescribe, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            BrutalButton(strings.camera, onPhoto, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(10.dp))
            BrutalButton(strings.pickPhotos, onGallery, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(10.dp))
            BrutalButton(strings.logWater, onWater, Modifier.fillMaxWidth(), filled = false)
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
        BrutalIconButton(RawIcons.caretLeft, strings.previousDay, onPrev, boxSize = 40.dp)
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
        BrutalIconButton(RawIcons.caretRight, strings.nextDay, onNext, boxSize = 40.dp)
    }
}

@Composable
private fun MacroHeader(
    calories: Pair<Int, Int>,
    protein: Pair<Int, Int>,
    carbs: Pair<Int, Int>,
    fat: Pair<Int, Int>,
    water: Pair<Int, Int>,
    onWaterClick: () -> Unit
) {
    val ink = RawColors.ink
    Column {
        MonoText(strings.kcal, weight = FontWeight.Bold, size = 11.sp)
        EditorialHeroNumber(text = calories.first.toString())
        MonoText("/ ${calories.second}", color = ink.copy(alpha = 0.68f), size = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MacroPill(strings.proteinShort, protein, Modifier.weight(1f))
            MacroPill(strings.carbsShort, carbs, Modifier.weight(1f))
            MacroPill(strings.fatShort, fat, Modifier.weight(1f))
            MacroPill(strings.waterShort, water, Modifier.weight(1f), unit = "ml", onClick = onWaterClick)
        }
    }
}

@Composable
private fun MacroPill(
    label: String,
    value: Pair<Int, Int>,
    modifier: Modifier = Modifier,
    unit: String = "",
    onClick: (() -> Unit)? = null
) {
    val ink = RawColors.ink
    val tileModifier = modifier
        .inkBorder(ink)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(8.dp)
    Column(modifier = tileModifier) {
        MonoText(label, weight = FontWeight.Bold, size = 10.sp)
        val displayValue = value.first.toString()
        androidx.compose.material3.Text(
            text = displayValue,
            color = ink,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                scaleX = when (displayValue.length) {
                    1, 2 -> 1.08f
                    3 -> 0.98f
                    4 -> 0.86f
                    else -> 0.74f
                }
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
        )
        MonoText(
            if (unit.isBlank()) "/${value.second}" else "/${value.second} $unit",
            color = ink.copy(alpha = 0.68f),
            size = 10.sp
        )
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
                strings.mealMacros(meal.calories, meal.proteinG, meal.carbsG, meal.fatG),
                color = ink.copy(alpha = 0.75f),
                size = 12.sp
            )
        }
        BrutalIconButton(RawIcons.delete, strings.delete, onDelete, boxSize = 32.dp)
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
        MonoText(strings.waterLogged(water.milliliters).removePrefix("+"), weight = FontWeight.Medium, size = 14.sp, modifier = Modifier.weight(1f))
        BrutalIconButton(RawIcons.delete, strings.delete, onDelete, boxSize = 32.dp)
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
    onAdd: () -> Unit,
    onDictate: () -> Unit,
    onSend: () -> Unit,
    isParsing: Boolean,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val composerHeight = 52.dp
        val composerMaxHeight = 132.dp
        BrutalIconButton(RawIcons.plus, strings.add, onAdd, boxSize = composerHeight)
        BrutalTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = strings.foodPlaceholder,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            minHeight = composerHeight,
            maxHeight = composerMaxHeight,
            singleLine = false,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            imeAction = ImeAction.Default,
            onImeAction = { if (!isParsing) onSend() },
            focusRequester = focusRequester
        )
        BrutalIconButton(
            icon = RawIcons.microphone,
            contentDescription = strings.dictate,
            onClick = onDictate,
            boxSize = composerHeight
        )
        BrutalIconButton(
            icon = RawIcons.send,
            contentDescription = strings.send,
            onClick = { if (!isParsing) onSend() },
            filled = true,
            boxSize = composerHeight
        )
    }
}
