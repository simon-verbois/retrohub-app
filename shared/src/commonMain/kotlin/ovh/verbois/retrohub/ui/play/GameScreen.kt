package ovh.verbois.retrohub.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.core.formatDate
import ovh.verbois.retrohub.core.formatRelative
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.emulation.ConsoleSettings
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.ControlsPlacement
import ovh.verbois.retrohub.emulation.CoreOptions
import ovh.verbois.retrohub.emulation.DsLayout
import ovh.verbois.retrohub.emulation.EmulatorEngine
import ovh.verbois.retrohub.emulation.GameLayouts
import ovh.verbois.retrohub.emulation.GameRotation
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.PortraitPosition
import ovh.verbois.retrohub.emulation.TouchPreset
import ovh.verbois.retrohub.game.Cheat
import ovh.verbois.retrohub.game.GameSession
import ovh.verbois.retrohub.game.InputRouter
import ovh.verbois.retrohub.game.SlotInfo
import ovh.verbois.retrohub.sync.SyncPhase
import ovh.verbois.retrohub.sync.SyncStatus
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.consoleButtonSurface
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

private enum class MenuPage { MAIN, SAVE, LOAD, SCREENS, CHEATS, OPTIONS }

/**
 * L’écran de jeu partagé : image du cœur placée selon l’orientation, commandes tactiles de la
 * console (masquées avec une manette), menu pause (états, avance rapide, écrans DS, quitter).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    session: GameSession,
    engine: EmulatorEngine,
    gamepadConnected: StateFlow<Boolean>,
    syncStatus: StateFlow<SyncStatus>,
    menuRequests: Flow<Unit>,
    backRequests: Flow<Unit> = emptyFlow(),
    haptic: () -> Unit,
    onQuit: () -> Unit,
    /** Enregistre un changement des réglages de la console (options avancées, commandes tactiles). */
    onConsoleSettings: ((ConsoleSettings) -> ConsoleSettings) -> Unit = {},
    onCheats: (List<Cheat>) -> Unit = {},
    onRumble: (Float) -> Unit = {},
    onRotation: (GameRotation) -> Unit = {},
    surface: @Composable () -> Unit,
) {
    val plan = session.plan
    val spec = Consoles[plan.console]!!
    val ui by session.ui.collectAsState()
    val gamepad by gamepadConnected.collectAsState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(MenuPage.MAIN) }
    var dsPortrait by remember { mutableStateOf(plan.dsLayoutPortrait) }
    var dsLandscape by remember { mutableStateOf(plan.dsLayoutLandscape) }
    var rotation by remember { mutableStateOf(plan.rotation) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var overwrite by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf(false) }
    var touchLayout by remember { mutableStateOf(plan.touchLayout) }
    var touchPreset by remember { mutableStateOf(plan.touchPreset) }
    var cheats by remember { mutableStateOf(plan.cheats) }
    val router =
        remember {
            InputRouter(
                engine,
                scope,
                plan.turbo.mapNotNull { name -> PadKey.entries.firstOrNull { it.name == name } }.toSet(),
                plan.stickDeadzone,
                plan.stickSensitivity,
            )
        }
    DisposableEffect(Unit) { onDispose { router.releaseAll() } }
    LaunchedEffect(ui.paused, gamepad) { router.releaseAll() }
    if (plan.rumble) LaunchedEffect(Unit) { engine.rumble.collect { if (it > 0.05f) onRumble(it) } }
    // Le compteur d’images change 60 fois par seconde : il n’est jamais lu comme état de l’écran.
    LaunchedEffect(Unit) {
        engine.frames.first { it > 0 }
        session.onFirstFrame()
    }
    LaunchedEffect(Unit) {
        menuRequests.collect {
            if (session.ui.value.paused) {
                session.resume()
            } else {
                page = MenuPage.MAIN
                session.pause()
            }
        }
    }
    LaunchedEffect(ui.message) {
        if (ui.message != null) {
            delay(3500)
            session.flash(null)
        }
    }
    val showTouch = !(gamepad && plan.hideTouchWithGamepad)
    val handleBack by rememberUpdatedState {
        when {
            confirmQuit -> confirmQuit = false
            confirmReset -> confirmReset = false
            overwrite != null -> overwrite = null
            editing -> editing = false
            page != MenuPage.MAIN -> page = MenuPage.MAIN
            else -> session.resume()
        }
    }
    LaunchedEffect(backRequests) { backRequests.collect { handleBack() } }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).onKeyEvent { event ->
            if (ui.paused && event.key in listOf(Key.ButtonB, Key.Escape)) {
                if (event.type == KeyEventType.KeyUp) handleBack()
                true
            } else {
                false
            }
        }.then(if (!plan.useDisplayCutout) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier),
    ) {
        val density = LocalDensity.current
        val portrait = maxHeight > maxWidth
        val dsLayout = if (portrait) dsPortrait else dsLandscape
        val aspect = rotation.aspect(if (spec.dualScreen) GameLayouts.dsAspect(dsLayout) else plan.displayAspect ?: spec.aspect)
        val pad = padSpec(spec.pad).let { if (plan.analog) it.copy(leftStick = true) else it }
        val layout =
            with(density) {
                GameLayouts.compute(
                    maxWidth.toPx(),
                    maxHeight.toPx(),
                    aspect,
                    showTouch || editing,
                    native = if (plan.integerScale) Consoles.nativeSize(plan.console, if (spec.dualScreen) dsLayout else null)?.let(rotation::native) else null,
                    portraitCentered = plan.portraitPosition == PortraitPosition.CENTER,
                    controlsUnit = (48.dp * plan.touchScale).toPx(),
                    portraitControlRows = if (pad.leftStick && pad.dpad) 6.6f else 5.5f,
                    preset = touchPreset,
                )
            }
        val cutout = WindowInsets.displayCutout
        val direction = LocalLayoutDirection.current
        val controlsLeft = if (plan.useDisplayCutout) cutout.getLeft(density, direction).toFloat() else 0f
        val controlsTop = if (plan.useDisplayCutout) cutout.getTop(density).toFloat() else 0f
        val controlsRight = if (plan.useDisplayCutout) cutout.getRight(density, direction).toFloat() else 0f
        val controlsBottom = if (plan.useDisplayCutout) cutout.getBottom(density).toFloat() else 0f
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        val safeGameBottom = (
            (layout.game.bottom * screenHeight - controlsTop) / (screenHeight - controlsTop - controlsBottom).coerceAtLeast(
                1f,
            )
            ).coerceIn(0f, 1f)
        val safeGameLeft = ((layout.game.left * screenWidth - controlsLeft) / (screenWidth - controlsLeft - controlsRight).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val controlsModifier = if (plan.useDisplayCutout) Modifier.windowInsetsPadding(cutout) else Modifier
        val orientationSuffix = if (portrait) "@p" else "@l"
        val suffix = "@${touchPreset.name.lowercase()}$orientationSuffix"
        val custom = touchLayout.filterKeys { it.endsWith(suffix) }.mapKeys { it.key.removeSuffix(suffix) }
        val groupLayout = if (custom.isEmpty() && touchPreset == TouchPreset.COMFORT) {
            touchLayout.filterKeys { it.endsWith(orientationSuffix) && it.count { char -> char == '@' } == 1 }
                .mapKeys { it.key.removeSuffix(orientationSuffix) }
        } else {
            custom
        }
        LaunchedEffect(touchPreset, portrait) { router.releaseAll() }
        LaunchedEffect(layout.game) { engine.setViewport(layout.game) }
        LaunchedEffect(rotation, dsLayout) {
            if (spec.dualScreen) engine.setOptions(mapOf("melonds_screen_layout" to CoreOptions.dsLayoutValue(dsLayout, plan.dsSwapScreens)))
            // Let the core publish its new geometry before applying the rotated aspect.
            val frame = engine.frames.value
            engine.frames.first { it > frame }
            engine.setRotation(rotation, aspect)
        }
        surface()
        if (showTouch || editing) {
            // Cadre de console portable autour de l’image : les commandes ne recouvrent pas le jeu.
            val game = layout.game
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val frame = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Palette.SkyLow, Palette.Deep.copy(alpha = 1f)))
                val left = game.left * size.width
                val top = game.top * size.height
                val right = game.right * size.width
                val bottom = game.bottom * size.height
                drawRect(frame, size = androidx.compose.ui.geometry.Size(size.width, top))
                drawRect(
                    frame,
                    androidx.compose.ui.geometry.Offset(0f, bottom),
                    androidx.compose.ui.geometry.Size(
                        size.width,
                        size.height - bottom,
                    ),
                )
                drawRect(frame, androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(left, bottom - top))
                drawRect(
                    frame,
                    androidx.compose.ui.geometry.Offset(right, top),
                    androidx.compose.ui.geometry.Size(
                        size.width - right,
                        bottom - top,
                    ),
                )
            }
        }
        key(touchPreset, portrait, dsLayout) {
            if (editing) {
                TouchControls(
                    padSpec(spec.pad).let { if (plan.analog) it.copy(leftStick = true) else it },
                    layout.controls,
                    maxOf(plan.touchOpacity, 0.8f),
                    plan.touchScale,
                    onKey = { _, _ -> },
                    onStick = { _, _, _ -> },
                    onScreenTouch = {},
                    onHaptic = {},
                    modifier = controlsModifier,
                    gameBottom = safeGameBottom,
                    gameLeft = safeGameLeft,
                    layout = groupLayout,
                    editing = true,
                    onLayout = { id, place -> touchLayout = touchLayout + (id + suffix to place) },
                )
                TouchEditorBar(
                    Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(top = 8.dp),
                    onReset = {
                        touchLayout =
                            touchLayout.filterKeys {
                                !it.endsWith(suffix) &&
                                    !(touchPreset == TouchPreset.COMFORT && it.endsWith(orientationSuffix) && it.count { char -> char == '@' } == 1)
                            }
                    },
                    onDone = {
                        editing = false
                        val saved = touchLayout
                        onConsoleSettings { it.copy(touchLayout = saved) }
                        session.resume()
                    },
                )
            } else if (showTouch && !ui.paused) {
                TouchControls(
                    padSpec(spec.pad).let { if (plan.analog) it.copy(leftStick = true) else it },
                    layout.controls,
                    plan.touchOpacity,
                    plan.touchScale,
                    onKey = { key, down -> router.key(key, down) },
                    onStick = { stick, x, y -> router.stick(stick, x, y) },
                    onScreenTouch = { point ->
                        val x = point?.x?.plus(controlsLeft)
                        val y = point?.y?.plus(controlsTop)
                        val game = layout.game
                        if (x == null || y == null || x < game.left * screenWidth || x > game.right * screenWidth || y < game.top * screenHeight ||
                            y > game.bottom * screenHeight
                        ) {
                            engine.touch(null, null)
                        } else {
                            val (u, v) = rotation.unrotate((x / screenWidth - game.left) / game.width, (y / screenHeight - game.top) / game.height)
                            engine.touch((game.left + u * game.width) * screenWidth, (game.top + v * game.height) * screenHeight)
                        }
                    },
                    onHaptic = { if (plan.haptics) haptic() },
                    modifier = controlsModifier,
                    gameBottom = safeGameBottom,
                    gameLeft = safeGameLeft,
                    layout = groupLayout,
                )
            }
        }
        if (!ui.paused && !editing) {
            Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 4.dp, end = 12.dp)) {
                InGameButtons(ui.fastForward, onMenu = {
                    page = MenuPage.MAIN
                    session.pause()
                }, onFastForward = { session.toggleFastForward() })
            }
        }
        if (plan.showFps) FpsText(engine.frames, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp))
        AnimatedVisibility(
            ui.message != null,
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(top = 56.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                ui.message ?: "",
                Modifier.widthIn(
                    max = 520.dp,
                ).padding(
                    horizontal = 16.dp,
                ).clip(RoundedCornerShape(16.dp)).background(Palette.Deep).padding(horizontal = 18.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!ui.started) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(plan.title, style = MaterialTheme.typography.headlineMedium)
                    Text("Démarrage de ${spec.name}…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
            }
        }
        AnimatedVisibility(
            ui.paused && !editing,
            enter = fadeIn() + slideInHorizontally { -it / 6 },
            exit =
            fadeOut() + slideOutHorizontally { -it / 6 },
        ) {
            PauseMenu(
                session,
                ui.fastForward,
                ui.slots,
                page,
                { page = it },
                dualScreen = spec.dualScreen,
                dsLayout = dsLayout,
                onDsLayout = { if (portrait) dsPortrait = it else dsLandscape = it },
                rotation = rotation,
                touchPreset = touchPreset,
                onTouchPreset = { value ->
                    touchPreset = value
                    onConsoleSettings { it.copy(touchPreset = value) }
                },
                onRotation = { value ->
                    rotation = value
                    onRotation(value)
                },
                onReset = { confirmReset = true },
                onSaveSlot = { slot ->
                    if (plan.confirmOverwrite && !slot.empty) {
                        overwrite = slot.slot
                    } else {
                        scope.launch { if (session.saveState(slot.slot)) session.resume() }
                    }
                },
                onEditControls = if (showTouch) ({ editing = true }) else null,
                cheats = cheats,
                onCheats = { updated ->
                    cheats = updated
                    session.applyCheats(updated)
                    onCheats(updated)
                },
                engine = engine,
                onOption = { key, value ->
                    engine.setOptions(mapOf(key to value))
                    onConsoleSettings { it.copy(options = it.options + (key to value)) }
                },
                onQuit = { confirmQuit = true },
            )
        }
        overwrite?.let { slot ->
            ConfirmDialog(
                "Remplacer l’emplacement $slot ?",
                "La partie de cet emplacement sera remplacée.",
                "Remplacer",
                onConfirm = {
                    overwrite = null
                    scope.launch { if (session.saveState(slot)) session.resume() }
                },
                onDismiss = { overwrite = null },
            )
        }
        if (confirmQuit) {
            ConfirmDialog(
                "Quitter la partie ?",
                "Votre partie est sauvegardée automatiquement.",
                "Quitter",
                onConfirm = {
                    confirmQuit = false
                    scope.launch {
                        session.finish()
                        onQuit()
                    }
                },
                onDismiss = { confirmQuit = false },
            )
        }
        if (confirmReset) {
            ConfirmDialog(
                "Redémarrer le jeu ?",
                "Pensez à sauvegarder avant.",
                "Redémarrer",
                onConfirm = {
                    confirmReset = false
                    scope.launch { session.reset() }
                },
                onDismiss = { confirmReset = false },
            )
        }
    }
}

