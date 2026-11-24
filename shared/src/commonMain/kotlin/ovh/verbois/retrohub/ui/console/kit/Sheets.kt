package ovh.verbois.retrohub.ui.console.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.LocalReducedMotion
import ovh.verbois.retrohub.ui.components.LocalScreenActive
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/**
 * The PS5 options panel: slides in from the right over a dimmed screen. B (or Start) closes it.
 * The caller makes the screen underneath inactive while [visible].
 */
@Composable
fun ConsoleSideSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 440.dp,
    initialFocus: FocusRequester? = null,
    tag: String = "console-side-sheet",
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val dismiss by rememberUpdatedState(onDismiss)
    AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(tween(if (reduced) 0 else 140)), exit = fadeOut(tween(if (reduced) 0 else 120))) {
        Box(Modifier.fillMaxSize().background(ConsoleColors.Scrim).pointerInput(Unit) { detectTapGestures { dismiss() } })
    }
    AnimatedVisibility(
        visible,
        Modifier.fillMaxSize(),
        enter = slideInHorizontally(tween(if (reduced) 0 else 200)) { it / 3 } + fadeIn(tween(if (reduced) 0 else 160)),
        exit = slideOutHorizontally(tween(if (reduced) 0 else 140)) { it / 3 } + fadeOut(tween(if (reduced) 0 else 120)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier.width(width).fillMaxHeight().testTag(tag)
                    .background(Brush.horizontalGradient(listOf(Palette.Deep.copy(alpha = 0.96f), Palette.Deep)))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.ButtonB, Key.Escape, Key.ButtonStart -> {
                                if (event.type == KeyEventType.KeyUp) dismiss()
                                true
                            }

                            Key.ButtonY, Key.ButtonL1, Key.ButtonR1, Key.ButtonSelect -> true

                            else -> false
                        }
                    }
                    .focusGroup().padding(top = 20.dp, bottom = HintBarHeight),
            ) {
                Text(title, Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall)
                CompositionLocalProvider(LocalScreenActive provides visible) {
                    ProvideHints(Hint(Pad.A, "Choisir"), Hint(Pad.B, "Fermer") { dismiss() })
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        content = content,
                    )
                    if (initialFocus != null && visible) AutoFocus(initialFocus)
                }
            }
        }
    }
}

/** A small uppercase heading between groups of options. */
@Composable
fun OptionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = dim)
}

/** One choice: label, optional description, and a selection mark (or a value and an arrow). */
@Composable
fun OptionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    value: String? = null,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    FocusSurface(
        onClick,
        modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { if (selected != null) this.selected = selected },
        tone = if (danger) Tone.DANGER else Tone.QUIET,
        enabled = enabled,
        role = if (selected != null) Role.RadioButton else Role.Button,
        focusScale = 1.015f,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = dim)
            }
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.invoke()
            when {
                selected != null -> SelectionMark(selected, ring = false)
                value != null && trailing == null -> Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = dim)
            }
        }
    }
}
