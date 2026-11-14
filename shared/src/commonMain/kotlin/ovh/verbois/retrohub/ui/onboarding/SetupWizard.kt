package ovh.verbois.retrohub.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.emulation.HintsMode
import ovh.verbois.retrohub.emulation.InterfaceOrientation
import ovh.verbois.retrohub.emulation.PerformanceProfile
import ovh.verbois.retrohub.resources.Res
import ovh.verbois.retrohub.resources.action_back
import ovh.verbois.retrohub.resources.action_continue
import ovh.verbois.retrohub.resources.performance_balanced
import ovh.verbois.retrohub.resources.performance_balanced_description
import ovh.verbois.retrohub.resources.performance_battery
import ovh.verbois.retrohub.resources.performance_battery_description
import ovh.verbois.retrohub.resources.performance_quality
import ovh.verbois.retrohub.resources.performance_quality_description
import ovh.verbois.retrohub.resources.recommended
import ovh.verbois.retrohub.resources.setup_background
import ovh.verbois.retrohub.resources.setup_background_description
import ovh.verbois.retrohub.resources.setup_both
import ovh.verbois.retrohub.resources.setup_both_description
import ovh.verbois.retrohub.resources.setup_cellular
import ovh.verbois.retrohub.resources.setup_cellular_description
import ovh.verbois.retrohub.resources.setup_comfort
import ovh.verbois.retrohub.resources.setup_discover
import ovh.verbois.retrohub.resources.setup_gamepad
import ovh.verbois.retrohub.resources.setup_gamepad_description
import ovh.verbois.retrohub.resources.setup_greeting
import ovh.verbois.retrohub.resources.setup_haptics
import ovh.verbois.retrohub.resources.setup_haptics_description
import ovh.verbois.retrohub.resources.setup_performance
import ovh.verbois.retrohub.resources.setup_progress
import ovh.verbois.retrohub.resources.setup_ready
import ovh.verbois.retrohub.resources.setup_ready_description
import ovh.verbois.retrohub.resources.setup_resume
import ovh.verbois.retrohub.resources.setup_resume_description
import ovh.verbois.retrohub.resources.setup_touch
import ovh.verbois.retrohub.resources.setup_touch_description
import ovh.verbois.retrohub.ui.components.ActionButtons
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.Badge
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

private enum class PlayStyle { TOUCH, GAMEPAD, BOTH }

private class Toggle(val title: String, val subtitle: String, val icon: ImageVector, val checked: Boolean, val onChange: (Boolean) -> Unit)

