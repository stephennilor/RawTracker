package com.rawtracker.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val GRAIN_STEP = 6f
private const val GRAIN_DOT_RADIUS = 1.15f
private const val GRAIN_ALPHA = 0.13f
// 12 lattice steps wide/tall so the tile repeats seamlessly (72 = 12 * 6).
private const val GRAIN_TILE_PX = 72

/**
 * Subtle halftone grain — duotone-safe (ink dots on canvas only).
 *
 * Performance: the dot lattice is baked once into a tiny [GRAIN_TILE_PX] tile and tiled by the GPU
 * via an [ImageShader], so each frame is a single drawRect instead of ~tens of thousands of
 * drawCircle calls. This is what keeps keyboard/scroll animations smooth.
 */
@Composable
fun SoilpunkGrain(modifier: Modifier = Modifier, ink: Color = RawColors.ink) {
    Spacer(
        modifier = modifier.drawWithCache {
            val tile = ImageBitmap(GRAIN_TILE_PX, GRAIN_TILE_PX)
            val tileCanvas = GraphicsCanvas(tile)
            val paint = Paint().apply {
                color = ink.copy(alpha = GRAIN_ALPHA)
                isAntiAlias = true
            }
            var row = 0
            var y = 0f
            while (y < GRAIN_TILE_PX) {
                val stagger = if (row % 2 == 0) 0f else GRAIN_STEP * 0.5f
                var x = stagger
                while (x < GRAIN_TILE_PX) {
                    tileCanvas.drawCircle(Offset(x, y), GRAIN_DOT_RADIUS, paint)
                    x += GRAIN_STEP
                }
                y += GRAIN_STEP
                row++
            }
            val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
            onDrawBehind { drawRect(brush) }
        }
    )
}

/** Micro-caps label with editorial crop marks. */
@Composable
fun EditorialSectionLabel(text: String, modifier: Modifier = Modifier) {
    val ink = RawColors.ink
    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .offset(y = 10.dp)
        ) {
            val y = size.height * 0.35f
            drawLine(ink.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            drawLine(ink.copy(alpha = 0.55f), Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.5f)
            drawLine(ink.copy(alpha = 0.55f), Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.5f)
        }
        MonoText(
            text = text,
            weight = FontWeight.Bold,
            size = 11.sp,
            modifier = Modifier.offset(x = 6.dp),
        )
    }
}

/**
 * Vogue-style hero numeral: horizontally stretched to fill the row,
 * with a faint mis-register ghost for soilpunk print grit.
 */
@Composable
fun EditorialHeroNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
) {
    val ink = RawColors.ink
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scaleX = remember(text, maxWidth) {
            when (text.length) {
                1 -> 1.42f
                2 -> 1.28f
                3 -> 1.14f
                4 -> 1.02f
                else -> 0.94f
            }.coerceIn(0.88f, 1.48f)
        }
        val layer = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.scaleX = scaleX
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
        Text(
            text = text,
            color = ink.copy(alpha = 0.14f),
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = layer.offset(x = 1.5.dp, y = 1.dp),
        )
        Text(
            text = text,
            color = ink,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = layer,
        )
    }
}
