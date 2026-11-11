package ovh.verbois.retrohub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ovh.verbois.retrohub.api.AccountSecurity
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.DeviceItem
import ovh.verbois.retrohub.api.PairingCode
import ovh.verbois.retrohub.api.PasswordChange
import ovh.verbois.retrohub.core.formatRelative
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.sync.SyncPhase
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.DialogField
import ovh.verbois.retrohub.ui.components.FormDialog
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.GlassPanel
import ovh.verbois.retrohub.ui.components.GroupDivider
import ovh.verbois.retrohub.ui.components.MessageBanner
import ovh.verbois.retrohub.ui.components.NavigationRow
import ovh.verbois.retrohub.ui.components.SectionLabel
import ovh.verbois.retrohub.ui.components.SettingRow
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.components.SliderRow
import ovh.verbois.retrohub.ui.components.SwitchRow
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.theme.Palette

/** Fonction du serveur qui accepte de renommer un appareil (docs/server-requests.md). */
const val FEATURE_DEVICE_RENAME = "device_rename"

private const val OFFLINE = "Cette action demande une connexion au serveur."

@Composable
internal fun AccountPage(controller: UiController) {
    val container = controller.container
    val account = container.account.account
    val settings by container.settings.settings.collectAsState()
    val status by container.serverStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Intervalle de sauvegarde : appliqué tout de suite, envoyé au compte quand le curseur s’arrête.
    var autoSave by remember { mutableStateOf(settings.autoSaveSeconds) }
    val initialAutoSave = remember { settings.autoSaveSeconds }
    LaunchedEffect(autoSave) {
        if (autoSave == initialAutoSave) return@LaunchedEffect
        delay(800)
        container.preferences.setAutoSave(autoSave)
    }
    val canRename = status?.features?.contains(FEATURE_DEVICE_RENAME) == true
    SettingsGroup {
        SettingRow(
            "Serveur",
            subtitle = account?.let { "${it.serverName} ${it.serverVersion}".trim() + " · " + it.server.removePrefix("https://") },
            icon = Lucide.Server,
        )
        GroupDivider()
        SettingRow("Compte", subtitle = account?.username, icon = Lucide.User)
        GroupDivider()
        SettingRow(
            "Cet appareil",
            subtitle = account?.deviceName + if (canRename) "" else " · le renommage arrivera avec une prochaine version du serveur",
            icon = Lucide.Smartphone,
            onClick = if (canRename) ({ renaming = true }) else null,
        ) { if (canRename) androidx.compose.material3.Icon(Lucide.Pencil, null, tint = Palette.TextDim) }
        GroupDivider()
        SettingRow("Déconnexion", icon = Lucide.LogOut, onClick = { confirm = true })
    }
    SectionLabel("Préférences de jeu · partagées avec le web")
    SettingsGroup {
        SwitchRow("Jeux en français", settings.preferFrench, { v ->
            container.preferences.setPreferFrench(v)
        }, null, Lucide.Languages)
        GroupDivider()
        SliderRow(
            "Sauvegarde automatique",
            autoSave.toFloat(),
            30f..300f,
            { v ->
                autoSave = (v / 30).toInt() * 30
                container.settings.update { it.copy(autoSaveSeconds = autoSave) }
            },
            "toutes les $autoSave s",
            null,
            Lucide.Save,
            steps = 8,
        )
        GroupDivider()
        NavigationRow(
            "Émulateur de chaque console",
            Lucide.Cpu,
            { controller.nav.go(Route.Settings(SettingsPage.CONSOLES)) },
        )
    }
    if (renaming) {
        FormDialog(
            "Renommer cet appareil",
            null,
            listOf(DialogField("Nom", initial = account?.deviceName.orEmpty())),
            "Renommer",
            onConfirm = { (name) ->
                val clean = name.trim()
                if (clean.isEmpty() || clean.length > 64) {
                    "Choisissez un nom de 1 à 64 caractères."
                } else {
                    try {
                        container.api.renameDevice(account?.deviceId.orEmpty(), clean)
                        container.account.renamed(clean)
                        renaming = false
                        null
                    } catch (error: ApiException.Offline) {
                        OFFLINE
                    } catch (error: ApiException) {
                        error.message ?: "Le serveur a refusé ce nom."
                    }
                }
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirm) {
        ConfirmDialog(
            "Se déconnecter ?",
            "Cet appareil ne sera plus relié à votre compte. Vos jeux et sauvegardes restent sur le téléphone ; les sauvegardes en attente partiront à la prochaine connexion.",
            "Se déconnecter",
            onConfirm = {
                confirm = false
                scope.launch { container.account.signOut() }
            },
            onDismiss = { confirm = false },
            destructive = true,
        )
    }
}

@Composable
internal fun SecurityPage(controller: UiController) {
    val container = controller.container
    val scope = rememberCoroutineScope()
    var security by remember { mutableStateOf<AccountSecurity?>(null) }
    var devices by remember { mutableStateOf<List<DeviceItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var changingPassword by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf<PairingCode?>(null) }
    var revokeDevice by remember { mutableStateOf<DeviceItem?>(null) }
    LaunchedEffect(refresh) {
        error = null
        try {
            security = container.api.security()
            devices = container.api.devices()
        } catch (failure: ApiException.Offline) {
            error = "Hors ligne : ces informations s’affichent avec une connexion au serveur."
        } catch (failure: ApiException) {
            error = failure.message ?: "Le serveur ne répond pas. Réessayez."
        }
    }
    error?.let { MessageBanner(it) { GlassButton("Réessayer", { refresh++ }, height = 40.dp) } }
    SettingsGroup {
        if (security?.localPassword != false) {
            SettingRow("Changer le mot de passe", icon = Lucide.KeyRound, onClick = { changingPassword = true })
            GroupDivider()
        }
        SettingRow("Relier un autre appareil", icon = Lucide.Link, onClick = {
            scope.launch {
                try {
                    pairing = container.api.createPairingCode()
                } catch (failure: ApiException.Offline) {
                    controller.show(OFFLINE)
                } catch (failure: ApiException) {
                    controller.show(failure.message ?: "Le code n’a pas pu être créé.")
                }
            }
        })
    }
    devices?.let { list ->
        SectionLabel("Appareils reliés")
        SettingsGroup {
            list.forEachIndexed { index, device ->
                if (index > 0) GroupDivider()
                val seen = parseInstant(device.lastSeenAt)?.let { "vu ${formatRelative(it).replaceFirstChar { c -> c.lowercase() }}" }
                SettingRow(
                    device.name + if (device.current) " · cet appareil" else "",
                    subtitle = listOfNotNull(platformLabel(device.platform), seen).joinToString(" · "),
                    icon = if (device.platform == "android" || device.platform == "ios") Lucide.Smartphone else Lucide.Monitor,
                    onClick = if (device.current) null else ({ revokeDevice = device }),
                ) { if (!device.current) Text("Retirer", style = MaterialTheme.typography.labelMedium, color = Palette.Danger) }
            }
        }
    }
    security?.let { info ->
        if (info.sessions.isNotEmpty()) {
            SectionLabel("Sessions du navigateur")
            SettingsGroup {
                info.sessions.forEachIndexed { index, session ->
                    if (index > 0) GroupDivider()
                    SettingRow(
                        session.device.ifBlank { "Navigateur" } + if (session.current) " · actuelle" else "",
                        subtitle = parseInstant(session.createdAt)?.let { "ouverte ${formatRelative(it).replaceFirstChar { c -> c.lowercase() }}" },
                        icon = Lucide.Globe,
                        onClick =
                        if (session.current) {
                            null
                        } else {
                            {
                                scope.launch {
                                    try {
                                        container.api.revokeSession(session.id)
                                        controller.show("Session fermée.")
                                        refresh++
                                    } catch (failure: ApiException) {
                                        controller.show(failure.message ?: OFFLINE)
                                    }
                                }
                            }
                        },
                    ) { if (!session.current) Text("Fermer", style = MaterialTheme.typography.labelMedium, color = Palette.Danger) }
                }
            }
        }
    }
    if (changingPassword) {
        FormDialog(
            "Changer le mot de passe",
            "Les autres sessions du navigateur seront fermées. Ce téléphone reste relié.",
            listOf(
                DialogField("Mot de passe actuel", secret = true),
                DialogField("Nouveau mot de passe", secret = true),
                DialogField("Confirmer", secret = true),
            ),
            "Changer",
            onConfirm = { (current, new, again) ->
                when {
                    new.length < 8 -> "Le nouveau mot de passe doit faire au moins 8 caractères."

                    new != again -> "Les deux mots de passe sont différents."

                    else ->
                        try {
                            container.api.changePassword(PasswordChange(current, new))
                            changingPassword = false
                            controller.show("Mot de passe changé.")
                            refresh++
                            null
                        } catch (failure: ApiException.Offline) {
                            OFFLINE
                        } catch (failure: ApiException) {
                            failure.message ?: "Le mot de passe actuel n’est pas le bon."
                        }
                }
            },
            onDismiss = { changingPassword = false },
        )
    }
    pairing?.let { code -> PairingDialog(code) { pairing = null } }
    revokeDevice?.let { device ->
        ConfirmDialog(
            "Retirer « ${device.name} » ?",
            "Cet appareil ne pourra plus synchroniser. Il faudra le relier de nouveau.",
            "Retirer",
            onConfirm = {
                revokeDevice = null
                scope.launch {
                    try {
                        container.api.revokeDevice(device.id)
                        refresh++
                    } catch (failure: ApiException) {
                        controller.show(failure.message ?: OFFLINE)
                    }
                }
            },
            onDismiss = { revokeDevice = null },
            destructive = true,
        )
    }
}

private fun platformLabel(platform: String): String? = when (platform) {
    "android" -> "Android"
    "ios" -> "iPhone ou iPad"
    "web" -> "Navigateur"
    "" -> null
    else -> platform
}

@Composable
private fun PairingDialog(code: PairingCode, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.widthIn(max = 460.dp).padding(24.dp)) {
            Column(
                Modifier.background(Palette.BackgroundHigh).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Code d’appairage", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Sur l’autre appareil, choisissez « J’ai un code » et saisissez :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                )
                Text(
                    code.code,
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Field).padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
                )
                parseInstant(code.expiresAt)?.let {
                    Text("Valable jusqu’à ${ovh.verbois.retrohub.core.formatDate(it)}.", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GlassButton("Fermer", onDismiss) }
            }
        }
    }
}

