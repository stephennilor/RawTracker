package com.rawtracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.rawtracker.R
import com.rawtracker.design.RawIcons

internal enum class WidgetFont { Display, Mono }

private object WidgetFonts {
    private var display: Typeface? = null
    private var mono: Typeface? = null

    fun display(context: Context): Typeface =
        display ?: ResourcesCompat.getFont(context, R.font.fredoka)!!.also { display = it }

    fun mono(context: Context): Typeface =
        mono ?: ResourcesCompat.getFont(context, R.font.jetbrains_mono)!!.also { mono = it }
}

private fun WidgetFont.typeface(context: Context): Typeface {
    val base = when (this) {
        WidgetFont.Display -> WidgetFonts.display(context)
        WidgetFont.Mono -> WidgetFonts.mono(context)
    }
    val weight = when (this) {
        WidgetFont.Display -> 900
        WidgetFont.Mono -> 500
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, weight, false)
    } else {
        Typeface.create(base, Typeface.BOLD)
    }
}

private fun WidgetFont.variationSettings(): String = when (this) {
    WidgetFont.Display -> "'wght' 700, 'wdth' 120"
    WidgetFont.Mono -> "'wght' 500"
}

private fun withAlpha(color: Int, alpha: Float): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    return color and 0x00FFFFFF or (a shl 24)
}

private fun drawSoilpunkGrain(canvas: Canvas, width: Int, height: Int, ink: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(ink, 0.055f)
    }
    val step = 5.5f
    var row = 0
    var y = 0f
    while (y < height) {
        val stagger = if (row % 2 == 0) 0f else step * 0.5f
        var x = stagger
        while (x < width) {
            canvas.drawCircle(x, y, 0.75f, paint)
            x += step
        }
        y += step
        row++
    }
}

private fun applySoilpunkFinish(bitmap: Bitmap, ink: Int) {
    drawSoilpunkGrain(Canvas(bitmap), bitmap.width, bitmap.height, ink)
}

private fun progressRatio(value: Int, goal: Int): Float =
    if (goal <= 0) 0f else (value.toFloat() / goal.toFloat()).coerceIn(0f, 1f)

private fun drawProgressBar(canvas: Canvas, rect: RectF, value: Int, goal: Int, ink: Int, inkDim: Int) {
    if (goal <= 0 || rect.width() <= 1f || rect.height() <= 1f) return
    val radius = rect.height() * 0.48f
    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(inkDim, 0.52f)
        style = Paint.Style.FILL
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(rect, radius, radius, track)
    val fillRight = rect.left + rect.width() * progressRatio(value, goal)
    if (fillRight > rect.left + 1f) {
        canvas.drawRoundRect(RectF(rect.left, rect.top, fillRight, rect.bottom), radius, radius, fill)
    }
}

private fun drawFilledStretchedText(
    canvas: Canvas,
    text: String,
    rect: RectF,
    typeface: Typeface,
    variationSettings: String,
    ink: Int,
    inkDim: Int,
    progress: Float,
    heightRatio: Float,
    minScaleX: Float,
    maxScaleX: Float,
    strokeRatio: Float,
    fillFromBottom: Boolean = true,
) {
    drawStretchedText(
        canvas = canvas,
        text = text,
        rect = rect,
        typeface = typeface,
        variationSettings = variationSettings,
        color = inkDim,
        heightRatio = heightRatio,
        minScaleX = minScaleX,
        maxScaleX = maxScaleX,
        ghost = false,
        fakeBold = true,
        strokeRatio = strokeRatio,
    )
    val ratio = progress.coerceIn(0f, 1f)
    if (ratio <= 0f) return
    canvas.save()
    if (fillFromBottom) {
        val top = rect.bottom - rect.height() * ratio
        canvas.clipRect(rect.left, top, rect.right, rect.bottom)
    } else {
        val right = rect.left + rect.width() * ratio
        canvas.clipRect(rect.left, rect.top, right, rect.bottom)
    }
    drawStretchedText(
        canvas = canvas,
        text = text,
        rect = rect,
        typeface = typeface,
        variationSettings = variationSettings,
        color = ink,
        heightRatio = heightRatio,
        minScaleX = minScaleX,
        maxScaleX = maxScaleX,
        ghost = false,
        fakeBold = true,
        strokeRatio = strokeRatio,
    )
    canvas.restore()
}

