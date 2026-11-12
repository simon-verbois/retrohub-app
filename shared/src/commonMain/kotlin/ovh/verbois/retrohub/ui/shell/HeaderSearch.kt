package ovh.verbois.retrohub.ui.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.LocalNavigationFocus
import ovh.verbois.retrohub.ui.components.consoleButtonSurface
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

internal class HeaderSearchState {
    var query by mutableStateOf("")
    var bounds by mutableStateOf(Rect.Zero)
    var fieldFocused by mutableStateOf(false)
    var resultsFocused by mutableStateOf(false)
    var hasResults by mutableStateOf(false)
    var hadFocus = false
    val field = FocusRequester()
    val firstResult = FocusRequester()
}

/** The right edge stays fixed while the field unfolds over the section tabs. */
@Composable
internal fun HeaderSearch(controller: UiController, state: HeaderSearchState, diameter: Dp, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val settings by controller.container.settings.settings.collectAsState()
    val reducedMotion = settings.reduceMotion || controller.container.platform.reducedMotion
    val tree = LocalNavigationFocus.current
    val keyboard = LocalSoftwareKeyboardController.current
    val games by controller.container.library.games.collectAsState()
    val scope = rememberCoroutineScope()
    val touchDiameter = diameter.coerceAtLeast(48.dp)
    val inset = (touchDiameter - diameter) / 2
    BoxWithConstraints(modifier) {
        val width by animateDpAsState(if (controller.searching) maxWidth else touchDiameter, tween(if (reducedMotion) 0 else 220))
        Box(
            Modifier.align(Alignment.CenterEnd).width(width).height(touchDiameter)
                .onFocusChanged { state.fieldFocused = it.hasFocus }.focusGroup(),
        ) {
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = inset).height(diameter)
                    .testTag("header-search-bubble").onGloballyPositioned { state.bounds = it.boundsInRoot() }
                    .consoleButtonSurface(CircleShape),
            )
            if (controller.searching) {
                BasicTextField(
                    value = state.query,
                    onValueChange = { state.query = it.take(250) },
                    modifier = Modifier.fillMaxSize().padding(horizontal = inset).testTag("header-search-field")
                        .focusRequester(state.field).onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionDown && state.hasResults) {
                                if (event.type == KeyEventType.KeyDown) {
                                    keyboard?.hide()
                                    state.firstResult.requestFocus()
                                }
                                true
                            } else {
                                false
                            }
                        }.focusProperties {
                            down = if (state.hasResults) state.firstResult else tree.content
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        val submitted = state.query
                        if (submitted.isNotBlank()) {
                            scope.launch(Dispatchers.Main.immediate) {
                                val first = withContext(Dispatchers.Default) { searchGames(games, submitted).firstOrNull() }
                                if (controller.searching && state.query == submitted && first != null) {
                                    onClose()
                                    controller.showGame(first.id)
                                }
                            }
                        }
                    }),
                    decorationBox = { input ->
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.Search, null, Modifier.padding(start = 12.dp, end = 8.dp).size(22.dp), tint = Palette.Text)
                            Box(Modifier.weight(1f)) {
                                if (state.query.isEmpty()) {
                                    Text(
                                        "Un jeu, une console…",
                                        color = Palette.TextFaint,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                }
                                input()
                            }
                            Box(
                                Modifier.size(touchDiameter).consoleFocus(CircleShape, onClose, label = "Fermer la recherche"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Lucide.X, "Fermer la recherche", Modifier.size(18.dp), tint = Palette.Text)
                            }
                        }
                    },
                )
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    state.field.requestFocus()
                    keyboard?.show()
                }
            } else {
                Box(
                    Modifier.fillMaxSize().testTag("header-search-button")
                        .consoleFocus(CircleShape, { controller.searching = true }, label = "Rechercher", focusScale = 1f, onFocus = {
                            if (it) {
                                controller.searching =
                                    true
                            }
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Search, "Rechercher", Modifier.size(22.dp), tint = Palette.Text)
                }
            }
        }
    }
}

/** A same-window overlay keeps directional focus and the current page intact. */
@Composable
internal fun SearchDropdown(controller: UiController, state: HeaderSearchState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) {
        onDispose {
            state.resultsFocused = false
            state.hasResults = false
        }
    }
    val games by controller.container.library.games.collectAsState()
    val query = state.query
    val matches by produceState<Pair<String, List<Game>>?>(null, query, games) {
        delay(100)
        value = query to withContext(Dispatchers.Default) { searchGames(games, query) }
    }
    val results = matches?.takeIf { it.first == query }?.second
    SideEffect { state.hasResults = !results.isNullOrEmpty() }
    GlassPanel(modifier.testTag("search-results").onFocusChanged { state.resultsFocused = it.hasFocus }.focusGroup(), shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.background(Palette.Deep)) {
            when {
                results == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Palette.Accent)
                }

                results.isEmpty() -> Text("Aucun jeu trouvé", Modifier.padding(16.dp), color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium)

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(results, key = { _, game -> game.id }) { index, game ->
                        Row(
                            Modifier.fillMaxWidth().testTag("search-result-${game.id}").then(
                                if (index ==
                                    0
                                ) {
                                    Modifier.focusRequester(state.firstResult)
                                } else {
                                    Modifier
                                },
                            )
                                .focusProperties {
                                    if (index == 0) up = state.field
                                    if (index == results.lastIndex) down = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                }
                                .semantics { contentDescription = game.title }
                                .consoleFocus(RoundedCornerShape(12.dp), {
                                    onClose()
                                    controller.showGame(game.id)
                                }, focusScale = 1f).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GameArt(
                                game.remote.coverUrl,
                                game.console,
                                game.title,
                                Modifier.width(44.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                                showTitleOnFallback = false,
                                muted = !game.downloaded,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Consoles.name(game.console), style = MaterialTheme.typography.bodySmall)
                            }
                            if (game.downloaded) Icon(Lucide.HardDriveDownload, "Téléchargé", Modifier.size(18.dp), tint = Palette.Success)
                        }
                    }
                }
            }
        }
    }
}
