package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

/** Le menu du jeu choisi, ancré sous son titre : jouer, partie neuve, favori, options. */
@Composable
fun GameMenu(controller: UiController, game: Game) {
    val downloads by controller.container.downloads.states.collectAsState()
    val close = { controller.gameMenu = false }
    DropdownMenu(
        expanded = controller.gameMenu,
        onDismissRequest = close,
        containerColor = Palette.Deep,
        shape = RoundedCornerShape(18.dp),
    ) {
        val action = gameAction(controller, game, downloads[game.playGameId])
        Item(action.icon, action.label) {
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
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.Text) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = Palette.Text) },
    )
}

/** Les mêmes actions, en rangée sous le titre (accueil en paysage, où la place manque dessous). */
@Composable
fun GameActionsRow(controller: UiController, game: Game, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 52.dp) {
    val downloads by controller.container.downloads.states.collectAsState()
    val first = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val close = { controller.gameMenu = false }
    val action = gameAction(controller, game, downloads[game.playGameId])
    androidx.compose.foundation.layout.Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = HomeEdge),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        ovh.verbois.retrohub.ui.components.PrimaryButton(
            action.label,
            {
                close()
                action.run()
            },
            Modifier.focusRequester(first),
            icon = action.icon,
            progress = action.progress,
            height = height,
        )
        if (game.downloaded && game.lastPlayed != null) {
            ovh.verbois.retrohub.ui.components.GlassButton("Nouvelle partie", {
                close()
                controller.play(game, fresh = true)
            }, icon = Lucide.RotateCcw, height = height)
        }
        ovh.verbois.retrohub.ui.components.GlassButton(if (game.favorite) "Retirer des favoris" else "Ajouter aux favoris", {
            close()
            controller.toggleFavorite(game)
        }, icon = Lucide.Heart, height = height)
        ovh.verbois.retrohub.ui.components.GlassButton("Options du jeu", {
            close()
            controller.nav.go(Route.Settings(SettingsPage.GAME, game.id))
        }, icon = Lucide.Settings2, height = height)
    }
    ovh.verbois.retrohub.ui.components.AutoFocus(first)
}
