package ovh.verbois.retrohub.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.emulation.HintsMode
import ovh.verbois.retrohub.emulation.PerformanceProfile
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.Badge
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

enum class PlayStyle(val label: String, val description: String) {
    TOUCH("Au doigt", "Commandes tactiles à l’écran, adaptées à chaque console."),
    GAMEPAD("À la manette", "Interface et jeux pilotés à la manette, sans rien à l’écran."),
    BOTH("Les deux", "Tactile par défaut, masqué dès qu’une manette se branche."),
}

/** Un réglage de confort du premier lancement. */
private class Toggle(val title: String, val subtitle: String, val icon: ImageVector, val checked: Boolean, val onChange: (Boolean) -> Unit)

/**
 * Réglages de départ, juste après la première connexion. L’écran est fixe : il occupe la hauteur
 * disponible sans défiler, en paysage comme en portrait. Tout reste modifiable ensuite.
 */
@Composable
fun SetupWizard(container: AppContainer, username: String) {
    val repo = container.settings
    val settings by repo.settings.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var style by remember { mutableStateOf(PlayStyle.BOTH) }
    val scope = rememberCoroutineScope()
    val steps = 4
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 520.dp
        val wide = maxWidth > maxHeight && maxWidth > 600.dp
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = if (wide) 980.dp else 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = if (compact) 14.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(steps) { index ->
                    val width = if (index == step) 36.dp else 16.dp
                    Box(Modifier.height(5.dp).width(width).clip(CircleShape).background(if (index <= step) Palette.Accent else Palette.Line))
                }
            }
            AnimatedContent(
                step,
                Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { (fadeIn() + slideInHorizontally { it / 10 }) togetherWith fadeOut() },
                label = "setup",
            ) { current ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                    when (current) {
                        0 -> {
                            Title("Bienvenue, $username", null, compact)
                            Options(
                                PlayStyle.entries,
                                style,
                                { style = it },
                                { it.label },
                                { it.description },
                                { playStyleIcon(it) },
                                recommended = PlayStyle.BOTH,
                                wide = wide,
                                compact = compact,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        1 -> {
                            Title("Performances", null, compact)
                            Options(
                                PerformanceProfile.entries,
                                settings.performance,
                                { v -> repo.update { it.copy(performance = v) } },
                                { it.label },
                                { it.description },
                                { Lucide.Gauge },
                                recommended = PerformanceProfile.BALANCED,
                                wide = wide,
                                compact = compact,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        2 -> {
                            Title("Confort", null, compact)
                            Toggles(
                                listOf(
                                    Toggle("Vibrations", "Un léger retour sous les doigts.", Lucide.Vibrate, settings.haptics) { v ->
                                        repo.update { it.copy(haptics = v) }
                                    },
                                    Toggle("Fond animé", "Halos et étoiles derrière l’interface.", Lucide.Sparkles, settings.animatedBackground) { v ->
                                        repo.update { it.copy(animatedBackground = v) }
                                    },
                                    Toggle("Reprendre ma partie", "Chaque jeu recharge votre dernière partie.", Lucide.History, settings.resumeLastState) { v ->
                                        repo.update { it.copy(resumeLastState = v) }
                                    },
                                    Toggle("Données mobiles", "Télécharger les jeux hors Wi-Fi.", Lucide.Globe, settings.cellularDownloads) { v ->
                                        repo.update { it.copy(cellularDownloads = v) }
                                    },
                                ),
                                wide = wide,
                                compact = compact,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        else -> {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Title(
                                    "C’est prêt !",
                                    "Téléchargez un jeu pour y jouer, même hors ligne.",
                                    compact,
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) GlassButton("Retour", { step-- }, icon = Lucide.ChevronLeft)
                Box(Modifier.weight(1f))
                PrimaryButton(
                    if (step == steps - 1) "Découvrir ma bibliothèque" else "Continuer",
                    {
                        if (step == 0) {
                            repo.update {
                                it.copy(
                                    hideTouchWithGamepad = style != PlayStyle.TOUCH,
                                    hints = if (style == PlayStyle.GAMEPAD) HintsMode.ALWAYS else HintsMode.AUTO,
                                )
                            }
                        }
                        if (step < steps - 1) {
                            step++
                        } else {
                            scope.launch { repo.updateNow { it.copy(setupDone = true) } }
                        }
                    },
                    icon = if (step == steps - 1) Lucide.Play else Lucide.ChevronRight,
                )
            }
        }
    }
}

private fun playStyleIcon(style: PlayStyle): ImageVector = when (style) {
    PlayStyle.TOUCH -> Lucide.Hand
    PlayStyle.GAMEPAD -> Lucide.Gamepad2
    PlayStyle.BOTH -> Lucide.MonitorSmartphone
}

@Composable
private fun Title(title: String, text: String?, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        Text(
            title,
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (text != null) {
            Text(
                text,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = Palette.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Cartes de choix qui se partagent la place disponible (en ligne en paysage, en colonne sinon). */
@Composable
private fun <T> Options(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    description: (T) -> String,
    icon: (T) -> ImageVector,
    recommended: T?,
    wide: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    val first = remember { FocusRequester() }
    val card: @Composable (T, Modifier) -> Unit = { option, cardModifier ->
        val active = option == selected
        val shape = RoundedCornerShape(22.dp)
        Column(
            cardModifier
                .consoleFocus(shape, { onSelect(option) }, role = Role.RadioButton, focusScale = 1.02f)
                .clip(shape)
                .background(if (active) Palette.Accent.copy(alpha = 0.12f) else Palette.Glass)
                .border(if (active) 2.dp else 1.dp, if (active) Palette.Accent else Palette.Line, shape)
                .padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp, Alignment.CenterVertically),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon(option), null, Modifier.size(if (compact) 22.dp else 26.dp), tint = if (active) Palette.Accent else Palette.Select)
                Text(label(option), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                if (option == recommended) Badge("Conseillé", Palette.Success)
            }
            Text(
                description(option),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (wide) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            options.forEachIndexed { i, option ->
                card(option, Modifier.weight(1f).fillMaxHeight().then(if (i == 0) Modifier.focusRequester(first) else Modifier))
            }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEachIndexed { i, option ->
                card(option, Modifier.weight(1f).fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier))
            }
        }
    }
    AutoFocus(first)
}

/** Réglages de confort : grille 2 × 2 en paysage, liste en portrait, toujours sans défilement. */
@Composable
private fun Toggles(items: List<Toggle>, wide: Boolean, compact: Boolean, modifier: Modifier) {
    val first = remember { FocusRequester() }
    val tile: @Composable (Toggle, Modifier) -> Unit = { item, tileModifier ->
        val shape = RoundedCornerShape(20.dp)
        Row(
            tileModifier
                .consoleFocus(shape, { item.onChange(!item.checked) }, role = Role.Switch, focusScale = 1.02f)
                .clip(shape)
                .background(Palette.Glass)
                .border(1.dp, Palette.Line, shape)
                .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(item.icon, null, Modifier.size(24.dp), tint = Palette.Select)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Switch(
                checked = item.checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.OnAccent,
                    checkedTrackColor = Palette.Accent,
                    uncheckedThumbColor = Palette.TextDim,
                    uncheckedTrackColor = Palette.Raised,
                    uncheckedBorderColor = Palette.Line,
                ),
            )
        }
    }
    val gap = if (compact) 10.dp else 12.dp
    if (wide) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    pair.forEachIndexed { i, item ->
                        val focus = if (row == 0 && i == 0) Modifier.focusRequester(first) else Modifier
                        tile(item, Modifier.weight(1f).fillMaxHeight().then(focus))
                    }
                }
            }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            items.forEachIndexed { i, item ->
                tile(item, Modifier.weight(1f).fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier))
            }
        }
    }
    AutoFocus(first)
}
