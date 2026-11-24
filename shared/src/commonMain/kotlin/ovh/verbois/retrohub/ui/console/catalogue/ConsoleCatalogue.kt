package ovh.verbois.retrohub.ui.console.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.CatalogueSource
import ovh.verbois.retrohub.api.ServerJob
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.catalogue.CatalogueFilters
import ovh.verbois.retrohub.ui.catalogue.rememberCatalogueFeed
import ovh.verbois.retrohub.ui.components.LocalScreenActive
import ovh.verbois.retrohub.ui.components.gridNavigation
import ovh.verbois.retrohub.ui.components.progressiveNavigation
import ovh.verbois.retrohub.ui.console.kit.ConsoleButton
import ovh.verbois.retrohub.ui.console.kit.ConsolePage
import ovh.verbois.retrohub.ui.console.kit.ConsoleSideSheet
import ovh.verbois.retrohub.ui.console.kit.ConsoleTextField
import ovh.verbois.retrohub.ui.console.kit.GameTile
import ovh.verbois.retrohub.ui.console.kit.Hint
import ovh.verbois.retrohub.ui.console.kit.LocalCurrentTab
import ovh.verbois.retrohub.ui.console.kit.OptionHeading
import ovh.verbois.retrohub.ui.console.kit.OptionRow
import ovh.verbois.retrohub.ui.console.kit.Pad
import ovh.verbois.retrohub.ui.console.kit.PadGlyph
import ovh.verbois.retrohub.ui.console.kit.PageInset
import ovh.verbois.retrohub.ui.console.kit.ProvideHints
import ovh.verbois.retrohub.ui.console.kit.RestoreFocus
import ovh.verbois.retrohub.ui.console.kit.dim
import ovh.verbois.retrohub.ui.console.kit.rememberFocusMemory
import ovh.verbois.retrohub.ui.console.kit.remembered
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route

private enum class CatalogueTab(val label: String) { ALL("Tous"), SERVER("Sur le serveur"), DEVICE("Sur l’appareil") }

/** One tile, whatever its source (server catalogue or games on this device). */
private data class Tile(val id: String, val title: String, val console: String, val coverUrl: String?, val onDevice: Boolean, val favorite: Boolean)

private val FiltersSaver = Saver<CatalogueFilters, String>(
    { ApiJson.encodeToString(CatalogueFilters.serializer(), it) },
    { runCatching { ApiJson.decodeFromString(CatalogueFilters.serializer(), it) }.getOrNull() },
)

