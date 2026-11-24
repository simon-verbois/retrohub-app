package ovh.verbois.retrohub.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.CenteredMessage
import ovh.verbois.retrohub.ui.components.LocalNavigationFocus
import ovh.verbois.retrohub.ui.components.NavigationFocus
import ovh.verbois.retrohub.ui.components.PageHeader
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.icons.Lucide

private const val SETTINGS_LOCK_REASON = "Les réglages sont protégés par le code parental."

/** True while the parental code still protects the settings; asks for it once. */
@Composable
fun settingsLocked(controller: UiController): Boolean {
    val settings by controller.container.settings.settings.collectAsState()
    val locked = settings.pinHash.isNotEmpty() && settings.lockSettings && !controller.settingsUnlocked
    if (locked) {
        LaunchedEffect(Unit) { controller.pinRequest = SETTINGS_LOCK_REASON to { controller.settingsUnlocked = true } }
    }
    return locked
}

/** Settings page shown while the parental code protects them. */
@Composable
fun SettingsLockedPage(controller: UiController) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Réglages", { controller.nav.back() })
        CenteredMessage("Réglages verrouillés", "Entrez le code parental pour les modifier.", Lucide.Lock) {
            PrimaryButton("Entrer le code", { controller.pinRequest = SETTINGS_LOCK_REASON to { controller.settingsUnlocked = true } })
        }
    }
}

/** Pages outside sections: a simple back button at the top, the title is in the page. */
@Composable
fun WithBack(controller: UiController, content: @Composable () -> Unit) {
    val parent = LocalNavigationFocus.current
    val navigation = remember { NavigationFocus() }
    SideEffect { parent.content = navigation.header }
    Column(Modifier.fillMaxSize()) {
        PageHeader(null, { controller.nav.back() }, backModifier = Modifier.focusRequester(navigation.header))
        CompositionLocalProvider(LocalNavigationFocus provides navigation) {
            Box(Modifier.weight(1f).fillMaxSize()) { content() }
        }
    }
    AutoFocus(navigation.header)
}
