package ovh.verbois.retrohub.ui.console.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.LocalReducedMotion
import ovh.verbois.retrohub.ui.components.ProgressLine
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide

private val TileShape = RoundedCornerShape(14.dp)

/**
 * A game tile of a console grid, cover only, like the PS5 library. With the focus the cover grows,
 * brightens and casts a shadow, and the name appears over its bottom; at rest the cover is slightly dimmed.
 */
@Composable
fun GameTile(
    title: String,
    console: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onDevice: Boolean = false,
    favorite: Boolean = false,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val shown = focused && keyboard
    val reduced = LocalReducedMotion.current
    val light by animateFloatAsState(if (shown || !keyboard) 1f else 0.8f, if (reduced) snap() else tween(120), label = "tile-light")
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(title, subtitle, "sur l’appareil".takeIf { onDevice }).joinToString(", ")
        }.consoleFocus(
            TileShape,
            onClick,
            focusScale = 1.07f,
            highlightFocus = false,
            onLongClick = onLongClick,
            onFocus = {
                focused = it
                onFocus(it)
            },
        ),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .then(if (shown) Modifier.shadow(14.dp, TileShape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                .clip(TileShape),
        ) {
            GameArt(
                coverUrl,
                console,
                title,
                Modifier.matchParentSize().graphicsLayer { alpha = light },
                showTitleOnFallback = false,
            )
            if (onDevice || favorite) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (onDevice) ConsoleColors.Selected else Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (onDevice) Lucide.Check else Lucide.HeartFilled,
                            if (onDevice) "Sur l’appareil" else "Favori",
                            Modifier.size(14.dp),
                            tint = if (onDevice) ConsoleColors.OnSelected else Color.White,
                        )
                    }
                }
            }
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp))
            // Only the focused game shows its name, over the bottom of its cover; with touch, all do.
            if (shown || !keyboard) {
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                        .padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
        }
    }
}
