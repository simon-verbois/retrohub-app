package ovh.verbois.retrohub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.UpdateLevel
import ovh.verbois.retrohub.core.updateLevel
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.GroupDivider
import ovh.verbois.retrohub.ui.components.NavigationRow
import ovh.verbois.retrohub.ui.components.PageHeader
import ovh.verbois.retrohub.ui.components.SectionLabel
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

/** Libellés des cœurs, pour le joueur. */
fun coreLabel(core: String): String = when (core) {
    "fceumm" -> "FCEUmm"
    "snes9x" -> "Snes9x"
    "gambatte" -> "Gambatte"
    "mgba" -> "mGBA"
    "genesis_plus_gx" -> "Genesis Plus GX"
    "pcsx_rearmed" -> "PCSX ReARMed"
    "swanstation" -> "SwanStation"
    "melonds" -> "melonDS"
    "stella2014" -> "Stella"
    "parallel_n64" -> "ParaLLEl N64"
    "dolphin" -> "Dolphin"
    "flycast" -> "Flycast"
    else -> core
}

/**
 * Les réglages, en arbre : ce qui est partagé avec le compte (et donc le web), ce qui ne vaut que
 * pour cet appareil, les consoles et les jeux, puis l’application.
 */
@Composable
fun SettingsScreen(controller: UiController, page: SettingsPage, console: String?) {
    val first = remember { FocusRequester() }
    LaunchedEffect(page) { controller.backdrop = Backdrop(null, console?.takeIf { Consoles[it] != null }) }
    val title =
        when {
            page == SettingsPage.CONSOLE && console != null -> Consoles.name(console)
            page == SettingsPage.REMAP && console != null -> "Manette · ${Consoles[console]?.shortName ?: ""}"
            page == SettingsPage.GAME && console != null -> controller.container.library.games.value.firstOrNull { it.id == console }?.title ?: page.title
            else -> page.title
        }
    Column(Modifier.fillMaxSize()) {
        PageHeader(title, { controller.nav.back() }, backModifier = Modifier.focusRequester(first))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp).widthIn(max = 860.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                SettingsPage.ROOT -> RootPage(controller)
                SettingsPage.ACCOUNT -> AccountPage(controller)
                SettingsPage.SECURITY -> SecurityPage(controller)
                SettingsPage.SYNC -> SyncPage(controller)
                SettingsPage.APPEARANCE -> AppearancePage(controller)
                SettingsPage.DISPLAY -> DisplayPage(controller)
                SettingsPage.CONTROLS -> ControlsPage(controller)
                SettingsPage.AUDIO -> AudioPage(controller)
                SettingsPage.PERFORMANCE -> PerformancePage(controller)
                SettingsPage.STORAGE -> StoragePage(controller)
                SettingsPage.PARENTAL -> ParentalPage(controller)
                SettingsPage.CONSOLES -> ConsolesPage(controller)
                SettingsPage.CONSOLE -> ConsolePage(controller, console ?: "nes")
                SettingsPage.REMAP -> RemapPage(controller, console ?: "nes")
                SettingsPage.GAME -> GameOptionsPage(controller, console.orEmpty())
                SettingsPage.ABOUT -> AboutPage(controller)
                SettingsPage.CHANGELOG -> ChangelogPage()
            }
        }
    }
    AutoFocus(first)
}

@Composable
private fun RootPage(controller: UiController) {
    val container = controller.container
    val settings by container.settings.settings.collectAsState()
    val status by container.serverStatus.collectAsState()
    val account = container.account.account
    val go = { page: SettingsPage -> controller.nav.go(Route.Settings(page)) }
    SectionLabel("Compte RetroHub")
    SettingsGroup {
        NavigationRow("Compte et préférences de jeu", Lucide.UserRound, { go(SettingsPage.ACCOUNT) }, account?.let { "${it.username} · ${it.serverName}" })
        GroupDivider()
        NavigationRow("Sécurité et appareils", Lucide.Shield, { go(SettingsPage.SECURITY) })
        GroupDivider()
        NavigationRow("Synchronisation", Lucide.RefreshCw, { go(SettingsPage.SYNC) })
    }
    SectionLabel("Cet appareil")
    SettingsGroup {
        NavigationRow("Apparence", Lucide.Palette, { go(SettingsPage.APPEARANCE) })
        GroupDivider()
        NavigationRow("Affichage", Lucide.Monitor, { go(SettingsPage.DISPLAY) })
        GroupDivider()
        NavigationRow("Commandes", Lucide.Gamepad2, { go(SettingsPage.CONTROLS) })
        GroupDivider()
        NavigationRow("Audio", Lucide.Volume2, { go(SettingsPage.AUDIO) })
        GroupDivider()
        NavigationRow("Performances", Lucide.Gauge, { go(SettingsPage.PERFORMANCE) }, value = settings.performance.label)
        GroupDivider()
        NavigationRow("Téléchargements et stockage", Lucide.HardDrive, { go(SettingsPage.STORAGE) })
        GroupDivider()
        NavigationRow("Contrôle parental", Lucide.Lock, { go(SettingsPage.PARENTAL) }, value = if (settings.pinHash.isEmpty()) "Désactivé" else "Activé")
    }
    SectionLabel("Consoles")
    SettingsGroup {
        NavigationRow("Réglages des consoles", Lucide.Layers, { go(SettingsPage.CONSOLES) })
    }
    SectionLabel("Application")
    SettingsGroup {
        val android = status?.apps?.android
        val update = updateLevel(container.platform.appVersion, android?.latestVersion, android?.minVersion)
        NavigationRow(
            "À propos",
            Lucide.BadgeInfo,
            { go(SettingsPage.ABOUT) },
            if (update != UpdateLevel.NONE) "Mise à jour disponible" else null,
            value = container.platform.appVersion,
        )
    }
}
