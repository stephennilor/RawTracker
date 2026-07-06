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
    return Typeface.create(base, Typeface.BOLD)
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

private fun drawStretchedText(
    canvas: Canvas,
    text: String,
    rect: RectF,
    typeface: Typeface,
    color: Int,
    heightRatio: Float,
    minScaleX: Float = 0.35f,
    maxScaleX: Float = 5.5f,
    ghost: Boolean = false,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        this.color = color
        textAlign = Paint.Align.LEFT
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

    val naturalWidth = paint.measureText(text).coerceAtLeast(1f)
    val scaleX = (rect.width() / naturalWidth).coerceIn(minScaleX, maxScaleX)
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
        color = color,
        heightRatio = if (font == WidgetFont.Display) 0.9f else 0.78f,
        minScaleX = if (font == WidgetFont.Display) 0.14f else 0.65f,
        maxScaleX = if (font == WidgetFont.Display) 1.65f else 1.18f,
        ghost = font == WidgetFont.Display,
    )
    applySoilpunkFinish(bitmap, color)
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

internal fun renderMacroStrip(
    context: Context,
    protein: Int,
    carbs: Int,
    fat: Int,
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
        "P" to protein.toString(),
        "C" to carbs.toString(),
        "F" to fat.toString()
    )
    val gap = (w * 0.035f).coerceIn(2f, 10f)
    val cellW = (w - gap * 2f) / 3f

    items.forEachIndexed { index, (label, value) ->
        val left = index * (cellW + gap)
        val right = left + cellW
        drawMacroCell(canvas, label, value, RectF(left, 0f, right, h.toFloat()), display, mono, ink, inkDim)
    }
    applySoilpunkFinish(bitmap, ink)
    return bitmap
}

internal fun renderMacroCell(
    context: Context,
    label: String,
    value: Int,
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
        rect = RectF(0f, 0f, w.toFloat(), h.toFloat()),
        display = WidgetFont.Display.typeface(context),
        mono = WidgetFont.Mono.typeface(context),
        ink = ink,
        inkDim = inkDim
    )
    applySoilpunkFinish(bitmap, ink)
    return bitmap
}

private fun drawMacroCell(
    canvas: Canvas,
    label: String,
    value: String,
    rect: RectF,
    display: Typeface,
    mono: Typeface,
    ink: Int,
    inkDim: Int,
) {
    val labelW = rect.width() * 0.22f
    val gap = rect.width() * 0.03f
    drawStretchedText(
        canvas = canvas,
        text = label,
        rect = RectF(rect.left, rect.top + rect.height() * 0.08f, rect.left + labelW, rect.bottom - rect.height() * 0.08f),
        typeface = mono,
        color = inkDim,
        heightRatio = 0.74f,
        minScaleX = 0.65f,
        maxScaleX = 1.0f
    )
    drawStretchedText(
        canvas = canvas,
        text = value,
        rect = RectF(rect.left + labelW + gap, rect.top, rect.right, rect.bottom),
        typeface = display,
        color = ink,
        heightRatio = 0.9f,
        minScaleX = 0.14f,
        maxScaleX = 1.45f,
        ghost = true,
    )
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
internal fun ActionIconImage(
    context: Context,
    water: Boolean,
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
        contentDescription = if (water) "Add water" else "Add food",
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
    val bitmap = remember(data.protein, data.carbs, data.fat, wPx, hPx, inkArgb, inkDimArgb) {
        renderMacroStrip(
            context = context,
            protein = data.protein,
            carbs = data.carbs,
            fat = data.fat,
            widthPx = wPx,
            heightPx = hPx,
            ink = inkArgb,
            inkDim = inkDimArgb
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "Macros ${data.protein} protein, ${data.carbs} carbs, ${data.fat} fat",
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
internal fun MacroCellImage(
    context: Context,
    label: String,
    value: Int,
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
    val bitmap = remember(label, value, wPx, hPx, inkArgb, inkDimArgb) {
        renderMacroCell(
            context = context,
            label = label,
            value = value,
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
