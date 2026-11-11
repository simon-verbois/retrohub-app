package ovh.verbois.retrohub.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.formatRelative
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.components.GameArt
import ovh.verbois.retrohub.ui.components.ProgressLine
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette
import ovh.verbois.retrohub.ui.theme.consoleColor

/** Tuile de la rangée d’accueil : carrée, agrandie et cerclée de blanc quand elle est choisie. */
@Composable
fun GameTile(
    game: Game,
    selected: Boolean,
    size: Dp,
    selectedSize: Dp,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    download: DownloadState? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val side by animateDpAsState(if (selected) selectedSize else size, spring(dampingRatio = 0.75f, stiffness = 500f))
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .size(side)
            .semantics { contentDescription = game.title }
            .consoleFocus(shape, onClick, focusScale = 1f, ringWidth = 0.dp, onFocus = { if (it) onFocus() }, onLongClick = onLongClick)
            .then(
                if (selected) {
                    Modifier.shadow(
                        26.dp,
                        shape,
                        ambientColor = Color.White,
                        spotColor = Color.White,
                    )
                } else {
                    Modifier.shadow(10.dp, shape)
                },
            )
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Palette.Line, shape),
    ) {
        GameArt(game.remote.coverUrl, game.console, game.title, Modifier.fillMaxSize())
        if (game.downloaded) {
            Box(
                Modifier.align(
                    Alignment.TopEnd,
                ).padding(8.dp).size(10.dp).clip(CircleShape).background(Palette.Success).border(2.dp, Palette.SkyLow, CircleShape),
            )
        }
        if (download is DownloadState.Running) {
            ProgressLine(download.fraction, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
        }
    }
}

/** Carte de grille (bibliothèque, accueil en portrait) : jaquette, titre, console et dernière partie. */
@Composable
fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier, download: DownloadState? = null) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.consoleFocus(shape, onClick, focusScale = 1.05f, label = game.title),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, shape).clip(shape).border(1.dp, Palette.Line, shape)) {
            GameArt(game.remote.coverUrl, game.console, game.title, Modifier.fillMaxSize(), showTitleOnFallback = false)
            if (game.downloaded) {
                Row(
                    Modifier.align(
                        Alignment.TopEnd,
                    ).padding(8.dp).clip(RoundedCornerShape(50)).background(Palette.Scrim).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Lucide.HardDriveDownload, null, Modifier.size(12.dp), tint = Palette.Success)
                    Text("Hors ligne", style = MaterialTheme.typography.labelSmall, color = Palette.Success)
                }
            }
            if (game.favorite) {
                Icon(Lucide.Heart, "Favori", Modifier.align(Alignment.TopStart).padding(10.dp).size(16.dp), tint = Palette.Accent)
            }
            if (download is DownloadState.Running) {
                ProgressLine(
                    download.fraction,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(consoleColor(game.console)))
                Text(
                    Consoles[game.console]?.shortName ?: game.console,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                game.lastPlayed?.let {
                    Icon(Lucide.Clock, null, Modifier.size(12.dp), tint = Palette.TextFaint)
                    Text(
                        formatRelative(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
