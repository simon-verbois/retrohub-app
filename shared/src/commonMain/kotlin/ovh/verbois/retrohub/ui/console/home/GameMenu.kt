package ovh.verbois.retrohub.ui.console.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.console.kit.ConsoleSideSheet
import ovh.verbois.retrohub.ui.console.kit.OptionRow
import ovh.verbois.retrohub.ui.home.downloadState
import ovh.verbois.retrohub.ui.home.gameAction
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage

/** Start on a home game: the PS5 options panel, sliding in from the right. */
@Composable
fun GameMenu(controller: UiController, game: Game) {
    val first = remember { FocusRequester() }
    val close = { controller.gameMenu = false }
    var removing by remember(game.id) { mutableStateOf(false) }
    val download = downloadState(controller, game)
    ConsoleSideSheet(controller.gameMenu, game.title, close, initialFocus = first, tag = "xmb-options-panel") {
        val action = gameAction(controller, game, download)
        OptionRow(action.label, {
            close()
            action.run()
        }, Modifier.focusRequester(first), icon = action.icon)
        OptionRow("Fiche du jeu", {
            close()
            controller.nav.go(Route.CatalogueGame(game.id))
        }, icon = Lucide.Info)
        if (game.downloaded && game.lastPlayed != null) {
            OptionRow("Nouvelle partie", {
                close()
                controller.play(game, fresh = true)
            }, icon = Lucide.RotateCcw)
        }
        OptionRow(if (game.favorite) "Retirer des favoris" else "Ajouter aux favoris", {
            close()
            controller.toggleFavorite(game)
        }, icon = if (game.favorite) Lucide.HeartFilled else Lucide.Heart)
        if (game.console == "3ds") {
            OptionRow("Mods", {
                close()
                controller.nav.go(Route.Settings(SettingsPage.MODS, game.id))
            }, icon = Lucide.Layers)
        }
        if (download is DownloadState.Failed && download.retryable) {
            OptionRow("Réessayer le téléchargement", {
                close()
                controller.container.downloads.start(game)
            }, icon = Lucide.RefreshCw)
        }
        if (download is DownloadState.Running || download is DownloadState.Failed) {
            OptionRow("Annuler le téléchargement", {
                close()
                controller.container.downloads.cancel(game.playGameId)
            }, icon = Lucide.X)
        }
        if (game.downloaded && download !is DownloadState.Running) {
            OptionRow("Supprimer du téléphone", {
                close()
                removing = true
            }, icon = Lucide.Trash2, danger = true)
        }
    }
    if (removing) {
        ConfirmDialog(
            "Supprimer du téléphone ?",
            "« ${game.title} » sera retiré de cet appareil. Vos sauvegardes sont conservées.",
            "Supprimer",
            onConfirm = {
                removing = false
                controller.deleteDownload(game)
            },
            onDismiss = { removing = false },
            destructive = true,
            focusDismiss = true,
        )
    }
}
