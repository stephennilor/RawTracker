package com.rawtracker.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rawtracker.resources.Fredoka
import com.rawtracker.resources.JetBrainsMono
import com.rawtracker.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
fun displayFamily(): FontFamily = FontFamily(
    Font(Res.font.Fredoka, weight = FontWeight.SemiBold),
    Font(Res.font.Fredoka, weight = FontWeight.Bold)
)

@Composable
fun monoFamily(): FontFamily = FontFamily(
    Font(Res.font.JetBrainsMono, weight = FontWeight.Normal),
    Font(Res.font.JetBrainsMono, weight = FontWeight.Medium),
    Font(Res.font.JetBrainsMono, weight = FontWeight.Bold)
)

@Composable
fun rawTypography(): Typography {
    val display = displayFamily()
    val mono = monoFamily()
    return Typography(
        displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 72.sp),
        displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 48.sp),
        headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
        titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        bodyLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp),
        labelSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
    )
}
