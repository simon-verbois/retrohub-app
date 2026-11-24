package ovh.verbois.retrohub.ui.console

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

/** A cached radial brush supplies the soft selection halo without a full-screen blur pass. */
internal fun Modifier.xmbHalo(intensity: () -> Float): Modifier = drawWithCache {
    val radius = size.minDimension * 0.7f
    val halo = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.26f),
        0.45f to Color.White.copy(alpha = 0.10f),
        1f to Color.Transparent,
        center = Offset(size.width / 2, size.height / 2),
        radius = radius,
    )
    onDrawBehind { drawCircle(halo, radius, alpha = intensity()) }
}
