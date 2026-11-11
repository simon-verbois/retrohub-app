package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.HomeOrder
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.sync.SyncPhase
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.CenteredMessage
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.LocalNavigationFocus
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.components.RoundButton
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.Palette

/**
 * Jeux de l’accueil : sans les consoles masquées (et seulement les jeux téléchargés si demandé),
 * dans l’ordre choisi. Par défaut, les parties récentes d’abord, puis le reste de A à Z.
 */
fun homeOrder(games: List<Game>, order: HomeOrder = HomeOrder.RECENT, hidden: Set<String> = emptySet(), downloadedOnly: Boolean = false): List<Game> {
    val shown = games.filter { it.console !in hidden && (!downloadedOnly || it.downloaded) }
    val byTitle = compareBy<Game> { it.sortTitle }
    return when (order) {
        HomeOrder.RECENT -> {
            val (recent, others) = shown.partition { it.lastPlayed != null }
            recent.sortedByDescending { it.lastPlayed } + others.sortedWith(byTitle)
        }

        HomeOrder.TITLE -> shown.sortedWith(byTitle)

        HomeOrder.FAVORITES -> shown.sortedWith(compareByDescending<Game> { it.favorite }.thenByDescending { it.lastPlayed }.then(byTitle))
    }
}

@Composable
fun HomeScreen(controller: UiController) {
    val container = controller.container
    val games by container.library.games.collectAsState()
    val sync by container.sync.status.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val ordered by produceState(emptyList<Game>(), games, settings.homeOrder, settings.hiddenConsoles, settings.offlineOnly) {
        value = withContext(Dispatchers.Default) { homeOrder(games, settings.homeOrder, settings.hiddenConsoles, settings.offlineOnly) }
    }
    val selected = ordered.firstOrNull { it.id == controller.homeGame } ?: games.firstOrNull { it.id == controller.homeGame } ?: ordered.firstOrNull()
    LaunchedEffect(selected?.console) { controller.backdrop = Backdrop(null, selected?.console) }
    if (selected == null) {
        if (sync.phase == SyncPhase.SYNCING) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        } else {
            CenteredMessage("Votre salle d’arcade est prête", "Ajoutez des jeux à votre bibliothèque depuis RetroHub sur le web.", Lucide.Gamepad2) {
                GlassButton("Actualiser", { controller.syncNow() }, icon = Lucide.RefreshCw)
            }
        }
        return
    }
    val tree = LocalNavigationFocus.current
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val selectedFocus = requesters.getOrPut(selected.id) { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    val downloads by container.downloads.states.collectAsState()
    val actionFocus = if (selected.supported && downloads[selected.playGameId] !is DownloadState.Running) playFocus else optionsFocus
    SideEffect { tree.content = selectedFocus }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val actionWidth = (maxWidth - HomeEdge * 2).coerceAtLeast(148.dp)
        val tile = if (controller.portrait) (maxWidth * 0.3f).coerceIn(96.dp, 144.dp) else (maxHeight * 0.36f).coerceIn(88.dp, 148.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GameRow(controller, ordered, selected, tile, requesters, actionFocus)
            Column(Modifier.fillMaxWidth().padding(horizontal = HomeEdge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    selected.title,
                    Modifier.semantics {
                        heading()
                    },
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Kicker(selected)
                if (!selected.supported) Text("Pas encore jouable dans l’application", style = MaterialTheme.typography.bodyMedium, color = Palette.Warning)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).focusProperties {
                        up = selectedFocus
                        down = FocusRequester.Cancel
                    }.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GamePlayButton(
                        controller,
                        selected,
                        Modifier.widthIn(max = actionWidth)
                            .focusRequester(playFocus).focusProperties { left = FocusRequester.Cancel },
                    )
                    RoundButton(
                        Lucide.Heart,
                        if (selected.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        { controller.toggleFavorite(selected) },
                        tint = if (selected.favorite) Palette.Accent else Palette.Text,
                    )
                    Box {
                        RoundButton(
                            Lucide.SlidersHorizontal,
                            "Options du jeu",
                            { controller.gameMenu = true },
                            Modifier.focusRequester(optionsFocus).focusProperties { right = FocusRequester.Cancel },
                        )
                        GameMenu(controller, selected)
                    }
                }
            }
        }
    }
    AutoFocus(selectedFocus)
}

internal val HomeEdge = 24.dp

/** Each cover owns a stable requester; leaving its actions returns to exactly that cover. */
@Composable
private fun GameRow(
    controller: UiController,
    games: List<Game>,
    selected: Game,
    tile: Dp,
    requesters: MutableMap<String, FocusRequester>,
    actions: FocusRequester,
) {
    val downloads by controller.container.downloads.states.collectAsState()
    val tree = LocalNavigationFocus.current
    val row = games.take(16).let { if (selected in it) it else listOf(selected) + it.take(15) }
    val state = rememberLazyListState()
    LaunchedEffect(selected.id) {
        val index = row.indexOfFirst { it.id == selected.id }
        if (index >= 0 && state.layoutInfo.visibleItemsInfo.none { it.key == selected.id }) state.scrollToItem(index)
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().testTag("home-games").height(tile * 1.2f + 16.dp).focusGroup(),
        contentPadding = PaddingValues(horizontal = HomeEdge, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items(row, key = { it.id }) { game ->
            val requester = requesters.getOrPut(game.id) { FocusRequester() }
            GameTile(
                game, selected = game.id == selected.id, size = tile, selectedSize = tile * 1.2f,
                onFocus = { controller.homeGame = game.id },
                onClick = {
                    controller.homeGame = game.id
                    actions.requestFocus()
                },
                modifier = Modifier.focusRequester(requester).testTag("home-game-${game.id}").focusProperties {
                    up = tree.header
                    down = actions
                    if (game.id == row.first().id) left = FocusRequester.Cancel
                },
                download = downloads[game.playGameId], onLongClick = {
                    controller.homeGame = game.id
                    controller.gameMenu = true
                },
            )
        }
        item(key = "library") {
            Box(
                Modifier.size(tile).focusProperties {
                    up = tree.header
                    down = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                    .consoleFocus(RoundedCornerShape(14.dp), { controller.nav.go(Route.Library()) })
                    .clip(RoundedCornerShape(14.dp)).background(Palette.GlassLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.LayoutGrid, "Toute la bibliothèque", Modifier.size(32.dp), tint = Palette.TextDim)
            }
        }
    }
}

/** Game information lives below the title, including play time, without a decorative dot. */
@Composable
fun Kicker(game: Game) {
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
private fun GamePlayButton(controller: UiController, game: Game, modifier: Modifier = Modifier) {
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
