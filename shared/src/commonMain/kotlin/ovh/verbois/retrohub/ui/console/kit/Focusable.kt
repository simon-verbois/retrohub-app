package ovh.verbois.retrohub.ui.console.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.components.FocusFill
import ovh.verbois.retrohub.ui.components.LocalBaseDensity
import ovh.verbois.retrohub.ui.components.LocalReducedMotion
import ovh.verbois.retrohub.ui.icons.Lucide

val ControlShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(percent = 50)

/** How a control looks when it does not have the focus. */
enum class Tone { GLASS, QUIET, PRIMARY, DANGER }

/**
 * The console control: no outline ever. With controller or keyboard focus it turns white with dark
 * content and grows slightly; at rest it is glass. Its content reads [LocalContentColor] (and [dim]).
 */
@Composable
fun FocusSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = ControlShape,
    tone: Tone = Tone.GLASS,
    enabled: Boolean = true,
    focusScale: Float = 1.03f,
    role: Role = Role.Button,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val rest = when (tone) {
        Tone.GLASS -> ConsoleColors.Surface
        Tone.QUIET -> Color.Transparent
        Tone.PRIMARY -> Color.White.copy(alpha = 0.22f)
        Tone.DANGER -> ConsoleColors.Surface
    }
    FocusFill(
        onClick,
        modifier,
        shape = shape,
        rest = rest,
        restContent = if (tone == Tone.DANGER) ConsoleColors.Danger else Color.White,
        enabled = enabled,
        focusScale = focusScale,
        role = role,
        label = label,
        onLongClick = onLongClick,
        onFocus = onFocus,
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Text button with an optional icon; [tone] PRIMARY for the main action of a screen. */
@Composable
fun ConsoleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone = Tone.GLASS,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 48.dp,
    padding: PaddingValues = PaddingValues(horizontal = 22.dp),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    FocusSurface(
        if (loading) null else onClick,
        modifier.heightIn(min = height),
        shape = PillShape,
        tone = tone,
        enabled = enabled,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing()
        }
    }
}

/** Round icon button (header tools); [label] is spoken and used by tests. */
@Composable
fun ConsoleIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tone: Tone = Tone.QUIET,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    FocusSurface(onClick, modifier.size(size), shape = CircleShape, tone = tone, label = label, focusScale = 1.08f, contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(size * 0.48f))
        badge?.invoke(this)
    }
}

/**
 * A PS5 round icon: no text at rest; with the focus it becomes a white disc and its short label appears
 * under it (or above, in the control center), without moving anything around it.
 */
@Composable
fun RoundIcon(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    labelAbove: Boolean = false,
    highlighted: Boolean = false,
    onFocus: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    // However much the console interface shrinks, a round icon stays a full 48 dp touch target.
    val density = LocalDensity.current.density
    val real = LocalBaseDensity.current?.density ?: density
    val size = maxOf(size, 48.dp * (real / density))
    Box(Modifier.size(size)) {
        FocusSurface(
            onClick,
            modifier.size(size),
            shape = CircleShape,
            tone = if (highlighted) Tone.GLASS else Tone.QUIET,
            label = label,
            focusScale = 1.1f,
            contentAlignment = Alignment.Center,
            onFocus = {
                focused = it
                onFocus(it)
            },
            content = { content() },
        )
        if (focused && keyboard) {
            val gap = 6.dp
            Text(
                label,
                Modifier.align(if (labelAbove) Alignment.TopCenter else Alignment.BottomCenter).layout { measurable, constraints ->
                    val text = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                    layout(0, 0) {
                        val y = if (labelAbove) -text.height - gap.roundToPx() else gap.roundToPx()
                        text.place(-text.width / 2, y)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/** Selection mark: a cyan check when chosen, a faint ring otherwise. Never white, so never mistaken for focus. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier, ring: Boolean = true) {
    Box(
        modifier.size(22.dp).clip(CircleShape).then(
            when {
                selected -> Modifier.background(ConsoleColors.Selected)
                ring -> Modifier.background(LocalContentColor.current.copy(alpha = 0.16f))
                else -> Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Lucide.Check, null, Modifier.size(14.dp), tint = ConsoleColors.OnSelected)
    }
}

/** Small rounded label (« Conseillé », « Installé »). */
@Composable
fun ConsoleTag(text: String, modifier: Modifier = Modifier, color: Color = ConsoleColors.Selected) {
    Text(
        text,
        modifier.clip(PillShape).background(color.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}
