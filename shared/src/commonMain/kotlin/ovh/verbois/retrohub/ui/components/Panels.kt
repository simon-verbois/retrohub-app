package ovh.verbois.retrohub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

val CardShape = RoundedCornerShape(22.dp)
val TileShape = RoundedCornerShape(14.dp)

/** Panneau en verre fumé : cartes, menus, dialogues. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = CardShape, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(shape).background(Palette.Glass).border(1.dp, Palette.Line, shape), content = content)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = Palette.Select,
    )
}

/** Groupe de lignes de réglages dans une carte arrondie (séparateurs fins entre les lignes). */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface).border(1.dp, Palette.Line, CardShape), content = content)
}

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Palette.Line)

/** Une ligne de réglage : icône, titre, explication et élément à droite. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    value: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.consoleFocus(CardShape, onClick, focusScale = 1.01f, role = role) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = Palette.Select)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Accent)
            }
            trailing?.invoke()
        }
        if (subtitle != null) Text(subtitle, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null) {
    SettingRow(
        title,
        Modifier.semantics {
            toggleableState = ToggleableState(checked)
        },
        subtitle = subtitle,
        icon = icon,
        onClick = { onChange(!checked) },
        role = Role.Switch,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors =
            SwitchDefaults.colors(
                checkedThumbColor = Palette.OnAccent,
                checkedTrackColor = Palette.Accent,
                uncheckedThumbColor = Palette.TextDim,
                uncheckedTrackColor = Palette.Raised,
                uncheckedBorderColor = Palette.Line,
            ),
        )
    }
}

@Composable
fun NavigationRow(title: String, icon: ImageVector, onClick: () -> Unit, subtitle: String? = null, value: String? = null) {
    SettingRow(title, subtitle = subtitle, icon = icon, onClick = onClick, value = value) {
        Icon(Lucide.ChevronRight, null, Modifier.size(20.dp), tint = Palette.TextFaint)
    }
}

/** Choix parmi quelques options, déplié sur place : simple à la manette comme au doigt. */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    describe: ((T) -> String?)? = null,
    recommended: T? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        SettingRow(title, subtitle = subtitle, icon = icon, onClick = { open = !open }, value = label(selected)) {
            Icon(if (open) Lucide.ChevronUp else Lucide.ChevronDown, null, Modifier.size(20.dp), tint = Palette.TextFaint)
        }
        AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(start = if (icon != null) 56.dp else 16.dp, end = 16.dp, bottom = 10.dp)) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics { this.selected = option == selected }
                            .consoleFocus(RoundedCornerShape(14.dp), {
                                onSelect(option)
                                open = false
                            }, focusScale = 1.01f, role = Role.RadioButton)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).border(
                                2.dp,
                                if (option ==
                                    selected
                                ) {
                                    Palette.Accent
                                } else {
                                    Palette.TextFaint
                                },
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (option == selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Accent))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label(option), style = MaterialTheme.typography.titleSmall)
                            if (option == recommended) Badge("Conseillé", Palette.Success)
                            describe?.invoke(option)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    valueLabel: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    steps: Int = 0,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = Palette.Select)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(valueLabel, style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
        }
        if (subtitle != null) Text(subtitle, Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(start = if (icon != null) 40.dp else 0.dp),
            colors = SliderDefaults.colors(
                thumbColor = Palette.Accent,
                activeTrackColor = Palette.Accent,
                inactiveTrackColor = Palette.Raised,
                activeTickColor = Palette.OnAccent,
                inactiveTickColor = Palette.TextFaint,
            ),
        )
    }
}

enum class MessageKind { INFO, SUCCESS, WARNING, ERROR }

/** Message en ligne : dit au joueur ce qui se passe et quoi faire, sans jargon. */
@Composable
fun MessageBanner(text: String, kind: MessageKind = MessageKind.INFO, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val color =
        when (kind) {
            MessageKind.INFO -> Palette.Select
            MessageKind.SUCCESS -> Palette.Success
            MessageKind.WARNING -> Palette.Warning
            MessageKind.ERROR -> Palette.Danger
        }
    val icon =
        when (kind) {
            MessageKind.INFO -> Lucide.Info
            MessageKind.SUCCESS -> Lucide.CircleCheck
            MessageKind.WARNING -> Lucide.TriangleAlert
            MessageKind.ERROR -> Lucide.CircleAlert
        }
    Row(
        modifier.clip(
            RoundedCornerShape(16.dp),
        ).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        action?.invoke()
    }
}

/** Dialogue de confirmation, centré, navigable à la manette. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "Annuler",
    destructive: Boolean = false,
    focusDismiss: Boolean = false,
) {
    val dismissFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(
            Modifier.widthIn(max = 460.dp).padding(24.dp).onPreviewKeyEvent { event ->
                if (event.key == Key.ButtonB || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                } else {
                    false
                }
            },
        ) {
            Column(
                Modifier.background(Palette.SkyLow).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
                ActionButtons(
                    confirm,
                    onConfirm,
                    Modifier.padding(top = 8.dp),
                    secondary = dismiss,
                    onSecondary = onDismiss,
                    secondaryModifier = Modifier.focusRequester(dismissFocus),
                )
            }
        }
        if (focusDismiss) AutoFocus(dismissFocus)
    }
}

@Composable
fun CenteredMessage(title: String, text: String, icon: ImageVector, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.GlassLight), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(30.dp), tint = Palette.Select)
            }
            Text(title, style = MaterialTheme.typography.displaySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            action?.invoke()
        }
    }
}

/** En-tête des pages hors sections : retour (B) et titre, fixe en haut de l’écran. */
@Composable
fun PageHeader(title: String?, onBack: () -> Unit, modifier: Modifier = Modifier, backModifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundButton(Lucide.ArrowLeft, "Retour", onBack, backModifier, size = 46.dp)
        if (title != null) {
            Text(
                title,
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}