private fun cheatSummary(cheats: List<Cheat>): String {
    val active = cheats.count { it.enabled }
    return when {
        cheats.isEmpty() -> "Aucun code"
        active == 0 -> "Aucun actif"
        active == 1 -> "1 actif"
        else -> "$active actifs"
    }
}

@Composable
private fun TouchEditorBar(modifier: Modifier, onReset: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Deep).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Glissez pour déplacer, pincez pour agrandir", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Par défaut", onReset, icon = Lucide.RotateCcw, height = 40.dp)
            GlassButton("Terminé", onDone, icon = Lucide.Check, height = 40.dp)
        }
    }
}

@Composable
private fun InGameButtons(fastForward: Boolean, onMenu: () -> Unit, onFastForward: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.size(width = 96.dp, height = 48.dp).testTag("game-utility-dock").semantics { contentDescription = "Commandes du jeu" }) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(32.dp).consoleButtonSurface(shape))
        Row(Modifier.fillMaxSize()) {
            SmallRound(Lucide.Menu, "Menu du jeu", onMenu)
            SmallRound(Lucide.FastForward, if (fastForward) "Vitesse normale" else "Avance rapide", onFastForward, active = fastForward)
        }
    }
}

@Composable
private fun SmallRound(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    Box(
        Modifier.size(48.dp).semantics { contentDescription = label }.consoleFocus(CircleShape, onClick, focusScale = 1f),
        contentAlignment = Alignment.Center,
    ) {
        if (active) Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.Accent))
        Icon(icon, null, Modifier.size(16.dp), tint = if (active) Palette.OnAccent else Palette.Text)
    }
}

