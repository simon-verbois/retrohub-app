package ovh.verbois.retrohub.ui.console.game

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.core.formatRelative
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.GameRotation
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.Backdrop
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.catalogue.CatalogueGameState
import ovh.verbois.retrohub.ui.catalogue.rememberCatalogueGameState
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.ConsoleChoiceDialog
import ovh.verbois.retrohub.ui.components.ConsoleTone
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.LocalScreenActive
import ovh.verbois.retrohub.ui.console.kit.ConsoleButton
import ovh.verbois.retrohub.ui.console.kit.ConsoleSideSheet
import ovh.verbois.retrohub.ui.console.kit.FocusMemory
import ovh.verbois.retrohub.ui.console.kit.FocusSurface
import ovh.verbois.retrohub.ui.console.kit.Hint
import ovh.verbois.retrohub.ui.console.kit.HintBarHeight
import ovh.verbois.retrohub.ui.console.kit.OptionRow
import ovh.verbois.retrohub.ui.console.kit.Pad
import ovh.verbois.retrohub.ui.console.kit.PageInset
import ovh.verbois.retrohub.ui.console.kit.PillShape
import ovh.verbois.retrohub.ui.console.kit.ProvideHints
import ovh.verbois.retrohub.ui.console.kit.RestoreFocus
import ovh.verbois.retrohub.ui.console.kit.Tone
import ovh.verbois.retrohub.ui.console.kit.dim
import ovh.verbois.retrohub.ui.console.kit.rememberFocusMemory
import ovh.verbois.retrohub.ui.console.kit.remembered
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.settings.CheatList
import ovh.verbois.retrohub.ui.settings.SaveHistory

/** The main action of the page, whatever the game's state. */
private data class MainAction(val label: String, val icon: ImageVector, val progress: Float? = null, val enabled: Boolean = true, val run: () -> Unit)

/**
 * The PS5-like game page: big cover, title and facts, one big main action (play, resume, download with
 * its progress inside), secondary actions, then the game's own options (saves, cheats, picture…).
 * Works offline from the library alone.
 */
