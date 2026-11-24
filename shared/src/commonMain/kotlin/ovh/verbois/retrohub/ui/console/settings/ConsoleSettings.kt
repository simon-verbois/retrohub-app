package ovh.verbois.retrohub.ui.console.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.ConsoleTone
import ovh.verbois.retrohub.ui.components.controllerVerticalScroll
import ovh.verbois.retrohub.ui.console.kit.ConsolePage
import ovh.verbois.retrohub.ui.console.kit.FocusSurface
import ovh.verbois.retrohub.ui.console.kit.Hint
import ovh.verbois.retrohub.ui.console.kit.Pad
import ovh.verbois.retrohub.ui.console.kit.PageInset
import ovh.verbois.retrohub.ui.console.kit.ProvideHints
import ovh.verbois.retrohub.ui.console.kit.RestoreFocus
import ovh.verbois.retrohub.ui.console.kit.Tone
import ovh.verbois.retrohub.ui.console.kit.rememberFocusMemory
import ovh.verbois.retrohub.ui.console.kit.remembered
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.settings.SettingsPageBody

/** Sections of the settings list, in order. */
internal val ConsoleSettingsSections: List<Triple<SettingsPage, String, ImageVector>> = listOf(
    Triple(SettingsPage.ACCOUNT, "Compte", Lucide.UserRound),
    Triple(SettingsPage.SECURITY, "Sécurité et appareils", Lucide.Shield),
    Triple(SettingsPage.SYNC, "Synchronisation", Lucide.RefreshCw),
    Triple(SettingsPage.APPEARANCE, "Apparence", Lucide.Palette),
    Triple(SettingsPage.DISPLAY, "Affichage", Lucide.Monitor),
    Triple(SettingsPage.CONTROLS, "Commandes", Lucide.Gamepad2),
    Triple(SettingsPage.AUDIO, "Audio", Lucide.Volume2),
    Triple(SettingsPage.PERFORMANCE, "Performances", Lucide.Gauge),
    Triple(SettingsPage.STORAGE, "Stockage", Lucide.HardDrive),
    Triple(SettingsPage.PARENTAL, "Contrôle parental", Lucide.Lock),
    Triple(SettingsPage.CONSOLES, "Consoles", Lucide.Layers),
    Triple(SettingsPage.ABOUT, "À propos", Lucide.BadgeInfo),
)

/**
 * PS5-like settings: sections on the left, their settings on the right. Moving in the list shows the
 * section; Right or A enters it; B or Left goes back to the list. Deeper pages open full screen.
 */
@Composable
fun ConsoleSettings(controller: UiController, page: SettingsPage, console: String?) {
    if (ConsoleSettingsSections.none { it.first == page } && page != SettingsPage.ROOT) {
        ConsoleSettingsSubPage(controller, page, console)
        return
    }
    val requested = if (page == SettingsPage.ROOT) SettingsPage.ACCOUNT else page
    var section by rememberSaveable { mutableStateOf(requested) }
    // Compose may focus the first row while the page appears; the first visit always lands on the requested section.
    var visited by rememberSaveable { mutableStateOf(false) }
    var inDetail by remember { mutableStateOf(false) }
    val memory = rememberFocusMemory()
    val focus = LocalFocusManager.current
    val backToList = {
        runCatching { memory.requester(section.name).requestFocus() }
        Unit
    }
    ProvideHints(
        Hint(Pad.A, "Choisir"),
        Hint(Pad.DPAD, if (inDetail) "Régler" else "Parcourir"),
        Hint(Pad.B, "Retour") { if (inDetail) backToList() else controller.back() },
    )
    ConsolePage("Paramètres", Modifier.testTag("console-settings")) {
        Row(Modifier.fillMaxSize().padding(start = PageInset - 12.dp, end = PageInset), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(
                Modifier.width(270.dp).fillMaxHeight().verticalScroll(rememberScrollState()).focusGroup().padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                ConsoleSettingsSections.forEach { (target, label, icon) ->
                    val chosen = target == section
                    FocusSurface(
                        {
                            section = target
                            focus.moveFocus(FocusDirection.Right)
                        },
                        // Short rows: the twelve sections fit the screen, like the PS5 settings list.
                        Modifier.fillMaxWidth().heightIn(min = 29.dp).remembered(memory, target.name).testTag("settings-section-${target.name.lowercase()}"),
                        tone = Tone.QUIET,
                        focusScale = 1.02f,
                        onFocus = { if (it) section = target },
                    ) { focused ->
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.width(3.dp).heightIn(min = 18.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (chosen && !focused) ConsoleTone.Selected else Color.Transparent),
                            )
                            Icon(icon, null, Modifier.size(17.dp))
                            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .onFocusChanged { inDetail = it.hasFocus }
                    .onPreviewKeyEvent { event ->
                        if (inDetail && (event.key == Key.ButtonB || event.key == Key.Escape)) {
                            if (event.type == KeyEventType.KeyUp) backToList()
                            true
                        } else {
                            false
                        }
                    }
                    .controllerVerticalScroll(rememberScrollState()).padding(bottom = 24.dp, top = 4.dp).widthIn(max = 860.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsPageBody(controller, section, console)
            }
        }
    }
    if (visited) {
        RestoreFocus(memory, section.name)
    } else {
        val target = memory.requester(requested.name)
        LaunchedEffect(Unit) {
            section = requested
            withFrameNanos { }
            runCatching { target.requestFocus() }
            section = requested
            visited = true
        }
    }
}

/** A settings page reached from a section (one console, its buttons, the changelog…), full screen. */
@Composable
private fun ConsoleSettingsSubPage(controller: UiController, page: SettingsPage, console: String?) {
    val games by controller.container.library.games.collectAsState()
    val title = when {
        page == SettingsPage.CONSOLE && console != null -> Consoles.name(console)
        page == SettingsPage.REMAP && console != null -> "Manette · ${Consoles[console]?.shortName ?: ""}"
        page == SettingsPage.MODS -> "Mods · ${games.firstOrNull { it.id == console }?.title.orEmpty()}"
        else -> page.title
    }
    ProvideHints(Hint(Pad.A, "Choisir"), Hint(Pad.B, "Retour") { controller.back() })
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    ConsolePage(title, Modifier.testTag("console-settings-page")) {
        Column(
            // Entering the group focuses its first control.
            Modifier.fillMaxSize().focusRequester(first).controllerVerticalScroll(rememberScrollState())
                .padding(horizontal = PageInset).padding(bottom = 24.dp).widthIn(max = 1000.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsPageBody(controller, page, console)
        }
    }
    ovh.verbois.retrohub.ui.components.AutoFocus(first)
}
