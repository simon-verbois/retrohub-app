package ovh.verbois.retrohub.ui.console

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.emulation.HintsMode
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.console.control.ControlCenter
import ovh.verbois.retrohub.ui.console.kit.ConsoleTheme
import ovh.verbois.retrohub.ui.console.kit.Hint
import ovh.verbois.retrohub.ui.console.kit.HintBar
import ovh.verbois.retrohub.ui.console.kit.HintBarHeight
import ovh.verbois.retrohub.ui.console.kit.HintRegistry
import ovh.verbois.retrohub.ui.console.kit.LocalHints
import ovh.verbois.retrohub.ui.console.kit.Pad
import ovh.verbois.retrohub.ui.console.kit.RoundIcon
import ovh.verbois.retrohub.ui.game.LaunchChoiceDialog
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.shell.StatusPill
import ovh.verbois.retrohub.ui.shell.profileStatus
import ovh.verbois.retrohub.ui.theme.Palette

/** Landscape has one permanent home. Every tool opens as a full-screen page above it. */
@Composable
fun ConsoleShell(controller: UiController, account: Account, content: @Composable (Route) -> Unit) {
    val settings by controller.container.settings.settings.collectAsState()
    val hints = remember { HintRegistry() }
    CompositionLocalProvider(
        LocalReducedMotion provides (settings.reduceMotion || controller.container.platform.reducedMotion),
        LocalHints provides hints,
        LocalConsoleStyle provides true,
    ) {
        ConsoleTheme { LandscapeContent(controller, account, hints, content) }
    }
}

/** Routes drawn by the home itself; everything else is a page. */
internal fun Route.isBrowsing(): Boolean =
    this == Route.Home || this is Route.Library || this is Route.Folder || this is Route.Game || this == Route.Favorites || this == Route.Downloads

