package ovh.verbois.retrohub.ui.console.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.LocalReducedMotion
import ovh.verbois.retrohub.ui.components.LocalScreenActive
import ovh.verbois.retrohub.ui.components.gridNavigation
import ovh.verbois.retrohub.ui.components.progressiveNavigation
import ovh.verbois.retrohub.ui.console.kit.ConsoleColors
import ovh.verbois.retrohub.ui.console.kit.ConsoleTextField
import ovh.verbois.retrohub.ui.console.kit.GameTile
import ovh.verbois.retrohub.ui.console.kit.Hint
import ovh.verbois.retrohub.ui.console.kit.HintBarHeight
import ovh.verbois.retrohub.ui.console.kit.Pad
import ovh.verbois.retrohub.ui.console.kit.PageInset
import ovh.verbois.retrohub.ui.console.kit.ProvideHints
import ovh.verbois.retrohub.ui.console.kit.dim
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.shell.searchGames

/** Y: full-screen search over a dimmed screen, the field on top and the matching games as tiles. */
@Composable
fun ConsoleSearch(controller: UiController) {
    val open = controller.searching
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(open, Modifier.fillMaxSize(), enter = fadeIn(tween(if (reduced) 0 else 140)), exit = fadeOut(tween(if (reduced) 0 else 120))) {
        CompositionLocalProvider(LocalScreenActive provides open) { SearchContent(controller) }
    }
}

@Composable
private fun SearchContent(controller: UiController) {
    val games by controller.container.library.games.collectAsState()
    var query by remember { mutableStateOf("") }
    val found = remember(games, query) { searchGames(games, query) }
    // An empty search suggests the games played lately, and only those; other games are not « recent ».
    val played = query.isBlank() && found.any { it.lastPlayed != null }
    val results = if (played) found.filter { it.lastPlayed != null } else found
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val close = { controller.searching = false }
    ProvideHints(Hint(Pad.A, "Ouvrir"), Hint(Pad.Y, "Saisir"), Hint(Pad.B, "Fermer") { close() })
    var edit by remember { mutableStateOf(1) }
    Column(
        Modifier.fillMaxSize().testTag("console-search").background(ConsoleColors.Scrim)
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.ButtonY -> {
                        if (event.type == KeyEventType.KeyUp) {
                            runCatching { field.requestFocus() }
                            edit++
                        }
                        true
                    }

                    Key.ButtonL1, Key.ButtonR1, Key.ButtonStart, Key.ButtonSelect -> true

                    else -> false
                }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 20.dp, bottom = HintBarHeight),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConsoleTextField(
            query,
            { query = it.take(120) },
            "Un jeu, une console…",
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = PageInset).focusRequester(field),
            icon = Lucide.Search,
            imeAction = ImeAction.Search,
            editRequest = edit,
            // Submitting moves to the results, like a console's on-screen keyboard.
            onDone = { runCatching { firstResult.requestFocus() } },
            tag = "header-search-field",
        )
        Text(
            when {
                played -> "Joués récemment"
                query.isBlank() -> "Quelques jeux"
                results.isEmpty() -> ""
                results.size == 1 -> "1 jeu"
                else -> "${results.size} jeux"
            },
            Modifier.fillMaxWidth().padding(horizontal = PageInset),
            style = MaterialTheme.typography.labelMedium,
            color = dim,
        )
        if (results.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Aucun jeu ne correspond à « $query ».", style = MaterialTheme.typography.titleMedium, color = dim)
            }
        } else {
            val grid = rememberLazyGridState()
            val columns = 6
            val navigation = gridNavigation(grid, results.size, columns)
            LazyVerticalGrid(
                GridCells.Fixed(columns),
                Modifier.weight(1f).fillMaxWidth().progressiveNavigation(),
                state = grid,
                contentPadding = PaddingValues(horizontal = PageInset, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(results, key = { _, game -> game.id }) { index, game ->
                    GameTile(
                        game.title,
                        game.console,
                        game.remote.coverUrl,
                        {
                            close()
                            controller.nav.go(Route.CatalogueGame(game.id))
                        },
                        navigation(index).testTag("search-result-${game.id}").focusProperties { if (index < columns) up = field }
                            .then(if (index == 0) Modifier.focusRequester(firstResult) else Modifier),
                        subtitle = Consoles.name(game.console),
                        onDevice = game.downloaded,
                        favorite = game.favorite,
                    )
                }
            }
        }
    }
}
