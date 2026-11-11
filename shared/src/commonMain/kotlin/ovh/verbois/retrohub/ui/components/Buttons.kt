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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.theme.Palette

private val Pill = RoundedCornerShape(percent = 50)

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
            .height(height)
            .consoleFocus(Pill, onClick, enabled = enabled && !loading, focusScale = 1.05f)
            .clip(Pill)
            .background(if (enabled) Palette.Accent else Palette.Accent.copy(alpha = 0.45f))
            .then(if (fraction != null) Modifier.drawBehind { drawProgress(fraction, stripes, glow) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 26.dp),
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
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = Palette.OnAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    Row(
        modifier
            .height(height)
            .consoleFocus(Pill, onClick, enabled = enabled)
            .clip(Pill)
            .background(Palette.GlassLight)
            .border(1.dp, Palette.Line, Pill)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) tint else tint.copy(alpha = 0.4f))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else tint.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
) {
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = label }
            .consoleFocus(CircleShape, onClick, focusScale = 1.08f)
            .clip(CircleShape)
            .background(if (selected) Palette.Text else background)
            .border(1.dp, Palette.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.42f), tint = if (selected) Palette.Background else tint)
    }
}

/** Pastille de filtre (consoles, tri), avec un point de couleur facultatif. */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dot: Color? = null) {
    Row(
        modifier
            .height(40.dp)
            .consoleFocus(Pill, onClick)
            .clip(Pill)
            .background(if (selected) Palette.Text else Palette.GlassLight)
            .border(1.dp, if (selected) Palette.Text else Palette.Line, Pill)
            .padding(PaddingValues(horizontal = 16.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
            color = if (selected) Palette.Background else Palette.Text,
            maxLines = 1,
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
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Accent) {
    Box(modifier.height(6.dp).clip(Pill).background(Palette.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(Pill).background(color))
    }
}
