package ovh.verbois.retrohub.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.emulation.HintsMode
import ovh.verbois.retrohub.resources.*
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.home.FolderEditor
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.theme.Palette

/** Landscape has one permanent home. Tools and decisions open above it. */
@Composable
internal fun ConsoleLandscapeShell(controller: UiController, account: Account, content: @Composable (Route) -> Unit) {
    val settings by controller.container.settings.settings.collectAsState()
    val online by controller.container.platform.online.collectAsState()
    val sync by controller.container.sync.status.collectAsState()
    val gamepad by controller.container.platform.gamepadConnected.collectAsState()
    val navigation = remember { NavigationFocus() }
    val search = remember { HeaderSearchState() }
    val overlayStates = rememberSaveableStateHolder()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var profileAnchor by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val route = controller.nav.contentRoute
    val browsing =
        route == Route.Home || route is Route.Library || route is Route.Folder || route is Route.Game || route == Route.Favorites || route == Route.Downloads
    val overlay = !browsing
    val active = !overlay && !controller.searching && !controller.menuOpen && controller.folderEditor == null
    val showHints = settings.hints == HintsMode.ALWAYS || (settings.hints == HintsMode.AUTO && gamepad)
    val closeSearch = {
        keyboard?.hide()
        controller.searching = false
        focus.clearFocus()
    }
    // Old links, search results and rotation all converge on the same home canvas.
    LaunchedEffect(route) {
        when (route) {
            is Route.Library -> {
                route.console?.let { controller.selectHomeColumn("console:$it") }
                controller.nav.section(Section.HOME)
            }

            is Route.Folder -> {
                controller.nav.section(Section.HOME)
            }

            Route.Favorites -> {
                controller.selectHomeColumn("favorites")
                controller.nav.section(Section.HOME)
            }

            Route.Downloads -> {
                controller.nav.section(Section.HOME)
                controller.nav.go(Route.Settings(ovh.verbois.retrohub.ui.nav.SettingsPage.STORAGE))
            }

            is Route.Game -> controller.showGame(route.id)

            else -> Unit
        }
    }
    LaunchedEffect(controller.searching) {
        if (!controller.searching) {
            search.query = ""
            search.hasResults = false
        }
    }
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { navigation.content.requestFocus() }
        } else {
            focus.clearFocus(force = true)
        }
    }
    CompositionLocalProvider(LocalNavigationFocus provides navigation, LocalReturnHome provides { controller.returnHome() }) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).onGloballyPositioned { origin = it.positionInRoot() }.onKeyEvent { event ->
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
            CompositionLocalProvider(LocalScreenActive provides (!overlay && !controller.menuOpen)) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp).focusProperties { down = navigation.content },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!controller.searching) {
                            Text("RetroHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light)
                            if (!online) {
                                Icon(Lucide.WifiOff, "Hors connexion", Modifier.size(16.dp), tint = Palette.TextDim)
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            HeaderSearch(
                                controller,
                                search,
                                40.dp,
                                closeSearch,
                                Modifier.align(Alignment.CenterEnd)
                                    .widthIn(max = 520.dp).fillMaxWidth().height(48.dp).focusRequester(navigation.header),
                            )
                        }
                        val (_, statusTint, statusLabel) = profileStatus(sync, online)
                        Box(
                            Modifier.size(48.dp).testTag("header-profile-button").onGloballyPositioned { profileAnchor = it.boundsInWindow() }
                                .semantics { contentDescription = "Compte et menu · $statusLabel" }
                                .consoleFocus(CircleShape, { controller.menuOpen = true }, highlightFocus = false),
                            contentAlignment = Alignment.Center,
                        ) {
                            ProfileAvatar(
                                account.username,
                                account.server,
                                account.avatar,
                                Modifier.size(28.dp).testTag("header-profile-avatar"),
                                showBackground = false,
                            )
                            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(6.dp).clip(CircleShape).background(statusTint))
                        }
                        StatusPill(controller.container.platform, transparent = true)
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        CompositionLocalProvider(LocalScreenActive provides active) { content(Route.Home) }
                    }
                    if (showHints) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                "A" to "Choisir",
                                "Y" to "Rechercher",
                                "LB / RB" to "Dossiers",
                                "Options" to "Actions du jeu",
                            ).forEach { (key, label) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(key, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                                }
                            }
                        }
                    }
                }
            }
            if (controller.searching) {
                Box(Modifier.fillMaxSize().padding(top = 48.dp).pointerInput(Unit) { detectTapGestures { closeSearch() } })
            }
            if (controller.searching && search.query.isNotBlank() && search.bounds.width > 0f) {
                val height = (maxHeight - with(density) { (search.bounds.bottom - origin.y).toDp() } - 12.dp).coerceAtLeast(0.dp)
                SearchDropdown(
                    controller,
                    search,
                    closeSearch,
                    Modifier.offset {
                        IntOffset(
                            (search.bounds.left - origin.x).toInt(),
                            (search.bounds.bottom - origin.y).toInt() + with(density) { 6.dp.roundToPx() },
                        )
                    }
                        .width(with(density) { search.bounds.width.toDp() }).heightIn(max = height),
                )
            }
            AccountMenu(controller, account, profileAnchor, onClose = { controller.back() })
            controller.toast?.let { message ->
                Text(
                    message,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 52.dp).clip(RoundedCornerShape(12.dp))
                        .background(Palette.Deep).padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (overlay) {
                Dialog(onDismissRequest = { controller.back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    val panelNavigation = remember { NavigationFocus() }
                    Box(
                        Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.92f).widthIn(max = 1200.dp).testTag("home-overlay")
                            .clip(RoundedCornerShape(20.dp)).background(Palette.Deep).controllerDialog({ controller.back() }),
                    ) {
                        CompositionLocalProvider(LocalNavigationFocus provides panelNavigation, LocalScreenActive provides true) {
                            overlayStates.SaveableStateProvider(route.toString()) { key(route) { content(route) } }
                        }
                    }
                }
            }
        }
        controller.folderEditor?.let { FolderEditor(controller, it) }
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
    }
}