@Composable
fun ConsoleGameScreen(controller: UiController, id: String) {
    val container = controller.container
    val library by container.library.games.collectAsState()
    val downloads by container.downloads.states.collectAsState()
    val online by container.platform.online.collectAsState()
    val state = rememberCatalogueGameState(container, id)
    val known = library.firstOrNull { it.id == id }
    val detail = state.detail
    val memory = rememberFocusMemory()
    var chooseVersion by remember { mutableStateOf(false) }
    var confirmVersion by remember { mutableStateOf(false) }
    val title = detail?.title ?: known?.title
    val console = detail?.console ?: known?.console
    val cover = detail?.coverUrl ?: known?.remote?.coverUrl
    LaunchedEffect(cover, console) { controller.backdrop = Backdrop(cover, console) }
    // Back on the home, its own background comes back.
    val previous = remember { controller.backdrop }
    DisposableEffect(Unit) { onDispose { controller.backdrop = previous } }
    if (title == null || console == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (online && state.error == null) CircularProgressIndicator(color = Color.White)
                Text(
                    state.error ?: if (online) "Chargement…" else "Connectez le téléphone pour ouvrir cette fiche.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.error != null) ConsoleButton("Réessayer", { state.reload() }, icon = Lucide.RefreshCw, enabled = online)
            }
        }
        ProvideHints(Hint(Pad.B, "Retour") { controller.back() })
        return
    }
    val download = known?.let { downloads[it.playGameId] }
    val main = mainAction(controller, state, known, download, online) { confirmVersion = true }
    var sheet by remember { mutableStateOf<GameSheet?>(null) }
    // What A does follows the focus: the main action, or opening the focused option.
    var focusedAction by remember { mutableStateOf<String?>(null) }
    ProvideHints(
        Hint(Pad.A, focusedAction ?: main.label.substringBefore(" ·")),
        Hint(Pad.B, "Retour") { controller.back() },
    )
    val active = LocalScreenActive.current
    CompositionLocalProvider(LocalScreenActive provides (active && sheet == null)) {
        // One screen, no scrolling: the header, the actions, then the options as a row of tiles.
        Column(
            Modifier.fillMaxSize().testTag("console-game")
                .padding(horizontal = PageInset).padding(top = 24.dp, bottom = HintBarHeight + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                GameArt(
                    cover,
                    console,
                    title,
                    Modifier.width(168.dp).aspectRatio(1f).shadow(18.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)),
                    showTitleOnFallback = false,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(facts(controller, detail?.year, detail?.genre, console, known), style = MaterialTheme.typography.bodyMedium, color = dim, maxLines = 1)
                    detail?.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            Modifier.widthIn(max = 760.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = dim,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MainButton(
                            main,
                            Modifier.remembered(memory, "main").testTag("game-primary-action"),
                        ) { if (it) focusedAction = null }
                        if (known != null) {
                            ConsoleButton(
                                if (known.favorite) "Favori" else "Ajouter aux favoris",
                                { controller.toggleFavorite(known) },
                                Modifier.remembered(memory, "favorite").focusAction { focusedAction = "Choisir" },
                                icon = if (known.favorite) Lucide.HeartFilled else Lucide.Heart,
                            )
                        }
                        val versions = detail?.versions.orEmpty()
                        if (versions.size > 1) {
                            ConsoleButton(
                                "Version",
                                { chooseVersion = true },
                                Modifier.remembered(memory, "version").focusAction { focusedAction = "Choisir" },
                                icon = Lucide.Layers,
                            )
                        }
                        if (download is DownloadState.Running && known != null) {
                            ConsoleButton(
                                "Annuler",
                                { container.downloads.cancel(known.playGameId) },
                                Modifier.focusAction { focusedAction = "Annuler" },
                                icon = Lucide.X,
                            )
                        }
                        if (state.job?.active == true) {
                            ConsoleButton(
                                "Annuler sur le serveur",
                                { state.cancelServerDownload() },
                                Modifier.focusAction { focusedAction = "Annuler" },
                                icon = Lucide.X,
                            )
                        }
                    }
                    state.version?.takeIf { (detail?.versions?.size ?: 0) > 1 }?.let { version ->
                        Text("Version : ${versionLabel(version)}", style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1)
                    }
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ConsoleTone.Selected, maxLines = 2) }
                    if (Consoles[console] == null) Text("Cette console n’est pas encore jouable ici.", style = MaterialTheme.typography.bodyMedium, color = dim)
                }
            }
            if (known != null) {
                Spacer(Modifier.weight(1f))
                GameOptionTiles(controller, known, download, memory, { sheet = it }) { focusedAction = it }
            }
        }
        // Also runs when a panel closes: the focus goes back to the tile that opened it.
        RestoreFocus(memory, "main")
    }
    if (known != null) GameSheets(controller, known, sheet) { sheet = null }
    if (chooseVersion) {
        val versions = detail?.versions.orEmpty()
        ConsoleChoiceDialog(
            "Version",
            versions,
            state.version ?: versions.first(),
            { versionLabel(it) },
            onDismiss = { chooseVersion = false },
        ) {
            state.select(it.id)
            chooseVersion = false
        }
    }
    if (confirmVersion) {
        ConfirmDialog(
            "Changer de version ?",
            "Cette version sera aussi sélectionnée sur le serveur.",
            "Télécharger",
            onConfirm = {
                confirmVersion = false
                state.downloadToDevice()
            },
            onDismiss = { confirmVersion = false },
        )
    }
    state.files?.let { files ->
        ConsoleChoiceDialog(
            "Fichier à télécharger",
            files,
            files.first(),
            { listOf(it.name, it.size).filter(String::isNotBlank).joinToString(" · ") },
            onDismiss = { state.dismissFiles() },
        ) { state.startServerDownload(it.id) }
    }
}

private fun versionLabel(version: ovh.verbois.retrohub.api.CatalogueVersion) =
    listOf(version.label, version.source, version.languages.joinToString(", ").uppercase()).filter { it.isNotBlank() }.joinToString(" · ")

private fun facts(controller: UiController, year: Int?, genre: String?, console: String, game: Game?): String {
    val played = game?.remote?.playedSeconds?.takeIf { it > 0 }?.let { "${formatPlayTime(it)} de jeu" }
    val last = game?.lastPlayed?.let { "Joué ${formatRelative(it, controller.container.clock.now()).replaceFirstChar { c -> c.lowercase() }}" }
    return listOfNotNull(Consoles.name(console), year?.toString(), genre?.takeIf { it.isNotBlank() }, played, last).joinToString(" · ")
}

