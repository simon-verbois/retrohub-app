package ovh.verbois.retrohub.ui.console.kit

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ovh.verbois.retrohub.ui.components.ConsoleDensity
import ovh.verbois.retrohub.ui.components.ConsoleTone
import ovh.verbois.retrohub.ui.components.LocalConsoleStyle
import ovh.verbois.retrohub.ui.theme.Palette

/**
 * Console colors. Focus never draws an outline: the focused control turns white and its content dark,
 * as on a PS5. Selection keeps a cyan mark, so a chosen value never looks focused.
 */
object ConsoleColors {
    val Focus = ConsoleTone.Focus
    val OnFocus = ConsoleTone.OnFocus
    val Selected = ConsoleTone.Selected
    val OnSelected = ConsoleTone.OnSelected
    val Danger get() = Palette.Danger
    val Surface get() = Palette.Glass
    val SurfaceHigh get() = Palette.Raised
    val Line get() = Palette.Line
    val Scrim = Color(0xB3021624)
}

/** Text that follows the content color, so it inverts inside a focused control. */
val dim: Color
    @Composable @ReadOnlyComposable
    get() = LocalContentColor.current.copy(alpha = 0.74f)

val faint: Color
    @Composable @ReadOnlyComposable
    get() = LocalContentColor.current.copy(alpha = 0.55f)

private fun TextStyle.contentColored(weight: FontWeight? = null) = copy(color = Color.Unspecified, fontWeight = weight ?: fontWeight, shadow = null)

/**
 * The landscape typography: light weights like the XMB, and no fixed color, so every text takes the
 * content color of its container (white on the sky, dark on a focused control).
 */
@Composable
fun ConsoleTheme(content: @Composable () -> Unit) = ConsoleTheme(enabled = true, content)

/** [enabled] false keeps the current theme; the composition structure is the same either way. */
@Composable
fun ConsoleTheme(enabled: Boolean, content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val type = remember(base, enabled) {
        if (!enabled) return@remember base
        Typography(
            displayLarge = base.displayLarge.contentColored(),
            displayMedium = base.displayMedium.contentColored(),
            displaySmall = base.displaySmall.contentColored(),
            headlineLarge = base.headlineLarge.contentColored(),
            headlineMedium = base.headlineMedium.contentColored(),
            headlineSmall = base.headlineSmall.contentColored(FontWeight.Light).copy(fontSize = 20.sp, lineHeight = 26.sp),
            titleLarge = base.titleLarge.contentColored(FontWeight.Normal).copy(fontSize = 19.sp, lineHeight = 25.sp),
            titleMedium = base.titleMedium.contentColored(FontWeight.Normal),
            titleSmall = base.titleSmall.contentColored(FontWeight.Normal),
            bodyLarge = base.bodyLarge.contentColored(),
            bodyMedium = base.bodyMedium.contentColored(),
            bodySmall = base.bodySmall.contentColored().copy(fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = base.labelLarge.contentColored(FontWeight.SemiBold),
            labelMedium = base.labelMedium.contentColored(FontWeight.Normal).copy(letterSpacing = 0.sp),
            labelSmall = base.labelSmall.contentColored(),
        )
    }
    CompositionLocalProvider(LocalContentColor provides if (enabled) Palette.Text else LocalContentColor.current) {
        MaterialTheme(typography = type, content = content)
    }
}

/** Console look for screens drawn outside the console shell (first launch, setup, global dialogs). */
@Composable
fun ConsoleStyleScope(enabled: Boolean = true, content: @Composable () -> Unit) {
    // Same structure on and off, so rotating keeps the state of the screens inside.
    ConsoleDensity(enabled) {
        CompositionLocalProvider(LocalConsoleStyle provides enabled) { ConsoleTheme(enabled, content) }
    }
}
