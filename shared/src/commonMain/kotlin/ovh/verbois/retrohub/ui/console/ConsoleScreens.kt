package ovh.verbois.retrohub.ui.console

import androidx.compose.runtime.Composable
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.console.catalogue.ConsoleCatalogue
import ovh.verbois.retrohub.ui.console.game.ConsoleGameScreen
import ovh.verbois.retrohub.ui.console.home.ConsoleHome
import ovh.verbois.retrohub.ui.console.settings.ConsoleSettings
import ovh.verbois.retrohub.ui.game.ConflictScreen
import ovh.verbois.retrohub.ui.game.ProgressionScreen
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.shell.SettingsLockedPage
import ovh.verbois.retrohub.ui.shell.WithBack
import ovh.verbois.retrohub.ui.shell.settingsLocked

/**
 * Every route of the landscape interface. Browsing routes (library, favorites, a game) all resolve to the
 * permanent home; the shell turns them into a home selection.
 */
@Composable
fun ConsoleScreen(controller: UiController, route: Route) {
    when (route) {
        Route.Home, is Route.Game, is Route.Library, is Route.Folder, Route.Favorites, Route.Downloads -> ConsoleHome(controller)

        Route.Catalogue -> ConsoleCatalogue(controller)

        is Route.CatalogueGame -> ConsoleGameScreen(controller, route.id)

        Route.Search, Route.User -> Unit

        is Route.Conflict -> WithBack(controller) { ConflictScreen(controller, route.gameId) }

        is Route.Progression -> WithBack(controller) { ProgressionScreen(controller, route.gameId) }

        is Route.Settings -> if (route.page == ovh.verbois.retrohub.ui.nav.SettingsPage.GAME && route.console != null) {
            ConsoleGameScreen(controller, route.console)
        } else if (settingsLocked(controller)) {
            SettingsLockedPage(controller)
        } else {
            ConsoleSettings(controller, route.page, route.console)
        }
    }
}
