package ovh.verbois.retrohub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/**
 * Set by the landscape console shell: shared rows, buttons and focus switch to the console look (focused
 * control turns white with dark content, never an outline). The portrait interface never sets it.
 */
val LocalConsoleStyle = staticCompositionLocalOf { false }

/**
 * The density before the console scope shrinks the interface. Touch controls in a game keep it, so
 * fingers get the same targets whatever the layout of the menus.
 */
val LocalBaseDensity = staticCompositionLocalOf<androidx.compose.ui.unit.Density?> { null }

/** The screen height the console interface is drawn for; shorter screens scale it down. */
private val CONSOLE_DESIGN_HEIGHT = 470.dp
private const val CONSOLE_MIN_SCALE = 0.8f

/**
 * Landscape phones are short: the console interface shrinks to fit a whole screen without scrolling,
 * like a TV interface. Bigger screens keep their size. [enabled] false keeps the same structure.
 */
@Composable
fun ConsoleDensity(enabled: Boolean, content: @Composable () -> Unit) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(androidx.compose.ui.Modifier.fillMaxSize()) {
        val scale = if (enabled && maxHeight > 0.dp && maxHeight != androidx.compose.ui.unit.Dp.Infinity) {
            (maxHeight / CONSOLE_DESIGN_HEIGHT).coerceIn(CONSOLE_MIN_SCALE, 1f)
        } else {
            1f
        }
        CompositionLocalProvider(
            LocalBaseDensity provides (LocalBaseDensity.current ?: base),
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * scale, base.fontScale),
            content = content,
        )
    }
}

/** Console focus and selection colors, shared by the common rows and the console kit. */
object ConsoleTone {
    val Focus = Color.White
    val OnFocus = Color(0xFF062B42)
    val Selected = Color(0xFF8AF0FF)
    val OnSelected = Color(0xFF032536)
    val Rest = Color(0x33062645)
}

internal val ConsoleRowShape = RoundedCornerShape(14.dp)

/**
 * A focusable surface that turns white with dark content under controller or keyboard focus. Its content
 * reads [LocalContentColor]. [rest] is the background at rest, [restContent] the content color at rest.
 */
