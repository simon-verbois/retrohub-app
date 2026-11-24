package ovh.verbois.retrohub.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.theme.Palette

private val Pill = RoundedCornerShape(28.dp)

/** Console look shared by the common buttons: glass at rest, white with dark content under focus. */
@Composable
private fun ConsoleStyleButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector?,
    enabled: Boolean,
    primary: Boolean = false,
    loading: Boolean = false,
    progress: Float? = null,
    tint: Color = Color.White,
    selected: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(percent = 50),
    minHeight: Dp = 48.dp,
    label: String? = null,
) {
    FocusFill(
        if (loading) null else onClick,
        // An icon-only button keeps its spoken name (« Retour », « Fermer »).
        modifier.heightIn(min = minHeight).then(if (text == null && label != null) Modifier.semantics { contentDescription = label } else Modifier),
        shape = shape,
        rest = when {
            selected -> ConsoleTone.Selected
            primary -> Color.White.copy(alpha = 0.24f)
            else -> ConsoleTone.Rest
        },
        restContent = if (selected) ConsoleTone.OnSelected else tint,
        enabled = enabled,
        label = label,
        focusScale = 1.04f,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.then(
                if (progress !=
                    null
                ) {
                    Modifier.drawBehind { drawRect(ConsoleTone.Selected.copy(alpha = 0.35f), size = Size(size.width * progress, size.height)) }
                } else {
                    Modifier
                },
            )
                .padding(horizontal = if (text == null) 0.dp else 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = androidx.compose.material3.LocalContentColor.current, strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
            }
            if (text != null) {
                Text(
                    text,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Le gros bouton d’action (Jouer, Reprendre, Continuer), couleur d’accent. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    progress: Float? = null,
    height: Dp = 52.dp,
) {
    if (LocalConsoleStyle.current) {
        ConsoleStyleButton(text, onClick, modifier, icon, enabled, primary = true, loading = loading, progress = progress, minHeight = 48.dp)
        return
    }
    val compact = LocalOverlayControls.current
    val shape = if (compact) OverlayControlShape else Pill
    // La progression est dessinée derrière le texte : la taille du bouton ne dépend que du libellé.
    val fraction = progress?.let { animateFloatAsState(it.coerceIn(0f, 1f), tween(450), label = "progress").value }
    // L’animation ne tourne que pendant un téléchargement.
    var stripes = 0f
    var glow = 0f
    if (fraction != null) {
        val motion = rememberInfiniteTransition(label = "loading")
        stripes = motion.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "stripes").value
        glow = motion.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow").value
    }
    Box(
        modifier
            .heightIn(min = if (compact) 48.dp else height)
            .consoleFocus(shape, onClick, enabled = enabled && !loading, focusScale = if (compact) 1f else 1.05f)
            .then(
                if (compact) {
                    Modifier.clip(
                        shape,
                    ).background(if (enabled) Palette.Accent else Palette.Raised)
                } else {
                    Modifier.consoleButtonSurface(shape, primary = true, enabled = enabled)
                },
            )
            .then(if (fraction != null) Modifier.drawBehind { drawProgress(fraction, stripes, glow) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading || fraction != null) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = Palette.OnAccent,
                    strokeWidth = 2.5.dp,
                    trackColor = Palette.OnAccent.copy(alpha = 0.15f),
                )
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(22.dp), tint = Palette.OnAccent)
            }
            Text(
                text,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = Palette.OnAccent,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Partie déjà téléchargée : rayures qui défilent et bord lumineux qui respire. */
private fun DrawScope.drawProgress(fraction: Float, phase: Float, glow: Float) {
    val filled = size.width * fraction
    if (filled <= 0f) return
    clipRect(right = filled) {
        drawRect(Palette.OnAccent.copy(alpha = 0.12f))
        val band = size.height * 0.7f
        val offset = phase * band * 2
        var x = -size.height - band * 2 + offset
        while (x < filled + size.height) {
            val stripe =
                Path().apply {
                    moveTo(x, size.height)
                    lineTo(x + band, size.height)
                    lineTo(x + band + size.height, 0f)
                    lineTo(x + size.height, 0f)
                    close()
                }
            drawPath(stripe, Palette.OnAccent.copy(alpha = 0.12f))
            x += band * 2
        }
    }
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f * glow)), startX = filled - 28.dp.toPx(), endX = filled),
        topLeft = Offset(filled - 28.dp.toPx(), 0f),
        size = Size(28.dp.toPx(), size.height),
    )
}

