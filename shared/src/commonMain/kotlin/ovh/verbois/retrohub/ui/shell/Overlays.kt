package ovh.verbois.retrohub.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.core.searchKey
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.sync.SyncPhase
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.ProfileAvatar
import ovh.verbois.retrohub.ui.components.consoleButtonSurface
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.components.controllerDialog
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

fun matches(game: Game, query: String): Boolean = searchKey(query).split(' ').filter { it.isNotBlank() }.all { it in game.searchText }

fun searchGames(games: List<Game>, query: String): List<Game> {
    val words = searchKey(query).split(' ').filter { it.isNotBlank() }
    return if (words.isEmpty()) {
        games.sortedByDescending { it.lastPlayed }.take(8)
    } else {
        games.asSequence().filter { game -> words.all { it in game.searchText } }.take(40).toList()
    }
}

/** A compact console quick panel: account, four shortcuts, then synchronization. */
@Composable
fun AccountMenu(controller: UiController, account: Account, anchor: Rect? = null, onClose: () -> Unit) {
    val settings by controller.container.settings.settings.collectAsState()
    val sync by controller.container.sync.status.collectAsState()
    val online by controller.container.platform.online.collectAsState()
    val reducedMotion = settings.reduceMotion || controller.container.platform.reducedMotion
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(controller.menuOpen) { visible.targetState = controller.menuOpen }
    val focus = remember { List(6) { FocusRequester() } }
    var lastShortcut by remember { mutableStateOf(1) }
    val density = LocalDensity.current
    var hostBounds by remember { mutableStateOf<Rect?>(null) }
    var pointerCenter by remember { mutableStateOf<Float?>(null) }
    val positionProvider = remember(anchor, density) {
        ProfileMenuPositionProvider(anchor, with(density) { 12.dp.roundToPx() }, with(density) { 4.dp.roundToPx() }) { pointerCenter = it }
    }
    val openSettings = { page: SettingsPage ->
        controller.menuOpen = false
        controller.nav.go(Route.Settings(page))
    }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { hostBounds = it.boundsInWindow() }) {
        val availableBelow = if (anchor != null && hostBounds != null) {
            with(density) { (hostBounds!!.bottom - anchor.bottom).toDp() } - 12.dp
        } else {
            maxHeight - 16.dp
        }
        val panelHeight = minOf(maxHeight - 16.dp, availableBelow).coerceAtLeast(48.dp)
        val pointerHeight = if (anchor == null) 0.dp else 10.dp
        val regularTileHeight = maxOf(72.dp, 50.dp + 18.dp * density.fontScale)
        val regularHeight = pointerHeight + 158.dp + regularTileHeight * 2
        val compact = regularHeight > panelHeight
        val padding = if (compact) 6.dp else 10.dp
        val gap = if (compact) 4.dp else 8.dp
        val gridGap = if (compact) 6.dp else 8.dp
        val actionPadding = if (compact) 4.dp else 8.dp
        val accountHeight = maxOf(48.dp, 22.dp * density.fontScale + actionPadding * 2)
        val syncHeight = maxOf(48.dp, 18.dp * density.fontScale + actionPadding * 2)
        val desiredTileHeight = if (compact) 34.dp + 18.dp * density.fontScale else regularTileHeight
        val fixedHeight = pointerHeight + padding * 2 + gap * 4 + 2.dp + accountHeight + syncHeight
        // Short landscape screens trade two rows for one wider row instead of hiding actions.
        val columns = if (fixedHeight + desiredTileHeight * 2 + gridGap > panelHeight) 4 else 2
        val rows = 4 / columns
        val panelWidth = minOf(
            if (columns == 4) 92.dp * density.fontScale * 4 + padding * 2 + gridGap * 3 else 288.dp * density.fontScale.coerceIn(1f, 1.35f),
            (maxWidth - 24.dp).coerceAtLeast(48.dp),
        )
        val desiredHeight = fixedHeight + desiredTileHeight * rows + gridGap * (rows - 1)
        val fit = minOf(1f, panelHeight / desiredHeight)
        val tileHeight = desiredTileHeight * fit
        val cellWidth = (panelWidth - padding * 2 - gridGap * (columns - 1)) / columns
        val menuDensity = Density(density.density, minOf(density.fontScale, (cellWidth - 16.dp) / 76.dp) * fit)
        val shape = remember(pointerCenter, anchor != null, panelWidth, density) {
            ProfileMenuShape(if (anchor == null) null else pointerCenter ?: with(density) { panelWidth.toPx() / 2 })
        }
        if (visible.currentState || visible.targetState) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { if (controller.menuOpen) onClose() },
                properties = PopupProperties(focusable = true),
            ) {
                CompositionLocalProvider(LocalDensity provides menuDensity) {
                    AnimatedVisibility(
                        visibleState = visible,
                        enter =
                        fadeIn(tween(if (reducedMotion) 0 else 160)) +
                            scaleIn(tween(if (reducedMotion) 0 else 160), initialScale = 0.96f, transformOrigin = TransformOrigin(1f, 0f)),
                        exit = fadeOut(tween(if (reducedMotion) 0 else 100)),
                    ) {
                        Box(
                            Modifier.width(panelWidth).heightIn(max = panelHeight).testTag("profile-menu")
                                .shadow(10.dp, shape, clip = false).clip(shape)
                                .background(Brush.verticalGradient(listOf(lerp(Palette.Deep, Palette.SkyTop, 0.28f), Palette.Deep)))
                                .border(1.dp, Palette.Line, shape).padding(top = pointerHeight * fit),
                        ) {
                            Column(
                                Modifier.focusGroup().controllerDialog({ if (controller.menuOpen) onClose() }, dismissOnStart = true).padding(padding * fit),
                                verticalArrangement = Arrangement.spacedBy(gap * fit),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().height(accountHeight * fit).testTag("profile-account")
                                        .focusRequester(focus[0]).focusProperties {
                                            up = FocusRequester.Cancel
                                            down = focus[1]
                                            left = FocusRequester.Cancel
                                            right = FocusRequester.Cancel
                                        }
                                        .consoleFocus(RoundedCornerShape(14.dp), { openSettings(SettingsPage.ACCOUNT) }, focusScale = 1f)
                                        .padding(horizontal = 8.dp * fit, vertical = actionPadding * fit),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    ProfileAvatar(
                                        account.username,
                                        account.server,
                                        account.avatar,
                                        Modifier.size((if (compact) 26.dp else 32.dp) * fit),
                                        showBackground = false,
                                    )
                                    Text(
                                        "Mon compte",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(Lucide.ChevronRight, null, Modifier.size(20.dp), tint = Palette.TextDim)
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp * fit).background(Palette.Line))
                                val shortcuts = listOf(
                                    Triple(Lucide.SlidersHorizontal, "Paramètres", SettingsPage.ROOT),
                                    Triple(Lucide.Gamepad2, "Manette", SettingsPage.CONTROLS),
                                    Triple(Lucide.Layers, "Consoles", SettingsPage.CONSOLES),
                                    Triple(Lucide.HardDrive, "Stockage", SettingsPage.STORAGE),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(gridGap * fit)) {
                                    shortcuts.chunked(columns).forEachIndexed { row, entries ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gridGap * fit)) {
                                            entries.forEachIndexed { column, (icon, label, page) ->
                                                val index = row * columns + column + 1
                                                ProfileShortcut(
                                                    icon,
                                                    label,
                                                    { openSettings(page) },
                                                    Modifier.weight(1f).testTag("profile-shortcut-${page.name.lowercase()}")
                                                        .focusRequester(focus[index]).focusProperties {
                                                            up = if (index <= columns) focus[0] else focus[index - columns]
                                                            down = if (index + columns <= 4) focus[index + columns] else focus[5]
                                                            left = if (column > 0) focus[index - 1] else FocusRequester.Cancel
                                                            right = if (column + 1 < columns) focus[index + 1] else FocusRequester.Cancel
                                                        },
                                                    onFocus = { if (it) lastShortcut = index },
                                                    reducedMotion = reducedMotion,
                                                    height = tileHeight,
                                                    iconSize = (if (compact) 22.dp else 28.dp) * fit,
                                                    gap = (if (compact) 4.dp else 6.dp) * fit,
                                                )
                                            }
                                        }
                                    }
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp * fit).background(Palette.Line))
                                val (_, tint, status) = profileStatus(sync, online)
                                Row(
                                    Modifier.fillMaxWidth().height(syncHeight * fit).testTag("profile-sync")
                                        .focusRequester(focus[5]).focusProperties {
                                            up = focus[lastShortcut]
                                            down = FocusRequester.Cancel
                                            left = FocusRequester.Cancel
                                            right = FocusRequester.Cancel
                                        }
                                        .semantics { stateDescription = status }
                                        .consoleFocus(RoundedCornerShape(24.dp), {
                                            if (sync.phase != SyncPhase.SYNCING) controller.syncNow()
                                        }, focusScale = 1f)
                                        .consoleButtonSurface(RoundedCornerShape(24.dp)).padding(horizontal = 12.dp * fit, vertical = actionPadding * fit),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (sync.phase == SyncPhase.SYNCING) {
                                        CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Text, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Lucide.RefreshCw, null, Modifier.size(20.dp), tint = Palette.Text)
                                    }
                                    Text(
                                        if (sync.phase ==
                                            SyncPhase.SYNCING
                                        ) {
                                            "Synchronisation…"
                                        } else {
                                            "Synchroniser"
                                        },
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
                                }
                            }
                        }
                    }
                    if (controller.menuOpen) AutoFocus(focus[1])
                }
            }
        }
    }
}

@Composable
private fun ProfileShortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit = {},
    reducedMotion: Boolean = false,
    height: Dp = 72.dp,
    iconSize: Dp = 28.dp,
    gap: Dp = 6.dp,
) {
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val emphasis by animateFloatAsState(if (focused && keyboard) 1f else 0f, tween(if (reducedMotion) 0 else 140))
    Column(
        modifier.height(height).consoleFocus(shape, onClick, focusScale = 1f, highlightFocus = false, onFocus = {
            focused = it
            onFocus(it)
        }).consoleButtonSurface(shape, emphasis = emphasis).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
    ) {
        Icon(icon, null, Modifier.size(iconSize), tint = Palette.Text)
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
