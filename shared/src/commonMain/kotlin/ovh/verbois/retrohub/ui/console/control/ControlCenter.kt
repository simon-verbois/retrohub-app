package ovh.verbois.retrohub.ui.console.control

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.sync.SyncPhase
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.LocalReducedMotion
import ovh.verbois.retrohub.ui.components.LocalScreenActive
import ovh.verbois.retrohub.ui.components.ProfileAvatar
import ovh.verbois.retrohub.ui.components.ProgressLine
import ovh.verbois.retrohub.ui.console.kit.ConsoleButton
import ovh.verbois.retrohub.ui.console.kit.ConsoleColors
import ovh.verbois.retrohub.ui.console.kit.FocusSurface
import ovh.verbois.retrohub.ui.console.kit.Hint
import ovh.verbois.retrohub.ui.console.kit.HintBarHeight
import ovh.verbois.retrohub.ui.console.kit.Pad
import ovh.verbois.retrohub.ui.console.kit.ProvideHints
import ovh.verbois.retrohub.ui.console.kit.RoundIcon
import ovh.verbois.retrohub.ui.console.kit.Tone
import ovh.verbois.retrohub.ui.console.kit.dim
import ovh.verbois.retrohub.ui.home.gameAction
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.shell.profileStatus
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.math.roundToInt

/** Icons of the control center, left to right. */
internal enum class ControlItem(val label: String) {
    HOME("Accueil"),
    ACCOUNT("Compte"),
    NOTICES("Notifications"),
    DOWNLOADS("Téléchargements"),
    SYNC("Synchronisation"),
    CONTROLLER("Manette"),
    SOUND("Son"),
    SETTINGS("Paramètres"),
    QUIT("Quitter"),
}

/**
 * The PS5-like control center: opened with the Guide button or the avatar, a band at the bottom of the
 * screen over the dimmed home. Cards above show what matters now; icons below lead to everything else.
 */
@Composable
fun ControlCenter(controller: UiController, account: Account) {
    val open = controller.menuOpen
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(open, enter = fadeIn(tween(if (reduced) 0 else 140)), exit = fadeOut(tween(if (reduced) 0 else 120))) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f))))
                .pointerInput(Unit) { detectTapGestures { controller.menuOpen = false } },
        )
    }
    AnimatedVisibility(
        open,
        Modifier.fillMaxSize(),
        enter = fadeIn(tween(if (reduced) 0 else 160)) + slideInVertically(tween(if (reduced) 0 else 200)) { it / 6 },
        exit = fadeOut(tween(if (reduced) 0 else 120)) + slideOutVertically(tween(if (reduced) 0 else 140)) { it / 6 },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalScreenActive provides open) {
            ControlBand(controller, account)
        }
    }
}

