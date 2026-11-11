package ovh.verbois.retrohub.ui.home

import androidx.compose.ui.graphics.vector.ImageVector
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.icons.Lucide

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