/** Bouton secondaire en verre, pour les actions à côté de l’action principale. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = Palette.Text,
    height: Dp = 48.dp,
) {
    if (LocalConsoleStyle.current) {
        ConsoleStyleButton(text, onClick, modifier, icon, enabled, tint = if (tint == Palette.Text) Color.White else tint, minHeight = 44.dp)
        return
    }
    val compact = LocalOverlayControls.current
    val shape = if (compact) OverlayControlShape else Pill
    Row(
        modifier
            .heightIn(min = height)
            .consoleFocus(shape, onClick, enabled = enabled, focusScale = if (compact) 1f else 1.04f)
            .then(
                if (compact) {
                    Modifier.clip(
                        shape,
                    ).background(Palette.Raised).border(1.dp, Palette.Line, shape)
                } else {
                    Modifier.consoleButtonSurface(shape, enabled = enabled)
                },
            )
            .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) tint else tint.copy(alpha = 0.4f))
        Text(
            text,
            Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else tint.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}

/** Bouton rond à icône (fiche, favori, recherche) ; [label] sert aux lecteurs d’écran et aux tests. */
@Composable
fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    selected: Boolean = false,
    tint: Color = Palette.Text,
    background: Color = Palette.GlassLight,
    selectedSurface: Boolean = true,
) {
    if (LocalConsoleStyle.current) {
        ConsoleStyleButton(
            null,
            onClick,
            modifier.size(size).semantics { this.selected = selected },
            icon,
            true,
            tint = if (tint == Palette.Text) Color.White else tint,
            selected = selected && selectedSurface,
            shape = CircleShape,
            minHeight = size,
            label = label,
        )
        return
    }
    Box(
        modifier
            .size(size)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .consoleFocus(CircleShape, onClick, focusScale = 1.08f)
            .consoleButtonSurface(
                CircleShape,
                primary = selected && selectedSurface,
                base = background.takeIf {
                    (!selected || !selectedSurface) &&
                        it != Palette.GlassLight
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.42f), tint = if (selected && selectedSurface) Palette.OnAccent else tint)
    }
}

/** Pastille de filtre (consoles, tri), avec un point de couleur facultatif. */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dot: Color? = null) {
    if (LocalConsoleStyle.current) {
        ConsoleStyleButton(text, onClick, modifier.semantics { this.selected = selected }, null, true, selected = selected, minHeight = 44.dp)
        return
    }
    val compact = LocalOverlayControls.current
    val shape = if (compact) OverlayControlShape else Pill
    Row(
        modifier
            .heightIn(min = 48.dp)
            .consoleFocus(shape, onClick, focusScale = if (compact) 1f else 1.04f)
            .then(
                if (compact) {
                    Modifier.clip(
                        shape,
                    ).background(
                        if (selected) Palette.Accent else Palette.Raised,
                    ).border(1.dp, Palette.Line, shape)
                } else {
                    Modifier.consoleButtonSurface(shape, primary = selected)
                },
            )
            .padding(PaddingValues(horizontal = 16.dp, vertical = 10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
            color = if (selected) Palette.OnAccent else Palette.Text,
        )
    }
}

@Composable
fun Badge(text: String, color: Color = Palette.Select, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(
            Pill,
        ).background(color.copy(alpha = 0.18f)).border(1.dp, color.copy(alpha = 0.5f), Pill).padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Accent) {
    Box(modifier.height(6.dp).clip(Pill).background(Palette.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(Pill).background(color))
    }
}
