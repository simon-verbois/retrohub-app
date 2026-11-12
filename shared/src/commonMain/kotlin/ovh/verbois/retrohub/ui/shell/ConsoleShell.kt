package ovh.verbois.retrohub.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.core.Platform
import ovh.verbois.retrohub.core.PowerStatus
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.emulation.HintsMode
import ovh.verbois.retrohub.resources.Res
import ovh.verbois.retrohub.resources.action_cancel
import ovh.verbois.retrohub.resources.download_confirm_action
import ovh.verbois.retrohub.resources.download_confirm_description
import ovh.verbois.retrohub.resources.download_confirm_title
import ovh.verbois.retrohub.sync.SyncPhase
import ovh.verbois.retrohub.sync.SyncStatus
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.ConsoleMenuItem
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.ProfileAvatar
import ovh.verbois.retrohub.ui.components.consoleButtonSurface
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.components.controllerDialog
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.time.Clock

private val Pill = RoundedCornerShape(percent = 50)

@Composable
fun ConsoleShell(controller: UiController, account: Account, content: @Composable (Route) -> Unit) {
    val container = controller.container
    val sync by container.sync.status.collectAsState()
    val online by container.platform.online.collectAsState()
    val gamepad by container.platform.gamepadConnected.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val contentRoute = controller.nav.contentRoute
    val screenStates = rememberSaveableStateHolder()
    val searchState = remember { HeaderSearchState() }
    var profileAnchor by remember { mutableStateOf<Rect?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val navigationFocus = remember { ovh.verbois.retrohub.ui.components.NavigationFocus() }
    var restoreHeaderFocus by remember { mutableStateOf(false) }
    val closeSearch = {
        keyboard?.hide()
        focus.clearFocus()
        controller.searching = false
        restoreHeaderFocus = true
        Unit
    }
    LaunchedEffect(controller.searching, restoreHeaderFocus) {
        if (!controller.searching && restoreHeaderFocus) {
            withFrameNanos { }
            runCatching { navigationFocus.header.requestFocus() }
            restoreHeaderFocus = false
        }
    }
    LaunchedEffect(controller.searching, searchState.fieldFocused, searchState.resultsFocused) {
        if (!controller.searching) {
            searchState.query = ""
            searchState.hasResults = false
            if (searchState.hadFocus) keyboard?.hide()
            searchState.hadFocus = false
        } else if (searchState.fieldFocused || searchState.resultsFocused) {
            searchState.hadFocus = true
        } else if (searchState.hadFocus) {
            delay(60)
            if (!searchState.fieldFocused && !searchState.resultsFocused) controller.searching = false
        }
    }
    controller.downloadRequest?.let { game ->
        ConfirmDialog(
            stringResource(Res.string.download_confirm_title),
            stringResource(Res.string.download_confirm_description, game.title, formatBytes(game.remote.rom?.size ?: 0)),
            stringResource(Res.string.download_confirm_action),
            onConfirm = { controller.confirmDownload() },
            onDismiss = { controller.dismissDownload() },
            dismiss = stringResource(Res.string.action_cancel),
        )
    }
    controller.launchIssue?.let { issue ->
        ConfirmDialog(
            "Quelle partie reprendre ?",
            "Plusieurs progressions sont disponibles. Choisissez celle à garder pour continuer.",
            "Choisir",
            onConfirm = {
                controller.launchIssue = null
                controller.nav.go(issue)
            },
            onDismiss = { controller.launchIssue = null },
            focusDismiss = true,
        )
    }
    val showHints = when (settings.hints) {
        HintsMode.ALWAYS -> true
        HintsMode.NEVER -> false
        HintsMode.AUTO -> gamepad
    }
    androidx.compose.runtime.CompositionLocalProvider(ovh.verbois.retrohub.ui.components.LocalNavigationFocus provides navigationFocus) {
        BoxWithConstraints(
            Modifier.fillMaxSize().onKeyEvent { event ->
                val button = when (event.key) {
                    Key.ButtonB, Key.Escape -> UiController.Button.B
                    Key.ButtonY -> UiController.Button.Y
                    Key.ButtonL1 -> UiController.Button.LB
                    Key.ButtonR1 -> UiController.Button.RB
                    Key.ButtonStart, Key.Menu -> UiController.Button.START
                    Key.ButtonSelect -> UiController.Button.SELECT
                    else -> null
                }
                if (button == null) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) {
                        if (button == UiController.Button.B && controller.searching) closeSearch() else controller.onButton(button)
                    }
                    true
                }
            },
        ) {
            // Home covers can scroll behind the cutout; fixed controls stay in the safe area.
            val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
                if (Section.of(contentRoute) != null) {
                    TopBar(
                        controller,
                        account,
                        sync,
                        online,
                        compact = controller.portrait,
                        section = Section.of(contentRoute),
                        modifier = Modifier.windowInsetsPadding(horizontalInsets),
                        searchState = searchState,
                        closeSearch = closeSearch,
                        onProfileBounds = { profileAnchor = it },
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = if (showHints) 32.dp else 0.dp)) {
                    AnimatedContent(
                        targetState = contentRoute,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                        label = "screen",
                    ) { target ->
                        screenStates.SaveableStateProvider(target.toString()) {
                            val insets = if (target == Route.Home && settings.useDisplayCutout) WindowInsets(0) else horizontalInsets
                            Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
                                content(target)
                            }
                        }
                    }
                    if (controller.searching) {
                        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { closeSearch() } })
                    }
                    Box(Modifier.fillMaxSize().windowInsetsPadding(horizontalInsets)) {
                        AccountMenu(
                            controller,
                            account,
                            profileAnchor.takeIf {
                                Section.of(contentRoute) !=
                                    null
                            },
                            onClose = { controller.back() },
                        )
                    }
                }
            }
            if (controller.searching && Section.of(contentRoute) == null) {
                TopBar(
                    controller, account, sync, online, controller.portrait, null,
                    Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing), searchState, closeSearch,
                )
            }
            if (controller.searching && searchState.query.isNotBlank() && searchState.bounds.width > 0f) {
                val dropdownHeight = (maxHeight - with(density) { searchState.bounds.bottom.toDp() } - 12.dp).coerceAtLeast(0.dp)
                SearchDropdown(
                    controller,
                    searchState,
                    closeSearch,
                    Modifier.offset { IntOffset(searchState.bounds.left.toInt(), searchState.bounds.bottom.toInt() + with(density) { 6.dp.roundToPx() }) }
                        .width(with(density) { searchState.bounds.width.toDp() }).heightIn(max = dropdownHeight),
                )
            }
            if (showHints) {
                Box(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing)) { HintsBar() }
            }
            AnimatedVisibility(
                controller.toast != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showHints) 56.dp else 24.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                GlassPanel(Modifier.widthIn(max = 560.dp).padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(controller.toast ?: "", Modifier.background(Palette.BackgroundHigh).padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    controller: UiController,
    account: Account,
    sync: SyncStatus,
    online: Boolean,
    compact: Boolean,
    section: Section?,
    modifier: Modifier = Modifier,
    searchState: HeaderSearchState,
    closeSearch: () -> Unit,
    onProfileBounds: (Rect) -> Unit = {},
) {
    val tree = ovh.verbois.retrohub.ui.components.LocalNavigationFocus.current
    val diameter = statusBubbleSize()
    val touchDiameter = diameter.coerceAtLeast(48.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = touchDiameter + 8.dp).padding(horizontal = if (compact) 16.dp else 20.dp)
            .focusGroup().focusProperties {
                up = FocusRequester.Cancel
                down = tree.content
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(touchDiameter)) {
            if (!controller.searching) {
                if (compact) {
                    CompactSectionMenu(controller, section, Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(end = touchDiameter + 8.dp))
                } else {
                    SectionTabs(controller, section, Modifier.fillMaxWidth().padding(end = touchDiameter + 8.dp))
                }
            }
            HeaderSearch(controller, searchState, diameter, closeSearch, Modifier.fillMaxSize())
        }
        val (icon, tint, label) = profileStatus(sync, online)
        Box(
            Modifier.size(touchDiameter).onGloballyPositioned { onProfileBounds(it.boundsInWindow()) }.testTag("header-profile-button").then(
                if (section ==
                    Section.USER
                ) {
                    Modifier.focusRequester(tree.header)
                } else {
                    Modifier
                },
            )
                .focusProperties { right = FocusRequester.Cancel }
                .semantics { contentDescription = "Compte et menu · $label" }
                .consoleFocus(CircleShape, { controller.menuOpen = true }, focusScale = 1.08f, highlightFocus = false),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(diameter)) {
                ProfileAvatar(account.username, account.server, account.avatar, Modifier.fillMaxSize().testTag("header-profile-avatar"), showBackground = false)
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(9.dp)
                        .clip(CircleShape).background(tint).border(1.dp, Palette.SkyLow, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(7.dp), tint = Palette.SkyLow)
                }
            }
        }
        StatusPill(controller.container.platform)
    }
}