private fun mainAction(
    controller: UiController,
    state: CatalogueGameState,
    known: Game?,
    download: DownloadState?,
    online: Boolean,
    askVersion: () -> Unit,
): MainAction {
    val detail = state.detail
    val version = state.version
    val job = state.job
    val playable = known != null && known.downloaded && !known.updateAvailable && (detail == null || version == null || detail.versionId == version.id)
    return when {
        download is DownloadState.Running -> MainAction("Téléchargement ${(download.fraction * 100).toInt()} %", Lucide.Download, download.fraction) {}

        job?.active == true -> MainAction("Sur le serveur ${job.progress.toInt()} %", Lucide.Download, job.progress / 100f) {}

        playable -> MainAction(
            if (known.lastPlayed != null || known.remote.saves.isNotEmpty()) "Reprendre" else "Jouer",
            Lucide.Play,
            enabled = known.supported && controller.launching != known.id,
        ) { controller.play(known) }

        known?.updateAvailable == true && detail == null -> MainAction("Mettre à jour", Lucide.Download) { controller.container.downloads.start(known) }

        detail == null || version == null -> MainAction("Indisponible hors connexion", Lucide.CloudOff, enabled = false) {}

        version.available -> MainAction(
            "Télécharger" + (known?.remote?.rom?.size?.let { " · ${formatBytes(it)}" } ?: ""),
            Lucide.Download,
            enabled = online && !state.busy && Consoles[detail.console] != null,
        ) { if (state.changesServerVersion) askVersion() else state.downloadToDevice() }

        version.downloadable -> MainAction(
            if (job?.status == "failed") "Réessayer sur le serveur" else "Télécharger sur le serveur",
            Lucide.Download,
            enabled = online && !state.busy,
        ) { state.prepareServerDownload() }

        else -> MainAction("Pas encore disponible", Lucide.Info, enabled = false) {}
    }
}

