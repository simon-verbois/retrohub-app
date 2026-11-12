package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import ovh.verbois.retrohub.api.AccountAvatar
import ovh.verbois.retrohub.api.avatarImageUrl
import ovh.verbois.retrohub.ui.theme.Palette

/** Cached server image; loading failures fall back to the initial, including while offline. */
@Composable
fun ProfileAvatar(username: String, server: String, avatar: AccountAvatar?, modifier: Modifier = Modifier, showBackground: Boolean = true) {
    val url = avatarImageUrl(server, avatar)
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier.clip(CircleShape).then(if (showBackground) Modifier.background(Palette.Raised).border(1.dp, Palette.Line, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Text(username.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.Text)
        if (url != null) {
            AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onState = { loaded = it is AsyncImagePainter.State.Success })
        }
    }
}