internal fun profileStatus(sync: SyncStatus, online: Boolean): Triple<ImageVector, Color, String> = when {
    !online || sync.phase == SyncPhase.SIGNED_OUT -> Triple(Lucide.X, Palette.TextFaint, "Déconnecté")
    sync.phase == SyncPhase.OFFLINE -> Triple(Lucide.CircleAlert, Palette.Warning, "Serveur injoignable")
    sync.phase == SyncPhase.ERROR -> Triple(Lucide.CircleAlert, Palette.Danger, "Erreur de synchronisation")
    sync.conflicts > 0 -> Triple(Lucide.CircleAlert, Palette.Warning, "Une sauvegarde attend votre choix")
    sync.phase == SyncPhase.MAINTENANCE -> Triple(Lucide.CircleAlert, Palette.Warning, "Serveur en maintenance")
    sync.phase == SyncPhase.SYNCING -> Triple(Lucide.RefreshCw, Palette.Success, "Synchronisation en cours")
    sync.pendingSaves > 0 || sync.pendingSessions > 0 -> Triple(Lucide.Clock, Palette.Warning, "Synchronisation en attente")
    else -> Triple(Lucide.Check, Palette.Success, "Tout est synchronisé")
}

@Composable
fun SectionTabs(controller: UiController, section: Section?, modifier: Modifier = Modifier) {
    val tree = ovh.verbois.retrohub.ui.components.LocalNavigationFocus.current
    Row(
        modifier.horizontalScroll(rememberScrollState()).focusGroup()
            .focusProperties {
                up = FocusRequester.Cancel
                down = tree.content
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Section.entries.filter { it.tab }.forEach { item ->
            SectionTab(
                item.title,
                item == section,
                Modifier.then(if (item == section) Modifier.focusRequester(tree.header) else Modifier)
                    .focusProperties { if (item == Section.HOME) left = FocusRequester.Cancel },
            ) {
                controller.searching = false
                controller.nav.section(item)
            }
        }
    }
}

/** Portrait keeps the four destinations in one compact header control. */
@Composable
private fun CompactSectionMenu(controller: UiController, section: Section?, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Section?>(null) }
    val tree = ovh.verbois.retrohub.ui.components.LocalNavigationFocus.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(pending, open, windowFocused) {
        val target = pending
        if (target != null && !open && windowFocused) {
            controller.nav.section(target)
            // The popup restores its opener; hand focus to the new page after its entry exists.
            withFrameNanos { }
            withFrameNanos { }
            if (controller.nav.current == target.route) runCatching { tree.content.requestFocus() }
            pending = null
        }
    }
    val first = remember { FocusRequester() }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("section-menu")
                .focusRequester(tree.header).consoleFocus(Pill, { open = true }, label = "Changer de section")
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                (section ?: Section.HOME).title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Lucide.ChevronDown, null, Modifier.size(16.dp), tint = Palette.Text)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.controllerDialog({ open = false }, dismissOnStart = true),
            containerColor = Palette.Deep,
            shape = RoundedCornerShape(18.dp),
        ) {
            Section.entries.filter { it.tab }.forEach { item ->
                ConsoleMenuItem(
                    item.title,
                    modifier = Modifier.testTag("section-choice-${item.name.lowercase()}").then(
                        if (item ==
                            (section ?: Section.HOME)
                        ) {
                            Modifier.focusRequester(first)
                        } else {
                            Modifier
                        },
                    ),
                    selected = item == section,
                    onClick = {
                        open = false
                        pending = item
                    },
                )
            }
            if (open) AutoFocus(first)
        }
    }
}