@Composable
internal fun SyncPage(controller: UiController) {
    val container = controller.container
    val repo = container.settings
    val sync by container.sync.status.collectAsState()
    val online by container.platform.online.collectAsState()
    val settings by repo.settings.collectAsState()
    val state =
        when {
            sync.phase == SyncPhase.SYNCING -> "Synchronisation en cours…"
            !online -> "Hors ligne : tout partira dès le retour du réseau."
            sync.phase == SyncPhase.MAINTENANCE -> "Serveur en maintenance : nouvel essai automatique."
            sync.conflicts > 0 -> "${sync.conflicts} sauvegarde(s) attendent votre choix (voir la fiche du jeu)."
            sync.pendingSaves > 0 || sync.pendingSessions > 0 -> "${sync.pendingSaves} sauvegarde(s) et ${sync.pendingSessions} session(s) en attente."
            else -> "Tout est synchronisé."
        }
    SettingsGroup {
        SettingRow("État", subtitle = state, icon = if (sync.upToDate) Lucide.CloudCheck else Lucide.Cloud)
        sync.lastSuccess?.let {
            GroupDivider()
            SettingRow("Dernière synchronisation", subtitle = formatRelative(it), icon = Lucide.Clock)
        }
        GroupDivider()
        SettingRow("Synchroniser maintenant", icon = Lucide.RefreshCw, onClick = { controller.syncNow() })
    }
    SectionLabel("Automatique")
    SettingsGroup {
        SwitchRow("Synchroniser tout seul", settings.autoSync, { v ->
            repo.update { it.copy(autoSync = v) }
            container.applyPeriodicSync()
        }, null, Lucide.Cloud)
        if (settings.autoSync) {
            GroupDivider()
            ChoiceRow(
                "En arrière-plan",
                listOf(0, 1, 3, 6, 12, 24),
                settings.periodicSyncHours,
                {
                    if (it == 0) {
                        "Jamais"
                    } else if (it == 1) {
                        "Toutes les heures"
                    } else {
                        "Toutes les $it h"
                    }
                },
                { v ->
                    repo.update { it.copy(periodicSyncHours = v) }
                    container.applyPeriodicSync()
                },
                null,
                Lucide.History,
                recommended = 6,
            )
        }
        GroupDivider()
        SwitchRow("Prévenir en cas de conflit", settings.conflictNotifications, { v ->
            repo.update { it.copy(conflictNotifications = v) }
        }, null, Lucide.Bell)
    }
    Text(
        "L’historique des sauvegardes d’un jeu, avec retour à une version précédente, se trouve dans ses options (fiche du jeu).",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextDim,
    )
}
