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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
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
    reduceMotion: Boolean = false,
) {
    val side by animateDpAsState(if (selected) selectedSize else size, androidx.compose.animation.core.tween(if (reduceMotion) 0 else 260))
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .size(side)
            .semantics { contentDescription = game.title }
            .consoleFocus(shape, onClick, focusScale = 1f, onFocus = { if (it) onFocus() }, onLongClick = onLongClick)
            .shadow(if (selected) 12.dp else 6.dp, shape)
            .clip(shape)
            .border(1.dp, Palette.Line, shape),
    ) {
        GameArt(game.remote.coverUrl, game.console, game.title, Modifier.fillMaxSize(), muted = !game.downloaded)
        if (download is DownloadState.Running) {
            DownloadOverlay(download.fraction, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Carte de grille (bibliothèque, accueil en portrait) : jaquette, titre, console et dernière partie. */
@Composable
fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier, download: DownloadState? = null, onLongClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.consoleFocus(shape, onClick, focusScale = 1.05f, label = game.title, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, shape).clip(shape).border(1.dp, Palette.Line, shape)) {
            GameArt(game.remote.coverUrl, game.console, game.title, Modifier.fillMaxSize(), showTitleOnFallback = false, muted = !game.downloaded)
            if (game.favorite) {
                Icon(Lucide.Heart, "Favori", Modifier.align(Alignment.TopStart).padding(10.dp).size(16.dp), tint = Palette.Accent)
            }
            if (download is DownloadState.Running) {
                DownloadOverlay(download.fraction, Modifier.align(Alignment.BottomCenter))
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        if (download is DownloadState.Failed) {
            Text(
                download.message,
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Warning,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Keep progress readable directly on the cover, independent of the artwork underneath. */
@Composable
private fun DownloadOverlay(fraction: Float, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag("game-download-progress")
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) }
            .background(Palette.SkyLow).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${(fraction.coerceIn(0f, 1f) * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, color = Palette.Text)
        ProgressLine(fraction, Modifier.fillMaxWidth())
    }
}
