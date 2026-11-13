package ovh.verbois.retrohub.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.Palette

@Composable
fun HomeScreen(controller: UiController) {
    if (controller.portrait) {
        Box(Modifier.fillMaxSize().testTag("home-content")) {
            ovh.verbois.retrohub.ui.library.LibraryScreen(controller, null, favoritesOnly = false)
        }
        return
    }
    val container = controller.container
    val games by container.library.games.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val baseEntries by produceState<List<HomeEntry>?>(null, games, settings) {
        value = withContext(Dispatchers.Default) { homeEntries(games, settings, null) }
    }
    val ready = baseEntries ?: return
    val entries = remember(ready, controller.homeGame, games) {
        val extra = games.firstOrNull { it.id == controller.homeGame && ready.none { entry -> entry.key == it.id } }
        if (extra == null) {
            ready
        } else {
            val afterPins = 1 + ready.drop(1).takeWhile { it.key in settings.homePins }.size
            ready.take(afterPins) + HomeEntry.GameItem(extra) + ready.drop(afterPins)
        }
    }
    val selected = entries.firstOrNull { it.key == controller.homeGame } ?: entries.getOrNull(1) ?: entries.first()
    val game = (selected as? HomeEntry.GameItem)?.game
    LaunchedEffect(selected.key) {
        // An empty initial Room emission must not make a utility tile the permanent selection.
        if (games.isNotEmpty() || settings.homeFolders.isNotEmpty() || controller.homeGame != null) controller.homeGame = selected.key
        controller.backdrop = Backdrop(null, game?.console)
    }
    val tree = LocalNavigationFocus.current
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    entries.forEach { requesters.getOrPut(it.key) { FocusRequester() } }
    val selectedFocus = requesters.getValue(selected.key)
    val playFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    val downloads by container.downloads.states.collectAsState()
    val actionFocus = if (game == null || (game.supported && downloads[game.playGameId] !is DownloadState.Running)) playFocus else optionsFocus
    SideEffect { if (controller.nav.contentRoute == Route.Home) tree.content = selectedFocus }
    val edge = if (settings.useDisplayCutout) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues() else PaddingValues(0.dp)
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val actionWidth = (maxWidth - HomeEdge * 2 - edge.calculateStartPadding(direction) - edge.calculateEndPadding(direction) - 16.dp).coerceAtLeast(148.dp)
        val short = maxHeight < 360.dp
        val tile = if (controller.portrait) (maxWidth * 0.26f).coerceIn(80.dp, 132.dp) else (maxHeight * 0.38f).coerceIn(72.dp, 144.dp)
        SubcomposeLayout(Modifier.fillMaxSize().testTag("home-content")) { constraints ->
            val details = subcompose("details") {
                Column(
                    Modifier.fillMaxWidth().padding(
                        start = HomeEdge + edge.calculateStartPadding(direction),
                        end =
                        HomeEdge + edge.calculateEndPadding(direction),
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        when (selected) {
                            is HomeEntry.GameItem -> selected.game.title
                            is HomeEntry.FolderItem -> selected.folder.name
                            HomeEntry.Catalogue -> "Catalogue"
                            HomeEntry.Library -> "Bibliothèque"
                            HomeEntry.NewFolder -> "Nouveau dossier"
                        },
                        Modifier.semantics { heading() },
                        style = if (short) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                        color = Palette.Text,
                        minLines = if (short) 1 else 2,
                        maxLines = if (short) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (game != null) Kicker(game, compact = true) else Text(" ", style = MaterialTheme.typography.bodySmall)
                    Row(
                        // Leave room for the focus scale inside the scroll viewport, including at its left edge.
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp).focusProperties {
                            up = selectedFocus
                            down = FocusRequester.Cancel
                        }.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (selected) {
                            is HomeEntry.GameItem -> {
                                GamePlayButton(
                                    controller,
                                    selected.game,
                                    Modifier.widthIn(max = actionWidth).focusRequester(playFocus).focusProperties {
                                        left =
                                            FocusRequester.Cancel
                                    },
                                )
                                RoundButton(
                                    if (game!!.favorite) Lucide.HeartFilled else Lucide.Heart,
                                    if (game.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                                    { controller.toggleFavorite(game) },
                                    Modifier.testTag("home-favorite"),
                                    selected = game.favorite,
                                    selectedSurface = false,
                                )
                                Box {
                                    RoundButton(
                                        Lucide.SlidersHorizontal,
                                        "Options du jeu",
                                        {
                                            controller.gameMenu = true
                                        },
                                        Modifier.focusRequester(optionsFocus).focusProperties {
                                            right =
                                                FocusRequester.Cancel
                                        },
                                    )
                                    GameMenu(controller, selected.game)
                                }
                            }

                            is HomeEntry.FolderItem -> FolderActions(controller, selected.folder, playFocus, optionsFocus, actionWidth)

                            HomeEntry.Catalogue -> PrimaryButton("Ouvrir le catalogue", {
                                controller.nav.go(Route.Catalogue)
                            }, Modifier.widthIn(max = actionWidth).focusRequester(playFocus), icon = Lucide.ShoppingBag)

                            HomeEntry.Library -> PrimaryButton("Toute la bibliothèque", {
                                controller.nav.go(Route.Library())
                            }, Modifier.widthIn(max = actionWidth).focusRequester(playFocus), icon = Lucide.LayoutGrid)

                            HomeEntry.NewFolder -> PrimaryButton("Créer un dossier", {
                                controller.newFolder()
                            }, Modifier.widthIn(max = actionWidth).focusRequester(playFocus), icon = Lucide.FolderPlus)
                        }
                    }
                }
            }.single().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            val gap = 8.dp.roundToPx()
            val available = (constraints.maxHeight - details.height - gap - 8.dp.roundToPx()).coerceAtLeast(0)
            val fittedTile = minOf(tile, ((available - 16.dp.roundToPx()).coerceAtLeast(1) / 1.2f).toDp())
            val covers = subcompose("covers") {
                HomeRow(controller, entries, selected, fittedTile, requesters, actionFocus, edge.calculateStartPadding(direction))
            }.single().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = available))
            layout(constraints.maxWidth, constraints.maxHeight) {
                covers.placeRelative(0, 0)
                details.placeRelative(0, covers.height + gap)
            }
        }
    }
    if (controller.nav.contentRoute == Route.Home && (games.isNotEmpty() || settings.homeFolders.isNotEmpty() || controller.homeGame != null)) {
        AutoFocus(selectedFocus)
    }
}