/** The big main button; a running download fills it from the left. */
@Composable
private fun MainButton(action: MainAction, modifier: Modifier, onFocus: (Boolean) -> Unit) {
    // Always focusable: while a download starts the button waits, and the focus stays on it for « Jouer ».
    val usable = action.enabled || action.progress != null
    FocusSurface(
        { if (action.enabled) action.run() },
        modifier.heightIn(min = 52.dp).alpha(if (usable) 1f else 0.55f),
        shape = PillShape,
        tone = Tone.PRIMARY,
        focusScale = 1.05f,
        onFocus = onFocus,
        contentAlignment = Alignment.Center,
    ) {
        val fill = ConsoleTone.Selected.copy(alpha = 0.45f)
        Row(
            Modifier.then(
                if (action.progress != null) Modifier.drawBehind { drawRect(fill, size = Size(size.width * action.progress, size.height)) } else Modifier,
            ).padding(horizontal = 28.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(action.icon, null, Modifier.size(24.dp))
            Text(action.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

private fun Modifier.focusAction(onFocused: () -> Unit) = onFocusChanged { if (it.isFocused) onFocused() }

private enum class GameSheet(val title: String) { SAVES("Sauvegardes"), CHEATS("Codes de triche"), ROTATION("Rotation de l’image") }

/**
 * The game's options as one row of tiles, like the PS5 game hub: each tile shows its current value and
 * opens a panel (saves, cheats, rotation) or acts directly (new game, delete).
 */
@Composable
private fun GameOptionTiles(
    controller: UiController,
    game: Game,
    download: DownloadState?,
    memory: FocusMemory,
    open: (GameSheet) -> Unit,
    onAction: (String) -> Unit,
) {
    val container = controller.container
    val settings by container.settings.settings.collectAsState()
    var saves by remember { mutableStateOf<Int?>(null) }
    var cheats by remember { mutableStateOf<Int?>(null) }
    var conflict by remember { mutableStateOf(false) }
    var confirmFresh by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val active = LocalScreenActive.current
    // Counts are read again each time a panel closes, so the tiles show what was just changed.
    LaunchedEffect(game.playGameId, active) {
        val local = container.saveStore.forGame(game.playGameId)
        saves = local.size
        conflict = local.any { it.inConflict }
        cheats = container.cheats.get(game.playGameId).size
    }
    val running = download is DownloadState.Running
    val rotation = settings.gameRotations[game.playGameId] ?: GameRotation.NORMAL
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Options du jeu", style = MaterialTheme.typography.labelMedium, color = dim)
        LazyRow(
            Modifier.focusProperties { up = memory.requester("main") },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            if (conflict) {
                item("conflict") {
                    OptionTile("Conflit", "Choisir la sauvegarde", Lucide.CloudAlert, memory, "conflict", onAction) {
                        controller.nav.go(Route.Conflict(game.playGameId))
                    }
                }
            }
            if (game.updateAvailable && !running) {
                item("update") {
                    OptionTile("Mise à jour", "Disponible", Lucide.Download, memory, "update", onAction) { controller.requestDownload(game) }
                }
            }
            item("saves") {
                OptionTile(
                    "Sauvegardes",
                    when (saves) {
                        null -> " "
                        0 -> "Aucune"
                        1 -> "1 sauvegarde"
                        else -> "$saves sauvegardes"
                    },
                    Lucide.History,
                    memory,
                    "saves",
                    onAction,
                ) { open(GameSheet.SAVES) }
            }
            item("cheats") {
                OptionTile(
                    "Codes de triche",
                    when (cheats) {
                        null -> " "
                        0 -> "Aucun"
                        1 -> "1 code"
                        else -> "$cheats codes"
                    },
                    Lucide.Sparkles,
                    memory,
                    "cheats",
                    onAction,
                ) { open(GameSheet.CHEATS) }
            }
            item("rotation") {
                OptionTile("Rotation", rotation.label, Lucide.RotateCw, memory, "rotation", onAction) { open(GameSheet.ROTATION) }
            }
            if (game.console == "3ds") {
                item("mods") {
                    OptionTile("Mods", "Gérer", Lucide.Layers, memory, "mods", onAction) {
                        controller.nav.go(Route.Settings(SettingsPage.MODS, game.id))
                    }
                }
            }
            if (settings.pinHash.isNotEmpty()) {
                item("lock") {
                    val locked = game.playGameId in settings.lockedGames
                    OptionTile("Protection", if (locked) "Activée" else "Désactivée", Lucide.Lock, memory, "lock", onAction) {
                        container.settings.update {
                            it.copy(lockedGames = if (locked) it.lockedGames - game.playGameId else it.lockedGames + game.playGameId)
                        }
                    }
                }
            }
            if (game.downloaded) {
                item("fresh") {
                    OptionTile("Nouvelle partie", "Depuis le début", Lucide.RotateCcw, memory, "fresh", onAction) { confirmFresh = true }
                }
                item("delete") {
                    OptionTile("Supprimer", "Du téléphone", Lucide.Trash2, memory, "delete", onAction, danger = true) { confirmDelete = true }
                }
            }
        }
    }
    if (confirmFresh) {
        ConfirmDialog("Nouvelle partie ?", "La dernière partie reste gardée.", "Jouer", onConfirm = {
            confirmFresh = false
            controller.play(game, fresh = true)
        }, onDismiss = { confirmFresh = false }, focusDismiss = true)
    }
    if (confirmDelete) {
        ConfirmDialog("Supprimer du téléphone ?", "Les sauvegardes sont gardées.", "Supprimer", onConfirm = {
            confirmDelete = false
            controller.deleteDownload(game)
            controller.back()
        }, onDismiss = { confirmDelete = false }, destructive = true, focusDismiss = true)
    }
}

@Composable
private fun OptionTile(
    label: String,
    value: String,
    icon: ImageVector,
    memory: FocusMemory,
    key: String,
    onAction: (String) -> Unit,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick,
        Modifier.size(width = 150.dp, height = 96.dp).remembered(memory, key).testTag("game-option-$key"),
        shape = RoundedCornerShape(16.dp),
        tone = if (danger) Tone.DANGER else Tone.GLASS,
        label = label,
        onFocus = { if (it) onAction(if (key == "fresh" || key == "delete" || key == "update") label else "Ouvrir") },
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, Modifier.size(22.dp))
            Column {
                Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The panels behind the tiles: saves history, cheat codes, picture rotation. */
@Composable
private fun GameSheets(controller: UiController, game: Game, sheet: GameSheet?, close: () -> Unit) {
    val first = remember { FocusRequester() }
    var shown by remember { mutableStateOf(GameSheet.SAVES) }
    if (sheet != null) shown = sheet
    ConsoleSideSheet(sheet != null, shown.title, close, width = 480.dp, initialFocus = first, tag = "game-options-panel") {
        Column(Modifier.focusRequester(first).focusGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (shown) {
                GameSheet.SAVES -> SaveHistory(controller, game.playGameId, game.console)

                GameSheet.CHEATS -> CheatList(controller, game.playGameId)

                GameSheet.ROTATION -> {
                    val settings by controller.container.settings.settings.collectAsState()
                    val current = settings.gameRotations[game.playGameId] ?: GameRotation.NORMAL
                    GameRotation.entries.forEach { rotation ->
                        OptionRow(rotation.label, {
                            controller.container.settings.update { it.copy(gameRotations = it.gameRotations + (game.playGameId to rotation)) }
                        }, selected = rotation == current)
                    }
                }
            }
        }
    }
}