@Composable
fun FocusFill(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = ConsoleRowShape,
    rest: Color = Color.Transparent,
    restContent: Color = Color.White,
    enabled: Boolean = true,
    focusScale: Float = 1.02f,
    role: Role = Role.Button,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val shown = focused && keyboard && enabled
    val reduced = LocalReducedMotion.current
    val background by animateColorAsState(if (shown) ConsoleTone.Focus else rest, if (reduced) snap() else tween(110), label = "focus-fill")
    Box(
        modifier
            .then(if (shown) Modifier.shadow(10.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .consoleFocus(
                shape,
                onClick,
                enabled = enabled,
                focusScale = focusScale,
                highlightFocus = false,
                role = role,
                label = label,
                onLongClick = onLongClick,
                onFocus = {
                    focused = it
                    onFocus(it)
                },
            )
            .clip(shape)
            .background(background)
            .alpha(if (enabled) 1f else 0.42f),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (shown) ConsoleTone.OnFocus else restContent) {
            MaterialTheme(typography = contentColoredTypography()) { content(shown) }
        }
    }
}

/** The current typography without fixed colors, so text follows [LocalContentColor] (dark on a focused fill). */
@Composable
fun contentColoredTypography(): androidx.compose.material3.Typography {
    val base = MaterialTheme.typography
    return remember(base) {
        fun androidx.compose.ui.text.TextStyle.free() = copy(color = Color.Unspecified, shadow = null)
        androidx.compose.material3.Typography(
            displayLarge = base.displayLarge.free(),
            displayMedium = base.displayMedium.free(),
            displaySmall = base.displaySmall.free(),
            headlineLarge = base.headlineLarge.free(),
            headlineMedium = base.headlineMedium.free(),
            headlineSmall = base.headlineSmall.free(),
            titleLarge = base.titleLarge.free(),
            titleMedium = base.titleMedium.free(),
            titleSmall = base.titleSmall.free(),
            bodyLarge = base.bodyLarge.free(),
            bodyMedium = base.bodyMedium.free(),
            bodySmall = base.bodySmall.free(),
            labelLarge = base.labelLarge.free(),
            labelMedium = base.labelMedium.free(),
            labelSmall = base.labelSmall.free(),
        )
    }
}

/** The console look of [SettingRow]. */
@Composable
internal fun ConsoleSettingRow(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    icon: ImageVector?,
    onClick: (() -> Unit)?,
    role: Role,
    value: String?,
    tint: Color,
    trailing: @Composable (() -> Unit)?,
) {
    val body: @Composable () -> Unit = {
        Row(
            // Compact rows: a whole settings section fits the landscape screen without scrolling.
            Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(19.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.72f))
            }
            if (value != null) {
                Text(
                    value,
                    Modifier.widthIn(max = 260.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.74f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
    val restContent = if (tint == Palette.Text) Color.White else tint
    if (onClick == null) {
        Box(modifier.fillMaxWidth()) { CompositionLocalProvider(LocalContentColor provides restContent) { body() } }
    } else {
        FocusFill(onClick, modifier.fillMaxWidth(), restContent = restContent, role = role, focusScale = 1.015f) { body() }
    }
}

/** A console on/off switch that inverts with its row. */
@Composable
internal fun ConsoleToggle(checked: Boolean) {
    val content = LocalContentColor.current
    val track = if (checked) ConsoleTone.Selected else content.copy(alpha = 0.22f)
    Box(Modifier.width(44.dp).height(26.dp).clip(RoundedCornerShape(13.dp)).background(track).padding(3.dp)) {
        Box(
            Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).size(20.dp).clip(CircleShape)
                .background(if (checked) ConsoleTone.OnSelected else content),
        )
    }
}

/** The console look of [ChoiceRow]: the row shows the value; A opens a centered list with a check. */
@Composable
internal fun <T> ConsoleChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String?,
    icon: ImageVector?,
    describe: ((T) -> String?)?,
    recommended: T?,
) {
    var open by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    ConsoleSettingRow(
        title,
        Modifier.focusRequester(rowFocus),
        subtitle,
        icon,
        { open = true },
        Role.Button,
        label(selected),
        Palette.Text,
    ) { Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = LocalContentColor.current.copy(alpha = 0.6f)) }
    if (open) {
        ConsoleChoiceDialog(title, options, selected, label, describe, recommended, onDismiss = {
            open = false
            runCatching { rowFocus.requestFocus() }
        }) {
            onSelect(it)
            open = false
            runCatching { rowFocus.requestFocus() }
        }
    }
}

/** A centered list of choices; the current one has a cyan check and receives the focus. */
@Composable
fun <T> ConsoleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    describe: ((T) -> String?)? = null,
    recommended: T? = null,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val dismiss by rememberUpdatedState(onDismiss)
    Dialog(onDismissRequest = { dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.padding(24.dp)) {
            Column(
                Modifier.widthIn(min = 360.dp, max = 520.dp).heightIn(max = maxHeight).clip(RoundedCornerShape(22.dp)).background(Palette.Deep)
                    .focusGroup().controllerDialog({ dismiss() }, dismissOnStart = true).padding(vertical = 16.dp),
            ) {
                Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    options.forEachIndexed { index, option ->
                        val chosen = option == selected
                        FocusFill(
                            { onSelect(option) },
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { this.selected = chosen }
                                .then(if (chosen || (selected !in options && index == 0)) Modifier.focusRequester(focus) else Modifier),
                            role = Role.RadioButton,
                            focusScale = 1.01f,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(label(option), style = MaterialTheme.typography.titleMedium)
                                        if (option == recommended) {
                                            Text(
                                                "Conseillé",
                                                Modifier.clip(RoundedCornerShape(50)).background(Palette.Success.copy(alpha = 0.25f))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    describe?.invoke(option)?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.72f))
                                    }
                                }
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape).background(if (chosen) ConsoleTone.Selected else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (chosen) Icon(Lucide.Check, null, Modifier.size(14.dp), tint = ConsoleTone.OnSelected)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Focus the current choice on the first frame, before any key can move it elsewhere.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
}

/** The console look of [SliderRow]: Left and Right change the value while the row has the focus. */
@Composable
internal fun ConsoleSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    valueLabel: String,
    subtitle: String?,
    icon: ImageVector?,
    steps: Int,
) {
    val step = (range.endInclusive - range.start) / (if (steps > 0) steps + 1 else 20)
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    FocusFill(
        null,
        Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            val direction = when (event.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown) change((current + direction * step).coerceIn(range.start, range.endInclusive))
            true
        },
        role = Role.Button,
        focusScale = 1.015f,
    ) { focused ->
        Column(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(19.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.72f))
                }
                if (focused) Icon(Lucide.ChevronLeft, null, Modifier.size(16.dp))
                Text(valueLabel, style = MaterialTheme.typography.titleSmall)
                if (focused) Icon(Lucide.ChevronRight, null, Modifier.size(16.dp))
            }
            val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
                val content = LocalContentColor.current
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(content.copy(alpha = 0.2f)))
                Box(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (focused) ConsoleTone.OnFocus else ConsoleTone.Selected),
                )
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = (maxWidth - 18.dp) * fraction).size(18.dp).clip(CircleShape)
                        .background(if (focused) ConsoleTone.OnFocus else Color.White),
                )
                // Touch: tap or drag along the bar.
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight().sliderTouch { change(range.start + it * (range.endInclusive - range.start)) },
                )
            }
        }
    }
}

