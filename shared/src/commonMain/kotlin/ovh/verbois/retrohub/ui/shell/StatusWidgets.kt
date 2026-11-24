package ovh.verbois.retrohub.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
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
import ovh.verbois.retrohub.ui.game.LaunchChoiceDialog
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.time.Clock

internal val Pill = RoundedCornerShape(percent = 50)

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
internal fun statusBubbleSize() = with(LocalDensity.current) { 16.sp.toDp() }.plus(10.dp).coerceAtLeast(30.dp)

/** Foreground clock and power state, including controllers attached after launch. */
@Composable
fun StatusPill(platform: Platform, transparent: Boolean = false, compact: Boolean = false) {
    val textStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = if (compact) 11.sp else 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
        fontWeight = if (compact) FontWeight.Normal else FontWeight.Bold,
    )
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gamepad by platform.gamepadConnected.collectAsState()
    val power by produceState(PowerStatus(), platform, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val polling = flow {
                while (true) {
                    emit(Unit)
                    delay(5_000)
                }
            }
            merge(polling, platform.powerStatusChanges).collectLatest {
                value = withContext(Dispatchers.IO) { platform.powerStatus() }
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
            .then(if (transparent) Modifier else Modifier.consoleButtonSurface(Pill)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (compact) Text("${local.day}/${local.month.number}", style = textStyle, maxLines = 1)
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
                style = if (compact) textStyle else MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        if (gamepad) {
            val controller = power.controllers.firstOrNull()
            Box(Modifier.width(1.dp).height(14.dp).background(Palette.Line))
            Row(
                Modifier.testTag("header-controller").semantics(mergeDescendants = true) {
                    contentDescription = if (controller == null) {
                        "Manette connectée"
                    } else {
                        "Batterie de ${controller.name} : ${controller.level.percent} %" + if (controller.level.charging) ", en charge" else ""
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Lucide.Gamepad2,
                    null,
                    Modifier.size(14.dp),
                    tint = if (controller != null &&
                        controller.level.percent <= 20
                    ) {
                        Palette.Warning
                    } else {
                        Palette.Text
                    },
                )
                if (controller != null) {
                    Text("${controller.level.percent} %", style = if (compact) textStyle else MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}