/** Natural card heights and a scrollable body leave translated text and actions room to grow. */
@Composable
fun SetupWizard(container: AppContainer, username: String) {
    val repo = container.settings
    val settings by repo.settings.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var style by rememberSaveable { mutableStateOf(PlayStyle.BOTH) }
    val scope = rememberCoroutineScope()
    val steps = 4
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.SkyTop, Palette.SkyLow)))
            .windowInsetsPadding(WindowInsets.safeDrawing).onKeyEvent { event ->
                if (step > 0 && event.key in listOf(Key.ButtonB, Key.Escape)) {
                    if (event.type == KeyEventType.KeyUp) step--
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxHeight < 480.dp
        val wide = maxWidth >= 760.dp && LocalDensity.current.fontScale <= 1.3f
        Column(
            Modifier.widthIn(max = 960.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = if (compact) 12.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
        ) {
            val progress = stringResource(Res.string.setup_progress, step + 1, steps)
            Row(Modifier.semantics { contentDescription = progress }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(steps) { index ->
                    Box(
                        Modifier.height(5.dp).width(
                            if (index ==
                                step
                            ) {
                                36.dp
                            } else {
                                16.dp
                            },
                        ).clip(CircleShape).background(if (index <= step) Palette.Accent else Palette.Line),
                    )
                }
            }
            Crossfade(
                step,
                Modifier.weight(1f).fillMaxWidth(),
                label = "setup",
            ) { current ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val availableHeight = maxHeight
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = availableHeight).padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        when (current) {
                            0 -> {
                                SetupTitle(stringResource(Res.string.setup_greeting, username), compact)
                                Options(
                                    PlayStyle.entries,
                                    style,
                                    { style = it },
                                    { playStyleLabel(it) },
                                    { playStyleDescription(it) },
                                    { playStyleIcon(it) },
                                    PlayStyle.BOTH,
                                    wide,
                                )
                                ChoiceRow("Orientation de l’interface", InterfaceOrientation.entries, settings.interfaceOrientation, { it.label }, { value ->
                                    repo.update { it.copy(interfaceOrientation = value) }
                                }, icon = Lucide.Smartphone, recommended = InterfaceOrientation.SYSTEM)
                            }

                            1 -> {
                                SetupTitle(stringResource(Res.string.setup_performance), compact)
                                Options(
                                    PerformanceProfile.entries,
                                    settings.performance,
                                    { v -> repo.update { it.copy(performance = v) } },
                                    { performanceLabel(it) },
                                    { performanceDescription(it) },
                                    { Lucide.Gauge },
                                    PerformanceProfile.BALANCED,
                                    wide,
                                )
                            }

                            2 -> {
                                SetupTitle(stringResource(Res.string.setup_comfort), compact)
                                Toggles(
                                    listOf(
                                        Toggle(
                                            stringResource(Res.string.setup_haptics),
                                            stringResource(Res.string.setup_haptics_description),
                                            Lucide.Vibrate,
                                            settings.haptics,
                                        ) { v ->
                                            repo.update { it.copy(haptics = v) }
                                        },
                                        Toggle(
                                            stringResource(Res.string.setup_background),
                                            stringResource(Res.string.setup_background_description),
                                            Lucide.Sparkles,
                                            settings.animatedBackground,
                                        ) { v ->
                                            repo.update { it.copy(animatedBackground = v) }
                                        },
                                        Toggle(
                                            stringResource(Res.string.setup_resume),
                                            stringResource(Res.string.setup_resume_description),
                                            Lucide.History,
                                            settings.resumeLastState,
                                        ) { v ->
                                            repo.update { it.copy(resumeLastState = v) }
                                        },
                                        Toggle(
                                            stringResource(Res.string.setup_cellular),
                                            stringResource(Res.string.setup_cellular_description),
                                            Lucide.Globe,
                                            settings.cellularDownloads,
                                        ) { v ->
                                            repo.update { it.copy(cellularDownloads = v) }
                                        },
                                    ),
                                    wide,
                                )
                            }

                            else -> {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    Box(Modifier.size(80.dp).clip(CircleShape).background(Palette.Accent), contentAlignment = Alignment.Center) {
                                        Icon(Lucide.Check, null, Modifier.size(36.dp), tint = Palette.OnAccent)
                                    }
                                    Text(
                                        stringResource(Res.string.setup_ready),
                                        style = MaterialTheme.typography.displayMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.semantics {
                                            heading()
                                        },
                                    )
                                    Text(
                                        stringResource(Res.string.setup_ready_description),
                                        Modifier.widthIn(max = 440.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Palette.TextDim,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            ActionButtons(
                stringResource(if (step == steps - 1) Res.string.setup_discover else Res.string.action_continue),
                {
                    if (step == 0) {
                        repo.update {
                            it.copy(
                                hideTouchWithGamepad = style != PlayStyle.TOUCH,
                                hints = if (style ==
                                    PlayStyle.GAMEPAD
                                ) {
                                    HintsMode.ALWAYS
                                } else {
                                    HintsMode.AUTO
                                },
                            )
                        }
                    }
                    if (step < steps - 1) step++ else scope.launch { repo.updateNow { it.copy(setupDone = true) } }
                },
                secondary = if (step > 0) stringResource(Res.string.action_back) else null,
                onSecondary = { step-- },
                primaryIcon = if (step == steps - 1) Lucide.Play else Lucide.ChevronRight,
                secondaryIcon = Lucide.ChevronLeft,
            )
        }
    }
}

@Composable
private fun SetupTitle(title: String, compact: Boolean) {
    Text(
        title,
        Modifier.fillMaxWidth().semantics {
            heading()
        },
        style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
    )
}

@Composable
private fun <T> Options(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    description: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    recommended: T?,
    wide: Boolean,
) {
    val first = remember { FocusRequester() }
    val card: @Composable (T, Modifier) -> Unit = { option, modifier ->
        val active = option == selected
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier.heightIn(min = 112.dp).semantics { this.selected = active }
                .consoleFocus(shape, { onSelect(option) }, role = Role.RadioButton, focusScale = 1.01f)
                .clip(shape).background(MaterialTheme.colorScheme.surface)
                .border(if (active) 2.dp else 1.dp, if (active) Palette.Accent else Palette.Line, shape).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon(option), null, Modifier.size(24.dp), tint = Palette.Select)
                Text(label(option), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            Text(description(option), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            if (option == recommended) Badge(stringResource(Res.string.recommended), Palette.Success)
        }
    }
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEachIndexed { i, option -> card(option, Modifier.weight(1f).then(if (i == 0) Modifier.focusRequester(first) else Modifier)) }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEachIndexed { i, option -> card(option, Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)) }
        }
    }
    AutoFocus(first)
}

@Composable
private fun Toggles(items: List<Toggle>, wide: Boolean) {
    val first = remember { FocusRequester() }
    val tile: @Composable (Toggle, Modifier) -> Unit = { item, modifier ->
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier.heightIn(min = 96.dp).semantics { toggleableState = ToggleableState(item.checked) }
                .consoleFocus(shape, { item.onChange(!item.checked) }, role = Role.Switch, focusScale = 1.01f)
                .clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, Palette.Line, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(item.icon, null, Modifier.size(24.dp), tint = Palette.Select)
                Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(
                    item.checked,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Palette.OnAccent,
                        checkedTrackColor = Palette.Accent,
                        uncheckedThumbColor = Palette.TextDim,
                        uncheckedTrackColor = Palette.Raised,
                        uncheckedBorderColor = Palette.Line,
                    ),
                )
            }
            Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
    }
    Column(Modifier.fillMaxWidth().testTag("setup-preferences"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(if (wide) 2 else 1).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { index, item ->
                    tile(
                        item,
                        Modifier.weight(1f).then(
                            if (row == 0 &&
                                index == 0
                            ) {
                                Modifier.focusRequester(first)
                            } else {
                                Modifier
                            },
                        ),
                    )
                }
            }
        }
    }
    AutoFocus(first)
}

