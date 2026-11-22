package ovh.verbois.retrohub.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.icons.XmbIcons
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.math.abs

@Composable
internal fun HomeColumnContent(
    controller: UiController,
    scene: HomeScene,
    geometry: HomeCrossLayout,
    current: Boolean,
    active: Boolean,
    categoryLane: Boolean,
    gameFocus: FocusRequester,
    restingId: String?,
    reduced: Boolean,
    changeGame: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val column = scene.column
    val laneWidth = geometry.laneWidth.dp
    Box(
        Modifier.fillMaxSize().then(if (current && column.games.isNotEmpty()) Modifier.testTag("home-games") else Modifier)
            .then(if (!current) Modifier.clearAndSetSemantics { } else Modifier)
            .pointerInput(column.key, active, geometry.pitch) {
                if (!active) return@pointerInput
                var drag = 0f
                detectVerticalDragGestures(onDragStart = { drag = 0f }) { change, delta ->
                    change.consume()
                    drag += delta
                    val threshold = (geometry.pitch * 0.85f).dp.toPx()
                    while (abs(drag) >= threshold) {
                        val step = if (drag < 0) 1 else -1
                        changeGame(step)
                        drag += step * threshold
                    }
                }
            },
    ) {
        if (column.key == "catalogue") {
            Row(
                Modifier.offset(x = geometry.rowStart.dp, y = (geometry.selectedCenter - geometry.selectedHeight / 2).dp)
                    .width(laneWidth).height(geometry.selectedHeight.dp)
                    .then(if (current) Modifier.testTag("home-catalogue-action").focusRequester(gameFocus) else Modifier)
                    .focusProperties { canFocus = active && !categoryLane }
                    .consoleFocus(RoundedCornerShape(0.dp), {
                        controller.nav.go(Route.Catalogue)
                    }, enabled = active, focusScale = 1f, highlightFocus = false, bringIntoView = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(geometry.coverBoxSize.dp).xmbHalo {
                        0.7f
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(XmbIcons.Catalogue, null, Modifier.size((geometry.coverBoxSize * 0.6f).dp), tint = Palette.Text) }
                Text("Parcourir le catalogue", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else if (column.games.isEmpty()) {
            Text(
                "Aucun favori",
                Modifier.offset(x = geometry.rowStart.dp, y = (geometry.selectedCenter - geometry.selectedHeight / 2).dp).width(laneWidth),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            for (index in geometry.visibleIndices(scene.selectedIndex, column.games.size)) {
                val item = column.games[index]
                key(item.id) {
                    val chosen = index == scene.selectedIndex
                    val relative = index - scene.selectedIndex
                    val y by animateFloatAsState(geometry.center(relative), tween(if (reduced) 0 else HOME_MOVE_MS))
                    val scale by animateFloatAsState(if (chosen) 1f else 0.58f, tween(if (reduced) 0 else HOME_MOVE_MS))
                    val opacity by animateFloatAsState(
                        if (chosen) 1f else (0.52f - abs(relative) * 0.06f).coerceAtLeast(0.2f),
                        tween(if (reduced) 0 else HOME_MOVE_MS),
                    )
                    val rowHeight = if (chosen) geometry.selectedHeight else geometry.itemHeight
                    Row(
                        Modifier.offset(x = geometry.rowStart.dp).width(laneWidth).height(rowHeight.dp)
                            .graphicsLayer { translationY = with(density) { (y - rowHeight / 2).dp.toPx() } }
                            .then(if (current) Modifier.testTag("home-game-${item.id}") else Modifier)
                            .then(if (chosen && current) Modifier.focusRequester(gameFocus) else Modifier)
                            .then(
                                if (current) {
                                    Modifier.semantics {
                                        selected = chosen
                                        if (!chosen) contentDescription = item.title
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .focusProperties { canFocus = active && chosen && !categoryLane }
                            .consoleFocus(
                                RoundedCornerShape(0.dp),
                                {
                                    if (chosen) controller.activateHomeGame() else controller.selectHomeGame(item.id)
                                },
                                enabled = active,
                                highlightFocus = false,
                                focusScale = 1f,
                                bringIntoView = false,
                                onLongClick = {
                                    controller.selectHomeGame(item.id)
                                    controller.gameMenu = true
                                },
                            ),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier.size(geometry.coverBoxSize.dp).then(if (current) Modifier.testTag("home-cover-${item.id}") else Modifier).xmbHalo {
                                if (chosen) 0.9f else 0f
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            GameArt(
                                item.remote.coverUrl,
                                item.console,
                                item.title,
                                Modifier.requiredSize(geometry.coverSize.dp).graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = opacity * if (chosen) 1f else geometry.verticalEdgeAlpha(y)
                                    compositingStrategy = if (chosen) CompositingStrategy.Auto else CompositingStrategy.Offscreen
                                }.drawWithContent {
                                    drawContent()
                                    if (!chosen) {
                                        val top = y.dp.toPx() - size.height * scale / 2
                                        val bottom = top + size.height * scale
                                        if (top < 72.dp.toPx()) {
                                            drawRect(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.White),
                                                    startY = -top / scale,
                                                    endY = (72.dp.toPx() - top) / scale,
                                                ),
                                                blendMode = BlendMode.DstIn,
                                            )
                                        }
                                        val edge = geometry.height.dp.toPx()
                                        if (bottom > edge - 24.dp.toPx()) {
                                            drawRect(
                                                Brush.verticalGradient(
                                                    listOf(Color.White, Color.Transparent),
                                                    startY = (edge - 24.dp.toPx() - top) / scale,
                                                    endY = (edge - top) / scale,
                                                ),
                                                blendMode = BlendMode.DstIn,
                                            )
                                        }
                                    }
                                }.clip(RoundedCornerShape(2.dp)),
                                showTitleOnFallback = false,
                            )
                        }
                        AnimatedVisibility(chosen, enter = fadeIn(tween(if (reduced) 0 else HOME_MOVE_MS)), exit = fadeOut(tween(if (reduced) 0 else 90))) {
                            Column(
                                Modifier.padding(horizontal = HOME_GAME_TEXT_PADDING.dp).heightIn(min = geometry.coverBoxSize.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                            ) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                HomeGameAction(controller, item)
                                if (current) {
                                    HomeGameInfo(controller, item, index, column.games.size, restingId == item.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomePreview(game: Game?, modifier: Modifier, reduced: Boolean) {
    AnimatedVisibility(
        game != null && (!game.remote.screenshots.firstOrNull().isNullOrBlank() || !game.remote.coverUrl.isNullOrBlank()),
        modifier = modifier,
        enter = fadeIn(tween(if (reduced) 0 else 220)),
        exit = fadeOut(tween(if (reduced) 0 else 90)),
    ) {
        if (game != null) {
            GameArt(
                game.remote.screenshots.firstOrNull()?.takeIf { it.isNotBlank() } ?: game.remote.coverUrl,
                game.console,
                game.title,
                Modifier.fillMaxWidth().aspectRatio(HOME_PREVIEW_ASPECT).testTag("home-preview-${game.id}").graphicsLayer { alpha = 0.3f },
                showTitleOnFallback = false,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun HomeGameInfo(controller: UiController, game: Game, index: Int, count: Int, visible: Boolean) {
    val opacity by animateFloatAsState(if (visible) 1f else 0f, tween(if (LocalReducedMotion.current) 0 else 140))
    Row(
        Modifier.testTag("console-game-info").then(if (!visible) Modifier.semantics { hideFromAccessibility() } else Modifier).graphicsLayer {
            alpha = opacity
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOfNotNull("${index + 1} / $count", game.remote.year?.toString(), formatPlayTime(game.remote.playedSeconds)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val download = downloadState(controller, game)
        if (download is DownloadState.Running) ProgressLine(download.fraction, Modifier.width(64.dp))
    }
}

@Composable
private fun HomeGameAction(controller: UiController, game: Game) {
    val action = gameAction(controller, game, downloadState(controller, game))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(action.icon, null, Modifier.size(12.dp), tint = Palette.TextDim)
        Text(
            if (controller.launching ==
                game.id
            ) {
                "Lancement…"
            } else {
                action.label
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
