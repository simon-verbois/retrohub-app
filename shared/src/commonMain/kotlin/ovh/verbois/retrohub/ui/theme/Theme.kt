package ovh.verbois.retrohub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import ovh.verbois.retrohub.emulation.ThemeMode
import ovh.verbois.retrohub.resources.Res
import ovh.verbois.retrohub.resources.manrope_extrabold
import ovh.verbois.retrohub.resources.manrope_light
import ovh.verbois.retrohub.resources.manrope_regular
import ovh.verbois.retrohub.resources.manrope_semibold

/** The azure console sky, opaque text and glossy controls, in day and night variants. */
@Immutable
data class SkyScheme(
    val dark: Boolean,
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    val glowTop: Color,
    val glowA: Color,
    val glowB: Color,
    val glowBottom: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val glass: Color,
    val glassStrong: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val deep: Color,
    val scrim: Color,
    val field: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val buttonTop: Color,
    val buttonBottom: Color,
    val buttonFocusTop: Color,
    val buttonFocusBottom: Color,
    val primaryTop: Color,
    val primaryBottom: Color,
)

val LightSky =
    SkyScheme(
        dark = false,
        skyTop = Color(0xFF0C6F9D),
        skyMid = Color(0xFF0B638F),
        skyLow = Color(0xFF084C76),
        glowTop = Color(0xFF0C7196),
        glowA = Color(0x181AA7BC),
        glowB = Color(0x142C8FAA),
        glowBottom = Color(0x60032A4B),
        text = Color(0xFFFFFFFF),
        textDim = Color(0xFFF0FAFF),
        textFaint = Color(0xFFEAF8FF),
        glass = Color(0x70062645),
        glassStrong = Color(0xB3094868),
        line = Color(0x548FDBEF),
        accent = Color(0xFFF3FDFF),
        onAccent = Color(0xFF043349),
        deep = Color(0xFF063751),
        scrim = Color(0xE6032339),
        field = Color(0xE006304A),
        success = Color(0xFF7BE8B0),
        warning = Color(0xFFFFD27A),
        danger = Color(0xFFFFB4AB),
        buttonTop = Color(0xFF0D668F),
        buttonBottom = Color(0xFF06446C),
        buttonFocusTop = Color(0xFF096E96),
        buttonFocusBottom = Color(0xFF075F8D),
        primaryTop = Color(0xFFFFFFFF),
        primaryBottom = Color(0xFFCDEFFF),
    )

val DarkSky =
    SkyScheme(
        dark = true,
        skyTop = Color(0xFF06283D),
        skyMid = Color(0xFF062337),
        skyLow = Color(0xFF041B2C),
        glowTop = Color(0xFF0B3E58),
        glowA = Color(0x142487AA),
        glowB = Color(0x102A8FAD),
        glowBottom = Color(0x8C02101C),
        text = Color(0xFFFFFFFF),
        textDim = Color(0xFFDFF4FF),
        textFaint = Color(0xFFC4E4F2),
        glass = Color(0x70041525),
        glassStrong = Color(0xB3083B55),
        line = Color(0x548FDBEF),
        accent = Color(0xFF86DDFF),
        onAccent = Color(0xFF03273A),
        deep = Color(0xFF052A3F),
        scrim = Color(0xE6021423),
        field = Color(0xE0032033),
        success = Color(0xFF6FD9A2),
        warning = Color(0xFFF5C86A),
        danger = Color(0xFFFFA59A),
        buttonTop = Color(0xFF0A5474),
        buttonBottom = Color(0xFF06344F),
        buttonFocusTop = Color(0xFF0A698E),
        buttonFocusBottom = Color(0xFF084B73),
        primaryTop = Color(0xFFB7F1FF),
        primaryBottom = Color(0xFF60CDEC),
    )

/**
 * Jetons de couleur de l’interface. Ils suivent le thème courant (clair ou sombre) : les lire
 * dans une composition suffit pour que l’écran se redessine quand le thème change.
 */
