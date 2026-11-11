package ovh.verbois.retrohub.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.library.StorageUsage
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.CenteredMessage
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.LocalNavigationFocus
import ovh.verbois.retrohub.ui.components.ProgressLine
import ovh.verbois.retrohub.ui.components.RoundButton
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.home.GameCard
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette
import ovh.verbois.retrohub.ui.theme.consoleColor

enum class Sort(val label: String) {
    RECENT("Récents"),
    TITLE("A → Z"),
    CONSOLE("Console"),
}

fun sortGames(games: List<Game>, sort: Sort): List<Game> = when (sort) {
    Sort.RECENT -> games.sortedWith(compareByDescending<Game> { it.lastPlayed }.thenBy { it.sortTitle })

    Sort.TITLE -> games.sortedBy { it.sortTitle }

    Sort.CONSOLE -> games.sortedWith(
        compareBy<Game> {
            Consoles.all.indexOfFirst { s -> s.id == it.console }.let { i ->
                if (i <
                    0
                ) {
                    99
                } else {
                    i
                }
            }
        }.thenBy { it.sortTitle },
    )
}

/** Bibliothèque et favoris : filtres par console, tri, grille de jaquettes. */
@Composable
fun LibraryScreen(controller: UiController, console: String?, favoritesOnly: Boolean) {
    val tree = LocalNavigationFocus.current
    val games by controller.container.library.games.collectAsState()
    val downloads by controller.container.downloads.states.collectAsState()
    var filter by rememberSaveable(console) { mutableStateOf(console) }
    var sort by rememberSaveable { mutableStateOf(Sort.RECENT) }
    var focusedGame by rememberSaveable { mutableStateOf<String?>(null) }
    val restoredFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val hidden = controller.container.settings.settings.collectAsState().value.hiddenConsoles
    val base =
        remember(games, favoritesOnly, hidden) {
            games.filter { (!favoritesOnly || it.favorite) && it.console !in hidden }
        }
    val consoles = remember(base) { Consoles.all.filter { spec -> base.any { it.console == spec.id } } }
    val sorted by produceState<List<Game>?>(null, base, filter, sort) {
        value = withContext(Dispatchers.Default) { sortGames(base.filter { filter == null || it.console == filter }, sort) }
    }
    val shown = sorted ?: return
    val grid = rememberLazyGridState()
    LaunchedEffect(filter) { controller.backdrop = Backdrop(null, filter ?: shown.firstOrNull()?.console) }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    if (focusedGame == null || shown.none { it.id == focusedGame }) ovh.verbois.retrohub.ui.components.AutoFocus(first)
    LaunchedEffect(shown) {
        val index = shown.indexOfFirst { it.id == focusedGame }
        if (index >= 0) {
            if (grid.layoutInfo.visibleItemsInfo.none { it.key == focusedGame }) grid.scrollToItem(index)
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == focusedGame } }.first { it }
            runCatching { restoredFocus.requestFocus() }
        }
    }
    if (base.isEmpty()) {
        SideEffect { tree.content = tree.header }
        ovh.verbois.retrohub.ui.components.AutoFocus(tree.header)
        CenteredMessage(
            if (favoritesOnly) "Aucun favori pour l’instant" else "Bibliothèque vide",
            if (favoritesOnly) {
                "Appui long sur un jeu, puis « Ajouter aux favoris »."
            } else {
                "Ajoutez des jeux depuis RetroHub sur le web, ils apparaîtront ici."
            },
            if (favoritesOnly) Lucide.Heart else Lucide.LibraryBig,
        )
        return
    }
    val filtersFocus = remember { FocusRequester() }
    val contentFocus = when {
        shown.isEmpty() -> filtersFocus
        shown.any { it.id == focusedGame } -> restoredFocus
        else -> first
    }
    SideEffect { tree.content = contentFocus }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.padding(start = 12.dp, top = 8.dp)) {
            FilterMenu(
                filter,
                sort,
                consoles,
                {
                    filter = it
                    focusedGame = null
                },
                {
                    sort = it
                    focusedGame = null
                },
                Modifier.focusRequester(filtersFocus).focusProperties {
                    up = tree.header
                    left = FocusRequester.Cancel
                    right = contentFocus
                    down = FocusRequester.Cancel
                },
            )
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f)) {
            val minimum = if (controller.portrait) 116.dp else 128.dp
            val columns = ((maxWidth - 32.dp + 12.dp) / (minimum + 12.dp)).toInt().coerceAtLeast(1)
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(shown, key = { _, game -> game.id }) { index, game ->
                    GameCard(
                        game,
                        { controller.showGame(game.id) },
                        modifier = (if (game.id == focusedGame) Modifier.focusRequester(restoredFocus) else Modifier)
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .focusProperties {
                                if (index < columns) up = tree.header
                                if (index % columns == 0) left = filtersFocus
                                if (index % columns == columns - 1 || index == shown.lastIndex) right = FocusRequester.Cancel
                                if (index + columns > shown.lastIndex) down = FocusRequester.Cancel
                            }
                            .testTag("library-game-${game.id}").onFocusChanged { if (it.isFocused) focusedGame = game.id },
                        download = downloads[game.playGameId],
                    )
                }
            }
        }
    }
}

