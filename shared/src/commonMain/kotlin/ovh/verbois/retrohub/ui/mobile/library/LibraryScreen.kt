package ovh.verbois.retrohub.ui.mobile.library

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.CenteredMessage
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.ConsoleMenuItem
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.LocalNavigationFocus
import ovh.verbois.retrohub.ui.components.ProgressLine
import ovh.verbois.retrohub.ui.components.RoundButton
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.components.controllerDialog
import ovh.verbois.retrohub.ui.home.*
import ovh.verbois.retrohub.ui.home.GameCard
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

enum class Sort(val label: String) {
    RECENT("Récents"),
    TITLE("A → Z"),
    CONSOLE("Console"),
    FAVORITES("Favoris"),
}

private val consoleOrder = Consoles.all.mapIndexed { index, console -> console.id to index }.toMap()

fun sortGames(games: List<Game>, sort: Sort): List<Game> = when (sort) {
    Sort.RECENT -> ovh.verbois.retrohub.ui.home.homeOrder(games)
    Sort.TITLE -> games.sortedBy { it.sortTitle }
    Sort.FAVORITES -> ovh.verbois.retrohub.ui.home.homeOrder(games, ovh.verbois.retrohub.emulation.HomeOrder.FAVORITES)
    Sort.CONSOLE -> games.sortedWith(compareBy<Game> { consoleOrder[it.console] ?: Int.MAX_VALUE }.thenBy { it.sortTitle })
}

