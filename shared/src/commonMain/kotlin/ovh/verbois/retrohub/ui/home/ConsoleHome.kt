package ovh.verbois.retrohub.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.icons.ConsoleIcons
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.Palette

/** One cross of navigation: categories slide horizontally, their games slide vertically. */
@Composable
fun ConsoleHome(controller: UiController) {
    val all by controller.container.library.games.collectAsState()
    val settings by controller.container.settings.settings.collectAsState()
    val downloads by controller.container.downloads.states.collectAsState()
    val columns = remember(all, settings) { homeColumns(all, settings) }
    val column = columns.firstOrNull { it.key == controller.homeColumn }
        ?: columns.firstOrNull { item -> item.games.any { it.id == controller.homeGame } }
        ?: columns.first { it.key == "favorites" }
    val game = column.games.firstOrNull { it.id == controller.homeGame } ?: column.games.firstOrNull()
    val categoryIndex = columns.indexOf(column)
    val gameIndex = column.games.indexOf(game).coerceAtLeast(0)
    val reduced = settings.reduceMotion || controller.container.platform.reducedMotion
    val tree = LocalNavigationFocus.current
    val inputMode = LocalInputModeManager.current.inputMode
    val active = LocalScreenActive.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val categories = remember { mutableMapOf<String, FocusRequester>() }
    val games = remember { mutableMapOf<String, FocusRequester>() }
    columns.forEach { categories.getOrPut(it.key) { FocusRequester() } }
    column.games.forEach { games.getOrPut(it.id) { FocusRequester() } }
    var categoryLane by remember { mutableStateOf(false) }
    var ownsFocus by remember { mutableStateOf(false) }
    var focusPending by remember { mutableStateOf(false) }
    val catalogue = remember { FocusRequester() }
    val selectedFocus = when {
        categoryLane -> categories.getValue(column.key)
        column.key == "catalogue" -> catalogue
        game != null -> games.getValue(game.id)
        else -> categories.getValue(column.key)
    }
    SideEffect {
        tree.content = selectedFocus
        if (controller.homeColumn != column.key && (all.isNotEmpty() || controller.homeColumn != null)) controller.homeColumn = column.key
        if (controller.homeGame != game?.id) controller.homeGame = game?.id
    }
    LaunchedEffect(column.key, game?.id) {
        controller.backdrop = Backdrop(null, column.console ?: game?.console)
    }
    val horizontal = rememberScrollState()
    val vertical = key(column.key) { rememberLazyListState() }
    val density = LocalDensity.current
    val pitch = 112.dp
    val changeColumn = { step: Int ->
        columns.getOrNull((categoryIndex + step).coerceIn(0, columns.lastIndex))?.let {
            focusPending = ownsFocus
            controller.selectHomeColumn(it.key)
        }
    }
    val changeGame = { step: Int ->
        column.games.getOrNull((gameIndex + step).coerceIn(0, (column.games.size - 1).coerceAtLeast(0)))?.let {
            categoryLane = false
            focusPending = ownsFocus
            controller.homeGame = it.id
        }
    }
    LaunchedEffect(categoryIndex, density) {
        val target = with(density) { pitch.roundToPx() * categoryIndex }
        snapshotFlow { horizontal.maxValue }.first { it > 0 || target == 0 }
        val bounded = target.coerceAtMost(horizontal.maxValue)
        if (reduced) horizontal.scrollTo(bounded) else horizontal.animateScrollTo(bounded, tween(220))
    }
    LaunchedEffect(column.key, gameIndex, game?.id) {
        if (game != null) {
            val offset = -with(density) { 48.dp.roundToPx() }
            if (reduced) vertical.scrollToItem(gameIndex + 1, offset) else vertical.animateScrollToItem(gameIndex + 1, offset)
        }
        if ((ownsFocus || focusPending || inputMode == InputMode.Keyboard) && active && !controller.searching && !controller.menuOpen &&
            controller.nav.contentRoute == Route.Home
        ) {
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
            focusPending = false
        }
    }
    LaunchedEffect(categoryLane) {
        if (active && inputMode == InputMode.Keyboard && !controller.searching && !controller.menuOpen && controller.nav.contentRoute == Route.Home) {
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        }
    }
    LaunchedEffect(inputMode, windowFocused, active) {
        if (inputMode == InputMode.Keyboard && windowFocused && active) {
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("home-content").onFocusChanged { ownsFocus = it.hasFocus }
            .focusProperties { onEnter = { runCatching { selectedFocus.requestFocus() } } }.focusGroup().progressiveNavigation()
            .onPreviewKeyEvent { event ->
                if (event.key !in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> changeColumn(-1)

                        Key.DirectionRight -> changeColumn(1)

                        Key.DirectionDown -> when {
                            column.key == "catalogue" -> {
                                if (categoryLane) categoryLane = false else controller.nav.go(Route.Catalogue)
                            }

                            game == null -> Unit

                            categoryLane -> categoryLane = false

                            else -> changeGame(1)
                        }

                        Key.DirectionUp -> if (categoryLane || (game == null && column.key != "catalogue")) {
                            runCatching { tree.header.requestFocus() }
                        } else if (gameIndex == 0) {
                            categoryLane = true
                        } else {
                            changeGame(-1)
                        }
                    }
                }
                true
            },
    ) {
        // Keep the intersection stable on small handhelds and wide tablets alike.
        val anchor = (maxWidth * 0.18f).coerceIn(88.dp, 200.dp)
        val short = maxHeight < 330.dp
        val categoryHeight = if (short) 80.dp else 88.dp
        val rowHeight = maxOf(if (short) 64.dp else 72.dp, with(density) { MaterialTheme.typography.titleLarge.lineHeight.toDp() } + 36.dp)
        val listTop = categoryHeight - 8.dp
        val viewportHeight = maxHeight
        val metadataWidth = maxWidth * 0.23f
        val listWidth = (maxWidth * 0.76f).coerceAtLeast(maxWidth - 220.dp)
        Row(
            Modifier.fillMaxWidth().height(categoryHeight).testTag("home-categories")
                .horizontalScroll(horizontal, enabled = false)
                .pointerInput(columns, categoryIndex) {
                    var drag = 0f
                    detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                        if (kotlin.math.abs(drag) > 28.dp.toPx()) changeColumn(if (drag < 0) 1 else -1)
                    }) { change, delta ->
                        change.consume()
                        drag += delta
                    }
                }
                .padding(start = anchor - pitch / 2, end = (maxWidth - anchor - pitch / 2).coerceAtLeast(0.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { item ->
                val chosen = item.key == column.key
                val emphasis by animateFloatAsState(if (chosen) 1f else 0.9f, tween(if (reduced) 0 else 180))
                Column(
                    Modifier.width(pitch).fillMaxHeight().testTag("home-category-${item.key}")
                        .focusRequester(categories.getValue(item.key)).semantics { selected = chosen }
                        .focusProperties { canFocus = active && chosen && (categoryLane || (game == null && item.key != "catalogue")) }
                        .consoleFocus(RoundedCornerShape(0.dp), {
                            if (!chosen) controller.selectHomeColumn(item.key)
                            if (item.key == "catalogue") {
                                controller.nav.go(Route.Catalogue)
                            } else {
                                categoryLane = false
                                focusPending = true
                                if (chosen) runCatching { games[game?.id]?.requestFocus() }
                            }
                        }, highlightFocus = false, focusScale = 1f)
                        .alpha(emphasis),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val icon = when {
                        item.console != null -> ConsoleIcons.forConsole(item.console)
                        item.key == "favorites" -> Lucide.Heart
                        item.key == "catalogue" -> Lucide.ShoppingBag
                        else -> Lucide.FolderOpen
                    }
                    Icon(
                        icon,
                        null,
                        Modifier.size(40.dp).graphicsLayer {
                            scaleX = if (chosen) 1.18f else 1f
                            scaleY = scaleX
                        },
                        tint = Palette.Text,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier.padding(
                            top = 6.dp,
                        ).width(18.dp).height(2.dp).background(if (chosen) Palette.Text else androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.padding(top = listTop).fillMaxSize()) {
            if (column.key == "catalogue") {
                Row(
                    Modifier.padding(top = 48.dp).width(listWidth).height(rowHeight).testTag("home-catalogue-action")
                        .focusRequester(catalogue).focusProperties { canFocus = active && !categoryLane }.consoleFocus(
                            RoundedCornerShape(0.dp),
                            { controller.nav.go(Route.Catalogue) },
                            focusScale = 1f,
                            highlightFocus = false,
                        )
                        .padding(start = anchor - 36.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        Icon(Lucide.ShoppingBag, null, Modifier.size(40.dp), tint = Palette.Text)
                    }
                    Text(
                        "Parcourir le catalogue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (game == null) {
                Column(Modifier.padding(start = anchor - 32.dp, top = 32.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Aucun favori", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
                    Text("Ajoutez un jeu depuis ses options.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
            } else {
                LazyColumn(
                    Modifier.width(listWidth).fillMaxHeight().testTag("home-games")
                        .pointerInput(column.key, gameIndex) {
                            var drag = 0f
                            detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                                if (kotlin.math.abs(drag) > 28.dp.toPx()) changeGame(if (drag < 0) 1 else -1)
                            }) { change, delta ->
                                change.consume()
                                drag += delta
                            }
                        },
                    state = vertical,
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(bottom = viewportHeight),
                ) {
                    item(key = "leading-space") { Spacer(Modifier.height(48.dp)) }
                    itemsIndexed(column.games, key = { _, item -> item.id }) { _, item ->
                        val chosen = item.id == game.id
                        val emphasis by animateFloatAsState(if (chosen) 1f else 0.9f, tween(if (reduced) 0 else 150))
                        Row(
                            Modifier.fillMaxWidth().height(rowHeight).testTag("home-game-${item.id}")
                                .focusRequester(games.getValue(item.id)).semantics { selected = chosen }
                                .focusProperties { canFocus = active && chosen && !categoryLane }
                                .consoleFocus(RoundedCornerShape(0.dp), {
                                    if (chosen) gameAction(controller, item, downloads[item.playGameId]).run() else controller.homeGame = item.id
                                }, highlightFocus = false, focusScale = 1f, onFocus = {
                                    if (it && chosen && controller.homeColumn == column.key) {
                                        categoryLane = false
                                    }
                                }, onLongClick = {
                                    controller.homeGame = item.id
                                    controller.gameMenu = true
                                })
                                .padding(start = anchor - 36.dp, end = 16.dp).alpha(emphasis),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Box(Modifier.size(72.dp).testTag("home-cover-${item.id}"), contentAlignment = Alignment.Center) {
                                GameArt(
                                    item.remote.coverUrl,
                                    item.console,
                                    item.title,
                                    Modifier.size(if (chosen) (if (short) 60.dp else 68.dp) else 40.dp).clip(RoundedCornerShape(4.dp)),
                                    showTitleOnFallback = false,
                                )
                                if (chosen) GameMenu(controller, item)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    item.title,
                                    style = if (chosen) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (chosen) {
                                    val action = gameAction(controller, item, downloads[item.playGameId])
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(action.icon, null, Modifier.size(14.dp), tint = Palette.TextDim)
                                        Text(
                                            if (controller.launching == item.id) "Lancement…" else action.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Palette.TextDim,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomEnd).widthIn(max = metadataWidth).padding(end = 24.dp, bottom = 20.dp).testTag("console-game-info"),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${gameIndex + 1} / ${column.games.size}", style = MaterialTheme.typography.bodySmall)
                    listOfNotNull(
                        game.remote.genre?.takeIf { it.isNotBlank() },
                        game.remote.year?.toString(),
                        "${formatPlayTime(game.remote.playedSeconds)} de jeu",
                        "Sur cet appareil".takeIf { game.downloaded },
                    )
                        .forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Light,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Palette.TextDim,
                            )
                        }
                    val download = downloads[game.playGameId]
                    if (download is DownloadState.Running) ProgressLine(download.fraction, Modifier.width(96.dp))
                }
            }
        }
    }
}
