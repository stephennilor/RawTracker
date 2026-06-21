package com.rawtracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.rawtracker.R
import kotlin.math.min

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

private fun drawStretchedText(
    canvas: Canvas,
    text: String,
    rect: RectF,
    typeface: Typeface,
    color: Int,
    heightRatio: Float,
    minScaleX: Float = 0.35f,
    maxScaleX: Float = 5.5f,
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
        heightRatio = if (font == WidgetFont.Display) 0.9f else 0.78f
    )
    return bitmap
}

internal fun renderFoodIcon(widthPx: Int, heightPx: Int, color: Int): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = min(w, h) * 0.08f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.strokeWidth = stroke
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val top = h * 0.12f
    val bottom = h * 0.88f
    val forkX = w * 0.36f
    val knifeX = w * 0.64f
    val tineTop = top
    val tineBottom = h * 0.38f
    val tineSpread = w * 0.07f
    canvas.drawLine(forkX, tineBottom, forkX, bottom, paint)
    canvas.drawLine(forkX - tineSpread, tineTop, forkX - tineSpread, tineBottom, paint)
    canvas.drawLine(forkX, tineTop, forkX, tineBottom, paint)
    canvas.drawLine(forkX + tineSpread, tineTop, forkX + tineSpread, tineBottom, paint)
    val knifePath = Path().apply {
        moveTo(knifeX, top)
        cubicTo(w * 0.78f, h * 0.28f, w * 0.72f, h * 0.56f, knifeX, h * 0.58f)
        lineTo(knifeX, bottom)
    }
    canvas.drawPath(knifePath, paint)
    return bitmap
}

internal fun renderWaterIcon(widthPx: Int, heightPx: Int, color: Int): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = min(w, h) * 0.08f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val cx = w / 2f
    val top = h * 0.08f
    val bottom = h * 0.9f
    val half = w * 0.32f
    val path = Path().apply {
        moveTo(cx, top)
        cubicTo(cx + half * 1.6f, top + h * 0.18f, cx + half * 1.6f, bottom - h * 0.12f, cx, bottom)
        cubicTo(cx - half * 1.6f, bottom - h * 0.12f, cx - half * 1.6f, top + h * 0.18f, cx, top)
        close()
    }
    canvas.drawPath(path, paint)
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
        minScaleX = 0.2f
    )
    drawStretchedText(
        canvas = canvas,
        text = value,
        rect = RectF(rect.left + labelW + gap, rect.top, rect.right, rect.bottom),
        typeface = display,
        color = ink,
        heightRatio = 0.9f,
        minScaleX = 0.2f
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
    val wPx = dpToPx(context, width)
    val hPx = dpToPx(context, height)
    val argb = color.toArgb()
    val bitmap = remember(water, wPx, hPx, argb) {
        if (water) renderWaterIcon(wPx, hPx, argb) else renderFoodIcon(wPx, hPx, argb)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = if (water) "Add water" else "Add food",
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