private fun drawStretchedText(
    canvas: Canvas,
    text: String,
    rect: RectF,
    typeface: Typeface,
    variationSettings: String,
    color: Int,
    heightRatio: Float,
    minScaleX: Float = 0.35f,
    maxScaleX: Float = 5.5f,
    ghost: Boolean = false,
    fakeBold: Boolean = false,
    strokeRatio: Float = 0f,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        this.color = color
        textAlign = Paint.Align.LEFT
        isFakeBoldText = fakeBold
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fontVariationSettings = variationSettings
        }
    }
    val bounds = Rect()
    var textSize = rect.height() * heightRatio
    paint.textSize = textSize
    paint.getTextBounds(text, 0, text.length, bounds)
    while (textSize > 6f && bounds.height() > rect.height()) {
        textSize *= 0.92f
        paint.textSize = textSize
        paint.getTextBounds(text, 0, text.length, bounds)
    }
    paint.style = if (strokeRatio > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
    paint.strokeWidth = if (strokeRatio > 0f) (textSize * strokeRatio).coerceIn(1.1f, 5.5f) else 0f
    paint.strokeJoin = Paint.Join.ROUND

    val naturalWidth = paint.measureText(text).coerceAtLeast(1f)
    val fitScaleX = rect.width() / naturalWidth
    val scaleX = if (fitScaleX < minScaleX) fitScaleX else fitScaleX.coerceIn(minScaleX, maxScaleX)
    val scaledWidth = naturalWidth * scaleX
    val baseY = rect.top + (rect.height() - bounds.height()) / 2f - bounds.top
    val startX = rect.left + (rect.width() - scaledWidth) / 2f
    if (ghost) {
        canvas.save()
        canvas.translate(startX + 1.5f, baseY + 1f)
        canvas.scale(scaleX, 1f)
        paint.color = withAlpha(color, 0.14f)
        canvas.drawText(text, 0f, 0f, paint)
        canvas.restore()
        paint.color = color
    }
    canvas.save()
    canvas.translate(startX, baseY)
    canvas.scale(scaleX, 1f)
    canvas.drawText(text, 0f, 0f, paint)
    canvas.restore()
}

private fun drawVerticalStretchedText(
    canvas: Canvas,
    text: String,
    rect: RectF,
    typeface: Typeface,
    variationSettings: String,
    color: Int,
    clockwise: Boolean,
) {
    canvas.save()
    if (clockwise) {
        canvas.translate(rect.right, rect.top)
        canvas.rotate(90f)
    } else {
        canvas.translate(rect.left, rect.bottom)
        canvas.rotate(-90f)
    }
    drawStretchedText(
        canvas = canvas,
        text = text,
        rect = RectF(0f, 0f, rect.height(), rect.width()),
        typeface = typeface,
        variationSettings = variationSettings,
        color = color,
        heightRatio = 0.66f,
        minScaleX = 0.16f,
        maxScaleX = 1.08f,
    )
    canvas.restore()
}

internal fun renderStretchedText(
    context: Context,
    text: String,
    widthPx: Int,
    heightPx: Int,
    color: Int,
    font: WidgetFont,
    padPx: Int = 2,
): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val availW = (w - padPx * 2).coerceAtLeast(1)
    val availH = (h - padPx * 2).coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawStretchedText(
        canvas = canvas,
        text = text,
        rect = RectF(
            padPx.toFloat(),
            padPx.toFloat(),
            (padPx + availW).toFloat(),
            (padPx + availH).toFloat()
        ),
        typeface = font.typeface(context),
        variationSettings = font.variationSettings(),
        color = color,
        heightRatio = if (font == WidgetFont.Display) 0.9f else 0.78f,
        minScaleX = if (font == WidgetFont.Display) 0.14f else 0.65f,
        maxScaleX = if (font == WidgetFont.Display) 1.65f else 1.18f,
        ghost = font == WidgetFont.Display,
        fakeBold = font == WidgetFont.Display,
        strokeRatio = if (font == WidgetFont.Display) 0.026f else 0f,
    )
    applySoilpunkFinish(bitmap, color)
    return bitmap
}

