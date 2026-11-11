package ovh.verbois.retrohub.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.emulation.HintsMode
import ovh.verbois.retrohub.sync.SyncPhase
import ovh.verbois.retrohub.sync.SyncStatus
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.time.Clock

private val Pill = RoundedCornerShape(percent = 50)

/** Le cœur des favoris quand on y est, comme sur la maquette. */
private val FavoriteTint = Color(0xFFFF8FA3)

/**
 * L’interface console : barre du haut (profil, sections, recherche, synchro, heure), écran courant
 * et barre des indications de boutons. LB/RB changent de section, B revient, Y cherche, Start
 * ouvre le menu ; les flèches et le stick déplacent le focus.
 */
@Composable
fun ConsoleShell(controller: UiController, account: Account, content: @Composable (Route) -> Unit) {
    val container = controller.container
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val sync by container.sync.status.collectAsState()
    val online by container.platform.online.collectAsState()
    val gamepad by container.platform.gamepadConnected.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val route = controller.nav.current
    val screenStates = rememberSaveableStateHolder()
    val showHints =
        when (settings.hints) {
            HintsMode.ALWAYS -> true
            HintsMode.NEVER -> false
            HintsMode.AUTO -> gamepad
        }
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent event.key in
                    setOf(Key.ButtonB, Key.ButtonY, Key.ButtonL1, Key.ButtonR1, Key.ButtonStart, Key.Menu, Key.ButtonSelect)
            }
            when (event.key) {
                Key.ButtonB, Key.Escape -> controller.onButton(UiController.Button.B)
                Key.ButtonY -> controller.onButton(UiController.Button.Y)
                Key.ButtonL1 -> controller.onButton(UiController.Button.LB)
                Key.ButtonR1 -> controller.onButton(UiController.Button.RB)
                Key.ButtonStart, Key.Menu -> controller.onButton(UiController.Button.START)
                Key.ButtonSelect -> controller.onButton(UiController.Button.SELECT)
                else -> false
            }
        },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // La barre du haut n’appartient qu’aux sections ; les autres pages ont leur en-tête et un retour.
            AnimatedVisibility(Section.of(route) != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                Column {
                    TopBar(controller, account, sync, online, compact = controller.portrait)
                    if (controller.portrait) {
                        SectionTabs(
                            controller,
                            Section.of(controller.nav.stack.first()),
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val dir = if (controller.nav.forward.value) 1 else -1
                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 * dir }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -it / 16 * dir })
                    },
                    label = "screen",
                ) { target -> screenStates.SaveableStateProvider(target.toString()) { content(target) } }
            }
            if (showHints) HintsBar()
        }
        AnimatedVisibility(
            controller.searching,
            enter = fadeIn() + slideInVertically { -it / 4 },
            exit =
            fadeOut() + slideOutVertically { -it / 4 },
        ) {
            SearchOverlay(controller)
        }
        AnimatedVisibility(controller.menuOpen, enter = fadeIn(), exit = fadeOut()) {
            AccountMenu(controller, account, onClose = { controller.menuOpen = false })
        }
        AnimatedVisibility(
            controller.toast != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showHints) 56.dp else 24.dp),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            GlassPanel(Modifier.widthIn(max = 560.dp).padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) {
                Text(
                    controller.toast ?: "",
                    Modifier.background(Palette.BackgroundHigh.copy(alpha = 0.9f)).padding(horizontal = 20.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        // Premier focus : l’élément principal de l’écran, pour la manette.
        delay(150)
        focus.moveFocus(FocusDirection.Enter)
    }
}

@Composable
private fun TopBar(controller: UiController, account: Account, sync: SyncStatus, online: Boolean, compact: Boolean) {
    val section = Section.of(controller.nav.stack.first())
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = if (compact) 12.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
    ) {
        if (compact) {
            Text("RetroHub", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        } else {
            SectionTabs(controller, section, Modifier.weight(1f))
        }
        if (!online) {
            Row(
                Modifier.clip(Pill).background(Palette.Warning.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Lucide.WifiOff, null, Modifier.size(14.dp), tint = Palette.Warning)
                if (!compact) Text("Hors ligne", style = MaterialTheme.typography.labelSmall, color = Palette.Warning)
            }
        }
        BarIcon(Lucide.Search, "Rechercher") { controller.searching = true }
        val favorites = section == Section.FAVORITES
        BarIcon(Lucide.Heart, "Favoris", tint = if (favorites) FavoriteTint else Palette.Text, selected = favorites) {
            controller.nav.section(if (favorites) Section.HOME else Section.FAVORITES)
        }
        BarIcon(Lucide.Settings, "Paramètres") { controller.nav.go(Route.Settings()) }
        SyncIcon(sync, online) { controller.nav.go(Route.Settings(SettingsPage.SYNC)) }
        if (!compact) ClockText()
        // Avatar seul, à droite : il ouvre le menu (paramètres, synchro, stockage, compte).
        // Même gabarit que les icônes voisines : une pastille de verre de 32 dp.
        Box(
            Modifier.size(42.dp).semantics {
                contentDescription = "Compte et menu"
            }.consoleFocus(CircleShape, { controller.menuOpen = true }, focusScale = 1.1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Palette.Raised).border(1.dp, Palette.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(account.username.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            }
        }
    }
}

@Composable
fun SectionTabs(controller: UiController, section: Section?, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Section.entries.filter { it.tab }.forEach { item -> SectionTab(item.title, item == section) { controller.nav.section(item) } }
    }
}

@Composable
private fun SectionTab(title: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .consoleFocus(Pill, onClick, focusScale = 1.06f)
            .clip(Pill)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .drawBehind {
                if (active) {
                    drawRoundRect(
                        Palette.Text,
                        topLeft = Offset(0f, size.height + 2.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    )
                }
            },
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (active) Palette.Text else Palette.TextFaint, maxLines = 1)
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, tint: Color = Palette.Text, rotation: Float = 0f, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).semantics {
            contentDescription = label
        }.consoleFocus(CircleShape, onClick, focusScale = 1.1f).clip(CircleShape)
            .background(if (selected) Palette.Glass else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp).graphicsLayer { rotationZ = rotation }, tint = tint)
    }
}

