package ovh.verbois.retrohub.ui.home

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.icons.Lucide

/** Observe only this game's progress, so other downloads do not invalidate the home. */
@androidx.compose.runtime.Composable
internal fun downloadState(controller: UiController, game: Game): DownloadState? {
    val states = controller.container.downloads.states
    val progress = androidx.compose.runtime.remember(states, game.playGameId) {
        states.map { it[game.playGameId] }.distinctUntilChanged()
    }
    return progress.collectAsState(initial = states.value[game.playGameId]).value
}

/** L’action principale d’un jeu selon son état : jouer, reprendre, télécharger, choisir… */
data class GameAction(val label: String, val icon: ImageVector, val progress: Float? = null, val run: () -> Unit)

fun gameAction(controller: UiController, game: Game, download: DownloadState?): GameAction = when {
    !game.supported -> GameAction("Pas encore jouable ici", Lucide.Info) {}

    download is DownloadState.Running ->
        GameAction("Téléchargement ${(download.fraction * 100).toInt()} %", Lucide.Download, download.fraction) {}

    game.remote.progressionChoiceRequired -> GameAction("Reprendre", Lucide.Play) { controller.play(game) }

    game.downloaded -> GameAction(
        if (game.lastPlayed != null ||
            game.remote.saves.isNotEmpty()
        ) {
            "Reprendre"
        } else {
            "Jouer"
        },
        Lucide.Play,
    ) { controller.play(game) }

    game.remote.rom != null ->
        GameAction("Télécharger · ${formatBytes(game.remote.rom.size)}", Lucide.Download) {
            controller.requestDownload(game)
        }

    else -> GameAction("Pas encore sur le serveur", Lucide.Info) {}
}
