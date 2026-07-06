package com.rawtracker.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawtracker.i18n.strings
import kotlinx.coroutines.delay

/** The single elevation primitive: hard ink border, tight radius for soilpunk edge. */
fun Modifier.inkBorder(ink: Color, width: androidx.compose.ui.unit.Dp = 2.dp): Modifier =
    this.border(width, ink, RoundedCornerShape(4.dp))

@Composable
fun BrutalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    val ink = RawColors.ink
    val canvas = RawColors.canvas
    Box(
        modifier = modifier
            .background(if (filled) ink else canvas, RoundedCornerShape(4.dp))
            .inkBorder(ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(),
            color = if (filled) canvas else ink,
            fontFamily = monoFamily(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontSize = 12.sp
        )
    }
}

@Composable
fun BrutalIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    boxSize: androidx.compose.ui.unit.Dp = 48.dp
) {
    val ink = RawColors.ink
    val canvas = RawColors.canvas
    Box(
        modifier = modifier
            .size(boxSize)
            .background(if (filled) ink else Color.Transparent, RoundedCornerShape(4.dp))
            .inkBorder(ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) canvas else ink,
            modifier = Modifier.size(boxSize * 0.5f)
        )
    }
}

/** Massive typographic stat used for the daily macro counters. */
@Composable
fun BigStat(
    value: Int,
    goal: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val ink = RawColors.ink
    Column(modifier = modifier) {
        Text(
            text = value.toString(),
            color = ink,
            style = androidx.compose.material3.MaterialTheme.typography.displayMedium
        )
        Text(
            text = "/ $goal  ${label.uppercase()}",
            color = ink,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RawColors.ink,
    weight: FontWeight = FontWeight.Normal,
    size: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = monoFamily(),
        fontWeight = weight,
        fontSize = size
    )
}

@Composable
fun BrutalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: Dp = 48.dp,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    val ink = RawColors.ink
    val fieldFocus = focusRequester ?: remember { FocusRequester() }
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .inkBorder(ink)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
            singleLine = singleLine,
            textStyle = TextStyle(color = ink, fontFamily = monoFamily(), fontSize = 16.sp),
            cursorBrush = SolidColor(ink),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onSend = { onImeAction?.invoke() },
                onDone = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() }
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        MonoText(text = placeholder, color = ink.copy(alpha = 0.45f), size = 16.sp)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun RowSpace(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

/**
 * Full-screen takeover shown while the AI request is in flight. Blocks all input,
 * shows honest elapsed-time feedback, and offers a cancel action.
 */
@Composable
fun ParsingOverlay(onCancel: () -> Unit) {
    val ink = RawColors.ink
    val canvas = RawColors.canvas

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds += 1
        }
    }

    val spin = rememberInfiniteTransition(label = "parsing")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas.copy(alpha = 0.94f))
            // Swallow every touch so the screen behind is fully locked out.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Canvas(modifier = Modifier.size(48.dp)) {
                val stroke = 5.dp.toPx()
                drawArc(
                    color = ink,
                    startAngle = angle,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Spacer(Modifier.height(20.dp))
            MonoText(
                text = strings.askingGemini,
                weight = FontWeight.Bold,
                size = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            MonoText(
                text = elapsedCopy(elapsedSeconds),
                color = ink.copy(alpha = 0.72f),
                size = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            MonoText(
                text = strings.elapsed(formatElapsed(elapsedSeconds)),
                color = ink.copy(alpha = 0.55f),
                size = 12.sp
            )
            Spacer(Modifier.height(28.dp))
            BrutalButton(strings.cancel, onCancel, filled = false)
        }
    }
}

private fun elapsedCopy(seconds: Int): String = when {
    seconds < 12 -> strings.parseQuickWait
    seconds < 30 -> strings.parsePhotoWait
    seconds < 60 -> strings.parseRetryWait
    else -> strings.parseLongWait
}

private fun formatElapsed(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
}
