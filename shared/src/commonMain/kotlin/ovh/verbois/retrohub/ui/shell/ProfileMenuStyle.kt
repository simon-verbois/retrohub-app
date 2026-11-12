package ovh.verbois.retrohub.ui.shell

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/** Position the panel within its safe host and keep its pointer under the avatar. */
internal class ProfileMenuPositionProvider(
    private val profile: Rect?,
    private val margin: Int,
    private val gap: Int,
    private val onPosition: (Float?) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val right = minOf(anchorBounds.right, windowSize.width) - margin
        val left = (anchorBounds.left + margin).coerceAtMost(right - popupContentSize.width)
        val x = (right - popupContentSize.width).coerceAtLeast(left).coerceAtLeast(0)
        val wantedY = profile?.bottom?.roundToInt()?.plus(gap) ?: (anchorBounds.top + gap)
        val maxY = (anchorBounds.bottom - popupContentSize.height - gap).coerceAtLeast(0)
        val y = wantedY.coerceIn(0, maxY)
        onPosition(profile?.center?.x?.minus(x))
        return IntOffset(x, y)
    }
}

/** One continuous outline avoids a border seam across the base of the pointer. */
internal class ProfileMenuShape(private val pointer: Float?) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val top = if (pointer == null) 0f else with(density) { 10.dp.toPx() }.coerceAtMost(size.height / 4)
        val radius = minOf(with(density) { 22.dp.toPx() }, size.width / 5, (size.height - top) / 2)
        val path = Path().apply {
            moveTo(radius, top)
            if (pointer != null) {
                val half = minOf(with(density) { 12.dp.toPx() }, (size.width - radius * 2) / 4)
                val center = pointer.coerceIn(radius + half, size.width - radius - half)
                lineTo(center - half, top)
                cubicTo(center - half * 0.6f, top, center - half * 0.25f, top * 0.25f, center, 0f)
                cubicTo(center + half * 0.25f, top * 0.25f, center + half * 0.6f, top, center + half, top)
            }
            lineTo(size.width - radius, top)
            quadraticTo(size.width, top, size.width, top + radius)
            lineTo(size.width, size.height - radius)
            quadraticTo(size.width, size.height, size.width - radius, size.height)
            lineTo(radius, size.height)
            quadraticTo(0f, size.height, 0f, size.height - radius)
            lineTo(0f, top + radius)
            quadraticTo(0f, top, radius, top)
            close()
        }
        return Outline.Generic(path)
    }
}
