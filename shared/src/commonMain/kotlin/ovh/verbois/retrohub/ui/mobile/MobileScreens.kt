package ovh.verbois.retrohub.ui.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.catalogue.CatalogueGameScreen
import ovh.verbois.retrohub.ui.catalogue.CatalogueScreen
import ovh.verbois.retrohub.ui.game.ConflictScreen
import ovh.verbois.retrohub.ui.game.ProgressionScreen
import ovh.verbois.retrohub.ui.mobile.home.GameDetailsScreen
import ovh.verbois.retrohub.ui.mobile.library.DownloadsScreen
import ovh.verbois.retrohub.ui.mobile.library.LibraryScreen
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.settings.SettingsScreen
import ovh.verbois.retrohub.ui.shell.SettingsLockedPage
import ovh.verbois.retrohub.ui.shell.WithBack
import ovh.verbois.retrohub.ui.shell.settingsLocked

/** Portrait home: the library grid. */
@Composable
fun MobileHome(controller: UiController) {
    Box(Modifier.fillMaxSize().testTag("home-content")) { LibraryScreen(controller, null, favoritesOnly = false) }
}

/** Every route of the portrait interface. */
@Composable
fun MobileScreen(controller: UiController, route: Route) {
    when (route) {
        Route.Home -> MobileHome(controller)

        is Route.Game -> WithBack(controller) { GameDetailsScreen(controller, route.id) }

        is Route.Library -> LibraryScreen(controller, route.console, favoritesOnly = false)

        is Route.Folder -> {
            LaunchedEffect(route.id) { controller.returnHome() }
            MobileHome(controller)
        }

        Route.Favorites -> LibraryScreen(controller, null, favoritesOnly = true)

        Route.Downloads -> DownloadsScreen(controller)

        Route.Catalogue -> CatalogueScreen(controller)

        is Route.CatalogueGame -> WithBack(controller) { CatalogueGameScreen(controller, route.id) }

        // Rendered by the shell, with the section bar.
        Route.Search, Route.User -> Unit

        is Route.Conflict -> WithBack(controller) { ConflictScreen(controller, route.gameId) }

        is Route.Progression -> WithBack(controller) { ProgressionScreen(controller, route.gameId) }

        is Route.Settings -> if (settingsLocked(controller)) SettingsLockedPage(controller) else SettingsScreen(controller, route.page, route.console)
    }
}
