package ovh.verbois.retrohub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.emulation.AspectChoice
import ovh.verbois.retrohub.emulation.BootMode
import ovh.verbois.retrohub.emulation.ConsoleSettings
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.CoreOptions
import ovh.verbois.retrohub.emulation.DsLayout
import ovh.verbois.retrohub.emulation.FrameSkip
import ovh.verbois.retrohub.emulation.GameLanguage
import ovh.verbois.retrohub.emulation.GameLayouts
import ovh.verbois.retrohub.emulation.Orientation
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.Region
import ovh.verbois.retrohub.emulation.Shader
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.FilterChip
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.GroupDivider
import ovh.verbois.retrohub.ui.components.NavigationRow
import ovh.verbois.retrohub.ui.components.SectionLabel
import ovh.verbois.retrohub.ui.components.SettingRow
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.components.SwitchRow
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.play.padSpec
import ovh.verbois.retrohub.ui.theme.Palette

@Composable
internal fun ConsolesPage(controller: UiController) {
    val settings by controller.container.settings.settings.collectAsState()
    val cores = remember { controller.container.platform.availableCores() }
    SettingsGroup {
        Consoles.all.forEachIndexed { index, spec ->
            if (index > 0) GroupDivider()
            val custom = settings.consoles[spec.id]?.let { it != ConsoleSettings() } == true
            SettingRow(
                spec.name,
                subtitle =
                coreLabel(settings.console(spec.id).core ?: spec.cores.first()) +
                    when {
                        spec.cores.none { it in cores } -> " · indisponible dans cette version"
                        spec.id in settings.hiddenConsoles -> " · masquée"
                        custom -> " · personnalisé"
                        else -> ""
                    },
                onClick = { controller.nav.go(Route.Settings(SettingsPage.CONSOLE, spec.id)) },
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(spec.color)))
            }
        }
    }
}