/** Library games use one grid and the same directional navigation. */
@Composable
fun LibraryScreen(controller: UiController, console: String?, favoritesOnly: Boolean) {
    val tree = LocalNavigationFocus.current
    val model by controller.home.collectAsState()
    val downloads by controller.container.downloads.states.collectAsState()
    val settings by controller.container.settings.settings.collectAsState()
    var filter by rememberSaveable(console) { mutableStateOf(console) }
    var sort by rememberSaveable(settings.homeOrder) {
        mutableStateOf(
            when (settings.homeOrder) {
                ovh.verbois.retrohub.emulation.HomeOrder.TITLE -> Sort.TITLE
                ovh.verbois.retrohub.emulation.HomeOrder.FAVORITES -> Sort.FAVORITES
                else -> Sort.RECENT
            },
        )
    }
    var focusedGame by rememberSaveable { mutableStateOf<String?>(null) }
    val base = if (favoritesOnly) model.favorites else model.games
    val consoles = remember(base) {
        val present = base.map { it.console }.toSet()
        Consoles.all.filter { it.id in present }
    }
    val shown by produceState<List<Game>>(emptyList(), base, filter, sort, settings.homeActivity) {
        value = withContext(Dispatchers.Default) {
            val filtered = base.filter { filter == null || it.console == filter }
            when (sort) {
                Sort.RECENT -> ovh.verbois.retrohub.ui.home.homeOrder(filtered, activity = settings.homeActivity)

                Sort.FAVORITES -> ovh.verbois.retrohub.ui.home.homeOrder(
                    filtered,
                    ovh.verbois.retrohub.emulation.HomeOrder.FAVORITES,
                    activity = settings.homeActivity,
                )

                else -> sortGames(filtered, sort)
            }
        }
    }
    if (favoritesOnly && base.isEmpty()) {
        SideEffect { tree.content = tree.header }
        AutoFocus(tree.header)
        CenteredMessage("Aucun favori pour l’instant", "", Lucide.Heart)
        return
    }
    val grid = rememberLazyGridState()
    val selectedFocus = remember { FocusRequester() }
    val filtersFocus = remember { FocusRequester() }
    val indices = remember(shown) { shown.mapIndexed { index, game -> game.id to index }.toMap() }
    val selected = shown.getOrNull(indices[focusedGame] ?: 0)
    val contentFocus = if (selected != null) selectedFocus else filtersFocus
    SideEffect { tree.content = contentFocus }
    AutoFocus(contentFocus)
    LaunchedEffect(shown) {
        val index = indices[focusedGame]
        if (index != null) {
            if (grid.layoutInfo.visibleItemsInfo.none { it.key == focusedGame }) grid.scrollToItem(index)
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == focusedGame } }.first { it }
            selectedFocus.requestFocus()
        }
    }
    LaunchedEffect(filter) { controller.backdrop = Backdrop(null, filter ?: base.firstOrNull()?.console) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = controller.portrait
        val toolbar: @Composable () -> Unit = {
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
                },
            )
        }
        if (portrait) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { toolbar() }
        } else {
            Column(Modifier.padding(start = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { toolbar() }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize().padding(start = if (portrait) 8.dp else 68.dp, top = if (portrait) 64.dp else 0.dp),
        ) {
            val minimum = if (portrait) 116.dp else 128.dp
            val columns = ((maxWidth - 32.dp + 12.dp) / (minimum + 12.dp)).toInt().coerceAtLeast(1)
            val navigation = ovh.verbois.retrohub.ui.components.gridNavigation(grid, shown.size, columns)
            if (shown.isEmpty()) {
                CenteredMessage("Aucun jeu", "", Lucide.FolderOpen)
            } else {
                LazyVerticalGrid(
                    state = grid,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize().focusGroup().testTag("library-grid"),
                    contentPadding = PaddingValues(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(shown, key = { _, game -> game.id }) { index, game ->
                        val modifier = navigation(index).then(
                            if (game.id ==
                                selected?.id
                            ) {
                                Modifier.focusRequester(selectedFocus)
                            } else {
                                Modifier
                            },
                        ).focusProperties {
                            if (index < columns) up = tree.header
                            if (index % columns == 0) left = filtersFocus
                            if (index % columns == columns - 1 || index == shown.lastIndex) right = FocusRequester.Cancel
                            if (index / columns == shown.lastIndex / columns) down = FocusRequester.Cancel
                        }.onFocusChanged { if (it.isFocused) focusedGame = game.id }
                        GameCard(game, { controller.showGame(game.id) }, modifier.testTag("library-game-${game.id}"), downloads[game.playGameId])
                    }
                }
            }
        }
    }
}

/** Downloaded games use the same cover cards as the library, with storage first. */
@Composable
fun DownloadsScreen(controller: UiController) {
    val container = controller.container
    val games by container.library.games.collectAsState()
    val downloads by container.downloads.states.collectAsState()
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var confirm by remember { mutableStateOf<Game?>(null) }
    var menuGame by remember { mutableStateOf<String?>(null) }
    val tree = LocalNavigationFocus.current
    val first = remember { FocusRequester() }
    val downloading = remember(downloads) { downloads.keys }
    val local = remember(games, downloading) { games.filter { it.downloaded || it.playGameId in downloading }.sortedBy { it.sortTitle } }
    val grid = rememberLazyGridState()
    SideEffect { tree.content = first }
    ovh.verbois.retrohub.ui.components.AutoFocus(first)
    LaunchedEffect(local) { usage = container.downloads.usage() }
    LaunchedEffect(Unit) { controller.backdrop = Backdrop(null, local.firstOrNull()?.console) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        val minimum = if (controller.portrait) 116.dp else 128.dp
        val columns = (maxWidth / (minimum + 12.dp)).toInt().coerceAtLeast(1)
        val navigation = ovh.verbois.retrohub.ui.components.gridNavigation(grid, local.size + 1, columns)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.fillMaxSize().focusGroup().testTag("downloads-grid"),
            contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "storage") {
                StorageCard(
                    usage,
                    {
                        controller.nav.go(ovh.verbois.retrohub.ui.nav.Route.Settings(ovh.verbois.retrohub.ui.nav.SettingsPage.STORAGE))
                    },
                    navigation(0).focusRequester(first).focusProperties {
                        up = tree.header
                        left = FocusRequester.Cancel
                        if (local.isEmpty()) right = FocusRequester.Cancel
                        if (local.size < columns) down = FocusRequester.Cancel
                    },
                )
            }
            itemsIndexed(local, key = { _, game -> game.id }) { index, game ->
                val position = index + 1
                val state = downloads[game.playGameId]
                val menuFocus = remember { FocusRequester() }
                Box {
                    GameCard(
                        game,
                        { controller.showGame(game.id) },
                        navigation(index + 1).testTag("download-game-${game.id}").focusProperties {
                            if (position < columns) up = tree.header
                            if (position % columns == 0) left = FocusRequester.Cancel
                            if (position % columns == columns - 1 || index == local.lastIndex) right = FocusRequester.Cancel
                            if (position / columns == local.size / columns) down = FocusRequester.Cancel
                        },
                        download = state,
                        onLongClick = { menuGame = game.id },
                    )
                    ovh.verbois.retrohub.ui.components.ConsoleDropdownMenu(
                        expanded = menuGame == game.id,
                        onDismissRequest = { menuGame = null },
                        modifier = Modifier.focusRequester(menuFocus).focusGroup().controllerDialog({ menuGame = null }, dismissOnStart = true),
                    ) {
                        if (state is ovh.verbois.retrohub.library.DownloadState.Running) {
                            ConsoleMenuItem(
                                "Annuler le téléchargement",
                                icon = Lucide.X,
                                onClick = {
                                    menuGame = null
                                    container.downloads.cancel(game.playGameId)
                                },
                            )
                        }
                        if (state is ovh.verbois.retrohub.library.DownloadState.Failed) {
                            if (state.retryable) {
                                ConsoleMenuItem(
                                    "Réessayer",
                                    icon = Lucide.RefreshCw,
                                    onClick = {
                                        menuGame = null
                                        container.downloads.start(game)
                                    },
                                )
                            }
                            ConsoleMenuItem(
                                "Annuler le téléchargement",
                                icon = Lucide.X,
                                onClick = {
                                    menuGame = null
                                    container.downloads.cancel(game.playGameId)
                                },
                            )
                        }
                        if (game.downloaded && state !is ovh.verbois.retrohub.library.DownloadState.Running) {
                            ConsoleMenuItem(
                                "Supprimer du téléphone",
                                icon = Lucide.Trash2,
                                tint = Palette.Danger,
                                onClick = {
                                    menuGame = null
                                    confirm = game
                                },
                            )
                        }
                        if (menuGame == game.id) AutoFocus(menuFocus)
                    }
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
private fun StorageCard(usage: StorageUsage?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.testTag("downloads-storage").consoleFocus(shape, onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(Palette.Glass)) {
            Column(
                Modifier.align(Alignment.Center).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Lucide.HardDrive, null, Modifier.size(32.dp), tint = Palette.Text)
                Text(usage?.let { formatBytes(it.total) } ?: "…", style = MaterialTheme.typography.headlineMedium, color = Palette.Text, maxLines = 1)
                Text("utilisés", style = MaterialTheme.typography.bodySmall)
            }
            usage?.let {
                ProgressLine(
                    (it.total.toDouble() / (it.total + it.free).coerceAtLeast(1)).toFloat(),
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
                )
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text("Stockage", style = MaterialTheme.typography.titleMedium)
            Text(usage?.let { "${formatBytes(it.free)} libres" } ?: "…", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
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
    val menuFocus = remember { FocusRequester() }
    Box {
        RoundButton(Lucide.SlidersHorizontal, "Filtrer et trier", { open = true }, modifier, size = 48.dp, selected = filter != null || sort != Sort.RECENT)
        ovh.verbois.retrohub.ui.components.ConsoleDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.focusRequester(menuFocus).focusGroup().controllerDialog({ open = false }, dismissOnStart = true),
        ) {
            // A single scrollable column stays usable on narrow phones and in split screen.
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 6.dp)) {
                MenuChoice("Toutes", filter == null, null) {
                    onFilter(null)
                    open = false
                }
                consoles.groupBy { it.generation }.toSortedMap().forEach { (generation, group) ->
                    MenuLabel("Génération $generation")
                    group.forEach { console ->
                        MenuChoice(console.shortName, filter == console.id, Color(console.color)) {
                            onFilter(console.id)
                            open = false
                        }
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
            if (open) AutoFocus(menuFocus)
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
