package ovh.verbois.retrohub.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.ui.theme.Palette

private class FocusActivity {
    var generation = 0
}

private val LocalFocusActivity = staticCompositionLocalOf { FocusActivity() }

/** Retour haptique léger, fourni par la plateforme (désactivable dans les réglages). */
val LocalHaptics = staticCompositionLocalOf<() -> Unit> { {} }

/** Petit son de navigation, quand le focus bouge à la manette (réglage « Sons de navigation »). */
val LocalNavSound = staticCompositionLocalOf<() -> Unit> { {} }

/** Outgoing animated screens must not reclaim focus from the incoming page. */
val LocalScreenActive = staticCompositionLocalOf { true }

/** Le bouton A de la manette (et Entrée, OK de télécommande) valide l’élément qui a le focus. */
internal val ConfirmKeys = setOf(Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar)

/**
 * Controller and keyboard focus uses a soft highlight and scale; touch presses gently shrink.
 */
@Composable
fun Modifier.consoleFocus(
    shape: Shape,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    focusScale: Float = 1.04f,
    highlightFocus: Boolean = true,
    role: Role = Role.Button,
    label: String? = null,
    onFocus: (Boolean) -> Unit = {},
    /** Appui long, ou bouton □ / X de la manette : le menu de l’élément. */
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val activity = LocalFocusActivity.current
    val intoView = remember { BringIntoViewRequester() }
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val pressed by interaction.collectIsPressedAsState()
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val haptics = LocalHaptics.current
    val navSound = LocalNavSound.current
    val scale by animateFloatAsState(
        when {
            pressed -> 0.97f
            focused && keyboard -> focusScale
            else -> 1f
        },
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
    )
    val highlight by animateFloatAsState(if (focused && keyboard && highlightFocus) 1f else 0f)
    LaunchedEffect(focused) { if (focused) intoView.bringIntoView() }
    return this
        .bringIntoViewRequester(intoView)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.onFocusChanged {
            if (it.isFocused && !focused) {
                if (keyboard) navSound()
            }
            focused = it.isFocused
            onFocus(it.isFocused)
        }.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) activity.generation++
            if (enabled && onClick != null && event.key == Key.ButtonA) {
                // Consume both edges before combinedClickable can perform its own activation.
                if (event.type == KeyEventType.KeyUp) {
                    haptics()
                    onClick()
                }
                true
            } else if (enabled && onLongClick != null && event.key == Key.ButtonX) {
                if (event.type == KeyEventType.KeyUp) {
                    haptics()
                    onLongClick()
                }
                true
            } else {
                false
            }
        }.combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = label,
            onLongClick =
            onLongClick?.let {
                {
                    activity.generation++
                    haptics()
                    it()
                }
            },
        ) {
            activity.generation++
            haptics()
            onClick?.invoke()
        }.drawWithContent {
            drawContent()
            if (highlight > 0f) {
                drawOutline(shape.createOutline(size, layoutDirection, this), Palette.ButtonFocusTop.copy(alpha = highlight * 0.22f))
            }
        }
}

/**
 * Donne le focus à l’élément principal d’un écran dès qu’on navigue à la manette ou au clavier
 * (en mode tactile, Compose ne focalise pas les éléments cliquables).
 */
@Composable
fun AutoFocus(requester: androidx.compose.ui.focus.FocusRequester) {
    val mode = LocalInputModeManager.current.inputMode
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val activity = LocalFocusActivity.current
    val active = LocalScreenActive.current
    val target by androidx.compose.runtime.rememberUpdatedState(requester)
    val generation = activity.generation
    androidx.compose.runtime.LaunchedEffect(mode, windowFocused, active) {
        if (mode != InputMode.Keyboard || !windowFocused || !active) return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        // A delayed default must never override a direction the player has already chosen.
        if (activity.generation == generation) runCatching { target.requestFocus() }
    }
}

@Composable
fun ProvideHaptics(enabled: Boolean, vibrate: () -> Unit, sound: (() -> Unit)? = null, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHaptics provides if (enabled) vibrate else ({}),
        LocalNavSound provides (sound ?: {}),
        content = content,
    )
}

/** Explicit parent/child link between the section bar and the current screen. */
class NavigationFocus {
    val header = androidx.compose.ui.focus.FocusRequester()
    var content by mutableStateOf(androidx.compose.ui.focus.FocusRequester.Default)
}

val LocalNavigationFocus = staticCompositionLocalOf { NavigationFocus() }

/** A Guide hold is available inside popup windows as well as the activity window. */
val LocalReturnHome = staticCompositionLocalOf<() -> Unit> { {} }

/** Dismiss the local overlay first and keep global shortcuts out of the page underneath. */
@Composable
fun Modifier.controllerDialog(onDismiss: () -> Unit, dismissOnStart: Boolean = false): Modifier {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val returnHome by androidx.compose.runtime.rememberUpdatedState(LocalReturnHome.current)
    val dismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val guide = remember { ovh.verbois.retrohub.game.GuideButton(scope, { dismiss() }, { returnHome() }) }
    androidx.compose.runtime.DisposableEffect(guide) { onDispose { guide.cancel() } }
    return onPreviewKeyEvent { event ->
        if (event.key == Key.ButtonMode || event.key == Key.Menu) {
            guide.event(event.type == KeyEventType.KeyDown)
            true
        } else {
            false
        }
    }.onKeyEvent { event ->
        val back = event.key == Key.ButtonB || event.key == Key.Escape || (dismissOnStart && event.key == Key.ButtonStart)
        if (back) {
            if (event.type == KeyEventType.KeyUp) onDismiss()
            true
        } else {
            event.key in setOf(Key.ButtonY, Key.ButtonL1, Key.ButtonR1, Key.ButtonStart, Key.ButtonSelect)
        }
    }
}

/** Scroll past the last setting too, so informational storage rows stay reachable with a pad. */
@Composable
fun Modifier.controllerVerticalScroll(scroll: androidx.compose.foundation.ScrollState): Modifier {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val step = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    return verticalScroll(scroll).focusGroup().onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.DirectionDown -> androidx.compose.ui.focus.FocusDirection.Down
            Key.DirectionUp -> androidx.compose.ui.focus.FocusDirection.Up
            else -> return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown && !focus.moveFocus(direction)) {
            scope.launch { scroll.animateScrollBy(if (event.key == Key.DirectionDown) step else -step) }
        }
        true
    }
}
