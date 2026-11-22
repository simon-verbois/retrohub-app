package ovh.verbois.retrohub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.FEATURE_GAME_MODS
import ovh.verbois.retrohub.api.ModSelection
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.mods.ModLocalState
import ovh.verbois.retrohub.mods.ModPlanner
import ovh.verbois.retrohub.mods.ModProblem
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.*
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

@Composable
internal fun ModsPage(controller: UiController, gameId: String) {
    val container = controller.container
    val games by container.library.games.collectAsState()
    val game = games.firstOrNull { it.id == gameId } ?: return
    val repository = container.mods
    val online by container.platform.online.collectAsState()
    val status by container.serverStatus.collectAsState()
    val available = status?.features?.contains(FEATURE_GAME_MODS) == true
    val changes by repository.changes.collectAsState()
    val transfers by repository.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var state by remember(gameId) { mutableStateOf(ModLocalState()) }
    var error by remember(gameId) { mutableStateOf<String?>(null) }
    var loading by remember(gameId) { mutableStateOf(true) }
    var work by remember(gameId) { mutableStateOf<Job?>(null) }
    var confirm by remember(gameId) { mutableStateOf<Pair<List<ModSelection>, Boolean>?>(null) }
    var removing by remember(gameId) { mutableStateOf<ModSelection?>(null) }
    val busy = work?.isActive == true

    fun message(failure: Exception): String = when (failure) {
        is ModProblem -> failure.message!!

        is ApiException.Offline -> "Le serveur est injoignable. Les mods déjà installés restent disponibles."

        is ApiException.Maintenance -> "Le serveur est en maintenance. Réessayez dans un instant."

        is ApiException.SaveConflict -> "Le profil a changé sur un autre appareil. Actualisez les mods puis choisissez le profil à garder."

        is ApiException.Http -> if (failure.status == 409) {
            "Le profil a changé sur un autre appareil. Actualisez les mods puis choisissez le profil à garder."
        } else {
            "Le serveur n’a pas pu préparer les mods. Réessayez après actualisation."
        }

        else -> "L’installation n’a pas abouti. Vérifiez l’espace libre puis réessayez."
    }

    fun run(action: suspend () -> Unit) {
        if (work?.isActive == true || controller.launching != null) return
        work = scope.launch {
            error = null
            try {
                action()
                state = repository.state(game)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = message(failure)
            } finally {
                work = null
            }
        }
    }

    LaunchedEffect(gameId, changes) {
        try {
            state = repository.state(game)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = message(failure)
        }
    }
    LaunchedEffect(gameId, online, available) {
        loading = true
        try {
            state = repository.refresh(game, online && available)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = message(failure)
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth().testTag("game-mods"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(game.title, style = MaterialTheme.typography.titleMedium)
        if (!online) Text("Hors ligne", color = Palette.TextDim, style = MaterialTheme.typography.labelLarge)
        if (loading) Text("Chargement des mods…", color = Palette.TextDim)
        if (!available && online) {
            SettingsGroup { SettingRow("Mods indisponibles sur ce serveur", icon = Lucide.Info) }
        }
        error?.let { text ->
            SettingsGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text, color = Palette.Danger)
                    GlassButton("Fermer", { error = null }, icon = Lucide.X)
                }
            }
        }
        if (busy) {
            val transfer = transfers[game.id]
            val fraction = transfer?.takeIf { it.total > 0 }?.let { it.received.toFloat() / it.total }
            PrimaryButton(if (fraction == null) "Préparation…" else "Téléchargement ${(fraction * 100).toInt()} %", {}, loading = true, progress = fraction)
            GlassButton("Annuler", { work?.cancel() }, icon = Lucide.X)
        }
        SettingsGroup {
            SettingRow(
                "Actualiser",
                icon = Lucide.RefreshCw,
                onClick = if (busy || !online || !available) {
                    null
                } else {
                    (
                        {
                            run { state = repository.refresh(game, true) }
                        }
                        )
                },
            )
            if (state.active?.releases?.isNotEmpty() == true) {
                GroupDivider()
                SettingRow(
                    "Réinstaller le profil",
                    icon = Lucide.Download,
                    onClick = if (busy) {
                        null
                    } else {
                        (
                            {
                                confirm = state.active!!.releases.map { it.selection } to false
                            }
                            )
                    },
                )
                GroupDivider()
                SettingRow("Désactiver tous les mods", icon = Lucide.Power, onClick = if (busy) null else ({ confirm = emptyList<ModSelection>() to false }))
            }
            if (state.pending) {
                GroupDivider()
                SettingRow(
                    if (state.profileConflict) "Garder le profil de cet appareil" else "Profil à synchroniser",
                    icon = Lucide.RefreshCw,
                    onClick = if (busy || !online || !available) {
                        null
                    } else {
                        (
                            {
                                confirm = state.active?.releases.orEmpty().map { it.selection } to false
                            }
                            )
                    },
                )
            }
            val remote = state.catalogue?.profile
            if (remote != null && (state.profileConflict || state.recoveryRequired || remote.selections != state.active?.profile?.selections.orEmpty())) {
                GroupDivider()
                SettingRow("Installer le profil du serveur", icon = Lucide.Download, onClick = if (busy) null else ({ confirm = remote.selections to false }))
            }
        }

        val releases = (state.catalogue?.releases.orEmpty() + state.installed).distinctBy { it.selection }
        if (!loading && releases.isEmpty() && (available || !online)) {
            SettingsGroup { SettingRow("Aucun mod disponible", icon = Lucide.Layers) }
        }
        for ((_, versions) in releases.groupBy { it.selection.id }) {
            SectionLabel(versions.first().name)
            SettingsGroup {
                versions.forEachIndexed { index, release ->
                    if (index > 0) GroupDivider()
                    val enabled = state.active?.releases?.any { it.selection == release.selection } == true
                    val installed = state.installed.any { it.selection == release.selection }
                    val reason = repository.compatibility(game, release)
                    val size = release.components.mapNotNull { it.artifact }.sumOf { it.size }
                    val variant = release.variantLabel.ifEmpty { release.selection.variant.takeUnless { it == "default" }.orEmpty() }
                    val label = listOf(release.selection.version, variant).filter { it.isNotBlank() }.joinToString(" · ")
                    SettingRow(
                        label,
                        Modifier.testTag("mod-${release.selection.id}-${release.selection.variant}"),
                        icon = if (enabled) Lucide.Check else Lucide.Layers,
                        value = when {
                            reason != null -> "Non compatible"
                            enabled -> "Activé"
                            installed -> "Installé"
                            size > 0 -> formatBytes(size)
                            else -> "Disponible"
                        },
                        onClick = if (busy) {
                            null
                        } else {
                            (
                                {
                                    if (reason != null) {
                                        error = reason
                                    } else {
                                        try {
                                            val localVariant = !enabled && state.active?.releases?.any {
                                                it.selection.id == release.selection.id && it.selection.version == release.selection.version
                                            } == true
                                            confirm = repository.selecting(state, release.selection, !enabled) to localVariant
                                        } catch (failure: Exception) {
                                            error = message(failure)
                                        }
                                    }
                                }
                                )
                        },
                    )
                    if (release.author.isNotBlank()) SettingRow(release.author, value = release.license.takeIf { it.isNotBlank() })
                    if (installed && !enabled) {
                        SettingRow("Supprimer de l’appareil", icon = Lucide.Trash2, onClick = if (busy) null else ({ removing = release.selection }))
                    }
                }
            }
        }
        val history = (
            state.history.map { it.releases.map { r -> r.selection } } +
                state.catalogue?.profileHistory.orEmpty().sortedByDescending { it.revision }.map { it.selections }
            )
            .distinctBy { it.toSet() }.take(20)
        if (history.isNotEmpty()) {
            SectionLabel("Profils précédents")
            SettingsGroup {
                history.forEachIndexed { index, selections ->
                    if (index > 0) GroupDivider()
                    SettingRow(
                        selections.joinToString(" + ") { selection ->
                            val release = releasesFor(state).firstOrNull { it.selection == selection }
                            "${release?.name ?: selection.id} ${selection.version} ${release?.variantLabel.orEmpty()}".trim()
                        }.ifEmpty { "Jeu original" },
                        icon = Lucide.RotateCcw,
                        onClick = if (busy) null else ({ confirm = selections to false }),
                    )
                }
            }
        }
    }
    confirm?.let { (selections, localVariant) ->
        val gameplay = selections.mapNotNull { selection -> releasesFor(state).firstOrNull { it.selection == selection } }
            .any { r -> r.components.any { it.installer != ModPlanner.TEXTURES } }
        val explanation = when {
            localVariant -> "Cette qualité s’applique à cet appareil."
            gameplay -> "Ce profil utilise sa propre progression. La progression du jeu original est conservée."
            else -> "La progression dans le jeu est conservée. Les sauvegardes instantanées demandent les mêmes mods."
        }
        ConfirmDialog(
            if (selections.isEmpty()) "Revenir au jeu original ?" else "Activer ce profil ?",
            explanation,
            "Appliquer",
            onConfirm = {
                confirm = null
                run { repository.apply(game, selections, online, localVariant) }
            },
            onDismiss = { confirm = null },
        )
    }
    removing?.let { selection ->
        ConfirmDialog(
            "Supprimer ce mod de l’appareil ?",
            "Les sauvegardes sont conservées. Un retour à cette version pourra demander un téléchargement.",
            "Supprimer",
            onConfirm = {
                removing = null
                run { repository.uninstall(game, selection) }
            },
            onDismiss = { removing = null },
            destructive = true,
        )
    }
}

private fun releasesFor(state: ModLocalState) = state.catalogue?.releases.orEmpty() + state.installed + state.history.flatMap { it.releases }
