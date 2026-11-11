package ovh.verbois.retrohub.ui.home

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.formatDate
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.HomeOrder
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.sync.SyncPhase
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.CenteredMessage
import ovh.verbois.retrohub.ui.components.FilterChip
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.RoundButton
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette
import ovh.verbois.retrohub.ui.theme.consoleColor

/**
 * Jeux de l’accueil : sans les consoles masquées (et seulement les jeux téléchargés si demandé),
 * dans l’ordre choisi. Par défaut, les parties récentes d’abord, puis le reste de A à Z.
 */
fun homeOrder(games: List<Game>, order: HomeOrder = HomeOrder.RECENT, hidden: Set<String> = emptySet(), downloadedOnly: Boolean = false): List<Game> {
    val shown = games.filter { it.console !in hidden && (!downloadedOnly || it.downloaded) }
    val byTitle = compareBy<Game> { it.title.lowercase() }
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
    val ordered =
        remember(games, settings.homeOrder, settings.hiddenConsoles, settings.offlineOnly) {
            homeOrder(games, settings.homeOrder, settings.hiddenConsoles, settings.offlineOnly)
        }
    val selected = ordered.firstOrNull { it.id == controller.homeGame } ?: games.firstOrNull { it.id == controller.homeGame } ?: ordered.firstOrNull()
    LaunchedEffect(selected?.id, selected?.remote?.coverUrl) {
        controller.backdrop =
            Backdrop(selected?.remote?.screenshots?.firstOrNull() ?: selected?.remote?.coverUrl, selected?.console, hero = !controller.portrait)
    }
    if (selected == null) {
        if (sync.phase == SyncPhase.SYNCING) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        } else {
            CenteredMessage(
                "Votre salle d’arcade est prête",
                "Ajoutez des jeux à votre bibliothèque depuis RetroHub sur le web : ils apparaîtront ici, prêts à télécharger.",
                Lucide.Gamepad2,
            ) { GlassButton("Actualiser", { controller.syncNow() }, icon = Lucide.RefreshCw) }
        }
        return
    }
    if (controller.portrait) {
        PortraitHome(controller, ordered, selected) { controller.homeGame = it }
    } else {
        LandscapeHome(controller, ordered, selected) { controller.homeGame = it }
    }
}

/** Tailles de l’accueil en paysage, choisies d’après la hauteur disponible. */
internal data class HomeMetrics(val tile: Dp, val label: Boolean, val title: Int, val button: Dp, val cardHeight: Dp?)

/**
 * Grand écran : tout, en grand. Téléphone : titre plus petit. Très peu de hauteur (manette et
 * barre d’aide) : plus de nom sous la tuile ; en dessous, plus de cartes.
 */
internal fun homeMetrics(height: Dp): HomeMetrics = when {
    height >= 440.dp -> HomeMetrics((height * 0.16f).coerceAtMost(96.dp), true, 2, 52.dp, 72.dp)
    height >= 340.dp -> HomeMetrics(height * 0.14f, true, 1, 46.dp, 62.dp)
    height >= 270.dp -> HomeMetrics((height * 0.15f).coerceAtLeast(44.dp), false, 0, 42.dp, 54.dp)
    else -> HomeMetrics(44.dp, false, 0, 40.dp, null)
}

/**
 * Accueil en paysage, à la manière de la PS5 : petites tuiles en haut à gauche (celle choisie
 * grandit et porte son nom), l’illustration du jeu en grand dans le fond, le jeu et son action
 * principale en bas à gauche, puis les cartes d’activité. Tout se dimensionne d’après la hauteur.
 */
@Composable
private fun LandscapeHome(controller: UiController, games: List<Game>, selected: Game, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val metrics = homeMetrics(maxHeight)
        // Respiration proportionnelle à la hauteur : marge du bas et écart entre le jeu et les cartes.
        val gap = (maxHeight * 0.06f).coerceIn(12.dp, 32.dp)
        // Toucher ailleurs ferme le menu du jeu (couche placée derrière, pour laisser passer ses boutons).
        if (controller.gameMenu) {
            Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { controller.gameMenu = false } })
        }
        Column(Modifier.fillMaxSize().padding(bottom = gap)) {
            GameRow(controller, games, selected, onSelect, metrics.tile, selectedScale = 1.45f)
            // Le jeu choisi un peu sous le milieu de l’espace libre : l’illustration garde le haut.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = HomeEdge),
                contentAlignment = androidx.compose.ui.BiasAlignment(-1f, 0.45f),
            ) {
                Spotlight(controller, selected, metrics)
            }
            // Le menu du jeu descend sous le titre et prend la place des cartes tant qu’il est ouvert.
            val height = metrics.cardHeight ?: metrics.button
            androidx.compose.animation.AnimatedContent(
                controller.gameMenu,
                Modifier.padding(top = gap / 2),
                transitionSpec = {
                    (androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 3 }) togetherWith
                        androidx.compose.animation.fadeOut()
                },
                label = "game-menu",
            ) { open ->
                if (open) {
                    GameActionsRow(controller, selected, height = minOf(height, 56.dp))
                } else if (metrics.cardHeight != null) {
                    ActivityCards(controller, selected, height = metrics.cardHeight)
                }
            }
        }
    }
}

