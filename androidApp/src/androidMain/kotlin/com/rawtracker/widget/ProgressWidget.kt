package com.rawtracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider

internal val Gap = 8.dp
private val StickerRadius = 8.dp
private val BorderWidth = 3.dp

class ProgressWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        val addIntent = widgetLaunchIntent(context, "rawtracker://add")
        val waterIntent = widgetLaunchIntent(context, "rawtracker://water")
        val openIntent = widgetLaunchIntent(context)
        provideContent { ProgressContent(context, data, addIntent, waterIntent, openIntent) }
    }
}

class ProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}

private data class WidgetPalette(
    val canvasArgb: Int,
    val inkArgb: Int,
    val canvas: ColorProvider,
    val ink: ColorProvider,
    val inkDim: ColorProvider,
)

private fun Dp.fraction(ratio: Float): Dp = (value * ratio).dp

private fun paletteFrom(data: WidgetTotals) = WidgetPalette(
    canvasArgb = data.canvas.toInt(),
    inkArgb = data.ink.toInt(),
    canvas = ColorProvider(Color(data.canvas)),
    ink = ColorProvider(Color(data.ink)),
    inkDim = ColorProvider(Color(data.ink).copy(alpha = 0.68f)),
)

private fun cols(w: Int) = when {
    w < 120 -> 1
    w < 200 -> 2
    w < 280 -> 3
    else -> 4
}

private fun rows(h: Int) = when {
    h < 120 -> 1
    h < 200 -> 2
    h < 280 -> 3
    else -> 4
}

@androidx.compose.runtime.Composable
internal fun ProgressContent(
    context: Context,
    data: WidgetTotals,
    addIntent: Intent,
    waterIntent: Intent,
    openIntent: Intent,
) {
    val size = LocalSize.current
    val w = size.width.value
    val h = size.height.value
    val palette = paletteFrom(data)
    val c = cols(w.toInt())
    val r = rows(h.toInt())

    Box(modifier = GlanceModifier.fillMaxSize().background(palette.canvas).padding(Gap)) {
        when {
            r == 1 && c == 1 -> Layout1x1(context, w, h, data, palette, openIntent)
            r == 1 -> LayoutStrip(context, w, h, data, palette, addIntent, waterIntent, openIntent)
            r == 2 && c == 1 -> Layout1x2(context, w, h, data, palette, addIntent, waterIntent, openIntent)
            r == 2 && c == 2 -> Layout2x2(context, w, h, data, palette, addIntent, waterIntent, openIntent)
            r == 2 && c >= 3 -> LayoutWidex2(context, w, h, data, palette, addIntent, waterIntent, openIntent)
            r >= 3 && c == 1 -> Layout1x3Plus(context, w, h, data, palette, addIntent, waterIntent, openIntent)
            r >= 3 && c >= 2 -> LayoutWidex3Plus(context, w, h, data, palette, addIntent, waterIntent, openIntent)
            else -> Layout1x1(context, w, h, data, palette, openIntent)
        }
    }
}

@androidx.compose.runtime.Composable
private fun Layout1x1(context: Context, w: Float, h: Float, data: WidgetTotals, palette: WidgetPalette, openIntent: Intent) {
    HeroAndMacrosTile(
        context,
        tileWidthDp(w, 1, 0),
        tileHeightDp(h, 1, 0),
        data,
        palette,
        openIntent,
        GlanceModifier.fillMaxSize(),
    )
}