internal fun renderHeroValue(
    context: Context,
    value: Int,
    goal: Int,
    kcalLabel: String,
    widthPx: Int,
    heightPx: Int,
    ink: Int,
    inkDim: Int,
    showBar: Boolean,
): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pad = (minOf(w, h) * 0.04f).coerceIn(2f, 10f)
    val showTarget = showBar && goal > 0
    val useVerticalLabels = showTarget && h >= 220 && w >= 320
    val textBottom = h - pad
    val sideW = if (useVerticalLabels) (w * 0.12f).coerceIn(12f, 32f) else 0f
    val mono = WidgetFont.Mono.typeface(context)
    val heroText = value.toString()
    val narrowLongValue = heroText.length >= 4 && w.toFloat() / h.toFloat().coerceAtLeast(1f) < 0.72f
    val headerH = if (showTarget && !useVerticalLabels) (h * 0.18f).coerceIn(11f, 26f) else 0f
    val textTop = if (headerH > 0f) pad + headerH * 0.82f else pad
    drawFilledStretchedText(
        canvas = canvas,
        text = heroText,
        rect = RectF(pad + sideW * 0.25f, textTop, w - pad - sideW * 0.25f, textBottom.coerceAtLeast(textTop + 1f)),
        typeface = WidgetFont.Display.typeface(context),
        variationSettings = WidgetFont.Display.variationSettings(),
        ink = ink,
        inkDim = if (showTarget) inkDim else ink,
        progress = if (showTarget) progressRatio(value, goal) else 1f,
        heightRatio = if (narrowLongValue) 0.56f else 0.96f,
        minScaleX = if (narrowLongValue) 0.24f else 0.08f,
        maxScaleX = 2.4f,
        strokeRatio = if (narrowLongValue) 0.016f else 0.026f,
        fillFromBottom = true,
    )
    if (showTarget) {
        if (useVerticalLabels) {
            drawVerticalStretchedText(
                canvas = canvas,
                text = kcalLabel,
                rect = RectF(pad, pad, pad + sideW, textBottom),
                typeface = mono,
                variationSettings = WidgetFont.Mono.variationSettings(),
                color = inkDim,
                clockwise = false,
            )
            drawVerticalStretchedText(
                canvas = canvas,
                text = "/ $goal",
                rect = RectF(w - pad - sideW, pad, w - pad, textBottom),
                typeface = mono,
                variationSettings = WidgetFont.Mono.variationSettings(),
                color = inkDim,
                clockwise = true,
            )
        } else {
            drawStretchedText(
                canvas = canvas,
                text = kcalLabel,
                rect = RectF(pad, pad, w * 0.40f, pad + headerH),
                typeface = mono,
                variationSettings = WidgetFont.Mono.variationSettings(),
                color = ink,
                heightRatio = 0.78f,
                minScaleX = 0.40f,
                maxScaleX = 1.16f,
            )
            drawStretchedText(
                canvas = canvas,
                text = "/ $goal",
                rect = RectF(w * 0.42f, pad, w - pad, pad + headerH),
                typeface = mono,
                variationSettings = WidgetFont.Mono.variationSettings(),
                color = inkDim,
                heightRatio = 0.70f,
                minScaleX = 0.22f,
                maxScaleX = 1.08f,
            )
        }
    } else {
        drawStretchedText(
            canvas = canvas,
            text = kcalLabel,
            rect = RectF(pad, textBottom - (h * 0.16f).coerceIn(10f, 28f), w - pad, textBottom),
            typeface = mono,
            variationSettings = WidgetFont.Mono.variationSettings(),
            color = inkDim,
            heightRatio = 0.72f,
            minScaleX = 0.55f,
            maxScaleX = 1.05f,
        )
    }
    applySoilpunkFinish(bitmap, ink)
    return bitmap
}

private fun collectVectorPaths(node: VectorNode, out: MutableList<VectorPath>) {
    when (node) {
        is VectorPath -> out.add(node)
        is VectorGroup -> node.forEach { collectVectorPaths(it, out) }
    }
}

/**
 * Rasterises a Phosphor [ImageVector] (the same icons the app uses) into a duotone-tinted bitmap
 * for Glance, which can't render vectors directly. The icon is scaled with preserved aspect ratio
 * and centred so it never distorts regardless of tile dimensions. Group transforms are ignored —
 * fine for Phosphor's flat single-/multi-path structure.
 */
