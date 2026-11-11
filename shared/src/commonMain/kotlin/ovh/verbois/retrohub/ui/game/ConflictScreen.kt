package ovh.verbois.retrohub.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.core.formatPlayedAt
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.sync.PushResult
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.Badge
import ovh.verbois.retrohub.ui.components.CenteredMessage
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette
import kotlin.time.Instant

/**
 * Conflit de sauvegarde : les deux versions côte à côte, avec date et appareil ; la plus récente
 * est mise en avant mais le joueur choisit toujours explicitement. Rien n’est écrasé sans lui.
 */
@Composable
fun ConflictScreen(controller: UiController, gameId: String) {
    val container = controller.container
    val saves by remember(gameId) { container.saveStore.observe(gameId) }.collectAsState(emptyList())
    val games by container.library.games.collectAsState()
    val game = games.firstOrNull { it.playGameId == gameId || it.id == gameId }
    val conflicts = saves.filter { it.inConflict }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (conflicts.isEmpty()) {
        CenteredMessage("Tout est en ordre", "Vos sauvegardes sont à jour. Bonne partie !", Lucide.CircleCheck) {
            if (game != null) {
                PrimaryButton("Jouer", {
                    controller.nav.back()
                    controller.play(game)
                }, icon = Lucide.Play)
            }
        }
        return
    }
    val save = conflicts.first()
    val first = remember { FocusRequester() }
    fun resolve(keepLocal: Boolean) {
        busy = true
        scope.launch {
            try {
                if (keepLocal) {
                    val result = container.saveSync.keepLocal(save, container.library.saveEpoch())
                    if (result is PushResult.Conflict) {
                        controller.show(
                            "Une autre partie vient d’arriver sur le serveur : choisissez de nouveau.",
                        )
                    }
                } else {
                    container.saveSync.keepServer(save)
                }
                container.sync.refreshCounts()
            } catch (error: ApiException.Offline) {
                controller.show("Connexion perdue. Votre choix sera possible dès le retour du réseau ; rien n’a été modifié.")
            } catch (error: ApiException) {
                controller.show(error.message ?: "Le choix n’a pas pu être enregistré. Réessayez.")
            } finally {
                busy = false
            }
        }
    }
    val localAt = parseInstant(save.localPlayedAt)
    val serverAt = parseInstant(save.conflictPlayedAt)
    val localNewer = localAt != null && (serverAt == null || localAt > serverAt)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Deux progressions différentes", Modifier.semantics { heading() }, style = MaterialTheme.typography.displayMedium)
        Text(
            "« ${slotLabel(
                save.kind,
                save.slot,
            )} » de ${game?.title ?: "ce jeu"} a avancé sur ce téléphone et ailleurs. Choisissez celle à garder : l’autre sera remplacée.",
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.TextDim,
        )
        val cards: @Composable (Modifier) -> Unit = { modifier ->
            VersionCard(
                "Sur ce téléphone",
                Lucide.Smartphone,
                localAt,
                "ce téléphone",
                localNewer,
                "Garder celle-ci",
                busy,
                { resolve(true) },
                modifier.then(if (localNewer) Modifier.focusRequester(first) else Modifier),
            )
            VersionCard(
                "Sur le serveur",
                Lucide.Cloud,
                serverAt,
                save.conflictDevice ?: "le web",
                !localNewer,
                "Garder celle-ci",
                busy,
                { resolve(false) },
                modifier.then(if (!localNewer) Modifier.focusRequester(first) else Modifier),
            )
        }
        if (controller.portrait) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { cards(Modifier.fillMaxWidth()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { cards(Modifier.weight(1f)) }
        }
        if (conflicts.size >
            1
        ) {
            Text(
                "${conflicts.size - 1} autre(s) emplacement(s) à choisir ensuite.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim,
            )
        }
    }
    LaunchedEffect(save.kind, save.slot) { runCatching { first.requestFocus() } }
}

@Composable
private fun VersionCard(
    title: String,
    icon: ImageVector,
    playedAt: Instant?,
    device: String,
    newest: Boolean,
    action: String,
    busy: Boolean,
    onChoose: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(
            shape,
        ).background(Palette.Glass).border(if (newest) 2.dp else 1.dp, if (newest) Palette.Accent else Palette.Line, shape).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = Palette.Select)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (newest) Badge("Plus récente", Palette.Accent)
        }
        Text(playedAt?.let { formatPlayedAt(it) } ?: "Date de la partie inconnue", style = MaterialTheme.typography.bodyLarge)
        Text("Appareil : $device", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        if (newest) {
            PrimaryButton(action, onChoose, Modifier.widthIn(min = 180.dp).padding(top = 6.dp), icon = Lucide.Check, loading = busy)
        } else {
            GlassButton(action, onChoose, Modifier.widthIn(min = 180.dp).padding(top = 6.dp), icon = Lucide.Check, enabled = !busy)
        }
    }
}

fun slotLabel(kind: String, slot: Int): String = when {
    kind == "sram" -> "Sauvegarde du jeu"
    slot == 0 -> "Sauvegarde automatique"
    else -> "Emplacement $slot"
}