@Composable
private fun ControlBand(controller: UiController, account: Account) {
    var detail by rememberSaveable { mutableStateOf<ControlItem?>(null) }
    val iconFocus = remember { ControlItem.entries.associateWith { FocusRequester() } }
    var lastIcon by rememberSaveable { mutableStateOf(ControlItem.HOME) }
    val firstCard = remember { FocusRequester() }
    val mode = LocalInputModeManager.current.inputMode
    val close = { controller.menuOpen = false }
    val hasCards = controlCards(controller).isNotEmpty()
    ProvideHints(
        Hint(Pad.A, "Choisir"),
        Hint(Pad.B, if (detail != null) "Retour" else "Fermer") { if (detail != null) detail = null else close() },
    )
    // Where each icon sits, so its panel opens right above it.
    val centers = remember { mutableStateMapOf<ControlItem, Float>() }
    var bandLeft by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (mode == InputMode.Keyboard) runCatching { if (hasCards) firstCard.requestFocus() else iconFocus.getValue(lastIcon).requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().testTag("profile-menu").onPreviewKeyEvent { event ->
            when (event.key) {
                Key.ButtonB, Key.Escape -> {
                    if (event.type == KeyEventType.KeyUp) {
                        if (detail != null) {
                            detail = null
                            runCatching { iconFocus.getValue(lastIcon).requestFocus() }
                        } else {
                            close()
                        }
                    }
                    true
                }

                Key.ButtonStart, Key.ButtonY, Key.ButtonL1, Key.ButtonR1, Key.ButtonSelect -> {
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonStart) close()
                    true
                }

                else -> false
            }
        },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().focusGroup().pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedContent(
                detail,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                label = "control-detail",
            ) { shown ->
                Box(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(horizontal = 40.dp)
                        .onGloballyPositioned { bandLeft = it.positionInWindow().x },
                ) {
                    if (shown == null) {
                        ControlCards(
                            controller,
                            firstCard,
                            close,
                            // Down from the cards always starts the icon row on « Accueil », as on a PS5.
                            Modifier.focusProperties { down = iconFocus.getValue(ControlItem.HOME) },
                        )
                    } else {
                        ControlDetail(
                            controller,
                            account,
                            shown,
                            close,
                            Modifier.layout { measurable, constraints ->
                                val panel = measurable.measure(constraints.copy(minWidth = 0))
                                val center = (centers[shown] ?: 0f) - bandLeft
                                val x = (center - panel.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - panel.width).coerceAtLeast(0))
                                layout(constraints.maxWidth, panel.height) { panel.place(x, 0) }
                            },
                        )
                    }
                }
            }
            // The function bar: a compact row of round icons, centred, straight over the home.
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = HintBarHeight + 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlItem.entries.forEach { item ->
                    ControlIcon(
                        controller,
                        account,
                        item,
                        Modifier.focusRequester(iconFocus.getValue(item)).focusProperties {
                            if (item == ControlItem.entries.first()) left = FocusRequester.Cancel
                            if (item == ControlItem.entries.last()) right = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }.onGloballyPositioned { centers[item] = it.positionInWindow().x + it.size.width / 2f },
                        open = detail == item,
                        onFocus = { lastIcon = item },
                    ) {
                        lastIcon = item
                        when (item) {
                            ControlItem.HOME -> {
                                close()
                                controller.returnHome()
                            }

                            ControlItem.SETTINGS -> {
                                close()
                                controller.nav.go(Route.Settings())
                            }

                            ControlItem.QUIT -> {
                                close()
                                controller.exitRequested = true
                            }

                            else -> {
                                detail = item
                                if (item == ControlItem.NOTICES) controller.markNoticesRead()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlIcon(
    controller: UiController,
    account: Account,
    item: ControlItem,
    modifier: Modifier,
    open: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val downloads by controller.container.downloads.states.collectAsState()
    val sync by controller.container.sync.status.collectAsState()
    val online by controller.container.platform.online.collectAsState()
    val gamepad by controller.container.platform.gamepadConnected.collectAsState()
    val settings by controller.container.settings.settings.collectAsState()
    // Icons only; the focused one turns into a white disc with its label above, as on a PS5.
    RoundIcon(
        item.label,
        onClick,
        modifier.testTag("control-${item.name.lowercase()}"),
        size = 46.dp,
        labelAbove = true,
        highlighted = open,
        onFocus = { if (it) onFocus() },
    ) {
        when (item) {
            ControlItem.ACCOUNT -> ProfileAvatar(account.username, account.server, account.avatar, Modifier.size(28.dp), showBackground = false)
            else -> Icon(item.icon(settings.audio), null, Modifier.size(22.dp))
        }
        val badge: Pair<String?, Color>? = when (item) {
            ControlItem.NOTICES -> controller.unreadNotices.takeIf { it > 0 }?.let { it.toString() to ConsoleColors.Selected }

            ControlItem.DOWNLOADS -> downloads.count {
                it.value is DownloadState.Running
            }.takeIf { it > 0 }?.let { it.toString() to ConsoleColors.Selected }

            ControlItem.SYNC -> null to profileStatus(sync, online).second

            ControlItem.CONTROLLER -> if (gamepad) null to Palette.Success else null

            else -> null
        }
        if (badge != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp).clip(CircleShape).background(badge.second)
                    .then(if (badge.first == null) Modifier.size(9.dp) else Modifier.padding(horizontal = 5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                badge.first?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = ConsoleColors.OnSelected) }
            }
        }
    }
}

private fun ControlItem.icon(audio: Boolean): ImageVector = when (this) {
    ControlItem.HOME -> Lucide.House
    ControlItem.ACCOUNT -> Lucide.UserRound
    ControlItem.NOTICES -> Lucide.Bell
    ControlItem.DOWNLOADS -> Lucide.Download
    ControlItem.SYNC -> Lucide.RefreshCw
    ControlItem.CONTROLLER -> Lucide.Gamepad2
    ControlItem.SOUND -> if (audio) Lucide.Volume2 else Lucide.VolumeX
    ControlItem.SETTINGS -> Lucide.Settings
    ControlItem.QUIT -> Lucide.Power
}

/** What the cards row shows: the game to resume, running downloads, the latest notice. */
private sealed interface ControlCard {
    data class Resume(val game: Game) : ControlCard
    data class Downloading(val game: Game, val state: DownloadState.Running) : ControlCard
    data class Conflicts(val count: Int) : ControlCard
    data class Latest(val notice: ovh.verbois.retrohub.ui.Notice) : ControlCard
}

@Composable
private fun controlCards(controller: UiController): List<ControlCard> {
    val games by controller.container.library.games.collectAsState()
    val downloads by controller.container.downloads.states.collectAsState()
    val sync by controller.container.sync.status.collectAsState()
    val resume = games.filter { it.downloaded && it.lastPlayed != null }.maxByOrNull { it.lastPlayed!! }
    val running = downloads.mapNotNull { (id, state) ->
        val game = games.firstOrNull { it.playGameId == id }
        if (state is DownloadState.Running && game != null) ControlCard.Downloading(game, state) else null
    }
    return listOfNotNull(resume?.let { ControlCard.Resume(it) }) + running.take(2) +
        listOfNotNull(sync.conflicts.takeIf { it > 0 }?.let { ControlCard.Conflicts(it) }) +
        listOfNotNull(controller.notices.firstOrNull()?.let { ControlCard.Latest(it) })
}

@Composable
private fun ControlCards(controller: UiController, firstCard: FocusRequester, close: () -> Unit, modifier: Modifier) {
    val cards = controlCards(controller)
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        cards.forEachIndexed { index, card ->
            val modifier = Modifier.width(300.dp).height(108.dp).then(if (index == 0) Modifier.focusRequester(firstCard) else Modifier)
            when (card) {
                is ControlCard.Resume -> CardFrame(
                    modifier,
                    "Dernière partie",
                    card.game.title,
                    onClick = {
                        close()
                        controller.play(card.game)
                    },
                    art = card.game,
                ) { Text(gameAction(controller, card.game, null).label, style = MaterialTheme.typography.bodySmall, color = dim) }

                is ControlCard.Downloading -> CardFrame(
                    modifier,
                    "Téléchargement",
                    card.game.title,
                    onClick = {
                        close()
                        controller.showGame(card.game.id)
                    },
                    art = card.game,
                ) { ProgressLine(card.state.fraction, Modifier.fillMaxWidth()) }

                is ControlCard.Conflicts -> CardFrame(
                    modifier,
                    "Sauvegardes",
                    if (card.count == 1) "Une sauvegarde attend votre choix" else "${card.count} sauvegardes attendent votre choix",
                    onClick = {
                        close()
                        controller.reviewSaveConflicts()
                    },
                    icon = Lucide.TriangleAlert,
                )

                is ControlCard.Latest -> CardFrame(
                    modifier,
                    "Notification",
                    card.notice.text,
                    onClick = {
                        close()
                        card.notice.route?.let { controller.nav.go(it) }
                    },
                    icon = Lucide.Bell,
                )
            }
        }
    }
}

@Composable
private fun CardFrame(
    modifier: Modifier,
    kicker: String,
    title: String,
    onClick: () -> Unit,
    art: Game? = null,
    icon: ImageVector? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    FocusSurface(
        onClick,
        modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Deep.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(18.dp),
        focusScale = 1.04f,
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (art != null) {
                GameArt(art.remote.coverUrl, art.console, art.title, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)), showTitleOnFallback = false)
            } else if (icon != null) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(LocalContentColor.current.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kicker, style = MaterialTheme.typography.labelSmall, color = dim, maxLines = 1)
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                footer?.invoke()
            }
        }
    }
}

