package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.theme.Palette

@Composable
fun HomeScreen(controller: UiController) {
    if (controller.portrait) {
        Box(Modifier.fillMaxSize().testTag("home-content")) {
            ovh.verbois.retrohub.ui.library.LibraryScreen(controller, null, favoritesOnly = false)
        }
        return
    }
    ConsoleHome(controller)
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
    val downloads by controller.container.downloads.states.collectAsState()
    val download = downloads[game.playGameId]
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