internal fun renderVectorIcon(
    vector: ImageVector,
    widthPx: Int,
    heightPx: Int,
    color: Int,
    fillRatio: Float = 0.7f,
    grain: Boolean = true,
): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val scale = minOf(w / vector.viewportWidth, h / vector.viewportHeight) * fillRatio
    val drawW = vector.viewportWidth * scale
    val drawH = vector.viewportHeight * scale
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postTranslate((w - drawW) / 2f, (h - drawH) / 2f)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val paths = mutableListOf<VectorPath>()
    collectVectorPaths(vector.root, paths)
    paths.forEach { vp ->
        val androidPath: Path = PathParser().addPathNodes(vp.pathData).toPath().asAndroidPath()
        androidPath.fillType =
            if (vp.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        androidPath.transform(matrix)
        canvas.drawPath(androidPath, paint)
    }
    if (grain) applySoilpunkFinish(bitmap, color)
    return bitmap
}

internal fun renderActionSticker(
    context: Context,
    water: Boolean,
    useIcon: Boolean,
    label: String,
    widthPx: Int,
    heightPx: Int,
    canvasColor: Int,
    ink: Int,
): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = (minOf(w, h) * 0.11f).coerceIn(8f, 28f)
    val pad = (minOf(w, h) * 0.08f).coerceIn(5f, 18f)
    val stickerRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val stickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(stickerRect, radius, radius, stickerPaint)
    if (useIcon) {
        val iconSize = minOf((w - pad * 2f).toInt(), (h - pad * 2f).toInt()).coerceAtLeast(1)
        val icon = renderVectorIcon(
            vector = if (water) RawIcons.water else RawIcons.food,
            widthPx = iconSize,
            heightPx = iconSize,
            color = canvasColor,
            fillRatio = 0.86f,
            grain = false,
        )
        canvas.drawBitmap(icon, (w - iconSize) / 2f, (h - iconSize) / 2f, null)
    } else {
        val base = WidgetFonts.mono(context)
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, 800, false)
        } else {
            Typeface.create(base, Typeface.BOLD)
        }
        val textPadX = (minOf(w, h) * 0.045f).coerceIn(3f, 12f)
        val textPadY = (minOf(w, h) * 0.032f).coerceIn(2f, 8f)
        drawStretchedText(
            canvas = canvas,
            text = label,
            rect = RectF(textPadX, textPadY, w - textPadX, h - textPadY),
            typeface = typeface,
            variationSettings = "'wght' 800",
            color = canvasColor,
            heightRatio = 0.9f,
            minScaleX = 0.42f,
            maxScaleX = 1.72f,
            fakeBold = true,
            strokeRatio = 0.012f,
        )
    }
    applySoilpunkFinish(bitmap, canvasColor)
    return bitmap
}

internal fun renderMacroStrip(
    context: Context,
    protein: Int,
    carbs: Int,
    fat: Int,
    proteinLabel: String,
    carbsLabel: String,
    fatLabel: String,
    goalProtein: Int,
    goalCarbs: Int,
    goalFat: Int,
    widthPx: Int,
    heightPx: Int,
    ink: Int,
    inkDim: Int,
): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val display = WidgetFont.Display.typeface(context)
    val mono = WidgetFont.Mono.typeface(context)
    val items = listOf(
        Triple(proteinLabel, protein, goalProtein),
        Triple(carbsLabel, carbs, goalCarbs),
        Triple(fatLabel, fat, goalFat)
    )
    val gap = (w * 0.035f).coerceIn(2f, 10f)
    val cellW = (w - gap * 2f) / 3f

    items.forEachIndexed { index, (label, value, goal) ->
        val left = index * (cellW + gap)
        val right = left + cellW
        drawMacroCell(canvas, label, value.toString(), goal, RectF(left, 0f, right, h.toFloat()), display, mono, ink, inkDim)
    }
    applySoilpunkFinish(bitmap, ink)
    return bitmap
}