@Composable
private fun FpsText(frames: StateFlow<Long>, modifier: Modifier) {
    var fps by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        var reference = frames.value
        while (true) {
            delay(1000)
            fps = frames.value - reference
            reference = frames.value
        }
    }
    Text(
        "$fps i/s",
        modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Success,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseMenu(
    session: GameSession,
    fastForward: Boolean,
    slots: List<SlotInfo>,
    page: MenuPage,
    onPage: (MenuPage) -> Unit,
    dualScreen: Boolean,
    dsLayout: DsLayout,
    onDsLayout: (DsLayout) -> Unit,
    rotation: GameRotation,
    touchPreset: TouchPreset,
    onTouchPreset: (TouchPreset) -> Unit,
    onRotation: (GameRotation) -> Unit,
    onReset: () -> Unit,
    onSaveSlot: (SlotInfo) -> Unit,
    onEditControls: (() -> Unit)?,
    cheats: List<Cheat>,
    onCheats: (List<Cheat>) -> Unit,
    engine: EmulatorEngine,
    onOption: (String, String) -> Unit,
    onQuit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    // Toucher à côté du menu le ferme et reprend la partie.
    Box(
        Modifier.fillMaxSize().background(Palette.Scrim.copy(alpha = 0.7f))
            .pointerInput(Unit) { detectTapGestures { session.resume() } },
    ) {
        Column(
            Modifier.fillMaxHeight().widthIn(
                max = 600.dp,
            ).fillMaxWidth()
                // Le panneau garde ses touchers pour lui.
                .pointerInput(Unit) { detectTapGestures { } }
                .background(
                    Palette.Deep,
                ).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(Consoles.name(session.plan.console).uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            Text(session.plan.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when (page) {
                MenuPage.MAIN -> {
                    MenuEntry(Lucide.Play, "Reprendre", Modifier.focusRequester(first)) { session.resume() }
                    MenuEntry(Lucide.Save, "Sauvegarder la partie") { onPage(MenuPage.SAVE) }
                    MenuEntry(Lucide.FolderOpen, "Charger une partie") { onPage(MenuPage.LOAD) }
                    MenuEntry(Lucide.FastForward, if (fastForward) "Avance rapide : activée" else "Avance rapide") {
                        session.toggleFastForward()
                    }
                    MenuEntry(Lucide.Monitor, "Image et écrans") { onPage(MenuPage.SCREENS) }
                    if (onEditControls != null) MenuEntry(Lucide.Gamepad2, "Déplacer les commandes", onClick = onEditControls)
                    MenuEntry(Lucide.Sparkles, "Codes de triche", subtitle = cheatSummary(cheats)) { onPage(MenuPage.CHEATS) }
                    MenuEntry(Lucide.SlidersHorizontal, "Options de l’émulateur") { onPage(MenuPage.OPTIONS) }
                    MenuEntry(Lucide.RotateCcw, "Redémarrer le jeu", onClick = onReset)
                    MenuEntry(Lucide.LogOut, "Quitter", tint = Palette.Danger, onClick = onQuit)
                }

                MenuPage.SAVE, MenuPage.LOAD -> {
                    BackEntry(Modifier.focusRequester(first)) { onPage(MenuPage.MAIN) }
                    Text(
                        if (page ==
                            MenuPage.SAVE
                        ) {
                            "Choisissez un emplacement"
                        } else {
                            "Choisissez la partie à charger"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        slots.filter { page == MenuPage.LOAD || it.slot > 0 }.forEach { slot ->
                            SlotCard(slot, enabled = page == MenuPage.SAVE || !slot.empty) {
                                if (page == MenuPage.SAVE) {
                                    onSaveSlot(slot)
                                } else {
                                    scope.launch { if (session.loadState(slot.slot)) session.resume() }
                                }
                            }
                        }
                    }
                }

                MenuPage.CHEATS -> {
                    BackEntry(Modifier.focusRequester(first)) { onPage(MenuPage.MAIN) }
                    if (cheats.isEmpty()) {
                        Text(
                            "Aucun code. Ajoutez-en dans les options du jeu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextDim,
                        )
                    }
                    cheats.forEachIndexed { index, cheat ->
                        MenuEntry(
                            if (cheat.enabled) Lucide.CircleCheck else Lucide.Circle,
                            cheat.name.ifBlank { "Code ${index + 1}" },
                            subtitle = if (cheat.enabled) "Actif" else "Inactif",
                        ) {
                            onCheats(cheats.mapIndexed { i, c -> if (i == index) c.copy(enabled = !c.enabled) else c })
                        }
                    }
                }

                MenuPage.OPTIONS -> {
                    BackEntry(Modifier.focusRequester(first)) { onPage(MenuPage.MAIN) }
                    var variables by remember { mutableStateOf(engine.variables()) }
                    if (variables.isEmpty()) {
                        Text("Cet émulateur ne propose pas d’options.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    }
                    variables.filter { it.values.size > 1 }.forEach { variable ->
                        MenuEntry(Lucide.SlidersHorizontal, variable.label, subtitle = variable.value) {
                            val next = variable.values[(variable.values.indexOf(variable.value) + 1) % variable.values.size]
                            onOption(variable.key, next)
                            variables = variables.map { if (it.key == variable.key) it.copy(value = next) else it }
                        }
                    }
                }

                MenuPage.SCREENS -> {
                    BackEntry(Modifier.focusRequester(first)) { onPage(MenuPage.MAIN) }
                    ChoiceRow("Disposition tactile", TouchPreset.entries, touchPreset, { it.label }, onTouchPreset)
                    Text("Rotation de l’image", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameRotation.entries.forEach { option ->
                            ovh.verbois.retrohub.ui.components.FilterChip(option.label, option == rotation, { onRotation(option) })
                        }
                    }
                    if (dualScreen) {
                        DsLayout.entries.forEach { option ->
                            MenuEntry(
                                if (option ==
                                    dsLayout
                                ) {
                                    Lucide.CircleCheck
                                } else {
                                    Lucide.Columns2
                                },
                                option.label,
                                subtitle = option.description,
                            ) {
                                onDsLayout(option)
                                session.resume()
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(page) {
        delay(80)
        runCatching { first.requestFocus() }
    }
}

@Composable
private fun MenuEntry(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = Palette.Text,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = if (subtitle != null) 68.dp else 56.dp).consoleFocus(
            RoundedCornerShape(16.dp),
            onClick,
            focusScale = 1.02f,
        ).clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (tint == Palette.Text) Palette.Select else tint)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = tint)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun BackEntry(modifier: Modifier, onClick: () -> Unit) {
    GlassButton("Retour", onClick, modifier, icon = Lucide.ChevronLeft, height = 44.dp)
}

@Composable
private fun SlotCard(slot: SlotInfo, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(
            140.dp,
        ).consoleFocus(
            shape,
            onClick,
            enabled = enabled,
        ).clip(shape).background(if (slot.empty) Color(0x0FFFFFFF) else Color(0x22FFFFFF)).border(1.dp, Palette.Line, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (slot.slot ==
                0
            ) {
                "Automatique"
            } else {
                "Emplacement ${slot.slot}"
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) Palette.Text else Palette.TextFaint,
        )
        Text(
            slot.save?.localPlayedAt?.let { parseInstant(it) }?.let { formatDate(it) } ?: "Vide",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