@androidx.compose.runtime.Composable
private fun LayoutStrip(context: Context, w: Float, h: Float, data: WidgetTotals, palette: WidgetPalette, addIntent: Intent, waterIntent: Intent, openIntent: Intent) {
    val actionCount = (if (data.showFood) 1 else 0) + (if (data.showWater) 1 else 0)
    val cells = 1 + actionCount
    val gaps = (cells - 1).coerceAtLeast(0)
    val cellW = tileWidthDp(w, cells, gaps)
    val cellH = tileHeightDp(h, 1, 0)

    Row(modifier = GlanceModifier.fillMaxSize()) {
        HeroAndMacrosTile(context, cellW, cellH, data, palette, openIntent, GlanceModifier.defaultWeight().fillMaxHeight())
        if (data.showFood) {
            Spacer(GlanceModifier.width(Gap))
            ActionTile(context, cellW, cellH, water = false, palette, addIntent, GlanceModifier.defaultWeight().fillMaxHeight())
        }
        if (data.showWater) {
            Spacer(GlanceModifier.width(Gap))
            ActionTile(context, cellW, cellH, water = true, palette, waterIntent, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@androidx.compose.runtime.Composable
private fun Layout1x2(context: Context, w: Float, h: Float, data: WidgetTotals, palette: WidgetPalette, addIntent: Intent, waterIntent: Intent, openIntent: Intent) {
    val actionCount = (if (data.showFood) 1 else 0) + (if (data.showWater) 1 else 0)
    val cells = 1 + actionCount
    val gaps = (cells - 1).coerceAtLeast(0)
    val cellW = tileWidthDp(w, 1, 0)
    val cellH = tileHeightDp(h, cells, gaps)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeroAndMacrosTile(context, cellW, cellH, data, palette, openIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        if (data.showFood) {
            Spacer(GlanceModifier.height(Gap))
            ActionTile(context, cellW, cellH, water = false, palette, addIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        }
        if (data.showWater) {
            Spacer(GlanceModifier.height(Gap))
            ActionTile(context, cellW, cellH, water = true, palette, waterIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        }
    }
}

@androidx.compose.runtime.Composable
private fun Layout2x2(context: Context, w: Float, h: Float, data: WidgetTotals, palette: WidgetPalette, addIntent: Intent, waterIntent: Intent, openIntent: Intent) {
    val actionCells = (if (data.showFood) 1 else 0) + (if (data.showWater) 1 else 0)
    if (actionCells == 0) {
        HeroAndMacrosTile(context, tileWidthDp(w, 1, 0), tileHeightDp(h, 1, 0), data, palette, openIntent, GlanceModifier.fillMaxSize())
        return
    }

    val cellW = tileWidthDp(w, 1, 0)
    val cellH = tileHeightDp(h, 2, 1)
    val actionW = tileWidthDp(w, actionCells, actionCells - 1)
    val actionH = tileHeightDp(h, 2, 1)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeroAndMacrosTile(context, cellW, cellH, data, palette, openIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        Spacer(GlanceModifier.height(Gap))
        Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            if (data.showFood) ActionTile(context, actionW, actionH, water = false, palette, addIntent, GlanceModifier.defaultWeight().fillMaxHeight())
            if (data.showFood && data.showWater) Spacer(GlanceModifier.width(Gap))
            if (data.showWater) ActionTile(context, actionW, actionH, water = true, palette, waterIntent, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@androidx.compose.runtime.Composable
private fun LayoutWidex2(context: Context, w: Float, h: Float, data: WidgetTotals, palette: WidgetPalette, addIntent: Intent, waterIntent: Intent, openIntent: Intent) {
    val topCells = if (data.showMacros) 4 else 1
    val topGaps = (topCells - 1).coerceAtLeast(0)
    val topCellW = tileWidthDp(w, topCells, topGaps)
    val actionCells = (if (data.showFood) 1 else 0) + (if (data.showWater) 1 else 0)
    val rowCount = 1 + if (actionCells > 0) 1 else 0
    val rowGaps = (rowCount - 1).coerceAtLeast(0)
    val topCellH = tileHeightDp(h, rowCount, rowGaps)
    val actionW = tileWidthDp(w, actionCells.coerceAtLeast(1), (actionCells - 1).coerceAtLeast(0))
    val actionH = tileHeightDp(h, rowCount, rowGaps)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            HeroTile(context, topCellW, topCellH, data, palette, openIntent, GlanceModifier.defaultWeight().fillMaxHeight())
            if (data.showMacros) {
                Spacer(GlanceModifier.width(Gap))
                MacroTile(context, "P", data.protein, data.goalProtein, topCellW, topCellH, palette, openIntent, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(Gap))
                MacroTile(context, "C", data.carbs, data.goalCarbs, topCellW, topCellH, palette, openIntent, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(Gap))
                MacroTile(context, "F", data.fat, data.goalFat, topCellW, topCellH, palette, openIntent, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
        if (actionCells > 0) {
            Spacer(GlanceModifier.height(Gap))
            Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                if (data.showFood) ActionTile(context, actionW, actionH, water = false, palette, addIntent, GlanceModifier.defaultWeight().fillMaxHeight())
                if (data.showFood && data.showWater) Spacer(GlanceModifier.width(Gap))
                if (data.showWater) ActionTile(context, actionW, actionH, water = true, palette, waterIntent, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Layout1x3Plus(context: Context, w: Float, h: Float, data: WidgetTotals, palette: WidgetPalette, addIntent: Intent, waterIntent: Intent, openIntent: Intent) {
    val actionCount = (if (data.showFood) 1 else 0) + (if (data.showWater) 1 else 0)
    val macroRow = if (data.showMacros) 1 else 0
    val cells = 1 + macroRow + actionCount
    val gaps = (cells - 1).coerceAtLeast(0)
    val cellW = tileWidthDp(w, 1, 0)
    val cellH = tileHeightDp(h, cells, gaps)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeroTile(context, cellW, cellH, data, palette, openIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        if (data.showMacros) {
            Spacer(GlanceModifier.height(Gap))
            MacroRow(context, w, cellH, data, palette, openIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        }
        if (data.showFood) {
            Spacer(GlanceModifier.height(Gap))
            ActionTile(context, cellW, cellH, water = false, palette, addIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        }
        if (data.showWater) {
            Spacer(GlanceModifier.height(Gap))
            ActionTile(context, cellW, cellH, water = true, palette, waterIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        }
    }
}

@androidx.compose.runtime.Composable
private fun LayoutWidex3Plus(context: Context, w: Float, h: Float, data: WidgetTotals, palette: WidgetPalette, addIntent: Intent, waterIntent: Intent, openIntent: Intent) {
    val cellW = tileWidthDp(w, 1, 0)
    val actionCells = (if (data.showFood) 1 else 0) + (if (data.showWater) 1 else 0)
    val macroRow = if (data.showMacros) 1 else 0
    val actionRow = if (actionCells > 0) 1 else 0
    val rows = 1 + macroRow + actionRow
    val rowH = tileHeightDp(h, rows, (rows - 1).coerceAtLeast(0))
    val actionW = tileWidthDp(w, actionCells.coerceAtLeast(1), (actionCells - 1).coerceAtLeast(0))

    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeroTile(context, cellW, rowH, data, palette, openIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        if (data.showMacros) {
            Spacer(GlanceModifier.height(Gap))
            MacroRow(context, w, rowH, data, palette, openIntent, GlanceModifier.defaultWeight().fillMaxWidth())
        }
        if (actionCells > 0) {
            Spacer(GlanceModifier.height(Gap))
            Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                if (data.showFood) ActionTile(context, actionW, rowH, water = false, palette, addIntent, GlanceModifier.defaultWeight().fillMaxHeight())
                if (data.showFood && data.showWater) Spacer(GlanceModifier.width(Gap))
                if (data.showWater) ActionTile(context, actionW, rowH, water = true, palette, waterIntent, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun GridTile(
    palette: WidgetPalette,
    modifier: GlanceModifier,
    inverted: Boolean,
    intent: Intent? = null,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    val bg = if (inverted) palette.ink else palette.canvas
    var mod = modifier.background(palette.ink).cornerRadius(StickerRadius).padding(BorderWidth)
    if (intent != null) mod = mod.clickable(actionStartActivity(intent))

    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Box(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(StickerRadius - BorderWidth),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@androidx.compose.runtime.Composable
private fun DataTile(
    palette: WidgetPalette,
    modifier: GlanceModifier,
    intent: Intent,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(palette.canvas)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@androidx.compose.runtime.Composable
private fun HeroAndMacrosTile(
    context: Context,
    width: Dp,
    height: Dp,
    data: WidgetTotals,
    palette: WidgetPalette,
    intent: Intent,
    modifier: GlanceModifier,
) {
    val innerW = width
    val innerH = height
    val macroH = if (data.showMacros) innerH.fraction(if (data.showGoal) 0.30f else 0.38f) else 0.dp
    val calH = innerH - macroH

    DataTile(palette, modifier, intent) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeroValueImage(
                context = context,
                value = data.cal,
                goal = data.goalCal,
                ink = Color(palette.inkArgb),
                inkDim = Color(palette.inkArgb).copy(alpha = 0.68f),
                width = innerW,
                height = calH,
                showBar = data.showGoal,
                modifier = GlanceModifier.fillMaxWidth().height(calH),
            )
            if (data.showMacros) {
                MacroStripImage(
                    context = context,
                    data = data,
                    ink = Color(palette.inkArgb),
                    inkDim = Color(palette.inkArgb).copy(alpha = 0.68f),
                    width = innerW,
                    height = macroH,
                    modifier = GlanceModifier.fillMaxWidth().height(macroH),
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HeroTile(
    context: Context,
    width: Dp,
    height: Dp,
    data: WidgetTotals,
    palette: WidgetPalette,
    intent: Intent,
    modifier: GlanceModifier,
) {
    val innerW = width
    val innerH = height

    DataTile(palette, modifier, intent) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeroValueImage(
                context = context,
                value = data.cal,
                goal = data.goalCal,
                ink = Color(palette.inkArgb),
                inkDim = Color(palette.inkArgb).copy(alpha = 0.68f),
                width = innerW,
                height = innerH,
                showBar = data.showGoal,
                modifier = GlanceModifier.fillMaxWidth().height(innerH),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun MacroTile(
    context: Context,
    label: String,
    value: Int,
    goal: Int,
    width: Dp,
    height: Dp,
    palette: WidgetPalette,
    intent: Intent,
    modifier: GlanceModifier,
) {
    MacroCellImage(
        context = context,
        label = label,
        value = value,
        goal = goal,
        ink = Color(palette.inkArgb),
        inkDim = Color(palette.inkArgb).copy(alpha = 0.68f),
        width = width,
        height = height,
        modifier = modifier.clickable(actionStartActivity(intent)),
    )
}

@androidx.compose.runtime.Composable
private fun MacroRow(context: Context, widgetW: Float, height: Dp, data: WidgetTotals, palette: WidgetPalette, intent: Intent, modifier: GlanceModifier) {
    val stripW = tileWidthDp(widgetW, 1, 0)
    if (stripW.value < 96f) {
        MacroStackImage(
            context = context,
            data = data,
            ink = Color(palette.inkArgb),
            inkDim = Color(palette.inkArgb).copy(alpha = 0.68f),
            width = stripW,
            height = height,
            modifier = modifier.fillMaxWidth().height(height).clickable(actionStartActivity(intent)),
        )
    } else {
        MacroStripImage(
            context = context,
            data = data,
            ink = Color(palette.inkArgb),
            inkDim = Color(palette.inkArgb).copy(alpha = 0.68f),
            width = stripW,
            height = height,
            modifier = modifier.fillMaxWidth().height(height).clickable(actionStartActivity(intent)),
        )
    }
}

@androidx.compose.runtime.Composable
private fun ActionTile(
    context: Context,
    width: Dp,
    height: Dp,
    water: Boolean,
    palette: WidgetPalette,
    intent: Intent,
    modifier: GlanceModifier,
) {
    ActionStickerImage(
        context = context,
        water = water,
        useIcon = useActionIcons(width, height),
        canvas = Color(palette.canvasArgb),
        ink = Color(palette.inkArgb),
        width = width,
        height = height,
        modifier = modifier.clickable(actionStartActivity(intent)),
    )
}
