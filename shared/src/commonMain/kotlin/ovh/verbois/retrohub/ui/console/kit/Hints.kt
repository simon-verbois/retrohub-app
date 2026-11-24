package ovh.verbois.retrohub.ui.console.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.verbois.retrohub.ui.components.LocalScreenActive
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/** Controller buttons shown in the hint bar (Xbox layout, the most common on Android). */
enum class Pad(val glyph: String, val spoken: String) {
    A("A", "A"),
    B("B", "B"),
    X("X", "X"),
    Y("Y", "Y"),
    LB_RB("LB RB", "LB et RB"),
    START("", "Start"),
    GUIDE("", "Guide"),
    DPAD("", "Croix directionnelle"),
}

/** One action of the current screen; [action] makes it reachable by touch too. */
data class Hint(val pad: Pad, val label: String, val action: (() -> Unit)? = null)

/** The hints of the visible screen: the last registered set wins (pages, panels, dialogs stack). */
class HintRegistry {
    internal val entries = mutableStateListOf<Pair<Any, List<Hint>>>()
    val current: List<Hint> get() = entries.lastOrNull()?.second.orEmpty()

    internal fun put(owner: Any, hints: List<Hint>) {
        val index = entries.indexOfFirst { it.first === owner }
        if (index >= 0) {
            if (entries[index].second != hints) entries[index] = owner to hints
        } else {
            entries += owner to hints
        }
    }

    internal fun remove(owner: Any) {
        entries.removeAll { it.first === owner }
    }
}

val LocalHints = staticCompositionLocalOf<HintRegistry?> { null }

/** Declares the actions of this screen while it is the active one. */
@Composable
fun ProvideHints(vararg hints: Hint) {
    val registry = LocalHints.current ?: return
    val active = LocalScreenActive.current
    val owner = remember { Any() }
    val list = hints.toList()
    SideEffect { if (active) registry.put(owner, list) else registry.remove(owner) }
    DisposableEffect(registry, owner) { onDispose { registry.remove(owner) } }
}

/** A controller button drawn as a small glyph. */
@Composable
fun PadGlyph(pad: Pad, modifier: Modifier = Modifier) {
    val face = when (pad) {
        Pad.A -> Palette.PadA
        Pad.B -> Palette.PadB
        Pad.X -> Palette.PadX
        Pad.Y -> Palette.PadY
        else -> null
    }
    if (face != null) {
        Box(modifier.size(22.dp).clip(CircleShape).background(face), contentAlignment = Alignment.Center) {
            Text(pad.glyph, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Color.White)
        }
        return
    }
    Box(
        modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (pad) {
            Pad.START -> Icon(Lucide.Menu, null, Modifier.size(14.dp), tint = Color.White)
            Pad.GUIDE -> Icon(Lucide.House, null, Modifier.size(14.dp), tint = Color.White)
            Pad.DPAD -> Icon(Lucide.Move, null, Modifier.size(14.dp), tint = Color.White)
            else -> Text(pad.glyph, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

/** The bottom bar: what each button does here. Tapping a hint performs it. */
@Composable
fun HintBar(hints: List<Hint>, modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("hint-bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { hint ->
            Row(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .then(if (hint.action != null) Modifier.clickable(role = Role.Button) { hint.action.invoke() } else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = "${hint.pad.spoken} : ${hint.label}" }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                PadGlyph(hint.pad)
                Text(hint.label, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
}

/**
 * Screens drawn before the console shell (first launch, setup) get a fixed hint bar too. [enabled]
 * false (portrait) keeps the same structure, so rotating never resets the screen inside.
 */
@Composable
fun StandaloneHints(enabled: Boolean, hints: List<Hint>, content: @Composable () -> Unit) {
    val keyboardShown = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val shown = enabled && !keyboardShown
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(bottom = if (shown) HintBarHeight else 0.dp)) { content() }
        if (shown) {
            HintBar(
                hints,
                Modifier.align(Alignment.BottomEnd).fillMaxWidth().background(Palette.SkyLow)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .height(HintBarHeight).padding(horizontal = 24.dp),
            )
        }
    }
}
