package ovh.verbois.retrohub.ui

import androidx.compose.runtime.Composable
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.ui.console.ConsoleScreen
import ovh.verbois.retrohub.ui.console.ConsoleShell
import ovh.verbois.retrohub.ui.console.home.ConsoleHome
import ovh.verbois.retrohub.ui.mobile.MobileHome
import ovh.verbois.retrohub.ui.mobile.MobileScreen
import ovh.verbois.retrohub.ui.mobile.MobileShell
import ovh.verbois.retrohub.ui.nav.Route

/*
 * With RetroHubRoot, the only file that knows both interfaces (docs/ui-architecture.md):
 * portrait renders ui.mobile, landscape renders ui.console.
 */

@Composable
fun AppShell(controller: UiController, account: Account, content: @Composable (Route) -> Unit) {
    if (controller.portrait) MobileShell(controller, account, content) else ConsoleShell(controller, account, content)
}

@Composable
fun AppScreen(controller: UiController, route: Route) {
    if (controller.portrait) MobileScreen(controller, route) else ConsoleScreen(controller, route)
}

@Composable
fun HomeScreen(controller: UiController) {
    if (controller.portrait) MobileHome(controller) else ConsoleHome(controller)
}

/** Gives landscape screens outside the shells (first launch, setup, global dialogs) the console look. */
@Composable
fun InterfaceStyle(portrait: Boolean, content: @Composable () -> Unit) {
    ovh.verbois.retrohub.ui.console.kit.ConsoleStyleScope(enabled = !portrait, content)
}