internal fun renderMacroCell(
    context: Context,
    label: String,
    value: Int,
    goal: Int,
    widthPx: Int,
    heightPx: Int,
    ink: Int,
    inkDim: Int,
): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawMacroCell(
        canvas = canvas,
        label = label,
        value = value.toString(),
        goal = goal,
        rect = RectF(0f, 0f, w.toFloat(), h.toFloat()),
        display = WidgetFont.Display.typeface(context),
        mono = WidgetFont.Mono.typeface(context),
        ink = ink,
        inkDim = inkDim
    )
    applySoilpunkFinish(bitmap, ink)
    return bitmap
}

internal fun renderMacroStack(
    context: Context,
    protein: Int,
    carbs: Int,
    fat: Int,
    proteinLabel: String,
    carbsLabel: String,
    fatLabel: String,
    goalProtein: Int,
    goalCarbs: Int,
    goalFat: Int,
    widthPx: Int,
    heightPx: Int,
    ink: Int,
    inkDim: Int,
): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val display = WidgetFont.Display.typeface(context)
    val mono = WidgetFont.Mono.typeface(context)
    val items = listOf(
        Triple(proteinLabel, protein, goalProtein),
        Triple(carbsLabel, carbs, goalCarbs),
        Triple(fatLabel, fat, goalFat)
    )
    val gap = (h * 0.025f).coerceIn(1f, 4f)
    val rowH = (h - gap * 2f) / 3f
    items.forEachIndexed { index, (label, value, goal) ->
        val top = index * (rowH + gap)
        val bottom = top + rowH
        drawMacroStackRow(
            canvas = canvas,
            label = label,
            value = value,
            goal = goal,
            rect = RectF(0f, top, w.toFloat(), bottom),
            display = display,
            mono = mono,
            ink = ink,
            inkDim = inkDim,
        )
    }
    applySoilpunkFinish(bitmap, ink)
    return bitmap
}

private fun drawMacroCell(
    canvas: Canvas,
    label: String,
    value: String,
    goal: Int,
    rect: RectF,
    display: Typeface,
    mono: Typeface,
    ink: Int,
    inkDim: Int,
) {
    val padX = rect.width() * 0.018f
    val padY = rect.height() * 0.025f
    val labelH = (rect.height() * 0.30f).coerceIn(10f, 28f)
    val valueBottom = rect.bottom - padY
    val valueInt = value.toIntOrNull() ?: 0
    val progress = progressRatio(valueInt, goal)
    drawFilledStretchedText(
        canvas = canvas,
        text = valueInt.toString(),
        rect = RectF(rect.left + padX, rect.top + padY + labelH * 0.78f, rect.right - padX, valueBottom),
        typeface = display,
        variationSettings = WidgetFont.Display.variationSettings(),
        ink = ink,
        inkDim = inkDim,
        progress = progress,
        heightRatio = 1.02f,
        minScaleX = 0.10f,
        maxScaleX = 2.35f,
        strokeRatio = 0.034f,
        fillFromBottom = true,
    )
    drawStretchedText(
        canvas = canvas,
        text = label,
        rect = RectF(rect.left + padX, rect.top + padY, rect.left + rect.width() * 0.38f, rect.top + padY + labelH),
        typeface = mono,
        variationSettings = WidgetFont.Mono.variationSettings(),
        color = ink,
        heightRatio = 0.82f,
        minScaleX = 0.55f,
        maxScaleX = 1.35f,
    )
    if (goal > 0 && rect.width() >= 72f && rect.height() >= 36f) {
        drawStretchedText(
            canvas = canvas,
            text = "/ $goal",
            rect = RectF(rect.left + rect.width() * 0.40f, rect.top + padY, rect.right - padX, rect.top + padY + labelH),
            typeface = mono,
            variationSettings = WidgetFont.Mono.variationSettings(),
            color = inkDim,
            heightRatio = 0.70f,
            minScaleX = 0.26f,
            maxScaleX = 1.08f,
        )
    }
}

