package ovh.verbois.retrohub.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.library.StorageUsage
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.CenteredMessage
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.FilterChip
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.ProgressLine
import ovh.verbois.retrohub.ui.components.RoundButton
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.home.GameCard
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.Palette
import ovh.verbois.retrohub.ui.theme.consoleColor

enum class Sort(val label: String) {
    RECENT("Récents"),
    TITLE("A → Z"),
    CONSOLE("Console"),
}

fun sortGames(games: List<Game>, sort: Sort): List<Game> = when (sort) {
    Sort.RECENT -> games.sortedWith(compareByDescending<Game> { it.lastPlayed }.thenBy { it.title.lowercase() })

    Sort.TITLE -> games.sortedBy { it.title.lowercase() }

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
        }.thenBy { it.title.lowercase() },
    )
}

/** Bibliothèque et favoris : filtres par console, tri, grille de jaquettes. */
@Composable
fun LibraryScreen(controller: UiController, console: String?, favoritesOnly: Boolean) {
    val games by controller.container.library.games.collectAsState()
    val downloads by controller.container.downloads.states.collectAsState()
    var filter by rememberSaveable(console) { mutableStateOf(console) }
    var sort by rememberSaveable { mutableStateOf(Sort.RECENT) }
    val hidden = controller.container.settings.settings.collectAsState().value.hiddenConsoles
    val base =
        remember(games, favoritesOnly, hidden) {
            games.filter { (!favoritesOnly || it.favorite) && it.console !in hidden }
        }
    val consoles = remember(base) { Consoles.all.filter { spec -> base.any { it.console == spec.id } } }
    val shown = remember(base, filter, sort) { sortGames(base.filter { filter == null || it.console == filter }, sort) }
    LaunchedEffect(filter) { controller.backdrop = Backdrop(null, filter ?: shown.firstOrNull()?.console) }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    ovh.verbois.retrohub.ui.components.AutoFocus(first)
    if (base.isEmpty()) {
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (controller.portrait) 150.dp else 168.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Un seul bouton : il ouvre le choix de la console et du tri.
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FilterMenu(filter, sort, consoles, { filter = it }, { sort = it }, Modifier.focusRequester(first))
                Text(
                    (if (favoritesOnly) "Favoris · " else "") + "${shown.size} jeu${if (shown.size > 1) "x" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                )
            }
        }
        items(shown, key = { it.id }) { game ->
            GameCard(game, { controller.showGame(game.id) }, download = downloads[game.playGameId])
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
    val local = remember(games, downloads) { games.filter { it.downloaded || downloads.containsKey(it.playGameId) }.sortedBy { it.title.lowercase() } }
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Glass).consoleFocus(RoundedCornerShape(18.dp), {
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
        ovh.verbois.retrohub.ui.components.GlassButton(
            listOfNotNull(filter?.let { Consoles[it]?.shortName } ?: "Toutes les consoles", sort.label).joinToString(" · "),
            { open = true },
            modifier,
            icon = Lucide.SlidersHorizontal,
            height = 42.dp,
        )
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Palette.Deep,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        ) {
            // Consoles en colonnes de six, tri à droite : le menu tient en paysage.
            val choices = listOf<Pair<String?, String>>(null to "Toutes") + consoles.map { it.id to it.shortName }
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                choices.chunked(6).forEachIndexed { index, column ->
                    Column(Modifier.width(170.dp)) {
                        MenuLabel(if (index == 0) "Console" else " ")
                        column.forEach { (id, name) ->
                            MenuChoice(name, filter == id, id?.let { Consoles[it]?.color }?.let { Color(it) }) {
                                onFilter(id)
                                open = false
                            }
                        }
                    }
                }
                Column(Modifier.width(150.dp)) {
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
        Modifier.fillMaxWidth().height(40.dp).consoleFocus(shape, onClick).clip(shape)
            .background(if (selected) Palette.Glass else Color.Transparent).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot ?: Color.Transparent))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1)
        if (selected) androidx.compose.material3.Icon(Lucide.Check, null, Modifier.size(18.dp), tint = Palette.Text)
    }
}
