package ovh.verbois.retrohub.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.components.controllerDialog
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/** A failed core cannot resume or create another checkpoint. Keep only useful recovery actions. */
@Composable
fun GameFailurePanel(message: String?, onQuit: () -> Unit, onReport: (() -> Unit)?) {
    val first = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize().background(Palette.Deep).testTag("game-failure").windowInsetsPadding(WindowInsets.safeDrawing).controllerDialog(onQuit)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Jeu interrompu", style = MaterialTheme.typography.headlineSmall)
            Text(message ?: "Votre dernière sauvegarde valide est conservée.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            PrimaryButton("Retour à l’accueil", onQuit, Modifier.fillMaxWidth().focusRequester(first), icon = Lucide.ArrowLeft)
            onReport?.let { GlassButton("Signaler un problème", it, Modifier.fillMaxWidth(), icon = Lucide.Share2) }
        }
    }
    AutoFocus(first)
}