@Composable
private fun LandscapeContent(controller: UiController, account: Account, hints: HintRegistry, content: @Composable (Route) -> Unit) {
    val settings by controller.container.settings.settings.collectAsState()
    val online by controller.container.platform.online.collectAsState()
    val sync by controller.container.sync.status.collectAsState()
    val navigation = remember { NavigationFocus() }
    val pageStates = rememberSaveableStateHolder()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var profileAnchor by remember { mutableStateOf<Rect?>(null) }
    val route = controller.nav.contentRoute
    val page = !route.isBrowsing()
    val active = !page && !controller.searching && !controller.menuOpen && !controller.gameMenu
    val reduced = LocalReducedMotion.current
    val homeOpacity by animateFloatAsState(
        when {
            page -> 0f
            controller.gameMenu -> 0.55f
            controller.menuOpen -> 0.45f
            controller.searching -> 0.15f
            else -> 1f
        },
        tween(if (reduced) 0 else 160),
    )
    var lastPage by remember { mutableStateOf<Route>(Route.Settings()) }
    SideEffect { if (page) lastPage = route }
    val shownPage = if (page) route else lastPage
    val pagePresentation = rememberUpdatedState(shownPage to page)
    val showHints = settings.hints != HintsMode.NEVER
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
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { navigation.content.requestFocus() }
        } else if (!page) {
            focus.clearFocus(force = true)
        }
    }
    CompositionLocalProvider(LocalNavigationFocus provides navigation, LocalReturnHome provides { controller.returnHome() }) {
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
                when {
                    button == null -> false

                    // Inside a page only Back is global; the page owns its other shortcuts.
                    page && button != UiController.Button.B -> true

                    else -> {
                        if (event.type == KeyEventType.KeyUp) {
                            if (button == UiController.Button.B && controller.searching) closeSearch() else controller.onButton(button)
                        }
                        true
                    }
                }
            },
        ) {
            CompositionLocalProvider(LocalScreenActive provides (!page && !controller.menuOpen)) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeOpacity }.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Box(Modifier.fillMaxSize().padding(bottom = if (showHints) HintBarHeight else 0.dp)) {
                        CompositionLocalProvider(LocalScreenActive provides active) { content(Route.Home) }
                    }
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp).padding(horizontal = 20.dp).focusProperties {
                            up = androidx.compose.ui.focus.FocusRequester.Cancel
                            down = navigation.content
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        run {
                            Text(
                                "retrohub.",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp, letterSpacing = (-0.92).sp),
                                fontWeight = FontWeight.ExtraBold,
                            )
                            if (!online) {
                                Icon(Lucide.WifiOff, "Hors connexion", Modifier.size(16.dp), tint = Palette.TextDim)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Up from the home lands on search: the header's entry point.
                        RoundIcon(
                            "Rechercher",
                            { controller.searching = true },
                            Modifier.testTag("header-search-button").focusRequester(navigation.header),
                        ) { Icon(Lucide.Search, null, Modifier.size(22.dp)) }
                        val (_, statusTint, statusLabel) = profileStatus(sync, online)
                        Box(Modifier.onGloballyPositioned { profileAnchor = it.boundsInWindow() }) {
                            RoundIcon(
                                "Compte",
                                { controller.menuOpen = true },
                                Modifier.testTag("header-profile-button").semantics { contentDescription = "Compte et menu · $statusLabel" },
                            ) {
                                ProfileAvatar(
                                    account.username,
                                    account.server,
                                    account.avatar,
                                    Modifier.size(30.dp).testTag("header-profile-avatar"),
                                    showBackground = false,
                                )
                            }
                            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(9.dp).clip(CircleShape).background(statusTint))
                        }
                        StatusPill(controller.container.platform, transparent = true, compact = true)
                    }
                }
            }
            // Full-screen pages: the home fades out, the page zooms in slightly.
            AnimatedVisibility(
                page,
                Modifier.fillMaxSize(),
                enter = fadeIn(tween(if (reduced) 0 else 180)) + scaleIn(tween(if (reduced) 0 else 200), initialScale = 0.97f),
                exit = fadeOut(tween(if (reduced) 0 else 120)) + scaleOut(tween(if (reduced) 0 else 140), targetScale = 0.97f),
            ) {
                Box(
                    Modifier.fillMaxSize().testTag("home-overlay").pointerInput(Unit) { detectTapGestures { } }.windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    val pageNavigation = remember { NavigationFocus() }
                    AnimatedContent(
                        targetState = pagePresentation.value.first,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val direction = if (controller.nav.forward.value) 1 else -1
                            (fadeIn(tween(if (reduced) 0 else 160)) + slideInHorizontally(tween(if (reduced) 0 else 180)) { it / 16 * direction })
                                .togetherWith(
                                    fadeOut(tween(if (reduced) 0 else 100)) + slideOutHorizontally(tween(if (reduced) 0 else 140)) {
                                        -it / 20 * direction
                                    },
                                )
                        },
                        label = "console-page",
                    ) { displayed ->
                        val live = pagePresentation.value
                        val current = live.second && displayed == live.first
                        Box(Modifier.fillMaxSize().then(if (current) Modifier else Modifier.clearAndSetSemantics { })) {
                            CompositionLocalProvider(
                                LocalNavigationFocus provides pageNavigation,
                                LocalScreenActive provides (current && !controller.menuOpen),
                                LocalOverlayControls provides true,
                            ) {
                                pageStates.SaveableStateProvider(displayed.toString()) { key(displayed) { content(displayed) } }
                            }
                        }
                    }
                }
            }
            ovh.verbois.retrohub.ui.console.search.ConsoleSearch(controller)
            // Start on a home game: full-height options panel above the home.
            if (!page) {
                val model by controller.home.collectAsState()
                controller.homeSelection(model).game?.let { game ->
                    Box(Modifier.fillMaxSize()) { ovh.verbois.retrohub.ui.console.home.GameMenu(controller, game) }
                }
            }
            ControlCenter(controller, account)
            // The on-screen keyboard replaces the hint bar, which would otherwise cover what is typed for.
            val keyboardShown = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            if (showHints && !keyboardShown) {
                val shown = hints.current.ifEmpty {
                    if (page) listOf(Hint(Pad.A, "Choisir"), Hint(Pad.B, "Retour") { controller.back() }) else emptyList()
                }
                HintBar(
                    shown,
                    Modifier.align(Alignment.BottomEnd).fillMaxWidth()
                        .background(
                            if (page) {
                                Brush.verticalGradient(listOf(Palette.SkyLow.copy(alpha = 0.7f), Palette.SkyLow.copy(alpha = 0.95f)))
                            } else {
                                Brush.verticalGradient(listOf(Color.Transparent, Palette.SkyLow.copy(alpha = 0.55f)))
                            },
                        )
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .height(HintBarHeight).padding(horizontal = 24.dp),
                )
            }
            AnimatedVisibility(
                controller.toast != null,
                Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 64.dp, end = 20.dp).widthIn(max = 420.dp),
                enter = fadeIn(tween(if (reduced) 0 else 160)) + slideInHorizontally(tween(if (reduced) 0 else 200)) { it / 3 },
                exit = fadeOut(tween(if (reduced) 0 else 140)),
            ) {
                var last by remember { mutableStateOf("") }
                controller.toast?.let { last = it }
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Deep.copy(alpha = 0.96f)).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Lucide.Bell, null, Modifier.size(18.dp))
                    Text(last, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // A single version needs no confirmation: the download starts and shows its progress in place.
        LaunchedEffect(controller.downloadRequest) { if (controller.downloadRequest != null) controller.confirmDownload() }
        LaunchChoiceDialog(controller)
    }
}
