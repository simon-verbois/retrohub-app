package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.theme.Palette

/** PSP-style vertical browsing keeps the title beside its cover and metadata in a quiet corner. */
@Composable
fun ConsoleFolderScreen(controller: UiController, console: String) {
    val all by controller.container.library.games.collectAsState()
    val settings by controller.container.settings.settings.collectAsState()
    val downloads by controller.container.downloads.states.collectAsState()
    var activeConsole by remember(console) { mutableStateOf(console) }
    val entries = remember(all, settings) { homeEntries(all, settings.copy(hiddenConsoles = emptySet(), offlineOnly = false), null) }
    val folder = entries.filterIsInstance<HomeEntry.FolderItem>().firstOrNull { it.folder.consoleId == activeConsole }
    val folderFocus = remember { mutableMapOf<String, FocusRequester>() }
    entries.forEach { folderFocus.getOrPut(it.key) { FocusRequester() } }
    val games = remember(all, settings, activeConsole) { homeOrder(all.filter { it.console == activeConsole }, activity = settings.homeActivity) }
    var selected by remember(activeConsole) { mutableStateOf<String?>(null) }
    val game = games.firstOrNull { it.id == selected } ?: games.firstOrNull() ?: return
    val first = remember { FocusRequester() }
    val list = key(activeConsole) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val reduced = settings.reduceMotion || controller.container.platform.reducedMotion
    val edge = if (settings.useDisplayCutout) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues() else PaddingValues(0.dp)
    val direction = LocalLayoutDirection.current
    val safeStart = edge.calculateStartPadding(direction)
    val safeEnd = edge.calculateEndPadding(direction)
    Column(Modifier.fillMaxSize().testTag("console-folder")) {
        if (folder != null) {
            HomeRow(controller, entries, folder, 56.dp, folderFocus, first, safeStart, onSelect = { entry ->
                controller.homeGame = entry.key
                val consoleId = (entry as? HomeEntry.FolderItem)?.folder?.consoleId
                if (consoleId != null) activeConsole = consoleId else controller.nav.section(Section.HOME)
            })
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val stripWidth = maxWidth * 0.65f
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxHeight().width(stripWidth).padding(bottom = 72.dp).progressiveNavigation(),
                contentPadding = PaddingValues(start = HomeEdge + 70.dp + safeStart, end = 12.dp, top = 12.dp, bottom = maxHeight * 0.65f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(games, key = { _, item -> item.id }) { index, item ->
                    val chosen = item.id == game.id
                    Row(
                        Modifier.fillMaxWidth().testTag("console-game-${item.id}").then(if (chosen) Modifier.focusRequester(first) else Modifier)
                            .focusProperties { if (index == 0 && folder != null) up = folderFocus.getValue(folder.key) }
                            .consoleFocus(RoundedCornerShape(14.dp), {
                                if (chosen) gameAction(controller, item, downloads[item.playGameId]).run() else selected = item.id
                            }, onFocus = {
                                if (it) {
                                    selected = item.id
                                    scope.launch { if (reduced) list.scrollToItem(index) else list.animateScrollToItem(index) }
                                }
                            }).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        GameArt(
                            item.remote.coverUrl,
                            item.console,
                            item.title,
                            Modifier.size(if (chosen) 72.dp else 48.dp).clip(RoundedCornerShape(10.dp)),
                            showTitleOnFallback = false,
                        )
                        Text(
                            item.title,
                            Modifier.weight(1f).testTag("console-game-title-${item.id}").then(
                                if (chosen &&
                                    !reduced
                                ) {
                                    Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                } else {
                                    Modifier
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (chosen) Palette.Text else Palette.TextDim,
                        )
                    }
                }
            }
            Row(
                Modifier.align(
                    Alignment.BottomStart,
                ).width(stripWidth).horizontalScroll(rememberScrollState()).padding(start = 16.dp + safeStart, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GamePlayButton(controller, game, Modifier.widthIn(max = 300.dp))
                RoundButton(
                    if (game.favorite) Lucide.HeartFilled else Lucide.Heart,
                    if (game.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                    { controller.toggleFavorite(game) },
                    selected = game.favorite,
                    selectedSurface = false,
                )
                Box {
                    RoundButton(Lucide.SlidersHorizontal, "Options du jeu", { controller.gameMenu = true })
                    GameMenu(controller, game)
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).widthIn(max = maxWidth * 0.32f).padding(end = 24.dp + safeEnd, bottom = 24.dp).testTag("console-game-info"),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOfNotNull(
                    ovh.verbois.retrohub.emulation.Consoles.name(game.console),
                    game.remote.genre?.takeIf { it.isNotBlank() },
                    game.remote.year?.toString(),
                    "${ovh.verbois.retrohub.core.formatPlayTime(game.remote.playedSeconds)} de jeu",
                    "Sur ce téléphone".takeIf { game.downloaded },
                ).forEach { info ->
                    Text(
                        info,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                        color = Palette.TextDim,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    AutoFocus(first)
}

/** Portrait opens a scrollable game page rather than rebuilding a console carousel. */
@Composable
fun GameDetailsScreen(controller: UiController, id: String) {
    val games by controller.container.library.games.collectAsState()
    val game = games.firstOrNull { it.id == id } ?: return
    val first = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("game-details"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameArt(game.remote.coverUrl, game.console, game.title, Modifier.size(if (controller.portrait) 160.dp else 96.dp), showTitleOnFallback = false)
        Text(game.title, style = MaterialTheme.typography.headlineMedium)
        Kicker(game)
        GamePlayButton(controller, game, Modifier.widthIn(max = 420.dp).focusRequester(first))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundButton(if (game.favorite) Lucide.HeartFilled else Lucide.Heart, if (game.favorite) "Retirer des favoris" else "Ajouter aux favoris", {
                controller.toggleFavorite(game)
            }, Modifier.testTag("home-favorite"), selected = game.favorite, selectedSurface = false)
            Box {
                RoundButton(Lucide.SlidersHorizontal, "Options du jeu", { controller.gameMenu = true })
                GameMenu(controller, game)
            }
        }
    }
    AutoFocus(first)
}