/** Marge gauche commune : onglets, tuiles, titre et cartes partent de la même ligne. */
internal val HomeEdge = 32.dp

/** Rangée de jaquettes : la tuile choisie s’agrandit, la dernière ouvre la bibliothèque. */
@Composable
private fun GameRow(
    controller: UiController,
    games: List<Game>,
    selected: Game,
    onSelect: (String) -> Unit,
    tile: Dp,
    modifier: Modifier = Modifier,
    selectedScale: Float = 1.25f,
    labelSelected: Boolean = false,
) {
    val downloads by controller.container.downloads.states.collectAsState()
    val first = remember { FocusRequester() }
    val label = if (labelSelected) 26.dp else 0.dp
    LazyRow(
        modifier = modifier.fillMaxWidth().height(tile * selectedScale + label + 12.dp),
        contentPadding = PaddingValues(horizontal = HomeEdge, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val row = games.take(24).let { if (selected in it) it else listOf(selected) + it.take(23) }
        items(row, key = { it.id }) { game ->
            val isSelected = game.id == selected.id
            Column {
                GameTile(
                    game,
                    selected = isSelected,
                    size = tile,
                    selectedSize = tile * selectedScale,
                    onFocus = { onSelect(game.id) },
                    onClick = { if (isSelected) gameAction(controller, game, downloads[game.playGameId]).run() else onSelect(game.id) },
                    modifier = if (isSelected) Modifier.focusRequester(first) else Modifier,
                    download = downloads[game.playGameId],
                    onLongClick = {
                        onSelect(game.id)
                        controller.gameMenu = true
                    },
                )
                // Le nom sous la tuile choisie, comme sur la PS5 ; il déborde librement à droite.
                if (labelSelected && isSelected) {
                    Text(
                        game.title,
                        Modifier.padding(top = 6.dp).layout { measurable, constraints ->
                            // Largeur nulle pour la rangée : le nom ne pousse pas les tuiles suivantes.
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = (tile * 4).roundToPx()))
                            layout(0, placeable.height) { placeable.place(0, 0) }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(key = "library") {
            Box(
                Modifier.size(tile).consoleFocus(RoundedCornerShape(14.dp), { controller.nav.go(Route.Library()) })
                    .clip(RoundedCornerShape(14.dp)).background(Palette.GlassLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.LayoutGrid, "Toute la bibliothèque", Modifier.size(tile * 0.3f), tint = Palette.TextDim)
            }
        }
    }
    AutoFocus(first)
}

@Composable
private fun Spotlight(controller: UiController, game: Game, metrics: HomeMetrics, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    Column(modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Kicker(game)
        Box {
            Text(
                game.title,
                Modifier.semantics { heading() },
                style =
                when (metrics.title) {
                    2 -> type.displayMedium
                    1 -> type.displaySmall
                    else -> type.headlineMedium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (game.supported) meta(game) else "Pas encore jouable dans l’application",
            style = if (metrics.title == 2) type.bodyLarge else type.bodyMedium,
            color = if (game.supported) Palette.TextDim else Palette.Warning,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Kicker(game: Game) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(consoleColor(game.console)))
        Text(
            listOfNotNull(
                Consoles.name(game.console),
                // « RPG , Aventure » des sources devient « RPG, Aventure ».
                game.remote.genre?.takeIf { it.isNotBlank() }?.split(',')?.joinToString(", ") { it.trim() },
                game.remote.year?.toString(),
            ).joinToString(" · ").uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun meta(game: Game): String = when {
    game.lastPlayed != null -> "${formatPlayTime(game.remote.playedSeconds)} de jeu · ${formatDate(game.lastPlayed!!)}"
    game.downloaded -> "Prêt à jouer, même hors ligne"
    game.remote.rom != null -> "À télécharger sur ce téléphone"
    else -> "Pas encore installé sur le serveur"
}

/** Portrait, fixe lui aussi : le jeu choisi en grande carte, la rangée de jeux en dessous. */
@Composable
private fun PortraitHome(controller: UiController, games: List<Game>, selected: Game, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tile = (maxWidth * 0.2f).coerceIn(64.dp, 104.dp)
        Column(Modifier.fillMaxSize().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroCard(controller, selected, Modifier.weight(1f).padding(horizontal = 16.dp))
            GameRow(controller, games, selected, onSelect, tile)
        }
    }
}

@Composable
private fun HeroCard(controller: UiController, game: Game, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val downloads by controller.container.downloads.states.collectAsState()
    Box(
        modifier.fillMaxWidth().clip(shape).consoleFocus(
            shape,
            { gameAction(controller, game, downloads[game.playGameId]).run() },
            focusScale = 1.01f,
            onLongClick = { controller.gameMenu = true },
        ),
    ) {
        GameArt(game.remote.screenshots.firstOrNull() ?: game.remote.coverUrl, game.console, game.title, Modifier.fillMaxSize(), showTitleOnFallback = false)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Palette.SkyLow.copy(alpha = 0.2f),
                    0.45f to Color.Transparent,
                    1f to Palette.SkyLow.copy(alpha = 0.95f),
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(game)
            Box {
                Text(game.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                GameMenu(controller, game)
            }
            Text(meta(game), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