internal val HomeEdge = 24.dp

/** The second slot is the anchor. The strip moves underneath one stationary selection frame. */
@Composable
internal fun HomeRow(
    controller: UiController,
    entries: List<HomeEntry>,
    selected: HomeEntry,
    tile: Dp,
    requesters: Map<String, FocusRequester>,
    actions: FocusRequester,
    safeStart: Dp,
    onSelect: (HomeEntry) -> Unit = { controller.homeGame = it.key },
) {
    val downloads by controller.container.downloads.states.collectAsState()
    val settings by controller.container.settings.settings.collectAsState()
    val reduced = settings.reduceMotion || controller.container.platform.reducedMotion
    val density = LocalDensity.current
    val tree = LocalNavigationFocus.current
    val scroll = rememberScrollState()
    val index = entries.indexOf(selected)
    val pitch = tile + 14.dp
    LaunchedEffect(index, tile) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        val offset = with(density) { index * (tile.roundToPx() + 14.dp.roundToPx()) }
        if (reduced) scroll.scrollTo(offset) else scroll.animateScrollTo(offset, tween(260))
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(tile * 1.2f + 16.dp).progressiveNavigation()) {
        val anchor = HomeEdge + safeStart + pitch
        Row(
            Modifier.fillMaxSize().testTag("home-games").horizontalScroll(scroll, enabled = false)
                .pointerInput(entries, index) {
                    var drag = 0f
                    detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                        if (kotlin.math.abs(drag) > 32.dp.toPx()) {
                            val next = (index + if (drag < 0) 1 else -1).coerceIn(0, entries.lastIndex)
                            onSelect(entries[next])
                            runCatching { requesters.getValue(entries[next].key).requestFocus() }
                        }
                    }) { change, delta ->
                        change.consume()
                        drag += delta
                    }
                }
                .padding(start = anchor, end = (maxWidth - anchor - tile).coerceAtLeast(HomeEdge), top = 8.dp, bottom = 8.dp)
                .focusProperties { onEnter = { runCatching { requesters.getValue(selected.key).requestFocus() } } }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            entries.forEachIndexed { position, entry ->
                key(entry.key) {
                    val chosen = entry.key == selected.key
                    val open = {
                        onSelect(entry)
                        when (entry) {
                            HomeEntry.Catalogue -> controller.nav.go(Route.Catalogue)
                            HomeEntry.Library -> controller.nav.go(Route.Library())
                            HomeEntry.NewFolder -> controller.newFolder()
                            else -> actions.requestFocus()
                        }
                        Unit
                    }
                    val modifier = Modifier.focusRequester(requesters.getValue(entry.key)).focusProperties {
                        up = tree.header
                        down = actions
                        left = entries.getOrNull(position - 1)?.let { requesters.getValue(it.key) } ?: FocusRequester.Cancel
                        right = entries.getOrNull(position + 1)?.let { requesters.getValue(it.key) } ?: FocusRequester.Cancel
                    }.onPreviewKeyEvent { event ->
                        if (entry == HomeEntry.Catalogue && event.key == Key.DirectionDown) {
                            if (event.type == KeyEventType.KeyDown) controller.nav.go(Route.Catalogue)
                            true
                        } else {
                            false
                        }
                    }
                    if (entry is HomeEntry.GameItem) {
                        GameTile(
                            entry.game, chosen, tile, tile * 1.2f, { onSelect(entry) }, open,
                            modifier.testTag("home-game-${entry.key}"), downloads[entry.game.playGameId],
                            onLongClick = {
                                onSelect(entry)
                                controller.gameMenu = true
                            }, reduceMotion = reduced,
                        )
                    } else {
                        val size by androidx.compose.animation.core.animateDpAsState(if (chosen) tile * 1.2f else tile, tween(if (reduced) 0 else 260))
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            modifier.size(
                                size,
                            ).testTag(
                                "home-${entry.key}",
                            ).consoleFocus(shape, open, label = (entry as? HomeEntry.FolderItem)?.folder?.name, focusScale = 1f, onFocus = {
                                if (it) {
                                    onSelect(entry)
                                }
                            })
                                .clip(shape).background(Palette.GlassLight).border(1.dp, Palette.Line, shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (entry) {
                                is HomeEntry.FolderItem -> FolderArtwork(entry.folder, Modifier.fillMaxSize(), showName = true, compact = tile < 72.dp)

                                else -> Icon(
                                    when (entry) {
                                        HomeEntry.Catalogue -> Lucide.ShoppingBag
                                        HomeEntry.Library -> Lucide.LayoutGrid
                                        else -> Lucide.FolderPlus
                                    },
                                    when (entry) {
                                        HomeEntry.Catalogue -> "Catalogue"
                                        HomeEntry.Library -> "Toute la bibliothèque"
                                        else -> "Créer un dossier"
                                    },
                                    Modifier.size(tile * 0.35f),
                                    tint = Palette.Text,
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(start = anchor, top = 8.dp).size(tile * 1.2f).testTag("home-selection").border(2.dp, Palette.Text, RoundedCornerShape(14.dp)))
    }
}

/** Game information lives below the title, including play time, without a decorative dot. */
@Composable
fun Kicker(game: Game, compact: Boolean = false) {
    if (compact) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(Consoles.name(game.console), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1)
            game.remote.genre?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1) }
            Text("${formatPlayTime(game.remote.playedSeconds)} de jeu", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1)
        }
        return
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val information = listOfNotNull(
            Consoles.name(game.console),
            game.remote.genre?.takeIf { it.isNotBlank() }?.split(',')?.joinToString(", ") { it.trim() },
            game.remote.year?.toString(),
            "${formatPlayTime(game.remote.playedSeconds)} de jeu",
        )
        information.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim) }
    }
}

@Composable
internal fun GamePlayButton(controller: UiController, game: Game, modifier: Modifier = Modifier) {
    val downloads by controller.container.downloads.states.collectAsState()
    val download = downloads[game.playGameId]
    val action = gameAction(controller, game, download)
    if (download is DownloadState.Running) {
        Text("Téléchargement en cours", modifier, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
    } else {
        PrimaryButton(
            action.label,
            action.run,
            modifier.testTag("game-primary-action"),
            icon = action.icon,
            enabled = game.supported,
            loading = controller.launching == game.id,
        )
    }
}
