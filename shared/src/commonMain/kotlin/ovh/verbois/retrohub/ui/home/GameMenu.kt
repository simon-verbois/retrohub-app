package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.XmbSidePanel
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.components.controllerDialog
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

/** Landscape actions form a side column; portrait keeps its cover-anchored menu. */
@Composable
fun GameMenu(controller: UiController, game: Game) {
    val first = remember { FocusRequester() }
    val close = { controller.gameMenu = false }
    var removing by remember(game.id) { mutableStateOf(false) }
    val items: @Composable () -> Unit = {
        val download = downloadState(controller, game)
        AutoFocus(first)
        val action = gameAction(controller, game, download)
        Item(action.icon, action.label, Modifier.focusRequester(first)) {
            close()
            action.run()
        }
        if (game.downloaded && game.lastPlayed != null) {
            Item(Lucide.RotateCcw, "Nouvelle partie") {
                close()
                controller.play(game, fresh = true)
            }
        }
        Item(Lucide.Heart, if (game.favorite) "Retirer des favoris" else "Ajouter aux favoris") {
            close()
            controller.toggleFavorite(game)
        }
        Item(Lucide.Settings2, "Options du jeu") {
            close()
            controller.nav.go(Route.Settings(SettingsPage.GAME, game.id))
        }
        if (download is DownloadState.Failed && download.retryable) {
            Item(Lucide.RefreshCw, "Réessayer le téléchargement") {
                close()
                controller.container.downloads.start(game)
            }
        }
        if (download is DownloadState.Running || download is DownloadState.Failed) {
            Item(Lucide.X, "Annuler le téléchargement") {
                close()
                controller.container.downloads.cancel(game.playGameId)
            }
        }
        if (game.downloaded && download !is DownloadState.Running) {
            Item(Lucide.Trash2, "Supprimer du téléphone") {
                close()
                removing = true
            }
        }
    }
    if (controller.portrait) {
        if (controller.gameMenu) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = close,
                modifier = Modifier.controllerDialog(close, dismissOnStart = true),
                containerColor = Palette.Deep,
                shape = RoundedCornerShape(18.dp),
                content = { items() },
            )
        }
    } else {
        XmbSidePanel(controller.gameMenu, close) {
            Column(
                Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(game.title, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
                items()
            }
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

@Composable
private fun Item(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(
            min = 48.dp,
        ).consoleFocus(RoundedCornerShape(0.dp), onClick, enabled = ovh.verbois.retrohub.ui.components.LocalScreenActive.current)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = Palette.Text)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
    }
}