@Composable
private fun SyncIcon(sync: SyncStatus, online: Boolean, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "sync")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val (icon, tint, label) =
        when {
            sync.phase == SyncPhase.SYNCING -> Triple(Lucide.RefreshCw, Palette.Select, "Synchronisation en cours")

            sync.conflicts > 0 -> Triple(Lucide.CloudAlert, Palette.Warning, "Une sauvegarde attend votre choix")

            !online || sync.phase == SyncPhase.OFFLINE -> Triple(
                Lucide.CloudOff,
                Palette.TextFaint,
                "Hors ligne : la synchronisation reprendra seule",
            )

            sync.phase == SyncPhase.MAINTENANCE -> Triple(Lucide.CloudOff, Palette.Warning, "Serveur en maintenance")

            sync.pendingSaves > 0 || sync.pendingSessions > 0 -> Triple(Lucide.Cloud, Palette.TextDim, "Synchronisation en attente")

            else -> Triple(Lucide.CloudCheck, Palette.Success, "Tout est synchronisé")
        }
    BarIcon(icon, label, tint, rotation = if (sync.phase == SyncPhase.SYNCING) angle else 0f, onClick = onClick)
}

@Composable
private fun ClockText() {
    val now by produceState(Clock.System.now()) {
        while (true) {
            value = Clock.System.now()
            delay(15_000)
        }
    }
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    Text(
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}",
        Modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

/** Indications des boutons, en bas comme sur une console (A vert, B rouge, Y jaune). */
@Composable
fun HintsBar(extra: List<Pair<String, String>> = emptyList()) {
    Row(
        Modifier.fillMaxWidth().height(
            40.dp,
        ).background(Brush.verticalGradient(listOf(Color.Transparent, Palette.SkyLow))).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hint("A", Palette.PadA, "Sélectionner")
        Hint("B", Palette.PadB, "Retour")
        Hint("Y", Palette.PadY, "Rechercher")
        Hint("LB RB", Color(0x26FFFFFF), "Sections")
        Hint("☰", Color(0x26FFFFFF), "Menu")
        extra.forEach { (key, label) -> Hint(key, Color(0x26FFFFFF), label) }
    }
}

@Composable
private fun Hint(key: String, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.height(20.dp).widthIn(min = 20.dp).clip(Pill).background(color).padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(key, style = MaterialTheme.typography.labelSmall, color = if (color == Palette.PadY) Color(0xFF2A1D10) else Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
    }
}