private fun drawMacroStackRow(
    canvas: Canvas,
    label: String,
    value: Int,
    goal: Int,
    rect: RectF,
    display: Typeface,
    mono: Typeface,
    ink: Int,
    inkDim: Int,
) {
    val padX = rect.width() * 0.026f
    val padY = rect.height() * 0.045f
    val textBottom = rect.bottom - padY
    val labelH = (rect.height() * 0.34f).coerceIn(10f, 22f)
    val progress = progressRatio(value, goal)
    drawFilledStretchedText(
        canvas = canvas,
        text = value.toString(),
        rect = RectF(rect.left + rect.width() * 0.24f, rect.top + padY + labelH * 0.18f, rect.right - padX, textBottom),
        typeface = display,
        variationSettings = WidgetFont.Display.variationSettings(),
        ink = ink,
        inkDim = inkDim,
        progress = progress,
        heightRatio = 0.98f,
        minScaleX = 0.10f,
        maxScaleX = 2.1f,
        strokeRatio = 0.032f,
        fillFromBottom = true,
    )
    drawStretchedText(
        canvas = canvas,
        text = label,
        rect = RectF(rect.left + padX, rect.top + padY, rect.left + rect.width() * 0.24f, rect.top + padY + labelH),
        typeface = mono,
        variationSettings = WidgetFont.Mono.variationSettings(),
        color = ink,
        heightRatio = 0.84f,
        minScaleX = 0.44f,
        maxScaleX = 1.22f,
    )
    if (goal > 0 && rect.width() >= 96f && rect.height() >= 42f) {
        drawStretchedText(
            canvas = canvas,
            text = "/ $goal",
            rect = RectF(rect.left + rect.width() * 0.48f, rect.top + padY, rect.right - padX, rect.top + padY + labelH),
            typeface = mono,
            variationSettings = WidgetFont.Mono.variationSettings(),
            color = inkDim,
            heightRatio = 0.66f,
            minScaleX = 0.24f,
            maxScaleX = 1.08f,
        )
    }
}

private fun dpToPx(context: Context, dp: Dp): Int {
    val density = context.resources.displayMetrics.density
    return (dp.value * density).toInt().coerceAtLeast(1)
}

