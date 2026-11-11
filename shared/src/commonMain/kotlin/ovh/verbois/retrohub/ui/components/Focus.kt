package ovh.verbois.retrohub.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.theme.Palette

/** Retour haptique léger, fourni par la plateforme (désactivable dans les réglages). */
val LocalHaptics = staticCompositionLocalOf<() -> Unit> { {} }

/** Petit son de navigation, quand le focus bouge à la manette (réglage « Sons de navigation »). */
val LocalNavSound = staticCompositionLocalOf<() -> Unit> { {} }

/** Le bouton A de la manette (et Entrée, OK de télécommande) valide l’élément qui a le focus. */
internal val ConfirmKeys = setOf(Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar)

/**
 * Élément sélectionnable à la manette, au clavier et au toucher : anneau de focus blanc quand on
 * navigue aux touches (comme :focus-visible), léger agrandissement, enfoncement au toucher.
 */
@Composable
fun Modifier.consoleFocus(
    shape: Shape,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    focusScale: Float = 1.04f,
    ringWidth: Dp = 3.dp,
    role: Role = Role.Button,
    label: String? = null,
    onFocus: (Boolean) -> Unit = {},
    /** Appui long, ou bouton □ / X de la manette : le menu de l’élément. */
    onLongClick: (() -> Unit)? = null,
): Modifier {
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
    val ring by animateFloatAsState(if (focused && keyboard) 1f else 0f)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.onFocusChanged {
            if (it.isFocused && !focused && keyboard) navSound()
            focused = it.isFocused
            onFocus(it.isFocused)
        }.onKeyEvent { event ->
            if (enabled && onClick != null && event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                haptics()
                onClick()
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
                    haptics()
                    it()
                }
            },
        ) {
            haptics()
            onClick?.invoke()
        }.then(if (ring > 0f) Modifier.border(ringWidth, Palette.Focus.copy(alpha = ring), shape) else Modifier)
}

/**
 * Donne le focus à l’élément principal d’un écran dès qu’on navigue à la manette ou au clavier
 * (en mode tactile, Compose ne focalise pas les éléments cliquables).
 */
@Composable
fun AutoFocus(requester: androidx.compose.ui.focus.FocusRequester) {
    val mode = LocalInputModeManager.current.inputMode
    androidx.compose.runtime.LaunchedEffect(mode) {
        kotlinx.coroutines.delay(60)
        runCatching { requester.requestFocus() }
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
