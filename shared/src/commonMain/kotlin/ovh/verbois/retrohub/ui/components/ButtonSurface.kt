package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.theme.Palette

/** Colored glass, a soft upper reflection and a fine brushed finish shared by console controls. */
fun Modifier.consoleButtonSurface(shape: Shape, primary: Boolean = false, emphasis: Float = 0f, enabled: Boolean = true, base: Color? = null): Modifier {
    val top = base ?: if (primary) Palette.PrimaryTop else lerp(Palette.ButtonTop, Palette.ButtonFocusTop, emphasis)
    val bottom = base ?: if (primary) Palette.PrimaryBottom else lerp(Palette.ButtonBottom, Palette.ButtonFocusBottom, emphasis)
    val opacity = if (enabled) 1f else 0.45f
    return shadow(2.dp, shape, clip = false).clip(shape)
        .background(Brush.verticalGradient(listOf(top.copy(alpha = top.alpha * opacity), bottom.copy(alpha = bottom.alpha * opacity))))
        .drawWithCache {
            val sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.08f * opacity), 0.45f to Color.Transparent)
            val grain = Color.White.copy(alpha = 0.012f * opacity)
            val step = 2.dp.toPx()
            onDrawBehind {
                drawRect(sheen)
                var y = step
                while (y < size.height) {
                    drawLine(grain, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
                    y += step
                }
            }
        }
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Palette.Text.copy(alpha = 0.42f * opacity), Palette.Line.copy(alpha = 0.22f * opacity))),
            shape,
        )
}