/** Valeur « comme le reste de l’application » dans un choix facultatif. */
private const val DEFAULT_LABEL = "Comme l’application"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConsolePage(controller: UiController, id: String) {
    val container = controller.container
    val repo = container.settings
    val settings by repo.settings.collectAsState()
    val spec = Consoles[id] ?: return
    val console = settings.console(id)
    val update = { change: (ConsoleSettings) -> ConsoleSettings -> repo.update { it.withConsole(id, change) } }
    val core = console.core ?: spec.cores.first()
    val device = container.platform.deviceClass
    SettingsGroup {
        if (spec.cores.size > 1) {
            ChoiceRow("Émulateur", spec.cores, core, ::coreLabel, { v ->
                container.preferences.setCore(id, v)
            }, null, Lucide.Cpu, recommended = spec.cores.first())
        } else {
            SettingRow("Émulateur", subtitle = coreLabel(core), icon = Lucide.Cpu)
        }
        GroupDivider()
        val defaultShader = CoreOptions.shader(spec, settings.copy(consoles = settings.consoles - id))
        ChoiceRow("Rendu de l’image", Shader.entries, CoreOptions.shader(spec, settings), {
            it.label
        }, { v -> update { it.copy(shader = v) } }, null, Lucide.Monitor, recommended = defaultShader)
        if (Consoles.nativeSize(id) != null) {
            GroupDivider()
            SwitchRow("Pixels parfaits", console.integerScale == true, { v ->
                update { it.copy(integerScale = v) }
            }, null, Lucide.ScanLine)
        }
        val max = CoreOptions.maxResolution(id)
        if (max > 1 && !(id == "psx" && core == "swanstation")) {
            GroupDivider()
            ChoiceRow(
                "Résolution interne",
                (1..max).toList(),
                CoreOptions.resolution(spec, settings, device),
                { if (it == 1) "D’origine" else "×$it" },
                { v -> update { it.copy(resolution = v) } },
                null,
                Lucide.Maximize,
                recommended = CoreOptions.resolution(spec, settings.copy(consoles = settings.consoles - id), device),
            )
        }
        if (CoreOptions.supportsWidescreen(id, core)) {
            GroupDivider()
            SwitchRow("Écran large (16:9)", console.widescreen == true, { v ->
                update { it.copy(widescreen = v) }
            }, null, Lucide.Maximize)
        }
        if (CoreOptions.supportsAspect(core)) {
            GroupDivider()
            ChoiceRow("Proportions", AspectChoice.entries, console.aspect ?: AspectChoice.TV, { it.label }, { v ->
                update { it.copy(aspect = v) }
            }, null, Lucide.Monitor, recommended = AspectChoice.TV)
        }
        GroupDivider()
        ChoiceRow(
            "Orientation",
            listOf<Orientation?>(null) + Orientation.entries,
            console.orientation,
            { it?.label ?: DEFAULT_LABEL },
            { v -> update { it.copy(orientation = v) } },
            null,
            Lucide.Smartphone,
            recommended = null,
        )
        GroupDivider()
        ChoiceRow(
            "Avance rapide",
            listOf(2, 3, 4, 6),
            console.fastForwardSpeed ?: 3,
            { "×$it" },
            { v -> update { it.copy(fastForwardSpeed = v) } },
            null,
            Lucide.FastForward,
            recommended = 3,
        )
        if (CoreOptions.supportsFrameSkip(core)) {
            GroupDivider()
            ChoiceRow("Saut d’images", FrameSkip.entries, console.frameSkip ?: FrameSkip.AUTO, { it.label }, { v ->
                update { it.copy(frameSkip = v) }
            }, null, Lucide.Gauge, recommended = FrameSkip.AUTO)
        }
    }
    val language = CoreOptions.supportsLanguage(core)
    val region = CoreOptions.supportsRegion(core)
    val boot = CoreOptions.supportsBoot(core)
    if (language || region || boot) {
        SectionLabel("Console")
        SettingsGroup {
            var first = true
            val divider = @Composable {
                if (!first) GroupDivider()
                first = false
            }
            if (language) {
                divider()
                ChoiceRow(
                    "Langue de la console",
                    listOf<GameLanguage?>(null) + GameLanguage.entries,
                    console.language,
                    { it?.label ?: "Selon le compte (${settings.language(id).label})" },
                    { v -> update { it.copy(language = v) } },
                    null,
                    Lucide.Languages,
                    recommended = null,
                )
            }
            if (region) {
                divider()
                ChoiceRow("Région", Region.entries, console.region ?: Region.AUTO, { it.label }, { v ->
                    update { it.copy(region = v) }
                }, null, Lucide.MapPin, recommended = Region.AUTO)
            }
            if (boot) {
                divider()
                ChoiceRow("Démarrage", BootMode.entries, console.boot ?: CoreOptions.defaultBoot(id), { it.label }, { v ->
                    update { it.copy(boot = v) }
                }, null, Lucide.Power, recommended = CoreOptions.defaultBoot(id))
            }
        }
    }
    if (spec.dualScreen) {
        SectionLabel("Écrans de la DS")
        SettingsGroup {
            ChoiceRow(
                "En portrait",
                DsLayout.entries,
                console.dsLayoutPortrait ?: GameLayouts.defaultDsLayout(true),
                { it.label },
                { v -> update { it.copy(dsLayoutPortrait = v) } },
                null,
                Lucide.Rows2,
                recommended = GameLayouts.defaultDsLayout(true),
            )
            GroupDivider()
            ChoiceRow(
                "En paysage",
                DsLayout.entries,
                console.dsLayoutLandscape ?: GameLayouts.defaultDsLayout(false),
                { it.label },
                { v -> update { it.copy(dsLayoutLandscape = v) } },
                null,
                Lucide.Columns2,
                recommended = GameLayouts.defaultDsLayout(false),
            )
            GroupDivider()
            SwitchRow("Inverser les écrans", console.dsSwapScreens == true, { v ->
                update { it.copy(dsSwapScreens = v) }
            }, null, Lucide.RotateCw)
        }
    }
    SectionLabel("Commandes")
    SettingsGroup {
        if (CoreOptions.supportsAnalog(id)) {
            SwitchRow("Manette analogique (DualShock)", console.analog == true, { v ->
                update { it.copy(analog = v) }
            }, null, Lucide.Joystick)
            GroupDivider()
        }
        NavigationRow(
            "Boutons de la manette",
            Lucide.Gamepad2,
            { controller.nav.go(Route.Settings(SettingsPage.REMAP, id)) },
            if (console.remap.isEmpty()) "Disposition d’origine" else "${console.remap.size} bouton(s) changé(s)",
        )
        if (console.touchLayout.isNotEmpty()) {
            GroupDivider()
            SettingRow(
                "Rétablir la place des commandes tactiles",
                icon = Lucide.Move,
                onClick = { update { it.copy(touchLayout = emptyMap()) } },
            )
        }
    }
    val turboKeys = remember(id) { turboCandidates(spec.pad) }
    SectionLabel("Tir automatique")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        turboKeys.forEach { (key, label) ->
            val on = key.name in console.turbo
            FilterChip(label, on, { update { it.copy(turbo = if (on) it.turbo - key.name else it.turbo + key.name) } })
        }
    }
    if (console.options.isNotEmpty()) {
        SectionLabel("Options avancées")
        SettingsGroup {
            console.options.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) GroupDivider()
                SettingRow(key, subtitle = value, icon = Lucide.SlidersHorizontal, onClick = {
                    update { it.copy(options = it.options - key) }
                }) { Text("Retirer", style = MaterialTheme.typography.labelMedium, color = Palette.Danger) }
            }
        }
    }
    if (settings.consoles[id] != null) {
        GlassButton("Rétablir les réglages conseillés", { repo.update { it.copy(consoles = it.consoles - id) } }, icon = Lucide.RotateCcw)
    }
}

