package ovh.verbois.retrohub.ui.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.CataloguePage
import ovh.verbois.retrohub.api.CatalogueQuery
import ovh.verbois.retrohub.api.CatalogueSource
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.api.ServerJob
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.LocalNavigationFocus
import ovh.verbois.retrohub.ui.components.ProgressLine
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.components.controllerDialog
import ovh.verbois.retrohub.ui.home.GameCard
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.Palette

/** Server-side, paginated browsing: the complete catalogue never has to fit on the phone. */
@Composable
fun CatalogueScreen(controller: UiController) {
    val container = controller.container
    val online by container.platform.online.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val library by container.library.games.collectAsState()
    var search by rememberSaveable { mutableStateOf("") }
    var console by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var installed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var source by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("recent") }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var filters by remember { mutableStateOf(false) }
    var cached by rememberSaveable { mutableStateOf("") }
    val result = remember(cached) { runCatching { ApiJson.decodeFromString(CataloguePage.serializer(), cached) }.getOrNull() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var jobs by remember { mutableStateOf<List<ServerJob>>(emptyList()) }
    var sources by remember { mutableStateOf<List<CatalogueSource>>(emptyList()) }
    var consoles by remember { mutableStateOf(Consoles.all.map { it.id to it.name }) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tree = LocalNavigationFocus.current
    val field = remember { FocusRequester() }
    val filtersFocus = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    SideEffect {
        tree.content = if (online) {
            filtersFocus
        } else if (result?.items?.isNotEmpty() == true) {
            first
        } else {
            tree.header
        }
    }
    AutoFocus(
        if (online) {
            filtersFocus
        } else if (result?.items?.isNotEmpty() == true) {
            first
        } else {
            tree.header
        },
    )
    val query = CatalogueQuery(search, console, language, installed, source, genre, year.toIntOrNull(), sort, page, settings.preferFrench)
    LaunchedEffect(query, online, refresh) {
        if (!online) return@LaunchedEffect
        loading = true
        error = null
        delay(300)
        try {
            cached = ApiJson.encodeToString(CataloguePage.serializer(), container.api.catalogue(query))
        } catch (failure: ApiException) {
            error = failure.message ?: "Le catalogue est indisponible. Réessayez."
        } finally {
            loading = false
        }
    }
    LaunchedEffect(online, lifecycle) {
        if (!online) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                consoles = container.api.consoles().map { it.id to it.name }
            } catch (_: ApiException) { }
            try {
                sources = container.api.catalogueSources()
            } catch (_: ApiException) { }
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
    Column(Modifier.fillMaxSize().testTag("catalogue").padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ovh.verbois.retrohub.ui.components.RoundButton(Lucide.ArrowLeft, "Retour à l’accueil", { controller.nav.back() }, size = 48.dp)
            OutlinedTextField(
                search,
                {
                    search = it.take(250)
                    page = 1
                },
                Modifier.weight(1f).focusRequester(field).focusProperties {
                    up = tree.header
                    down = if (result?.items?.isNotEmpty() == true) first else filtersFocus
                    right = filtersFocus
                },
                label = { Text("Chercher dans le catalogue") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    if (result?.items?.isNotEmpty() == true) first.requestFocus() else filtersFocus.requestFocus()
                }),
                singleLine = true,
                enabled = online,
                shape = RoundedCornerShape(20.dp),
            )
            if (!controller.portrait) {
                ovh.verbois.retrohub.ui.components.FilterChip("Sur le serveur", installed == true, {
                    installed = if (installed == true) null else true
                    page = 1
                }, Modifier.testTag("catalogue-installed"))
                GlassButton(
                    "Filtres",
                    { filters = true },
                    Modifier.focusRequester(filtersFocus).focusProperties {
                        up = tree.header
                        left = field
                        down = if (result?.items?.isNotEmpty() == true) first else FocusRequester.Cancel
                    },
                    icon = Lucide.SlidersHorizontal,
                    enabled = online,
                )
            }
        }
        if (controller.portrait) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ovh.verbois.retrohub.ui.components.FilterChip("Sur le serveur", installed == true, {
                    installed = if (installed == true) null else true
                    page = 1
                }, Modifier.testTag("catalogue-installed"))
                GlassButton("Filtres", { filters = true }, Modifier.focusRequester(filtersFocus), icon = Lucide.SlidersHorizontal, enabled = online)
            }
        }
        if (!online) Text("Connectez le téléphone pour actualiser le catalogue.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        jobs.take(2).forEach { job ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sur le serveur · ${job.label.ifBlank { "Téléchargement" }} · ${job.progress.toInt()} %", style = MaterialTheme.typography.bodySmall)
                ProgressLine(job.progress / 100f, Modifier.fillMaxWidth())
            }
        }
        error?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Palette.Warning)
                GlassButton("Réessayer", { refresh++ })
            }
        }
        if (loading && result == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        } else if (result?.items.isNullOrEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (online) "Aucun jeu dans cette sélection." else "Le catalogue s’affiche avec une connexion au serveur.", color = Palette.TextDim)
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = ((maxWidth + 16.dp) / 160.dp).toInt().coerceAtLeast(1)
                val grid = rememberLazyGridState()
                val navigation = ovh.verbois.retrohub.ui.components.gridNavigation(grid, result!!.items.size, columns)
                LazyVerticalGrid(
                    GridCells.Fixed(columns),
                    Modifier.fillMaxSize(),
                    state = grid,
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(result!!.items, key = { _, game -> game.id }) { index, entry ->
                        val known = library.firstOrNull { it.id == entry.id }
                        val game =
                            Game(
                                LibraryGame(
                                    entry.id,
                                    entry.id,
                                    title = entry.title,
                                    console = entry.console,
                                    coverUrl = entry.coverUrl,
                                    genre = entry.genre,
                                    year = entry.year,
                                ),
                                known?.local,
                            )
                        GameCard(
                            game,
                            { controller.nav.go(Route.CatalogueGame(entry.id)) },
                            navigation(index).testTag("catalogue-game-${entry.id}").then(
                                if (index ==
                                    0
                                ) {
                                    Modifier.focusRequester(first)
                                } else {
                                    Modifier
                                },
                            ).focusProperties { if (index < columns) up = field },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            GlassButton("Précédent", { page-- }, enabled = online && page > 1 && !loading)
            Text("$page / ${maxOf(1, ((result?.total ?: 0) + 23) / 24)}", style = MaterialTheme.typography.labelLarge)
            GlassButton("Suivant", { page++ }, enabled = online && !loading && page * 24 < (result?.total ?: 0))
        }
    }
    if (filters) {
        CatalogueDialog("Filtres", { filters = false }) {
            val change = { page = 1 }
            SettingsGroup {
                ChoiceRow(
                    "Console",
                    listOf("" to "Toutes les consoles") + consoles,
                    (listOf("" to "Toutes les consoles") + consoles).firstOrNull {
                        it.first == console
                    } ?: ("" to "Toutes les consoles"),
                    { it.second },
                    {
                        console =
                            it.first
                        change()
                    },
                )
                ChoiceRow("Langue", listOf("", "fr", "en", "ja"), language, {
                    when (it) {
                        "fr" -> "Français"
                        "en" -> "Anglais"
                        "ja" -> "Japonais"
                        else -> "Toutes les langues"
                    }
                }, {
                    language =
                        it
                    change()
                })
                ChoiceRow("Installation", listOf<Boolean?>(null, true, false), installed, {
                    when (it) {
                        true -> "Sur le serveur"
                        false -> "À télécharger sur le serveur"
                        null -> "Tous les jeux"
                    }
                }, {
                    installed =
                        it
                    change()
                })
                val allSources = listOf(CatalogueSource("", "Toutes les sources")) + sources
                ChoiceRow("Source", allSources, allSources.firstOrNull { it.id == source } ?: allSources.first(), { it.name }, {
                    source = it.id
                    change()
                })
                ChoiceRow("Trier par", listOf("recent", "title"), sort, { if (it == "title") "Titre" else "Derniers ajouts" }, {
                    sort = it
                    change()
                })
            }
            OutlinedTextField(genre, {
                genre = it
                change()
            }, label = { Text("Genre") }, singleLine = true)
            OutlinedTextField(year, {
                year = it.filter(Char::isDigit).take(4)
                change()
            }, label = { Text("Année") }, singleLine = true)
            GlassButton("Réinitialiser", {
                console = ""
                language = ""
                installed = null
                source = ""
                genre = ""
                year = ""
                sort = "recent"
                page = 1
            })
            GlassButton("Fermer", { filters = false })
        }
    }
}

@Composable
internal fun CatalogueDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(
                max = 600.dp,
            ).fillMaxWidth().background(Palette.Deep, RoundedCornerShape(24.dp)).verticalScroll(rememberScrollState()).padding(20.dp)
                .focusRequester(first).focusGroup().controllerDialog(onDismiss),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
        AutoFocus(first)
    }
}
