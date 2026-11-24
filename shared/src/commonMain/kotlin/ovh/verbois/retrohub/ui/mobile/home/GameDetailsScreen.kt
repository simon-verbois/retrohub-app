package ovh.verbois.retrohub.ui.mobile.home

import androidx.compose.foundation.horizontalScroll
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
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.home.*
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

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

/** Game information lives below the title, including play time, without a decorative dot. */
@Composable
fun Kicker(game: Game, compact: Boolean = false) {
    if (compact) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(Consoles.name(game.console), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1)
            game.remote.genre?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1) }
            Text("${formatPlayTime(game.remote.playedSeconds)} de jeu", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1)
        }
        return
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val information = listOfNotNull(
            Consoles.name(game.console),
            game.remote.genre?.takeIf { it.isNotBlank() }?.split(',')?.joinToString(", ") { it.trim() },
            game.remote.year?.toString(),
            "${formatPlayTime(game.remote.playedSeconds)} de jeu",
        )
        information.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim) }
    }
}

@Composable
internal fun GamePlayButton(controller: UiController, game: Game, modifier: Modifier = Modifier) {
    val download = downloadState(controller, game)
    val action = gameAction(controller, game, download)
    if (download is DownloadState.Running) {
        Text("Téléchargement en cours", modifier, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
    } else {
        PrimaryButton(
            action.label,
            action.run,
            modifier.testTag("game-primary-action"),
            icon = action.icon,
            enabled = game.supported,
            loading = controller.launching == game.id,
        )
    }
}