/** Boutons d’action d’une manette, avec le libellé de la console. */
private fun turboCandidates(layout: ovh.verbois.retrohub.emulation.PadLayout): List<Pair<PadKey, String>> {
    val spec = padSpec(layout)
    return spec.face.map { it.key to it.label } + spec.shoulders.map { it.key to it.label }
}

/** Libellé d’une touche de manette Android (codes KeyEvent). */
fun keyLabel(code: Int): String = when (code) {
    19 -> "Croix haut"
    20 -> "Croix bas"
    21 -> "Croix gauche"
    22 -> "Croix droite"
    96 -> "A"
    97 -> "B"
    98 -> "C"
    99 -> "X"
    100 -> "Y"
    101 -> "Z"
    102 -> "L1"
    103 -> "R1"
    104 -> "L2"
    105 -> "R2"
    106 -> "L3"
    107 -> "R3"
    108 -> "Start"
    109 -> "Select"
    110 -> "Guide"
    else -> "Touche $code"
}

private val RemapKeys =
    listOf(
        PadKey.A to "A",
        PadKey.B to "B",
        PadKey.X to "X",
        PadKey.Y to "Y",
        PadKey.L to "L",
        PadKey.R to "R",
        PadKey.L2 to "L2",
        PadKey.R2 to "R2",
        PadKey.L3 to "L3",
        PadKey.R3 to "R3",
        PadKey.START to "Start",
        PadKey.SELECT to "Select",
    )

@Composable
internal fun RemapPage(controller: UiController, id: String) {
    val repo = controller.container.settings
    val settings by repo.settings.collectAsState()
    val gamepad by controller.container.platform.gamepadConnected.collectAsState()
    val console = settings.console(id)
    var waiting by remember { mutableStateOf<PadKey?>(null) }
    DisposableEffect(waiting) {
        val key = waiting
        controller.keyCapture =
            if (key == null) {
                null
            } else {
                { code ->
                    repo.update { it.withConsole(id) { c -> c.copy(remap = c.remap.filterValues { v -> v != code } + (key.name to code)) } }
                    waiting = null
                }
            }
        onDispose { controller.keyCapture = null }
    }
    Text(
        if (gamepad) {
            "Choisissez un bouton, puis appuyez sur la touche voulue."
        } else {
            "Aucune manette branchée."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextDim,
    )
    SettingsGroup {
        RemapKeys.forEachIndexed { index, (key, label) ->
            if (index > 0) GroupDivider()
            val code = console.remap[key.name]
            SettingRow(
                "Bouton $label",
                subtitle =
                when {
                    waiting == key -> "Appuyez sur une touche de la manette…"
                    code != null -> "Touche ${keyLabel(code)}"
                    else -> "Touche d’origine"
                },
                icon = if (waiting == key) Lucide.Keyboard else Lucide.Gamepad2,
                onClick = if (gamepad) ({ waiting = if (waiting == key) null else key }) else null,
            )
        }
    }
    if (console.remap.isNotEmpty()) {
        GlassButton("Disposition d’origine", { repo.update { it.withConsole(id) { c -> c.copy(remap = emptyMap()) } } }, icon = Lucide.RotateCcw)
    }
}