/** The landscape catalogue: a full-screen PS5-like library with LB/RB tabs, an endless grid and an options panel. */
@Composable
fun ConsoleCatalogue(controller: UiController) {
    val container = controller.container
    val online by container.platform.online.collectAsState()
    val library by container.library.games.collectAsState()
    val downloads by container.downloads.states.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filters by rememberSaveable(stateSaver = FiltersSaver) { mutableStateOf(CatalogueFilters()) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var editSearch by remember { mutableIntStateOf(0) }
    var jobs by remember { mutableStateOf<List<ServerJob>>(emptyList()) }
    var sources by remember { mutableStateOf<List<CatalogueSource>>(emptyList()) }
    var consoles by remember { mutableStateOf(Consoles.all.map { it.id to it.name }) }
    val current = CatalogueTab.entries[tab]
    val serverFilters = filters.copy(installed = if (current == CatalogueTab.SERVER) true else filters.installed)
    val feed = rememberCatalogueFeed(container, serverFilters, online && current != CatalogueTab.DEVICE, refresh)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val memory = rememberFocusMemory()
    val searchFocus = remember { FocusRequester() }
    val sheetFocus = remember { FocusRequester() }
    val active = LocalScreenActive.current
    // The library already knows which catalogue games are on this device: refresh it when the server installs one.
    LaunchedEffect(online, lifecycle) {
        if (!online) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            runCatching { consoles = container.api.consoles().map { it.id to it.name } }
            runCatching { sources = container.api.catalogueSources() }
            while (true) {
                try {
                    val next = container.api.activeServerDownloads()
                    if (jobs.any { old -> next.none { it.id == old.id } }) {
                        container.library.refresh()
                        refresh++
                    }
                    jobs = next
                } catch (_: ApiException) { }
                delay(3000)
            }
        }
    }
    LaunchedEffect(library.size) { if (online && current != CatalogueTab.DEVICE && feed.loaded) refresh++ }
    val byId = remember(library) { library.associateBy { it.id } }
    val tiles = if (current == CatalogueTab.DEVICE) {
        library.filter { game ->
            game.downloaded && (filters.console.isBlank() || game.console == filters.console) &&
                (filters.search.isBlank() || ovh.verbois.retrohub.ui.shell.matches(game, filters.search))
        }.sortedBy { if (filters.sort == "title") it.sortTitle else "" }.map { Tile(it.id, it.title, it.console, it.remote.coverUrl, true, it.favorite) }
    } else {
        feed.items.map { entry ->
            val known = byId[entry.id]
            Tile(entry.id, entry.title, entry.console, entry.coverUrl, known?.downloaded == true, known?.favorite == true)
        }
    }
    val openTile = { id: String -> controller.nav.go(Route.CatalogueGame(id)) }
    if (!sheet) {
        ProvideHints(
            Hint(Pad.A, "Ouvrir"),
            Hint(Pad.Y, "Rechercher") { editSearch++ },
            Hint(Pad.START, "Filtres") { sheet = true },
            Hint(Pad.B, "Retour") { controller.back() },
        )
    }
    CompositionLocalProvider(LocalScreenActive provides (active && !sheet)) {
        ConsolePage(
            "Catalogue",
            Modifier.testTag("catalogue").onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.ButtonY -> {
                        if (event.type == KeyEventType.KeyUp) {
                            runCatching { searchFocus.requestFocus() }
                            editSearch++
                        }
                        true
                    }

                    Key.ButtonStart -> {
                        if (event.type == KeyEventType.KeyUp) sheet = true
                        true
                    }

                    else -> false
                }
            },
            subtitle = subtitle(current, feed.total, tiles.size, online, jobs),
            tabs = CatalogueTab.entries.map { it.label },
            tab = tab,
            onTab = { tab = it },
            trailing = {
                ConsoleTextField(
                    filters.search,
                    { filters = filters.copy(search = it.take(250)) },
                    "Rechercher",
                    Modifier.width(260.dp).focusRequester(searchFocus),
                    icon = Lucide.Search,
                    imeAction = ImeAction.Search,
                    editRequest = editSearch,
                    tag = "catalogue-search",
                )
                ConsoleButton(
                    if (filters.active) "Filtres •" else "Filtres",
                    { sheet = true },
                    Modifier.testTag("catalogue-filters"),
                    icon = Lucide.SlidersHorizontal,
                ) { PadGlyph(Pad.START) }
            },
        ) {
            when {
                current != CatalogueTab.DEVICE && !online -> Message("Le catalogue s’affiche avec une connexion au serveur.")

                current != CatalogueTab.DEVICE && feed.error != null && tiles.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(feed.error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    ConsoleButton("Réessayer", { refresh++ }, icon = Lucide.RefreshCw)
                }

                current != CatalogueTab.DEVICE && !feed.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                }

                tiles.isEmpty() -> Message(
                    when {
                        filters.active || filters.search.isNotBlank() -> "Aucun jeu avec ces filtres."
                        current == CatalogueTab.DEVICE -> "Aucun jeu sur cet appareil."
                        else -> "Aucun jeu sur le serveur."
                    },
                )

                else -> {
                    val grid = rememberLazyGridState()
                    val currentTab = LocalCurrentTab.current
                    val columns = 6
                    val navigation = gridNavigation(grid, tiles.size, columns)
                    LaunchedEffect(grid, tiles.size) {
                        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect {
                            if (it >= tiles.size - columns * 2) feed.more()
                        }
                    }
                    LazyVerticalGrid(
                        GridCells.Fixed(columns),
                        Modifier.fillMaxSize().testTag("catalogue-grid").progressiveNavigation(),
                        state = grid,
                        contentPadding = PaddingValues(horizontal = PageInset, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(tiles, key = { _, tile -> tile.id }) { index, tile ->
                            val running = downloads[byId[tile.id]?.playGameId] as? DownloadState.Running
                            GameTile(
                                tile.title,
                                tile.console,
                                tile.coverUrl,
                                { openTile(tile.id) },
                                navigation(index).remembered(memory, tile.id).testTag("catalogue-game-${tile.id}")
                                    // Up from the first row: the current tab, as on a PS5 hub.
                                    .focusProperties { if (index < columns) up = currentTab ?: searchFocus },
                                subtitle = Consoles.name(tile.console),
                                onDevice = tile.onDevice,
                                favorite = tile.favorite,
                                progress = running?.fraction,
                            )
                        }
                    }
                    RestoreFocus(memory, tiles.first().id)
                }
            }
        }
    }
    ConsoleSideSheet(sheet, "Filtres et tri", { sheet = false }, initialFocus = sheetFocus, tag = "catalogue-filter-sheet") {
        val choose = { next: CatalogueFilters -> filters = next }
        OptionHeading("Trier par")
        OptionRow("Derniers ajouts", { choose(filters.copy(sort = "recent")) }, Modifier.focusRequester(sheetFocus), selected = filters.sort == "recent")
        OptionRow("Titre", { choose(filters.copy(sort = "title")) }, selected = filters.sort == "title")
        OptionHeading("Console")
        OptionRow("Toutes les consoles", { choose(filters.copy(console = "")) }, selected = filters.console.isBlank())
        consoles.forEach { (id, name) -> OptionRow(name, { choose(filters.copy(console = id)) }, selected = filters.console == id) }
        if (current != CatalogueTab.DEVICE) {
            OptionHeading("Langue")
            listOf("" to "Toutes les langues", "fr" to "Français", "en" to "Anglais", "ja" to "Japonais").forEach { (id, name) ->
                OptionRow(name, { choose(filters.copy(language = id)) }, selected = filters.language == id)
            }
            if (sources.isNotEmpty()) {
                OptionHeading("Source")
                OptionRow("Toutes les sources", { choose(filters.copy(source = "")) }, selected = filters.source.isBlank())
                sources.forEach { source -> OptionRow(source.name, { choose(filters.copy(source = source.id)) }, selected = filters.source == source.id) }
            }
            OptionHeading("Genre et année")
            ConsoleTextField(filters.genre, { choose(filters.copy(genre = it.take(60))) }, "Genre", icon = Lucide.Type)
            ConsoleTextField(
                filters.year?.toString().orEmpty(),
                { choose(filters.copy(year = it.filter(Char::isDigit).take(4).toIntOrNull())) },
                "Année",
                icon = Lucide.Clock,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
        }
        OptionHeading("")
        OptionRow("Réinitialiser les filtres", { choose(CatalogueFilters(search = filters.search)) }, icon = Lucide.RotateCcw)
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = PageInset), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = dim)
    }
}

private fun subtitle(tab: CatalogueTab, total: Int, shown: Int, online: Boolean, jobs: List<ServerJob>): String? {
    val count = if (tab == CatalogueTab.DEVICE) shown else total
    val games = when {
        tab != CatalogueTab.DEVICE && !online -> null
        count == 0 -> null
        count == 1 -> "1 jeu"
        else -> "$count jeux"
    }
    val job = jobs.firstOrNull()?.let { "Sur le serveur : ${it.label.ifBlank { "téléchargement" }} ${it.progress.toInt()} %" }
    return listOfNotNull(games, job).joinToString(" · ").ifBlank { null }
}
