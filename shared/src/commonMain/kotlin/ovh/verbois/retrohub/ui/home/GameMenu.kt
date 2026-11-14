package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.components.controllerDialog
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

/** Le menu du jeu choisi, ancré sous son titre : jouer, partie neuve, favori, options. */
@Composable
fun GameMenu(controller: UiController, game: Game) {
    val downloads by controller.container.downloads.states.collectAsState()
    val first = remember { FocusRequester() }
    val close = { controller.gameMenu = false }
    var removing by remember(game.id) { mutableStateOf(false) }
    DropdownMenu(
        expanded = controller.gameMenu,
        onDismissRequest = close,
        modifier = Modifier.controllerDialog(close, dismissOnStart = true),
        containerColor = Palette.Deep,
        shape = RoundedCornerShape(18.dp),
    ) {
        if (controller.gameMenu) AutoFocus(first)
        val action = gameAction(controller, game, downloads[game.playGameId])
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
        if (controller.portrait) PinMenuItems(controller, game.id)
        Item(Lucide.Settings2, "Options du jeu") {
            close()
            controller.nav.go(Route.Settings(SettingsPage.GAME, game.id))
        }
        val download = downloads[game.playGameId]
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
        modifier.fillMaxWidth().heightIn(min = 48.dp).consoleFocus(RoundedCornerShape(12.dp), onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = Palette.Text)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
    }
}

@Composable
internal fun PinMenuItems(controller: UiController, key: String) {
    val settings by controller.container.settings.settings.collectAsState()
    val pins = settings.homePins
    Item(Lucide.Pin, if (key in pins) "Détacher de l’accueil" else "Épingler à l’accueil") {
        controller.container.settings.update { it.toggleHomePin(key) }
        controller.gameMenu = false
    }
    if (key in pins) {
        if (pins.indexOf(key) > 0) {
            Item(Lucide.ArrowLeft, "Déplacer à gauche") {
                controller.container.settings.update { it.moveHomePin(key, -1) }
                controller.gameMenu = false
            }
        }
        if (pins.indexOf(key) < pins.lastIndex) {
            Item(Lucide.ChevronRight, "Déplacer à droite") {
                controller.container.settings.update { it.moveHomePin(key, 1) }
                controller.gameMenu = false
            }
        }
    }
}
