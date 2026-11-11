package ovh.verbois.retrohub.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.core.searchKey
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.LocalNavigationFocus
import ovh.verbois.retrohub.ui.components.RoundButton
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

fun matches(game: Game, query: String): Boolean = searchKey(query).split(' ').filter { it.isNotBlank() }.all { it in game.searchText }

fun searchGames(games: List<Game>, query: String): List<Game> {
    val words = searchKey(query).split(' ').filter { it.isNotBlank() }
    return if (words.isEmpty()) {
        games.sortedByDescending { it.lastPlayed }.take(8)
    } else {
        games.asSequence().filter { game -> words.all { it in game.searchText } }.take(40).toList()
    }
}

@Composable
fun SearchOverlay(controller: UiController) {
    val games by controller.container.library.games.collectAsState()
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val results by produceState(emptyList<Game>(), query, games) {
        if (query.isNotBlank()) delay(100)
        value = withContext(Dispatchers.Default) { searchGames(games, query) }
    }
    val field = remember { FocusRequester() }
    val tree = LocalNavigationFocus.current
    SideEffect { tree.content = field }
    Box(
        Modifier.fillMaxSize().focusGroup(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(field).focusProperties { up = tree.header },
                placeholder = { Text("Un jeu, une console…", color = Palette.TextFaint) },
                leadingIcon = { Icon(Lucide.Search, null, tint = Palette.TextDim) },
                trailingIcon = {
                    Box(
                        Modifier.size(48.dp).consoleFocus(RoundedCornerShape(50), {
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
                keyboardActions = KeyboardActions(onSearch = {
                    val submitted = query
                    scope.launch {
                        val first = withContext(Dispatchers.Default) { searchGames(games, submitted).firstOrNull() }
                        if (query == submitted) first?.let { open(controller, it) }
                    }
                }),
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
                    "Résultats"
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
                            muted = !game.downloaded,
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
    val tree = LocalNavigationFocus.current
    SideEffect { tree.content = first }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 420.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.background(Palette.SkyLow).verticalScroll(rememberScrollState()).focusGroup()
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                    .padding(vertical = 10.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Profil", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    RoundButton(Lucide.X, "Fermer le profil", onClose, size = 48.dp)
                }
                MenuItem(Lucide.SlidersHorizontal, "Paramètres de l’application", Modifier.focusRequester(first).focusProperties { up = tree.header }) {
                    controller.nav.go(Route.Settings())
                }
                MenuItem(Lucide.Gamepad2, "Commandes et manette") { controller.nav.go(Route.Settings(SettingsPage.CONTROLS)) }
                MenuItem(Lucide.Layers, "Réglages des consoles") { controller.nav.go(Route.Settings(SettingsPage.CONSOLES)) }
                MenuItem(Lucide.RefreshCw, "Synchroniser maintenant") { controller.syncNow() }
                MenuItem(Lucide.HardDrive, "Stockage") { controller.nav.go(Route.Settings(SettingsPage.STORAGE)) }
                MenuItem(
                    Lucide.UserRound,
                    "Compte RetroHub",
                    Modifier.focusProperties {
                        down = FocusRequester.Cancel
                    },
                ) { controller.nav.go(Route.Settings(SettingsPage.ACCOUNT)) }
            }
        }
    }
    AutoFocus(first)
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 54.dp).consoleFocus(RoundedCornerShape(14.dp), onClick, focusScale = 1.01f).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Palette.Select)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    }
}
