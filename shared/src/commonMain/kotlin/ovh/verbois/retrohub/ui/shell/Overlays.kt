package ovh.verbois.retrohub.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

/** Ignore les accents et la casse : « pokemon » trouve « Pokémon ». */
fun searchKey(text: String): String = text.lowercase().map {
    when (it) {
        'à', 'â', 'ä', 'á' -> 'a'
        'é', 'è', 'ê', 'ë' -> 'e'
        'î', 'ï', 'í' -> 'i'
        'ô', 'ö', 'ó' -> 'o'
        'ù', 'û', 'ü', 'ú' -> 'u'
        'ç' -> 'c'
        else -> it
    }
}.joinToString("").filter { it.isLetterOrDigit() || it == ' ' }

fun matches(game: Game, query: String): Boolean {
    val words = searchKey(query).split(' ').filter { it.isNotBlank() }
    val haystack = searchKey(game.title + " " + Consoles.name(game.console) + " " + (game.remote.genre ?: ""))
    return words.all { it in haystack }
}

@Composable
fun SearchOverlay(controller: UiController) {
    val games by controller.container.library.games.collectAsState()
    var query by remember { mutableStateOf("") }
    val results =
        remember(query, games) {
            if (query.isBlank()) games.sortedByDescending { it.lastPlayed }.take(8) else games.filter { matches(it, query) }.take(40)
        }
    val field = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize().background(Palette.Scrim).clickable(remember { MutableInteractionSource() }, null) {
            controller.searching =
                false
        }.windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(field),
                placeholder = { Text("Un jeu, une console…", color = Palette.TextFaint) },
                leadingIcon = { Icon(Lucide.Search, null, tint = Palette.TextDim) },
                trailingIcon = {
                    Box(
                        Modifier.size(40.dp).consoleFocus(RoundedCornerShape(50), {
                            controller.searching = false
                        }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.X, "Fermer la recherche", tint = Palette.TextDim)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { open(controller, it) } }),
                colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Select,
                    unfocusedBorderColor = Palette.Line,
                    focusedContainerColor = Palette.Glass,
                    unfocusedContainerColor = Palette.Glass,
                    cursorColor = Palette.Accent,
                ),
            )
            Text(
                if (query.isBlank()) {
                    "Récemment joués"
                } else if (results.isEmpty()) {
                    "Aucun jeu ne correspond dans votre bibliothèque."
                } else {
                    "${results.size} jeu${if (results.size > 1) "x" else ""}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextDim,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { game ->
                    Row(
                        Modifier.fillMaxWidth().consoleFocus(RoundedCornerShape(16.dp), {
                            open(controller, game)
                        }, focusScale = 1.01f).clip(RoundedCornerShape(16.dp)).background(Palette.Glass).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        GameArt(
                            game.remote.coverUrl,
                            game.console,
                            game.title,
                            Modifier.width(52.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                            showTitleOnFallback = false,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Consoles.name(game.console), style = MaterialTheme.typography.bodySmall)
                        }
                        if (game.downloaded) Icon(Lucide.HardDriveDownload, "Téléchargé", Modifier.size(18.dp), tint = Palette.Success)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { field.requestFocus() }
}

private fun open(controller: UiController, game: Game) {
    controller.showGame(game.id)
}

/** Menu du profil (Start) : comme le menu déroulant d’une console. */
@Composable
fun AccountMenu(controller: UiController, account: Account, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize().background(Palette.Scrim.copy(alpha = 0.6f)).clickable(
            remember {
                MutableInteractionSource()
            },
            null,
            onClick = onClose,
        ).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        GlassPanel(Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 68.dp).width(340.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.background(Palette.BackgroundHigh.copy(alpha = 0.94f)).padding(vertical = 10.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(account.username, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${account.deviceName} · ${account.server.removePrefix("https://")}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MenuItem(Lucide.SlidersHorizontal, "Paramètres de l’application", Modifier.focusRequester(first)) {
                    onClose()
                    controller.nav.go(Route.Settings())
                }
                MenuItem(Lucide.Gamepad2, "Commandes et manette") {
                    onClose()
                    controller.nav.go(Route.Settings(SettingsPage.CONTROLS))
                }
                MenuItem(Lucide.Layers, "Réglages des consoles") {
                    onClose()
                    controller.nav.go(Route.Settings(SettingsPage.CONSOLES))
                }
                MenuItem(Lucide.RefreshCw, "Synchroniser maintenant") {
                    onClose()
                    controller.syncNow()
                }
                MenuItem(Lucide.HardDrive, "Stockage") {
                    onClose()
                    controller.nav.go(Route.Settings(SettingsPage.STORAGE))
                }
                MenuItem(Lucide.UserRound, "Compte RetroHub") {
                    onClose()
                    controller.nav.go(Route.Settings(SettingsPage.ACCOUNT))
                }
            }
        }
    }
    AutoFocus(first)
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(
            54.dp,
        ).consoleFocus(RoundedCornerShape(14.dp), onClick, focusScale = 1.01f).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Palette.Select)
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
