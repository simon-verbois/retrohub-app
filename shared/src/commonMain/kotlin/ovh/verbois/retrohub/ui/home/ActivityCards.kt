package ovh.verbois.retrohub.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.core.formatRelative
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.consoleFocus
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

/** Une carte d’activité : petit titre (et sa précision à droite), puis la valeur. */
data class ActivityCard(val kicker: String, val value: String, val aside: String? = null, val tone: Tone = Tone.NORMAL) {
    enum class Tone { NORMAL, WARNING }
}

/** Où reprendre : la sauvegarde la plus récente du jeu sur ce téléphone. */
fun resumeCard(saves: List<LocalSave>): ActivityCard {
    val last = saves.filter { it.localSha != null }.maxByOrNull { parseInstant(it.localPlayedAt)?.toEpochMilliseconds() ?: 0L }
        ?: return ActivityCard("Commencer", "Nouvelle partie")
    val value =
        when {
            last.kind == "sram" -> "Sauvegarde du jeu"
            last.slot == 0 -> "Sauvegarde auto"
            else -> "Emplacement ${last.slot}"
        }
    return ActivityCard("Reprendre", value, parseInstant(last.localPlayedAt)?.let { formatRelative(it) })
}

fun playTimeCard(game: Game): ActivityCard =
    ActivityCard("Temps de jeu", if (game.remote.playedSeconds > 0) formatPlayTime(game.remote.playedSeconds) else "Pas encore joué")

/** État de la synchronisation des sauvegardes de ce jeu. */
fun syncCard(saves: List<LocalSave>, online: Boolean): ActivityCard = when {
    saves.any { it.inConflict } -> ActivityCard("Synchronisation", "À choisir", tone = ActivityCard.Tone.WARNING)
    saves.any { it.pendingUpload } && !online -> ActivityCard("Synchronisation", "En attente")
    saves.any { it.pendingUpload } -> ActivityCard("Synchronisation", "En cours")
    else -> ActivityCard("Synchronisation", "À jour")
}

/** Cartes d’activité sous le jeu choisi (accueil en paysage) : reprendre, temps de jeu, synchronisation. */
@Composable
fun ActivityCards(controller: UiController, game: Game, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    val container = controller.container
    val sync by container.sync.status.collectAsState()
    val online by container.platform.online.collectAsState()
    val saves by produceState(emptyList<LocalSave>(), game.playGameId, sync) { value = container.saveStore.forGame(game.playGameId) }
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = HomeEdge).height(height),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val width = height * 3f
        Card(resumeCard(saves), width) { controller.play(game) }
        Card(playTimeCard(game), width) { controller.nav.go(Route.Settings(SettingsPage.GAME, game.id)) }
        val state = syncCard(saves, online)
        Card(state, width) {
            if (state.tone == ActivityCard.Tone.WARNING) controller.nav.go(Route.Conflict(game.playGameId)) else controller.syncNow()
        }
    }
}

@Composable
private fun Card(card: ActivityCard, width: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(width).fillMaxHeight()
            .consoleFocus(shape, onClick, focusScale = 1.04f)
            .clip(shape)
            .background(Palette.Glass)
            .border(1.dp, Palette.Line, shape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(card.kicker.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, maxLines = 1)
            card.aside?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, maxLines = 1) }
        }
        Text(
            card.value,
            style = MaterialTheme.typography.titleMedium,
            color = if (card.tone == ActivityCard.Tone.WARNING) Palette.Warning else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
