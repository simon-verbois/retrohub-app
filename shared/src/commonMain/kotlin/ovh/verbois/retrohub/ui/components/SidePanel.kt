package ovh.verbois.retrohub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.verbois.retrohub.ui.theme.Palette

/** Landscape typography is deliberately lighter than the portrait cards and forms. */
@Composable
internal fun XmbTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val type = remember(base) {
        val shadow = Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 1f), 3f)
        base.copy(
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 20.sp, lineHeight = 26.sp, shadow = shadow),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 25.sp, shadow = shadow),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Normal, shadow = shadow),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Normal, shadow = shadow),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, shadow = shadow),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Normal),
            labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        )
    }
    MaterialTheme(typography = type, content = content)
}

/** One modal focus boundary, with a visible parent at the left and a reversible entrance. */
@Composable
internal fun XmbSidePanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    tag: String = "xmb-options-panel",
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val isExpanded = rememberUpdatedState(expanded)
    val dismiss by rememberUpdatedState({ if (expanded) onDismiss() })
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return
    Dialog(onDismissRequest = { dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val distance = with(LocalDensity.current) { 36.dp.roundToPx() }
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = fadeIn(tween(if (reduced) 0 else 160)) + slideInHorizontally(tween(if (reduced) 0 else 180)) { distance },
                exit = fadeOut(tween(if (reduced) 0 else 100)) + slideOutHorizontally(tween(if (reduced) 0 else 120)) { distance },
            ) {
                Box(
                    modifier.width((maxWidth * if (wide) 0.70f else 0.48f).coerceAtMost(if (wide) 1200.dp else 480.dp))
                        .fillMaxHeight().testTag(tag)
                        .background(Brush.horizontalGradient(listOf(Palette.SkyMid.copy(alpha = 0.96f), Palette.SkyLow)))
                        .controllerDialog({ dismiss() }, dismissOnStart = true)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    CompositionLocalProvider(LocalScreenActive provides isExpanded.value, LocalOverlayControls provides true) { XmbTheme(content) }
                }
            }
        }
    }
}
