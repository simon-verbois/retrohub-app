package ovh.verbois.retrohub.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.RemoteSave
import ovh.verbois.retrohub.core.formatDate
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.game.Cheat
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.DialogField
import ovh.verbois.retrohub.ui.components.FormDialog
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.GroupDivider
import ovh.verbois.retrohub.ui.components.SectionLabel
import ovh.verbois.retrohub.ui.components.SettingRow
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.components.SwitchRow
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/** Codes de triche, historique des sauvegardes et protection d’un jeu. */
@Composable
internal fun GameOptionsPage(controller: UiController, gameId: String) {
    val container = controller.container
    val games by container.library.games.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val game = games.firstOrNull { it.id == gameId } ?: return
    val scope = rememberCoroutineScope()
    var cheats by remember { mutableStateOf<List<Cheat>>(emptyList()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(game.playGameId) { cheats = container.cheats.get(game.playGameId) }
    val save = { list: List<Cheat> ->
        cheats = list
        scope.launch { container.cheats.put(game.playGameId, list) }
    }
    GamePhoneActions(controller, game)
    SectionLabel("Codes de triche")
    if (cheats.isNotEmpty()) {
        SettingsGroup {
            cheats.forEachIndexed { index, cheat ->
                if (index > 0) GroupDivider()
                SwitchRow(cheat.name.ifBlank { "Code ${index + 1}" }, cheat.enabled, { v ->
                    save(cheats.mapIndexed { i, c -> if (i == index) c.copy(enabled = v) else c })
                }, cheat.code.lines().first() + if (cheat.code.lines().size > 1) " …" else "")
                SettingRow("Modifier ou supprimer", icon = Lucide.Pencil, onClick = { editing = index })
            }
        }
    }
    GlassButton("Ajouter un code", { editing = -1 }, icon = Lucide.Plus)

    SectionLabel("Historique des sauvegardes")
    SaveHistory(controller, game.playGameId, game.console)

    if (settings.pinHash.isNotEmpty()) {
        SectionLabel("Contrôle parental")
        SettingsGroup {
            SwitchRow("Protéger ce jeu", game.playGameId in settings.lockedGames, { v ->
                container.settings.update { it.copy(lockedGames = if (v) it.lockedGames + game.playGameId else it.lockedGames - game.playGameId) }
            }, null, Lucide.Lock)
        }
    }

    editing?.let { index ->
        val current = cheats.getOrNull(index)
        var deleting by remember { mutableStateOf(false) }
        if (deleting) {
            ConfirmDialog("Supprimer ce code ?", current?.name ?: "", "Supprimer", onConfirm = {
                save(cheats.filterIndexed { i, _ -> i != index })
                editing = null
            }, onDismiss = { deleting = false }, destructive = true)
        } else {
            FormDialog(
                if (current == null) "Nouveau code" else "Modifier le code",
                if (current != null) "Pour le supprimer, videz le champ « Code »." else null,
                listOf(
                    DialogField("Nom (facultatif)", initial = current?.name.orEmpty()),
                    DialogField("Code", initial = current?.code.orEmpty(), singleLine = false),
                ),
                "Enregistrer",
                onConfirm = { (name, code) ->
                    val clean = code.trim()
                    when {
                        clean.isEmpty() && current != null -> {
                            deleting = true
                            null
                        }

                        clean.isEmpty() -> "Saisissez le code."

                        else -> {
                            val cheat = Cheat(name.trim(), clean, current?.enabled ?: true)
                            save(if (current == null) cheats + cheat else cheats.mapIndexed { i, c -> if (i == index) cheat else c })
                            editing = null
                            null
                        }
                    }
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun SaveHistory(controller: UiController, playGameId: String, console: String) {
    val container = controller.container
    val scope = rememberCoroutineScope()
    var saves by remember { mutableStateOf<List<LocalSave>>(emptyList()) }
    val versions = remember { mutableStateMapOf<String, List<RemoteSave>>() }
    var restoring by remember { mutableStateOf<RemoteSave?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(playGameId, refresh) { saves = container.saveStore.forGame(playGameId).sortedWith(compareBy({ it.kind }, { it.slot })) }
    if (saves.isEmpty()) {
        Text("Pas encore de sauvegarde pour ce jeu.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        return
    }
    SettingsGroup {
        saves.forEachIndexed { index, save ->
            if (index > 0) GroupDivider()
            val key = "${save.kind}-${save.slot}"
            val label =
                when {
                    save.kind == "sram" -> "Sauvegarde du jeu"
                    save.slot == 0 -> "Sauvegarde automatique"
                    else -> "Emplacement ${save.slot}"
                }
            SettingRow(
                label,
                subtitle = parseInstant(save.localPlayedAt)?.let { "Dernière partie ${formatDate(it)}" } ?: "Version ${save.baseRevision}",
                icon = Lucide.History,
                onClick = {
                    if (versions.containsKey(key)) {
                        versions.remove(key)
                    } else {
                        scope.launch {
                            try {
                                versions[key] = container.api.saveVersions(playGameId, save.kind, save.slot).sortedByDescending { it.revision }
                            } catch (error: ApiException.Offline) {
                                controller.show("L’historique s’affiche avec une connexion au serveur.")
                            } catch (error: ApiException) {
                                controller.show(error.message ?: "Historique indisponible pour le moment.")
                            }
                        }
                    }
                },
            ) { androidx.compose.material3.Icon(if (versions.containsKey(key)) Lucide.ChevronUp else Lucide.ChevronDown, null, tint = Palette.TextDim) }
            versions[key]?.forEach { version ->
                val current = version.revision == save.baseRevision
                SettingRow(
                    "Version ${version.revision}" + if (current) " · actuelle" else "",
                    subtitle =
                    listOfNotNull(
                        parseInstant(version.playedAt ?: version.createdAt)?.let { formatDate(it) },
                        version.device?.name,
                    ).joinToString(" · "),
                    onClick = if (current) null else ({ restoring = version }),
                ) { if (!current) Text("Revenir", style = MaterialTheme.typography.labelMedium, color = Palette.Accent) }
            }
        }
    }
    restoring?.let { version ->
        ConfirmDialog(
            "Revenir à la version ${version.revision} ?",
            "Elle remplace la sauvegarde actuelle, ici et sur le web.",
            "Revenir",
            onConfirm = {
                restoring = null
                scope.launch {
                    try {
                        container.saveSync.restore(playGameId, console, version, container.library.saveEpoch())
                        controller.show("Sauvegarde restaurée.")
                        versions.clear()
                        refresh++
                        container.sync.refreshCounts()
                    } catch (error: ApiException.Offline) {
                        controller.show("La restauration demande une connexion au serveur.")
                    } catch (error: ApiException) {
                        controller.show(error.message ?: "La restauration n’a pas abouti.")
                    }
                }
            },
            onDismiss = { restoring = null },
        )
    }
}

/** Ce que la fiche du jeu proposait : mise à jour, partie neuve, conflit, suppression. */
@Composable
private fun GamePhoneActions(controller: UiController, game: ovh.verbois.retrohub.library.Game) {
    val container = controller.container
    val scope = rememberCoroutineScope()
    val downloads by container.downloads.states.collectAsState()
    val running = downloads[game.playGameId] is ovh.verbois.retrohub.library.DownloadState.Running
    var conflict by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmFresh by remember { mutableStateOf(false) }
    LaunchedEffect(game.playGameId) { conflict = container.saveStore.forGame(game.playGameId).any { it.inConflict } }
    if (!game.downloaded && !running && !conflict) return
    SettingsGroup {
        var first = true
        val divider = @Composable {
            if (!first) GroupDivider()
            first = false
        }
        if (conflict) {
            divider()
            SettingRow("Choisir la sauvegarde à garder", icon = Lucide.CloudAlert, onClick = {
                controller.nav.go(ovh.verbois.retrohub.ui.nav.Route.Conflict(game.playGameId))
            })
        }
        if (running) {
            divider()
            SettingRow("Annuler le téléchargement", icon = Lucide.X, onClick = { container.downloads.cancel(game.playGameId) })
        } else if (game.updateAvailable) {
            divider()
            SettingRow("Mettre à jour le jeu", icon = Lucide.Download, onClick = { container.downloads.start(game) })
        }
        if (game.downloaded) {
            divider()
            SettingRow("Nouvelle partie", icon = Lucide.RotateCcw, onClick = { confirmFresh = true })
            divider()
            SettingRow("Supprimer du téléphone", icon = Lucide.Trash2, onClick = { confirmDelete = true })
        }
    }
    if (confirmFresh) {
        ConfirmDialog("Nouvelle partie ?", "La dernière partie reste gardée.", "Jouer", onConfirm = {
            confirmFresh = false
            controller.play(game, fresh = true)
        }, onDismiss = { confirmFresh = false })
    }
    if (confirmDelete) {
        ConfirmDialog("Supprimer du téléphone ?", "Les sauvegardes sont gardées.", "Supprimer", onConfirm = {
            confirmDelete = false
            scope.launch {
                container.downloads.delete(game.playGameId)
                controller.nav.back()
            }
        }, onDismiss = { confirmDelete = false }, destructive = true)
    }
}