private fun Modifier.sliderTouch(onFraction: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onFraction((down.position.x / size.width).coerceIn(0f, 1f))
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull() ?: break
            if (!change.pressed) break
            onFraction((change.position.x / size.width).coerceIn(0f, 1f))
            change.consume()
        }
    }
}

/** A controller button glyph for dialogs (A green, B red), drawn without the console kit. */
@Composable
internal fun DialogPadGlyph(letter: String, color: Color) {
    Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/**
 * The console confirmation: a compact window that fits the screen, buttons labelled with their controller
 * button (B to cancel, A to confirm), the destructive action in red and the focus on the safe one.
 */
@Composable
internal fun ConsoleConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String,
    destructive: Boolean,
    focusDismiss: Boolean,
) {
    val dismissFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val close by rememberUpdatedState(onDismiss)
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.padding(24.dp)) {
            Column(
                Modifier.widthIn(min = 380.dp, max = 560.dp).heightIn(max = maxHeight).clip(RoundedCornerShape(24.dp)).background(Palette.Deep)
                    .focusGroup().controllerDialog({ close() }).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (text.isNotBlank()) {
                    Text(
                        text,
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    FocusFill(
                        onDismiss,
                        Modifier.heightIn(min = 48.dp).focusRequester(dismissFocus),
                        shape = RoundedCornerShape(percent = 50),
                        rest = ConsoleTone.Rest,
                        focusScale = 1.04f,
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DialogPadGlyph("B", Palette.PadB)
                            Text(dismiss, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    FocusFill(
                        onConfirm,
                        Modifier.heightIn(min = 48.dp).focusRequester(confirmFocus),
                        shape = RoundedCornerShape(percent = 50),
                        rest = if (destructive) Palette.Danger.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.24f),
                        restContent = if (destructive) Palette.Danger else Color.White,
                        focusScale = 1.04f,
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DialogPadGlyph("A", Palette.PadA)
                            Text(confirm, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        AutoFocus(if (focusDismiss || destructive) dismissFocus else confirmFocus)
    }
}