private fun playStyleIcon(style: PlayStyle): ImageVector = when (style) {
    PlayStyle.TOUCH -> Lucide.Hand
    PlayStyle.GAMEPAD -> Lucide.Gamepad2
    PlayStyle.BOTH -> Lucide.MonitorSmartphone
}

@Composable
private fun playStyleLabel(style: PlayStyle): String = stringResource(
    when (style) {
        PlayStyle.TOUCH -> Res.string.setup_touch
        PlayStyle.GAMEPAD -> Res.string.setup_gamepad
        PlayStyle.BOTH -> Res.string.setup_both
    },
)

@Composable
private fun playStyleDescription(style: PlayStyle): String = stringResource(
    when (style) {
        PlayStyle.TOUCH -> Res.string.setup_touch_description
        PlayStyle.GAMEPAD -> Res.string.setup_gamepad_description
        PlayStyle.BOTH -> Res.string.setup_both_description
    },
)

@Composable
private fun performanceLabel(profile: PerformanceProfile): String = stringResource(
    when (profile) {
        PerformanceProfile.BATTERY -> Res.string.performance_battery
        PerformanceProfile.BALANCED -> Res.string.performance_balanced
        PerformanceProfile.QUALITY -> Res.string.performance_quality
    },
)

@Composable
private fun performanceDescription(profile: PerformanceProfile): String = stringResource(
    when (profile) {
        PerformanceProfile.BATTERY -> Res.string.performance_battery_description
        PerformanceProfile.BALANCED -> Res.string.performance_balanced_description
        PerformanceProfile.QUALITY -> Res.string.performance_quality_description
    },
)
