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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.SaveContext
import ovh.verbois.retrohub.core.formatDate
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.Badge
import ovh.verbois.retrohub.ui.components.CenteredMessage
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/** Choix de la progression quand plusieurs versions d’un jeu ont des sauvegardes. */
@Composable
fun ProgressionScreen(controller: UiController, id: String) {
    val container = controller.container
    val games by container.library.games.collectAsState()
    val game = games.firstOrNull { it.id == id } ?: run {
        CenteredMessage("Jeu introuvable", "Ce jeu n’est plus dans votre bibliothèque.", Lucide.CircleAlert)
        return
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    val contexts = game.remote.saveContexts
    val newest = contexts.maxByOrNull { parseInstant(it.lastSave?.playedAt ?: it.lastPlayedAt)?.toEpochMilliseconds() ?: 0L }
    fun choose(context: SaveContext) {
        val version = context.versionId ?: return
        busy = context.gameId
        scope.launch {
            try {
                if (context.installed) {
                    container.library.chooseProgression(game, version)
                    controller.show("Progression choisie : ${context.label.ifBlank { context.title }}")
                    controller.nav.back()
                } else {
                    container.api.installOnServer(game.id, version)
                    controller.show("Installation de cette version lancée sur le serveur. Choisissez-la dans quelques instants.")
                }
            } catch (error: ApiException) {
                controller.show(error.message ?: "Le choix n’a pas pu être enregistré.")
            } finally {
                busy = null
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Quelle progression reprendre ?", Modifier.semantics { heading() }, style = MaterialTheme.typography.displayMedium)
                Text(
                    "« ${game.title} » existe en plusieurs versions, chacune avec ses sauvegardes. Rien n’est supprimé : vous pourrez changer plus tard.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextDim,
                )
            }
        }
        items(contexts, key = { it.gameId }) { context ->
            val shape = RoundedCornerShape(20.dp)
            Row(
                Modifier.fillMaxWidth().consoleFocus(shape, {
                    choose(context)
                }, focusScale = 1.01f, enabled = busy == null).clip(shape).background(Palette.Glass)
                    .border(
                        if (context ==
                            newest
                        ) {
                            2.dp
                        } else {
                            1.dp
                        },
                        if (context == newest) Palette.Accent else Palette.Line,
                        shape,
                    ).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Lucide.History, null, Modifier.size(24.dp), tint = Palette.Select)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(context.label.ifBlank { context.title }, style = MaterialTheme.typography.titleMedium)
                        if (context == newest) Badge("La plus récente", Palette.Accent)
                        if (!context.installed) Badge("À installer", Palette.Warning)
                    }
                    Text(describe(context), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                Icon(
                    if (busy ==
                        context.gameId
                    ) {
                        Lucide.RefreshCw
                    } else {
                        Lucide.ChevronRight
                    },
                    null,
                    Modifier.size(20.dp),
                    tint = Palette.TextFaint,
                )
            }
        }
    }
}

fun describe(context: SaveContext): String {
    val parts = mutableListOf<String>()
    context.lastSave?.let { save ->
        val at = parseInstant(save.playedAt)
        if (at != null) parts += "dernière sauvegarde ${formatDate(at)} sur ${save.device?.name ?: "le web"}"
    }
    if (context.playedSeconds > 0) parts += "${formatPlayTime(context.playedSeconds)} de jeu"
    parts += "${context.saves} sauvegarde${if (context.saves > 1) "s" else ""}"
    return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
}
