package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        if (game.console == "3ds") {
            Item(Lucide.Layers, "Mods") {
                close()
                controller.nav.go(Route.Settings(SettingsPage.MODS, game.id))
            }
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
            ovh.verbois.retrohub.ui.components.ConsoleDropdownMenu(
                expanded = true,
                onDismissRequest = close,
                modifier = Modifier.controllerDialog(close, dismissOnStart = true),
                content = { items() },
            )
        }
    } else {
        XmbSidePanel(controller.gameMenu, close) {
            Column(
                Modifier.fillMaxHeight().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        game.title,
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    ovh.verbois.retrohub.ui.components.RoundButton(Lucide.X, "Fermer", close, size = 48.dp)
                }
                androidx.compose.material3.HorizontalDivider(color = Palette.Line)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { items() }
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
    ovh.verbois.retrohub.ui.components.ConsoleMenuItem(label, onClick, modifier, icon = icon)
}
