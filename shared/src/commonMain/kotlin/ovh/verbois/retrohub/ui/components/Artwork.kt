package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ovh.verbois.retrohub.api.ServerAddress
import ovh.verbois.retrohub.emulation.Artwork
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.resources.Res
import ovh.verbois.retrohub.resources.artwork_deep_blue
import ovh.verbois.retrohub.resources.artwork_moss_garden
import ovh.verbois.retrohub.resources.artwork_night_drive
import ovh.verbois.retrohub.resources.artwork_orbital_lounge
import ovh.verbois.retrohub.resources.artwork_star_canyon
import ovh.verbois.retrohub.ui.theme.Palette

/** Adresse du serveur, pour résoudre les URL relatives des illustrations. */
val LocalServer = compositionLocalOf<String?> { null }

fun artworkResource(console: String): DrawableResource = when (Consoles[console]?.artwork ?: Artwork.ORBITAL_LOUNGE) {
    Artwork.STAR_CANYON -> Res.drawable.artwork_star_canyon
    Artwork.MOSS_GARDEN -> Res.drawable.artwork_moss_garden
    Artwork.DEEP_BLUE -> Res.drawable.artwork_deep_blue
    Artwork.ORBITAL_LOUNGE -> Res.drawable.artwork_orbital_lounge
    Artwork.NIGHT_DRIVE -> Res.drawable.artwork_night_drive
}

@Composable
fun resolveArtwork(url: String?): String? {
    val server = LocalServer.current
    return if (url.isNullOrBlank() || server == null) null else ServerAddress.resolve(server, url)
}

/**
 * Jaquette d’un jeu servie par RetroHub ; sans image (ou 404), l’illustration locale de la console
 * avec le titre, comme le web.
 */
@Composable
fun GameArt(
    url: String?,
    console: String,
    title: String,
    modifier: Modifier = Modifier,
    showTitleOnFallback: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    muted: Boolean = false,
) {
    val model = resolveArtwork(url)
    val filter = if (muted) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    var failed by remember(model) { mutableStateOf(model == null) }
    Box(modifier.background(Palette.Surface)) {
        if (failed || model == null) {
            Image(painterResource(artworkResource(console)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = filter)
            if (showTitleOnFallback) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Palette.SkyLow.copy(alpha = 0.9f))))
                Text(
                    title,
                    Modifier.align(Alignment.BottomStart).padding(10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Accent,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                colorFilter = filter,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) failed = true
                    if (state is AsyncImagePainter.State.Success) failed = false
                },
            )
        }
    }
}
