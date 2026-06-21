package com.rawtracker.design

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.CalendarBlank
import com.adamglin.phosphoricons.fill.Camera
import com.adamglin.phosphoricons.fill.CaretDown
import com.adamglin.phosphoricons.fill.CaretLeft
import com.adamglin.phosphoricons.fill.CaretRight
import com.adamglin.phosphoricons.fill.CaretUp
import com.adamglin.phosphoricons.fill.Clock
import com.adamglin.phosphoricons.fill.Drop
import com.adamglin.phosphoricons.fill.ForkKnife
import com.adamglin.phosphoricons.fill.Gear
import com.adamglin.phosphoricons.fill.Image
import com.adamglin.phosphoricons.fill.Minus
import com.adamglin.phosphoricons.fill.PaperPlaneRight
import com.adamglin.phosphoricons.fill.Plus
import com.adamglin.phosphoricons.fill.Trash
import com.adamglin.phosphoricons.fill.X

/** Central mapping of semantic icons to Phosphor Fill so swaps are one-line. */
object RawIcons {
    val camera: ImageVector get() = PhosphorIcons.Fill.Camera
    val gallery: ImageVector get() = PhosphorIcons.Fill.Image
    val send: ImageVector get() = PhosphorIcons.Fill.PaperPlaneRight
    val plus: ImageVector get() = PhosphorIcons.Fill.Plus
    val settings: ImageVector get() = PhosphorIcons.Fill.Gear
    val water: ImageVector get() = PhosphorIcons.Fill.Drop
    val close: ImageVector get() = PhosphorIcons.Fill.X
    val delete: ImageVector get() = PhosphorIcons.Fill.Trash
    val caretDown: ImageVector get() = PhosphorIcons.Fill.CaretDown
    val caretUp: ImageVector get() = PhosphorIcons.Fill.CaretUp
    val caretLeft: ImageVector get() = PhosphorIcons.Fill.CaretLeft
    val caretRight: ImageVector get() = PhosphorIcons.Fill.CaretRight
    val minus: ImageVector get() = PhosphorIcons.Fill.Minus
    val calendar: ImageVector get() = PhosphorIcons.Fill.CalendarBlank
    val clock: ImageVector get() = PhosphorIcons.Fill.Clock
    val food: ImageVector get() = PhosphorIcons.Fill.ForkKnife
}
