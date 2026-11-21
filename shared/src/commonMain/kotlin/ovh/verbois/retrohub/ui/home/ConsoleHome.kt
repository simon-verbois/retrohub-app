package ovh.verbois.retrohub.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.icons.ConsoleIcons
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.icons.XmbIcons
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.ceil

private const val PREVIEW_DELAY_MS = 420L
internal const val HOME_MOVE_MS = 140
internal data class HomeScene(val column: HomeColumn, val selectedIndex: Int)
private data class HomePresentation(
    val columnKey: String,
    val active: Boolean,
    val categoryLane: Boolean,
    val restingId: String?,
    val geometry: HomeCrossLayout,
    val reducedMotion: Boolean,
)

/** One logical selection; only the bounded presentation around it is animated. */
@Composable
fun ConsoleHome(controller: UiController) {
    val model by controller.home.collectAsState()
    val settings by controller.container.settings.settings.collectAsState()
    val selection = controller.homeSelection(model)
    val column = selection.column
    val game = selection.game
    val reduced = settings.reduceMotion || controller.container.platform.reducedMotion
    val tree = LocalNavigationFocus.current
    val haptics = LocalHaptics.current
    val inputMode = LocalInputModeManager.current.inputMode
    val active = LocalScreenActive.current && !controller.gameMenu
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val categoryFocus = remember { FocusRequester() }
    val gameFocus = remember { FocusRequester() }
    var categoryLane by remember { mutableStateOf(false) }
    var ownsFocus by remember { mutableStateOf(false) }
    val selectedFocus = if (categoryLane || (game == null && column.key != "catalogue")) categoryFocus else gameFocus
    SideEffect {
        tree.content = selectedFocus
        if (model !== HomeLibrary.Empty && model === controller.home.value) {
            if (controller.homeColumn != column.key && (model.games.isNotEmpty() || controller.homeColumn != null)) controller.homeColumn = column.key
            if (controller.homeGame != game?.id) controller.homeGame = game?.id
        }
    }
    LaunchedEffect(column.key, game?.id) { controller.backdrop = Backdrop(null, column.console ?: game?.console) }
    LaunchedEffect(column.key, game?.id, categoryLane, active, inputMode, windowFocused) {
        if ((ownsFocus || inputMode == InputMode.Keyboard) && active && windowFocused && !controller.searching && !controller.menuOpen &&
            controller.nav.contentRoute == Route.Home
        ) {
            runCatching { selectedFocus.requestFocus() }
        }
    }
    val changeColumn = { step: Int -> controller.cycleHomeColumn(step) }
    val changeGame = { step: Int ->
        categoryLane = false
        controller.moveHomeGame(step)
    }
    val settledId by produceState<String?>(null, game?.id, column.key, active, categoryLane, windowFocused) {
        value = null
        if (active && windowFocused && !categoryLane && game != null) {
            delay(PREVIEW_DELAY_MS)
            value = game.id
        }
    }
    val restingGame = game?.takeIf { active && windowFocused && !categoryLane && it.id == settledId }
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("home-content").clipToBounds().onFocusChanged { ownsFocus = it.hasFocus }
            .focusProperties { onEnter = { runCatching { selectedFocus.requestFocus() } } }.focusGroup().progressiveNavigation()
            .onPreviewKeyEvent { event ->
                if (!active) return@onPreviewKeyEvent false
                val current = controller.homeSelection()
                if (event.key in ConfirmKeys) {
                    if (event.type == KeyEventType.KeyUp) {
                        haptics()
                        when {
                            current.column.key == "catalogue" -> controller.nav.go(Route.Catalogue)
                            categoryLane -> categoryLane = false
                            else -> controller.activateHomeGame()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.key == Key.ButtonX) {
                    if (event.type == KeyEventType.KeyUp && current.game != null) {
                        haptics()
                        controller.gameMenu = true
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.key !in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> changeColumn(-1)

                        Key.DirectionRight -> changeColumn(1)

                        Key.DirectionDown -> when {
                            current.column.key == "catalogue" -> if (categoryLane) categoryLane = false else controller.nav.go(Route.Catalogue)
                            current.game == null -> Unit
                            categoryLane -> categoryLane = false
                            else -> changeGame(1)
                        }

                        Key.DirectionUp -> when {
                            categoryLane || (current.game == null && current.column.key != "catalogue") -> runCatching { tree.header.requestFocus() }
                            current.gameIndex == 0 -> categoryLane = true
                            else -> changeGame(-1)
                        }
                    }
                }
                true
            },
    ) {
        val density = LocalDensity.current
        val categoryLabelHeight = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp().value }
        val titleHeight = with(density) { MaterialTheme.typography.titleLarge.lineHeight.toDp().value }
        val previewAvailable = game?.let { !it.remote.screenshots.firstOrNull().isNullOrBlank() || !it.remote.coverUrl.isNullOrBlank() } == true
        val geometry = remember(maxWidth, maxHeight, categoryLabelHeight, titleHeight, previewAvailable) {
            HomeCrossLayout.fit(
                maxWidth.value,
                maxHeight.value,
                categoryLabelHeight * 2,
                titleHeight * 2 + categoryLabelHeight * 3 + 4f,
                titleHeight * 7,
                previewAvailable,
            )
        }
        val pitch = geometry.categoryPitch.dp
        val categoryPosition by animateFloatAsState(selection.categoryIndex.toFloat(), tween(if (reduced) 0 else HOME_MOVE_MS))
        val scene = HomeScene(column, selection.gameIndex)
        // AnimatedContent retains outgoing content lambdas. Read live presentation through State.
        val presentation =
            rememberUpdatedState(HomePresentation(column.key, active, categoryLane, restingGame?.id, geometry, reduced))
        if (geometry.previewWidth > 0f) {
            HomePreview(restingGame, Modifier.align(Alignment.CenterEnd).padding(end = geometry.inset.dp).width(geometry.previewWidth.dp), reduced)
        }
        AnimatedContent(
            targetState = scene,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it.column.key },
            transitionSpec = {
                val direction = if ((model.columnIndices[targetState.column.key] ?: 0) >= (model.columnIndices[initialState.column.key] ?: 0)) 1 else -1
                (fadeIn(tween(if (reduced) 0 else 160)) + slideInHorizontally(tween(if (reduced) 0 else 180)) { it / 14 * direction })
                    .togetherWith(fadeOut(tween(if (reduced) 0 else 100)) + slideOutHorizontally(tween(if (reduced) 0 else 140)) { -it / 18 * direction })
                    .using(SizeTransform(clip = false))
            },
            label = "home-column",
        ) { displayed ->
            val live = presentation.value
            val current = displayed.column.key == live.columnKey
            HomeColumnContent(
                controller = controller,
                scene = displayed,
                geometry = live.geometry,
                current = current,
                active = live.active && current,
                categoryLane = live.categoryLane,
                gameFocus = gameFocus,
                restingId = live.restingId,
                reduced = live.reducedMotion,
                changeGame = changeGame,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(geometry.categoryHeight.dp).offset(y = (geometry.categoryCenter - geometry.categoryHeight / 2).dp)
                .testTag("home-categories").pointerInput(model.columns, active, geometry.categoryPitch) {
                    if (!active) return@pointerInput
                    var drag = 0f
                    detectHorizontalDragGestures(onDragStart = { drag = 0f }) { change, delta ->
                        change.consume()
                        drag += delta
                        val threshold = (geometry.categoryPitch * 0.54f).dp.toPx()
                        while (abs(drag) >= threshold) {
                            val step = if (drag < 0) 1 else -1
                            changeColumn(step)
                            drag += step * threshold
                        }
                    }
                },
        ) {
            val radius = ceil(geometry.width / pitch.value).toInt() + 1
            val indices = (selection.categoryIndex - radius).coerceAtLeast(0)..(selection.categoryIndex + radius).coerceAtMost(model.columns.lastIndex)
            for (index in indices) {
                val item = model.columns[index]
                key(item.key) {
                    val chosen = item.key == column.key
                    val emphasis by animateFloatAsState(if (chosen) 1f else 0.48f, tween(if (reduced) 0 else HOME_MOVE_MS))
                    val scale by animateFloatAsState(if (chosen) 1.16f else 0.9f, tween(if (reduced) 0 else HOME_MOVE_MS))
                    Column(
                        Modifier.offset {
                            IntOffset(with(density) { (geometry.anchor.dp - pitch / 2 + pitch * (index - selection.categoryIndex)).roundToPx() }, 0)
                        }
                            .width(pitch).fillMaxHeight().graphicsLayer {
                                translationX =
                                    with(density) { pitch.toPx() * (selection.categoryIndex - categoryPosition) }
                            }
                            .testTag("home-category-${item.key}")
                            .then(if (chosen) Modifier.focusRequester(categoryFocus) else Modifier)
                            .semantics {
                                selected = chosen
                                if (!chosen) contentDescription = item.title
                            }
                            .focusProperties { canFocus = active && (categoryLane || (chosen && game == null && item.key != "catalogue")) }
                            .consoleFocus(RoundedCornerShape(0.dp), {
                                if (!chosen) controller.selectHomeColumn(item.key)
                                if (item.key == "catalogue") controller.nav.go(Route.Catalogue) else categoryLane = false
                            }, enabled = active, highlightFocus = false, focusScale = 1f, bringIntoView = false),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(geometry.categoryBoxSize.dp).xmbHalo { if (chosen) (if (categoryLane) 1f else 0.65f) else 0f },
                            contentAlignment = Alignment.Center,
                        ) {
                            val icon = when {
                                item.console != null -> ConsoleIcons.forConsole(item.console)
                                item.key == "favorites" -> Lucide.HeartFilled
                                else -> XmbIcons.Catalogue
                            }
                            Icon(
                                icon,
                                null,
                                Modifier.size(geometry.categoryIconSize.dp).graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = emphasis * geometry.horizontalEdgeAlpha(geometry.anchor + geometry.categoryPitch * (index - categoryPosition))
                                },
                                tint = Palette.Text,
                            )
                        }
                        AnimatedVisibility(chosen, enter = fadeIn(tween(if (reduced) 0 else HOME_MOVE_MS)), exit = fadeOut(tween(if (reduced) 0 else 90))) {
                            Text(
                                item.title,
                                Modifier.requiredWidth(geometry.categoryLabelWidth.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Text,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        // Keep the modal outside the animated column: it cannot be recreated by an outgoing scene.
        if (game != null) GameMenu(controller, game)
    }
}
