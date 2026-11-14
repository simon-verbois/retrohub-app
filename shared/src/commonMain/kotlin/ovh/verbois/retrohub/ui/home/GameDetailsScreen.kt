package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.icons.Lucide

/** Portrait opens a scrollable game page rather than rebuilding a console carousel. */
@Composable
fun GameDetailsScreen(controller: UiController, id: String) {
    val games by controller.container.library.games.collectAsState()
    val game = games.firstOrNull { it.id == id } ?: return
    val first = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("game-details"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameArt(game.remote.coverUrl, game.console, game.title, Modifier.size(if (controller.portrait) 160.dp else 96.dp), showTitleOnFallback = false)
        Text(game.title, style = MaterialTheme.typography.headlineMedium)
        Kicker(game)
        GamePlayButton(controller, game, Modifier.widthIn(max = 420.dp).focusRequester(first))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundButton(if (game.favorite) Lucide.HeartFilled else Lucide.Heart, if (game.favorite) "Retirer des favoris" else "Ajouter aux favoris", {
                controller.toggleFavorite(game)
            }, Modifier.testTag("home-favorite"), selected = game.favorite, selectedSurface = false)
            Box {
                RoundButton(Lucide.SlidersHorizontal, "Options du jeu", { controller.gameMenu = true })
                GameMenu(controller, game)
            }
        }
    }
    AutoFocus(first)
}