object Palette {
    var scheme by mutableStateOf(LightSky)

    val SkyTop get() = scheme.skyTop
    val SkyMid get() = scheme.skyMid
    val SkyLow get() = scheme.skyLow
    val Background get() = scheme.skyMid
    val BackgroundHigh get() = scheme.deep
    val Deep get() = scheme.deep
    val Scrim get() = scheme.scrim
    val Field get() = scheme.field
    val Surface get() = scheme.glass
    val Raised get() = scheme.glassStrong
    val Glass get() = scheme.glass
    val GlassLight get() = scheme.glass
    val Line get() = scheme.line
    val Text get() = scheme.text
    val TextDim get() = scheme.textDim
    val TextFaint get() = scheme.textFaint
    val Accent get() = scheme.accent
    val OnAccent get() = scheme.onAccent
    val Select get() = scheme.text
    val Success get() = scheme.success
    val Danger get() = scheme.danger
    val Warning get() = scheme.warning
    val ButtonTop get() = scheme.buttonTop
    val ButtonBottom get() = scheme.buttonBottom
    val ButtonFocusTop get() = scheme.buttonFocusTop
    val ButtonFocusBottom get() = scheme.buttonFocusBottom
    val PrimaryTop get() = scheme.primaryTop
    val PrimaryBottom get() = scheme.primaryBottom

    // Boutons des manettes : couleurs fixes, lisibles sur les deux ciels.
    val PadA = Color(0xFF3FB27F)
    val PadB = Color(0xFFE0565B)
    val PadX = Color(0xFF5B8DEF)
    val PadY = Color(0xFFE3B341)
}

@Immutable
data class RetroHubFonts(val display: FontFamily, val body: FontFamily)

val LocalFonts = staticCompositionLocalOf { RetroHubFonts(FontFamily.Default, FontFamily.Default) }

@Composable
fun RetroHubTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val scheme = if (dark) DarkSky else LightSky
    if (Palette.scheme != scheme) Palette.scheme = scheme
    // Manrope partout, comme le site ; les titres en ExtraBold.
    val body =
        FontFamily(
            Font(Res.font.manrope_light, FontWeight.Light),
            Font(Res.font.manrope_regular, FontWeight.Normal),
            Font(Res.font.manrope_semibold, FontWeight.SemiBold),
            Font(Res.font.manrope_semibold, FontWeight.Medium),
            Font(Res.font.manrope_extrabold, FontWeight.Bold),
            Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
        )
    val base = TextStyle(fontFamily = body, color = scheme.text)
    val title = base.copy(fontWeight = FontWeight.ExtraBold)
    val typography =
        MaterialTheme.typography.copy(
            displayLarge = title.copy(fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.8).sp),
            displayMedium = title.copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
            displaySmall = title.copy(fontSize = 27.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
            headlineSmall = title.copy(fontSize = 20.sp, lineHeight = 26.sp),
            headlineMedium = title.copy(fontSize = 22.sp, lineHeight = 28.sp),
            titleLarge = base.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
            titleMedium = base.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = base.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
            bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp, color = scheme.textDim),
            labelLarge = base.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
            labelMedium = base.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
            labelSmall = base.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
        )
    val colors =
        darkColorScheme(
            primary = scheme.accent,
            onPrimary = scheme.onAccent,
            secondary = scheme.text,
            onSecondary = scheme.onAccent,
            background = scheme.skyMid,
            onBackground = scheme.text,
            surface = scheme.skyLow,
            onSurface = scheme.text,
            surfaceVariant = scheme.glassStrong,
            onSurfaceVariant = scheme.textDim,
            outline = scheme.line,
            error = scheme.danger,
        )
    CompositionLocalProvider(LocalFonts provides RetroHubFonts(body, body), LocalContentColor provides scheme.text) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** Couleur d’accent d’une console, reprise du web. */
fun consoleColor(console: String): Color = ovh.verbois.retrohub.emulation.Consoles[console]?.color?.let { Color(it) } ?: Palette.Select