@Composable
private fun SectionTab(title: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.consoleFocus(Pill, onClick).padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
            color = Palette.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun statusBubbleSize() = with(LocalDensity.current) { 18.sp.toDp() }.plus(12.dp).coerceAtLeast(40.dp)

@Composable
internal fun StatusPill(platform: Platform) {
    val textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power by produceState(PowerStatus(), platform, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = platform.powerStatus()
                delay(15_000)
            }
        }
    }
    val now by produceState(Clock.System.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = Clock.System.now()
                delay(15_000)
            }
        }
    }
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    Row(
        Modifier.heightIn(
            min = statusBubbleSize(),
        ).testTag("header-status").semantics(mergeDescendants = true) {}
            .consoleButtonSurface(Pill).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}", style = textStyle, maxLines = 1)
        power.phone?.let { level ->
            Box(Modifier.width(1.dp).height(14.dp).background(Palette.Line))
            Icon(
                if (level.charging) Lucide.Zap else Lucide.Battery,
                null,
                Modifier.size(14.dp),
                tint = if (level.percent <=
                    20
                ) {
                    Palette.Warning
                } else {
                    Palette.Text
                },
            )
            Text(
                "${level.percent} %",
                Modifier.semantics {
                    contentDescription =
                        "Batterie du téléphone : ${level.percent} %" + if (level.charging) ", en charge" else ""
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        power.controllers.take(1).forEach { controller ->
            Box(Modifier.width(1.dp).height(14.dp).background(Palette.Line))
            Icon(Lucide.Gamepad2, null, Modifier.size(14.dp), tint = Palette.Text)
            Text(
                "${controller.level.percent} %",
                Modifier.semantics {
                    contentDescription = "Batterie de ${controller.name} : ${controller.level.percent} %"
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** Small transparent overlay: each controller symbol is paired with a familiar action icon. */
@Composable
fun HintsBar(extra: List<Pair<String, String>> = emptyList()) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hint("A", Palette.PadA, Lucide.Check, "Sélectionner")
        Hint("B", Palette.PadB, Lucide.ArrowLeft, "Retour")
        Hint("Y", Palette.PadY, Lucide.Search, "Rechercher")
        Hint("LB RB", Palette.TextDim, Lucide.ChevronRight, "Sections")
        Hint("☰", Palette.TextDim, Lucide.UserRound, "Profil")
        extra.forEach { (key, label) -> Text("$key $label", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun Hint(key: String, color: Color, icon: ImageVector, label: String) {
    Row(
        Modifier.semantics {
            contentDescription = "$key : $label"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            key,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.drawBehind {
                drawRoundRect(Palette.SkyLow.copy(alpha = 0.85f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Icon(icon, null, Modifier.size(14.dp), tint = Palette.TextDim)
    }
}