/** Jeux présents sur le téléphone, espace utilisé et suppression (les sauvegardes restent). */
@Composable
fun DownloadsScreen(controller: UiController) {
    val container = controller.container
    val games by container.library.games.collectAsState()
    val downloads by container.downloads.states.collectAsState()
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var confirm by remember { mutableStateOf<Game?>(null) }
    val tree = LocalNavigationFocus.current
    val first = remember { FocusRequester() }
    val downloading = remember(downloads) { downloads.keys }
    val local = remember(games, downloading) { games.filter { it.downloaded || it.playGameId in downloading }.sortedBy { it.sortTitle } }
    SideEffect { tree.content = if (local.isEmpty()) tree.header else first }
    ovh.verbois.retrohub.ui.components.AutoFocus(if (local.isEmpty()) tree.header else first)
    LaunchedEffect(local.size) { usage = container.downloads.usage() }
    LaunchedEffect(Unit) { controller.backdrop = Backdrop(null, local.firstOrNull()?.console) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { usage?.let { StorageSummary(it) } }
        if (local.isEmpty()) {
            item {
                Text(
                    "Aucun jeu sur ce téléphone.",
                    Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextDim,
                )
            }
        }
        items(local, key = { it.id }) { game ->
            val state = downloads[game.playGameId]
            Row(
                Modifier.fillMaxWidth().then(
                    if (game.id ==
                        local.firstOrNull()?.id
                    ) {
                        Modifier.focusRequester(first).focusProperties { up = tree.header }
                    } else {
                        Modifier
                    },
                )
                    .clip(RoundedCornerShape(18.dp)).background(Palette.Glass).consoleFocus(RoundedCornerShape(18.dp), {
                        controller.showGame(game.id)
                    }, focusScale = 1.01f).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GameArt(
                    game.remote.coverUrl,
                    game.console,
                    game.title,
                    Modifier.width(64.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                    showTitleOnFallback = false,
                    muted = !game.downloaded,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(consoleColor(game.console)))
                        Text(
                            Consoles.name(game.console) + (game.local?.let { " · ${formatBytes(it.size)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (game.updateAvailable) Text("· Mise à jour disponible", style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
                    }
                    when (state) {
                        is ovh.verbois.retrohub.library.DownloadState.Running -> ProgressLine(state.fraction, Modifier.fillMaxWidth().padding(top = 4.dp))

                        is ovh.verbois.retrohub.library.DownloadState.Failed -> Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Warning,
                        )

                        null -> {}
                    }
                }
                if (state is ovh.verbois.retrohub.library.DownloadState.Running) {
                    RoundButton(Lucide.X, "Annuler le téléchargement", { container.downloads.cancel(game.playGameId) }, size = 44.dp)
                } else if (game.downloaded) {
                    RoundButton(Lucide.Trash2, "Supprimer ${game.title} du téléphone", { confirm = game }, size = 44.dp, tint = Palette.Danger)
                }
            }
        }
    }
    confirm?.let { game ->
        ConfirmDialog(
            "Supprimer du téléphone ?",
            "« ${game.title} » sera retiré de ce téléphone pour libérer ${formatBytes(game.local?.size ?: 0)}. Vos sauvegardes restent, ici et sur le serveur.",
            "Supprimer",
            onConfirm = {
                confirm = null
                scope.launch {
                    container.downloads.delete(game.playGameId)
                    usage = container.downloads.usage()
                    controller.show("« ${game.title} » a été supprimé du téléphone. Vos sauvegardes sont conservées.")
                }
            },
            onDismiss = { confirm = null },
            destructive = true,
        )
    }
}

@Composable
fun StorageSummary(usage: StorageUsage) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Lucide.HardDrive, null, Modifier.size(22.dp), tint = Palette.Select)
                Text("${formatBytes(usage.total)} utilisés par RetroHub", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${formatBytes(usage.free)} libres", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
            val all = (usage.total + usage.free).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Palette.Line)) {
                if (usage.roms > 0) Box(Modifier.weight(usage.roms.toFloat() / all).height(10.dp).background(Palette.Accent))
                if (usage.saves > 0) Box(Modifier.weight(usage.saves.toFloat() / all).height(10.dp).background(Palette.Select))
                if (usage.system > 0) Box(Modifier.weight(usage.system.toFloat() / all).height(10.dp).background(Palette.Success))
                Box(Modifier.weight((usage.free.toFloat() / all).coerceAtLeast(0.0001f)).height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Legend(Palette.Accent, "Jeux ${formatBytes(usage.roms)}")
                Legend(Palette.Select, "Sauvegardes ${formatBytes(usage.saves)}")
                Legend(Palette.Success, "Fichiers système ${formatBytes(usage.system)}")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Bouton de filtre : console et tri, dans un petit menu qui s’ouvre dessous. */
@Composable
private fun FilterMenu(
    filter: String?,
    sort: Sort,
    consoles: List<ovh.verbois.retrohub.emulation.ConsoleSpec>,
    onFilter: (String?) -> Unit,
    onSort: (Sort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton(Lucide.SlidersHorizontal, "Filtrer et trier", { open = true }, modifier, size = 48.dp, selected = filter != null || sort != Sort.RECENT)
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Palette.Deep,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        ) {
            // A single scrollable column stays usable on narrow phones and in split screen.
            val choices = listOf<Pair<String?, String>>(null to "Toutes") + consoles.map { it.id to it.shortName }
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 6.dp)) {
                MenuLabel("Console")
                choices.forEach { (id, name) ->
                    MenuChoice(name, filter == id, id?.let { Consoles[it]?.color }?.let { Color(it) }) {
                        onFilter(id)
                        open = false
                    }
                }
                MenuLabel("Trier")
                Sort.entries.forEach { option ->
                    MenuChoice(option.label, sort == option, null) {
                        onSort(option)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextDim,
    )
}

@Composable
private fun MenuChoice(text: String, selected: Boolean, dot: Color?, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).consoleFocus(shape, onClick).clip(shape)
            .background(if (selected) Palette.Glass else Color.Transparent).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot ?: Color.Transparent))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        if (selected) androidx.compose.material3.Icon(Lucide.Check, null, Modifier.size(18.dp), tint = Palette.Text)
    }
}
