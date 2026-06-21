package com.rawtracker.design

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Builds an opaque [Color] from hue (0..360), saturation (0..1) and value (0..1). */
fun hsvColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/** Decomposes a [Color] into (hue, saturation, value). */
fun Color.toHsv(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    var h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    if (h < 0f) h += 360f
    val s = if (max == 0f) 0f else d / max
    return Triple(h, s, max)
}

/**
 * Compact HSV picker: a saturation/value square plus a hue strip.
 * Cross-platform (pure Compose drawing + pointer input). Emits a live opaque colour.
 */
@Composable
fun HsvColorPicker(color: Color, onColorChange: (Color) -> Unit) {
    val initial = remember { color.toHsv() }
    var hue by remember { mutableFloatStateOf(initial.first) }
    var sat by remember { mutableFloatStateOf(initial.second) }
    var value by remember { mutableFloatStateOf(initial.third) }

    fun emit() = onColorChange(hsvColor(hue, sat, value))

    val hueColor = hsvColor(hue, 1f, 1f)

    Column {
        // Saturation / Value square
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(hueColor)
                .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .inkBorder(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        sat = (p.x / size.width).coerceIn(0f, 1f)
                        value = (1f - p.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                }
        )
        Spacer(Modifier.height(10.dp))
        // Hue strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(
                        (0..6).map { hsvColor(it * 60f, 1f, 1f) }
                    )
                )
                .inkBorder(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        hue = (p.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                }
        )
    }
}