@Composable
internal fun StretchedTextImage(
    context: Context,
    text: String,
    color: Color,
    width: Dp,
    height: Dp,
    font: WidgetFont,
    modifier: GlanceModifier = GlanceModifier,
    contentDescription: String = text,
) {
    val wPx = dpToPx(context, width)
    val hPx = dpToPx(context, height)
    val argb = color.toArgb()
    val bitmap = remember(text, wPx, hPx, argb, font) {
        renderStretchedText(context, text, wPx, hPx, argb, font)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
internal fun HeroValueImage(
    context: Context,
    value: Int,
    goal: Int,
    kcalLabel: String,
    contentDescription: String,
    ink: Color,
    inkDim: Color,
    width: Dp,
    height: Dp,
    showBar: Boolean,
    modifier: GlanceModifier = GlanceModifier,
) {
    val wPx = dpToPx(context, width)
    val hPx = dpToPx(context, height)
    val inkArgb = ink.toArgb()
    val inkDimArgb = inkDim.toArgb()
    val bitmap = remember(value, goal, kcalLabel, wPx, hPx, inkArgb, inkDimArgb, showBar) {
        renderHeroValue(
            context = context,
            value = value,
            goal = goal,
            kcalLabel = kcalLabel,
            widthPx = wPx,
            heightPx = hPx,
            ink = inkArgb,
            inkDim = inkDimArgb,
            showBar = showBar,
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
internal fun ActionStickerImage(
    context: Context,
    water: Boolean,
    label: String,
    contentDescription: String,
    useIcon: Boolean,
    canvas: Color,
    ink: Color,
    width: Dp,
    height: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val wPx = dpToPx(context, width)
    val hPx = dpToPx(context, height)
    val canvasArgb = canvas.toArgb()
    val inkArgb = ink.toArgb()
    val bitmap = remember(water, label, useIcon, wPx, hPx, canvasArgb, inkArgb) {
        renderActionSticker(
            context = context,
            water = water,
            useIcon = useIcon,
            label = label,
            widthPx = wPx,
            heightPx = hPx,
            canvasColor = canvasArgb,
            ink = inkArgb,
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
internal fun ActionIconImage(
    context: Context,
    water: Boolean,
    contentDescription: String,
    color: Color,
    width: Dp,
    height: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    VectorIconImage(
        context = context,
        vector = if (water) RawIcons.water else RawIcons.food,
        color = color,
        width = width,
        height = height,
        modifier = modifier,
        contentDescription = contentDescription,
        fillRatio = 0.74f,
    )
}

@Composable
internal fun VectorIconImage(
    context: Context,
    vector: ImageVector,
    color: Color,
    width: Dp,
    height: Dp,
    modifier: GlanceModifier = GlanceModifier,
    contentDescription: String = "",
    fillRatio: Float = 0.7f,
) {
    val wPx = dpToPx(context, width)
    val hPx = dpToPx(context, height)
    val argb = color.toArgb()
    val bitmap = remember(vector, wPx, hPx, argb, fillRatio) {
        renderVectorIcon(vector, wPx, hPx, argb, fillRatio)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
internal fun MacroStripImage(
    context: Context,
    data: WidgetTotals,
    strings: WidgetStrings,
    ink: Color,
    inkDim: Color,
    width: Dp,
    height: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val wPx = dpToPx(context, width)
    val hPx = dpToPx(context, height)
    val inkArgb = ink.toArgb()
    val inkDimArgb = inkDim.toArgb()
    val bitmap = remember(data.protein, data.carbs, data.fat, strings, wPx, hPx, inkArgb, inkDimArgb) {
        renderMacroStrip(
            context = context,
            protein = data.protein,
            carbs = data.carbs,
            fat = data.fat,
            proteinLabel = strings.protein,
            carbsLabel = strings.carbs,
            fatLabel = strings.fat,
            goalProtein = data.goalProtein,
            goalCarbs = data.goalCarbs,
            goalFat = data.goalFat,
            widthPx = wPx,
            heightPx = hPx,
            ink = inkArgb,
            inkDim = inkDimArgb
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = strings.macrosDescription(data.protein, data.carbs, data.fat),
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
internal fun MacroStackImage(
    context: Context,
    data: WidgetTotals,
    strings: WidgetStrings,
    ink: Color,
    inkDim: Color,
    width: Dp,
    height: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val wPx = dpToPx(context, width)
    val hPx = dpToPx(context, height)
    val inkArgb = ink.toArgb()
    val inkDimArgb = inkDim.toArgb()
    val bitmap = remember(
        data.protein,
        data.carbs,
        data.fat,
        strings,
        data.goalProtein,
        data.goalCarbs,
        data.goalFat,
        wPx,
        hPx,
        inkArgb,
        inkDimArgb,
    ) {
        renderMacroStack(
            context = context,
            protein = data.protein,
            carbs = data.carbs,
            fat = data.fat,
            proteinLabel = strings.protein,
            carbsLabel = strings.carbs,
            fatLabel = strings.fat,
            goalProtein = data.goalProtein,
            goalCarbs = data.goalCarbs,
            goalFat = data.goalFat,
            widthPx = wPx,
            heightPx = hPx,
            ink = inkArgb,
            inkDim = inkDimArgb,
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = strings.macrosDescription(data.protein, data.carbs, data.fat),
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
internal fun MacroCellImage(
    context: Context,
    label: String,
    value: Int,
    goal: Int,
    ink: Color,
    inkDim: Color,
    width: Dp,
    height: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val wPx = dpToPx(context, width)
    val hPx = dpToPx(context, height)
    val inkArgb = ink.toArgb()
    val inkDimArgb = inkDim.toArgb()
    val bitmap = remember(label, value, goal, wPx, hPx, inkArgb, inkDimArgb) {
        renderMacroCell(
            context = context,
            label = label,
            value = value,
            goal = goal,
            widthPx = wPx,
            heightPx = hPx,
            ink = inkArgb,
            inkDim = inkDimArgb
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "$label $value",
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

internal fun tileWidthDp(widgetWidthDp: Float, cells: Int, gaps: Int): Dp {
    val inner = widgetWidthDp - Gap.value * 2 - Gap.value * gaps
    return (inner / cells).coerceAtLeast(1f).dp
}

internal fun tileHeightDp(widgetHeightDp: Float, cells: Int, gaps: Int): Dp {
    val inner = widgetHeightDp - Gap.value * 2 - Gap.value * gaps
    return (inner / cells).coerceAtLeast(1f).dp
}

internal fun useActionIcons(width: Dp, height: Dp): Boolean =
    width.value < 62f || height.value < 52f
