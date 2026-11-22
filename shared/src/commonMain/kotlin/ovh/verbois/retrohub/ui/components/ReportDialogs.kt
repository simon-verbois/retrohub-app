package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.diagnostics.FEATURE_BUG_REPORTS
import ovh.verbois.retrohub.diagnostics.FEATURE_DIAGNOSTICS
import ovh.verbois.retrohub.diagnostics.LocalReport
import ovh.verbois.retrohub.diagnostics.MAX_DESCRIPTION_CHARS
import ovh.verbois.retrohub.diagnostics.sanitizeLog
import ovh.verbois.retrohub.ui.theme.Palette

/** Shared by Home and the game activity. Only the resumed window offers a crash report. */
@Composable
fun ReportDialogs(container: AppContainer) {
    val capture = container.platform.reportCapture ?: return
    val reports by capture.reports.collectAsState()
    val request by container.reportRequest.collectAsState()
    val auth by container.account.auth.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val status by container.serverStatus.collectAsState()
    val owner = remember(auth) { container.reportOwner() }
    if (lifecycle != Lifecycle.State.RESUMED || auth is ovh.verbois.retrohub.account.AuthState.Loading) return
    val visible = reports.filter { it.owner == null || it.owner == owner }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    if (request == "history") {
        val close = { container.reportRequest.value = null }
        ModalPanel(
            "Rapports sur cet appareil",
            close,
            maxWidth = 560.dp,
            footer = { GlassButton("Fermer", close) },
        ) {
            visible.reversed().forEach { report ->
                ConsoleMenuItem(
                    "${reportLabel(report)} · ${reportStatus(report)}",
                    { container.reportRequest.value = report.id },
                )
            }
            if (visible.isEmpty()) Text("Aucun rapport enregistré.")
        }
        return
    }
    val report = visible.firstOrNull { it.id == request }
        ?: visible.firstOrNull { it.prompt && it.approved == null && !it.sent }
        ?: return
    val dismiss = {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { capture.update(report.id) { it.copy(prompt = false) } }
                container.reportRequest.value = null
            } catch (_: Exception) {
                message = "Le choix n’a pas pu être enregistré. Vérifiez l’espace libre."
            }
        }
        Unit
    }
    ReportDialog(
        report,
        status?.features.orEmpty(),
        owner != null,
        message,
        onDismiss = dismiss,
        onShare = { description ->
            container.platform.shareLogs(report.copy(description = sanitizeLog(description.take(MAX_DESCRIPTION_CHARS))).export())
        },
        onDelete = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { capture.remove(report.id) }
                    container.reportRequest.value = null
                } catch (_: Exception) {
                    message = "Le rapport n’a pas pu être supprimé. Réessayez."
                }
            }
        },
        onSend = { description ->
            try {
                container.reportRequest.value = report.id
                withContext(Dispatchers.IO) {
                    container.reports?.approve(report.id, status?.features.orEmpty(), description)
                    container.platform.scheduleReports()
                }
                null
            } catch (_: IllegalArgumentException) {
                "Reconnectez le compte qui a créé ce rapport."
            } catch (error: IllegalStateException) {
                error.message
            } catch (_: Exception) {
                "Le rapport n’a pas pu être enregistré. Vérifiez l’espace libre."
            }
        },
    )
}

private fun reportLabel(report: LocalReport): String = when (report.kind) {
    "game_crash" -> "Jeu interrompu"
    "game_failure" -> "Erreur de jeu"
    "app_crash" -> "Application interrompue"
    else -> "Signalement"
}

private fun reportStatus(report: LocalReport): String = when {
    report.sent -> "envoyé"
    report.deliveryError -> "envoi refusé · partage disponible"
    report.approved != null -> "en attente d’envoi"
    else -> "non envoyé"
}

@Composable
private fun ReportDialog(
    report: LocalReport,
    features: List<String>,
    signedIn: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    onDelete: () -> Unit,
    onSend: suspend (String) -> String?,
) {
    var description by remember(report.id) { mutableStateOf(report.description) }
    var preview by remember(report.id) { mutableStateOf(false) }
    var busy by remember(report.id) { mutableStateOf(false) }
    var error by remember(report.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val full = FEATURE_BUG_REPORTS in features
    val canSend = signedIn && (full || (FEATURE_DIAGNOSTICS in features && report.diagnostic != null))
    val send: () -> Unit = {
        if (!busy) {
            scope.launch {
                busy = true
                try {
                    error = onSend(description)
                } finally {
                    busy = false
                }
            }
        }
    }
    val dismissLabel = if (report.prompt) "Ignorer" else "Fermer"
    val sendAction = !report.sent && report.approved == null && canSend
    ModalPanel(
        reportLabel(report),
        onDismiss,
        Modifier.testTag("report-dialog"),
        maxWidth = 600.dp,
        initialFocus = first,
        footer = {
            if (sendAction) {
                ActionButtons(
                    if (full) "Envoyer le rapport" else "Envoyer le diagnostic",
                    send,
                    secondary = dismissLabel,
                    onSecondary = { if (!busy) onDismiss() },
                    loading = busy,
                )
            } else {
                GlassButton(dismissLabel, onDismiss, enabled = !busy)
            }
        },
    ) {
        Text(
            if (report.prompt) {
                "Un arrêt inattendu a été détecté. Envoyer un rapport ?"
            } else {
                "${reportStatus(report).replaceFirstChar {
                    it.uppercase()
                }}."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!report.sent && report.approved == null) {
            Text(
                when {
                    !canSend -> "Ce serveur ne reçoit pas encore ce rapport. Vous pouvez le partager."

                    full -> "Envoi à votre serveur : informations techniques, journal filtré et description. Aucun envoi sans votre accord."

                    else ->
                        "Seules les informations techniques de l’émulateur seront envoyées à votre serveur. " +
                            "Le journal et la description restent dans le rapport à partager."
                },
                color = Palette.TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                description,
                { description = it.take(MAX_DESCRIPTION_CHARS) },
                Modifier.fillMaxWidth(),
                label = { Text("Que s’est-il passé ? (facultatif)") },
                minLines = 2,
                maxLines = 4,
            )
        }
        ConsoleMenuItem(if (preview) "Masquer le rapport" else "Voir le rapport", { preview = !preview }, Modifier.focusRequester(first))
        if (preview) {
            SelectionContainer {
                Text(report.copy(description = sanitizeLog(description)).export(), style = MaterialTheme.typography.bodySmall)
            }
        }
        (error ?: message)?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodyMedium) }
        if (report.approved != null) {
            Text(
                when {
                    report.sent -> "Réception confirmée par le serveur."
                    report.deliveryError -> "Le serveur a refusé l’envoi. Utilisez Partager."
                    else -> "Rapport autorisé. L’envoi reprendra avec la connexion."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim,
            )
        }
        ConsoleMenuItem("Partager le rapport complet", { if (!busy) onShare(description) }, icon = ovh.verbois.retrohub.ui.icons.Lucide.Share2, enabled = !busy)
        ConsoleMenuItem("Supprimer de l’appareil", {
            if (!busy) onDelete()
        }, icon = ovh.verbois.retrohub.ui.icons.Lucide.Trash2, tint = Palette.Danger, enabled = !busy)
    }
}
