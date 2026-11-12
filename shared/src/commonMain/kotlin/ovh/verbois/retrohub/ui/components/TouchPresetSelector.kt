package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.emulation.ControlsPlacement
import ovh.verbois.retrohub.emulation.GameLayouts
import ovh.verbois.retrohub.emulation.TouchPreset
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/** Small diagrams show which presets reserve space and which put controls over the game. */
@Composable
fun TouchPresetSelector(selected: TouchPreset, onSelect: (TouchPreset) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 460.dp || LocalDensity.current.fontScale > 1.3f
        val card: @Composable (TouchPreset, Modifier) -> Unit = { preset, modifier ->
            val active = selected == preset
            val ink = if (active) Palette.OnAccent else Palette.Text
            val shape = RoundedCornerShape(16.dp)
            val surface = modifier.semantics { this.selected = active }
                .consoleFocus(shape, { onSelect(preset) }, role = Role.RadioButton, focusScale = 1.02f)
                .consoleButtonSurface(shape, primary = active).padding(12.dp)
            val preview: @Composable (Modifier) -> Unit = { previewModifier ->
                Canvas(previewModifier.aspectRatio(2.2f)) {
                    val layout = GameLayouts.compute(640f, 290f, 512f / 192f, true, controlsUnit = 48f, preset = preset)
                    val game = layout.game
                    val origin = Offset(game.left * size.width, game.top * size.height)
                    val image = Size(game.width * size.width, game.height * size.height)
                    drawRoundRect(ink.copy(alpha = 0.18f), origin, image)
                    drawRect(ink.copy(alpha = 0.6f), origin, image, style = Stroke(1.dp.toPx()))
                    drawLine(ink.copy(alpha = 0.45f), origin + Offset(image.width / 2, 0f), origin + Offset(image.width / 2, image.height))
                    val y = if (layout.controls == ControlsPlacement.SIDES) 0.65f else 0.82f
                    drawCircle(ink, size.height * 0.09f, Offset(size.width * 0.12f, size.height * y))
                    drawCircle(ink, size.height * 0.09f, Offset(size.width * 0.88f, size.height * y))
                }
            }
            if (stacked) {
                Row(surface.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    preview(Modifier.size(width = 112.dp, height = 52.dp))
                    Text(preset.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ink)
                    if (active) Icon(Lucide.Check, null, Modifier.size(18.dp), tint = ink)
                }
            } else {
                Column(surface, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    preview(Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(preset.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ink)
                        if (active) Icon(Lucide.Check, null, Modifier.size(18.dp), tint = ink)
                    }
                }
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { TouchPreset.entries.forEach { card(it, Modifier) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TouchPreset.entries.forEach { card(it, Modifier.weight(1f)) } }
        }
    }
}