/** The card an icon opens above the row. */
@Composable
private fun ControlDetail(controller: UiController, account: Account, item: ControlItem, close: () -> Unit, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val mode = LocalInputModeManager.current.inputMode
    LaunchedEffect(item) {
        withFrameNanos { }
        if (mode == InputMode.Keyboard) runCatching { focus.requestFocus() }
    }
    val openSettings = { page: SettingsPage ->
        close()
        controller.nav.go(Route.Settings(page))
    }
    Column(
        modifier.widthIn(max = 620.dp).clip(RoundedCornerShape(22.dp)).background(Palette.Deep.copy(alpha = 0.95f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.label, style = MaterialTheme.typography.titleLarge)
        when (item) {
            ControlItem.ACCOUNT -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileAvatar(account.username, account.server, account.avatar, Modifier.size(44.dp), showBackground = false)
                    Column {
                        Text(account.username, style = MaterialTheme.typography.titleMedium)
                        Text(account.deviceName, style = MaterialTheme.typography.bodySmall, color = dim)
                    }
                }
                ConsoleButton("Ouvrir le compte", { openSettings(SettingsPage.ACCOUNT) }, Modifier.focusRequester(focus), icon = Lucide.UserRound)
            }

            ControlItem.NOTICES -> {
                if (controller.notices.isEmpty()) {
                    Text("Aucune notification", style = MaterialTheme.typography.bodyMedium, color = dim)
                    Spacer(Modifier.height(0.dp).focusRequester(focus))
                } else {
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        controller.notices.take(12).forEachIndexed { index, notice ->
                            val time = notice.at.toLocalDateTime(TimeZone.currentSystemDefault())
                            FocusSurface(
                                {
                                    close()
                                    notice.route?.let { controller.nav.go(it) }
                                },
                                Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
                                tone = Tone.QUIET,
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = dim,
                                    )
                                    Text(
                                        notice.text,
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    ConsoleButton("Tout effacer", { controller.clearNotices() }, icon = Lucide.Trash2)
                }
            }

            ControlItem.DOWNLOADS -> {
                val downloads by controller.container.downloads.states.collectAsState()
                val games by controller.container.library.games.collectAsState()
                val running = downloads.mapNotNull { (id, state) ->
                    (state as? DownloadState.Running)?.let { s ->
                        games.firstOrNull { it.playGameId == id }?.let {
                            it to
                                s
                        }
                    }
                }
                if (running.isEmpty()) {
                    Text("Aucun téléchargement en cours", style = MaterialTheme.typography.bodyMedium, color = dim)
                } else {
                    running.forEach { (game, state) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(game.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ProgressLine(state.fraction, Modifier.width(140.dp))
                        }
                    }
                }
                ConsoleButton("Stockage", { openSettings(SettingsPage.STORAGE) }, Modifier.focusRequester(focus), icon = Lucide.HardDrive)
            }

            ControlItem.SYNC -> {
                val sync by controller.container.sync.status.collectAsState()
                val online by controller.container.platform.online.collectAsState()
                val (_, tint, label) = profileStatus(sync, online)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val syncing = sync.phase == SyncPhase.SYNCING
                    ConsoleButton(
                        if (syncing) "Synchronisation…" else "Synchroniser",
                        { controller.syncNow() },
                        Modifier.focusRequester(focus).testTag("profile-sync"),
                        icon = Lucide.RefreshCw,
                        loading = syncing,
                    )
                    ConsoleButton("Réglages", { openSettings(SettingsPage.SYNC) }, icon = Lucide.Settings)
                }
            }

            ControlItem.CONTROLLER -> {
                val gamepad by controller.container.platform.gamepadConnected.collectAsState()
                Text(if (gamepad) "Manette connectée" else "Aucune manette", style = MaterialTheme.typography.bodyMedium, color = dim)
                ConsoleButton("Réglages de la manette", { openSettings(SettingsPage.CONTROLS) }, Modifier.focusRequester(focus), icon = Lucide.Gamepad2)
            }

            ControlItem.SOUND -> {
                val settings by controller.container.settings.settings.collectAsState()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConsoleButton(
                        if (settings.audio) "Couper le son des jeux" else "Activer le son des jeux",
                        { controller.container.settings.update { it.copy(audio = !it.audio) } },
                        Modifier.focusRequester(focus),
                        icon = if (settings.audio) Lucide.VolumeX else Lucide.Volume2,
                    )
                    ConsoleButton("Réglages audio", { openSettings(SettingsPage.AUDIO) }, icon = Lucide.Settings)
                }
            }

            else -> Unit
        }
    }
}
