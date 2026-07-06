package com.rawtracker.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rawtracker.data.DuotonePrefs

data class DuotoneColors(val canvas: Color, val ink: Color)

val LocalDuotone = staticCompositionLocalOf {
    DuotoneColors(canvas = Color(0xFFFFE9CE), ink = Color(0xFF8A53FF))
}

/** Convenient accessor inside composables. */
object RawColors {
    val canvas: Color @Composable get() = LocalDuotone.current.canvas
    val ink: Color @Composable get() = LocalDuotone.current.ink
}

@Composable
fun RawTrackerTheme(prefs: DuotonePrefs, content: @Composable () -> Unit) {
    val colors = DuotoneColors(canvas = Color(prefs.canvas), ink = Color(prefs.ink))
    val scheme = lightColorScheme(
        primary = colors.ink,
        onPrimary = colors.canvas,
        background = colors.canvas,
        onBackground = colors.ink,
        surface = colors.canvas,
        onSurface = colors.ink,
        secondary = colors.ink,
        onSecondary = colors.canvas
    )
    CompositionLocalProvider(LocalDuotone provides colors) {
        MaterialTheme(colorScheme = scheme, typography = rawTypography()) {
            Box(Modifier.fillMaxSize().background(colors.canvas)) {
                SoilpunkGrain(Modifier.fillMaxSize(), ink = colors.ink)
                content()
            }
        }
    }
}
