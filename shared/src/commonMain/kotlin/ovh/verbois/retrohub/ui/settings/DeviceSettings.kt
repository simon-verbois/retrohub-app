package ovh.verbois.retrohub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ovh.verbois.retrohub.core.Pin
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.HintsMode
import ovh.verbois.retrohub.emulation.HomeOrder
import ovh.verbois.retrohub.emulation.MenuShortcut
import ovh.verbois.retrohub.emulation.Orientation
import ovh.verbois.retrohub.emulation.PerformanceProfile
import ovh.verbois.retrohub.emulation.PortraitPosition
import ovh.verbois.retrohub.emulation.StorageLocation
import ovh.verbois.retrohub.emulation.TextSize
import ovh.verbois.retrohub.emulation.ThemeMode
import ovh.verbois.retrohub.library.StorageUsage
import ovh.verbois.retrohub.resources.Res
import ovh.verbois.retrohub.resources.setting_audio_description
import ovh.verbois.retrohub.resources.setting_battery_description
import ovh.verbois.retrohub.resources.setting_cache_description
import ovh.verbois.retrohub.resources.setting_cleanup_description
import ovh.verbois.retrohub.resources.setting_deadzone_description
import ovh.verbois.retrohub.resources.setting_favorites_description
import ovh.verbois.retrohub.resources.setting_fps_description
import ovh.verbois.retrohub.resources.setting_fullscreen
import ovh.verbois.retrohub.resources.setting_fullscreen_description
import ovh.verbois.retrohub.resources.setting_hide_touch_description
import ovh.verbois.retrohub.resources.setting_hints_description
import ovh.verbois.retrohub.resources.setting_latency_description
import ovh.verbois.retrohub.resources.setting_menu_shortcut_description
import ovh.verbois.retrohub.resources.setting_motion_description
import ovh.verbois.retrohub.resources.setting_multiplayer_description
import ovh.verbois.retrohub.resources.setting_mute_fast_forward_description
import ovh.verbois.retrohub.resources.setting_navigation_sound_description
import ovh.verbois.retrohub.resources.setting_offline_description
import ovh.verbois.retrohub.resources.setting_order_description
import ovh.verbois.retrohub.resources.setting_orientation_description
import ovh.verbois.retrohub.resources.setting_overwrite_description
import ovh.verbois.retrohub.resources.setting_parental_description
import ovh.verbois.retrohub.resources.setting_performance_description
import ovh.verbois.retrohub.resources.setting_portrait_image_description
import ovh.verbois.retrohub.resources.setting_rumble_description
import ovh.verbois.retrohub.resources.setting_sensitivity_description
import ovh.verbois.retrohub.resources.setting_start_last_description
import ovh.verbois.retrohub.resources.setting_text_description
import ovh.verbois.retrohub.resources.setting_theme_description
import ovh.verbois.retrohub.resources.setting_touch_opacity_description
import ovh.verbois.retrohub.resources.setting_touch_size_description
import ovh.verbois.retrohub.resources.setup_background_description
import ovh.verbois.retrohub.resources.setup_cellular_description
import ovh.verbois.retrohub.resources.setup_haptics_description
import ovh.verbois.retrohub.resources.setup_resume_description
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.DialogField
import ovh.verbois.retrohub.ui.components.FilterChip
import ovh.verbois.retrohub.ui.components.FormDialog
import ovh.verbois.retrohub.ui.components.GroupDivider
import ovh.verbois.retrohub.ui.components.NavigationRow
import ovh.verbois.retrohub.ui.components.SectionLabel
import ovh.verbois.retrohub.ui.components.SettingRow
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.components.SliderRow
import ovh.verbois.retrohub.ui.components.SwitchRow
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.library.StorageSummary
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearancePage(controller: UiController) {
    val repo = controller.container.settings
    val settings by repo.settings.collectAsState()
    SettingsGroup {
        ChoiceRow("Thème", ThemeMode.entries, settings.theme, {
            it.label
        }, { v -> repo.update { it.copy(theme = v) } }, null, Lucide.Palette, recommended = ThemeMode.SYSTEM)
        GroupDivider()
        SwitchRow("Fond animé", settings.animatedBackground, { v ->
            repo.update { it.copy(animatedBackground = v) }
        }, null, Lucide.Sparkles)
        GroupDivider()
        SwitchRow("Réduire les animations", settings.reduceMotion, { v ->
            repo.update { it.copy(reduceMotion = v) }
        }, null, Lucide.Pause)
        GroupDivider()
        ChoiceRow("Taille du texte", TextSize.entries, settings.textSize, { it.label }, { v ->
            repo.update { it.copy(textSize = v) }
        }, null, Lucide.Type, recommended = TextSize.NORMAL)
        GroupDivider()
        SwitchRow("Sons de navigation", settings.navigationSounds, { v ->
            repo.update { it.copy(navigationSounds = v) }
        }, null, Lucide.Music)
    }
    SectionLabel("Accueil")
    SettingsGroup {
        ChoiceRow("Ordre des jeux", HomeOrder.entries, settings.homeOrder, { it.label }, { v ->
            repo.update { it.copy(homeOrder = v) }
        }, null, Lucide.List, recommended = HomeOrder.RECENT)
        GroupDivider()
        SwitchRow("Seulement les jeux téléchargés", settings.offlineOnly, { v ->
            repo.update { it.copy(offlineOnly = v) }
        }, null, Lucide.WifiOff)
        GroupDivider()
        SwitchRow("Relancer le dernier jeu", settings.startOnLastGame, { v ->
            repo.update { it.copy(startOnLastGame = v) }
        }, null, Lucide.Play)
    }
    SectionLabel("Consoles affichées")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Consoles.all.forEach { spec ->
            val shown = spec.id !in settings.hiddenConsoles
            FilterChip(spec.shortName, shown, {
                repo.update { it.copy(hiddenConsoles = if (shown) it.hiddenConsoles + spec.id else it.hiddenConsoles - spec.id) }
            }, dot = Color(spec.color))
        }
    }
}

@Composable
internal fun DisplayPage(controller: UiController) {
    val repo = controller.container.settings
    val settings by repo.settings.collectAsState()
    SettingsGroup {
        SwitchRow(stringResource(Res.string.setting_fullscreen), settings.useDisplayCutout, { v ->
            repo.update { it.copy(useDisplayCutout = v) }
        }, null, Lucide.Maximize)
        GroupDivider()
        ChoiceRow("Orientation en jeu", Orientation.entries, settings.orientation, { it.label }, { v ->
            repo.update { it.copy(orientation = v) }
        }, null, Lucide.Smartphone, recommended = Orientation.AUTO)
        GroupDivider()
        ChoiceRow("Image en portrait", PortraitPosition.entries, settings.portraitPosition, { it.label }, { v ->
            repo.update { it.copy(portraitPosition = v) }
        }, null, Lucide.Rows2, recommended = PortraitPosition.TOP)
        GroupDivider()
        SwitchRow("Compteur d’images", settings.showFps, { v ->
            repo.update { it.copy(showFps = v) }
        }, null, Lucide.Gauge)
    }
}

@Composable
internal fun ControlsPage(controller: UiController) {
    val repo = controller.container.settings
    val settings by repo.settings.collectAsState()
    val gamepad by controller.container.platform.gamepadConnected.collectAsState()
    SectionLabel("Manette")
    SettingsGroup {
        SettingRow(
            if (gamepad) "Manette branchée" else "Aucune manette détectée",
            subtitle =
            if (gamepad) {
                "Elle pilote l’interface et les jeux. En jeu, ${settings.menuShortcut.label} ouvre le menu."
            } else {
                "Branchez ou appairez une manette Bluetooth : elle est reconnue automatiquement."
            },
            icon = Lucide.Gamepad2,
        )
        GroupDivider()
        ChoiceRow("Ouvrir le menu en jeu", MenuShortcut.entries, settings.menuShortcut, { it.label }, { v ->
            repo.update { it.copy(menuShortcut = v) }
        }, null, Lucide.Menu, recommended = MenuShortcut.SELECT_START)
        GroupDivider()
        SwitchRow("Vibrations des jeux", settings.rumble, { v ->
            repo.update { it.copy(rumble = v) }
        }, null, Lucide.Vibrate)
        GroupDivider()
        SwitchRow("Plusieurs joueurs", settings.multiplayer, { v ->
            repo.update { it.copy(multiplayer = v) }
        }, null, Lucide.Swords)
        GroupDivider()
        SliderRow(
            "Zone morte des sticks",
            settings.stickDeadzone,
            0f..0.4f,
            { v -> repo.update { it.copy(stickDeadzone = v) } },
            "${(settings.stickDeadzone * 100).toInt()} %",
            null,
            Lucide.Joystick,
        )
        GroupDivider()
        SliderRow(
            "Sensibilité des sticks",
            settings.stickSensitivity,
            0.5f..1.5f,
            { v -> repo.update { it.copy(stickSensitivity = v) } },
            "${(settings.stickSensitivity * 100).toInt()} %",
            null,
            Lucide.Gauge,
        )
        GroupDivider()
        SwitchRow("Masquer les commandes tactiles", settings.hideTouchWithGamepad, { v ->
            repo.update { it.copy(hideTouchWithGamepad = v) }
        }, null, Lucide.EyeOff)
        GroupDivider()
        ChoiceRow("Aides de boutons", HintsMode.entries, settings.hints, {
            it.label
        }, { v -> repo.update { it.copy(hints = v) } }, null, Lucide.Keyboard)
    }
    SectionLabel("Commandes tactiles")
    SettingsGroup {
        SliderRow("Opacité", settings.touchOpacity, 0.2f..1f, { v ->
            repo.update { it.copy(touchOpacity = v) }
        }, "${(settings.touchOpacity * 100).toInt()} %", subtitle = null, icon = Lucide.Eye)
        GroupDivider()
        SliderRow("Taille", settings.touchScale, 0.75f..1.35f, { v ->
            repo.update { it.copy(touchScale = v) }
        }, "${(settings.touchScale * 100).toInt()} %", subtitle = null, icon = Lucide.Hand)
        GroupDivider()
        SwitchRow("Retour au toucher", settings.haptics, { v ->
            repo.update { it.copy(haptics = v) }
        }, null, Lucide.Vibrate)
    }
}

@Composable
internal fun AudioPage(controller: UiController) {
    val repo = controller.container.settings
    val settings by repo.settings.collectAsState()
    SettingsGroup {
        SwitchRow("Son des jeux", settings.audio, { v ->
            repo.update { it.copy(audio = v) }
        }, null, if (settings.audio) Lucide.Volume2 else Lucide.VolumeX)
        GroupDivider()
        SwitchRow("Couper le son en avance rapide", settings.muteFastForward, { v ->
            repo.update { it.copy(muteFastForward = v) }
        }, null, Lucide.FastForward)
        GroupDivider()
        SwitchRow("Son à faible latence", settings.lowLatencyAudio, { v ->
            repo.update { it.copy(lowLatencyAudio = v) }
        }, null, Lucide.Zap)
    }
}

@Composable
internal fun PerformancePage(controller: UiController) {
    val repo = controller.container.settings
    val settings by repo.settings.collectAsState()
    SettingsGroup {
        ChoiceRow(
            "Profil",
            PerformanceProfile.entries,
            settings.performance,
            { it.label },
            { v -> repo.update { it.copy(performance = v) } },
            null,
            Lucide.Gauge,
            recommended = PerformanceProfile.BALANCED,
        )
        GroupDivider()
        SwitchRow("Économie de batterie", settings.batterySaver, { v ->
            repo.update { it.copy(batterySaver = v) }
        }, null, Lucide.Battery)
    }
    SectionLabel("Parties")
    SettingsGroup {
        SwitchRow("Reprendre là où j’en étais", settings.resumeLastState, { v ->
            repo.update { it.copy(resumeLastState = v) }
        }, null, Lucide.History)
        GroupDivider()
        SwitchRow("Confirmer avant de remplacer", settings.confirmOverwrite, { v ->
            repo.update { it.copy(confirmOverwrite = v) }
        }, null, Lucide.Save)
    }
}

@Composable
internal fun StoragePage(controller: UiController) {
    val imageLoader = SingletonImageLoader.get(LocalPlatformContext.current)
    val container = controller.container
    val repo = container.settings
    val settings by repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var moving by remember { mutableStateOf(false) }
    var confirmCovers by remember { mutableStateOf(false) }
    val sdCard = remember { container.platform.sdCardRoot() }
    LaunchedEffect(moving) { usage = container.downloads.usage() }
    usage?.let { StorageSummary(it) }
    SectionLabel("Téléchargements")
    SettingsGroup {
        SwitchRow("Télécharger en données mobiles", settings.cellularDownloads, { v ->
            repo.update { it.copy(cellularDownloads = v) }
        }, null, Lucide.Globe)
        GroupDivider()
        SwitchRow("Télécharger les favoris", settings.autoDownloadFavorites, { v ->
            repo.update { it.copy(autoDownloadFavorites = v) }
        }, null, Lucide.Heart)
        GroupDivider()
        ChoiceRow(
            "Libérer de la place",
            listOf(0, 30, 60, 90),
            settings.cleanupDays,
            { if (it == 0) "Jamais" else "Après $it jours" },
            { v -> repo.update { it.copy(cleanupDays = v) } },
            null,
            Lucide.Trash2,
            recommended = 0,
        )
        GroupDivider()
        NavigationRow("Gérer les jeux téléchargés", Lucide.HardDriveDownload, {
            controller.nav.section(Section.DOWNLOADS)
        }, "Supprimer un jeu garde ses sauvegardes.")
    }
    SectionLabel("Emplacement")
    SettingsGroup {
        if (sdCard != null) {
            ChoiceRow(
                "Jeux enregistrés sur",
                StorageLocation.entries,
                settings.storage,
                { it.label },
                { v ->
                    if (v != settings.storage && !moving) {
                        moving = true
                        scope.launch {
                            repo.updateNow { it.copy(storage = v) }
                            runCatching { container.downloads.moveAll() }
                                .onSuccess { controller.show("Les jeux ont été déplacés.") }
                                .onFailure { controller.show("Le déplacement n’a pas abouti : vérifiez la place libre.") }
                            moving = false
                        }
                    }
                },
                if (moving) "Déplacement des jeux en cours…" else "Les sauvegardes restent dans le téléphone.",
                Lucide.MemoryStick,
                recommended = StorageLocation.INTERNAL,
            )
        } else {
            SettingRow("Mémoire du téléphone", subtitle = "Aucune carte SD détectée.", icon = Lucide.MemoryStick)
        }
        GroupDivider()
        SettingRow("Vider le cache des jaquettes", subtitle = null, icon = Lucide.Image, onClick = {
            confirmCovers = true
        })
    }
    usage?.let { Text("Espace libre : ${formatBytes(it.free)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim) }
    if (confirmCovers) {
        ConfirmDialog("Vider le cache des jaquettes ?", "Elles seront téléchargées de nouveau.", "Vider", onConfirm = {
            confirmCovers = false
            scope.launch {
                imageLoader.memoryCache?.clear()
                withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
                usage = container.downloads.usage()
            }
        }, onDismiss = { confirmCovers = false })
    }
}

@Composable
internal fun ParentalPage(controller: UiController) {
    val repo = controller.container.settings
    val settings by repo.settings.collectAsState()
    val games by controller.container.library.games.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val enabled = settings.pinHash.isNotEmpty()
    SettingsGroup {
        SettingRow(if (enabled) "Changer le code" else "Choisir un code", icon = Lucide.Lock, onClick = { editing = true })
        if (enabled) {
            GroupDivider()
            SwitchRow("Protéger les réglages", settings.lockSettings, { v ->
                repo.update { it.copy(lockSettings = v) }
            }, null, Lucide.Settings)
            GroupDivider()
            SettingRow("Retirer le code", icon = Lucide.X, onClick = { removing = true })
        }
    }
    if (enabled) {
        SectionLabel("Jeux protégés")
        if (games.isEmpty()) Text("Aucun jeu dans la bibliothèque.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        SettingsGroup {
            games.sortedBy { it.title.lowercase() }.forEachIndexed { index, game ->
                if (index > 0) GroupDivider()
                val locked = game.playGameId in settings.lockedGames
                SwitchRow(game.title, locked, { v ->
                    repo.update { it.copy(lockedGames = if (v) it.lockedGames + game.playGameId else it.lockedGames - game.playGameId) }
                }, Consoles.name(game.console))
            }
        }
    }
    if (editing) {
        FormDialog(
            if (enabled) "Nouveau code" else "Choisir un code",
            "4 à 8 chiffres.",
            listOf(DialogField("Code", secret = true, digits = true), DialogField("Confirmer le code", secret = true, digits = true)),
            "Enregistrer",
            onConfirm = { (pin, again) ->
                when {
                    !Pin.valid(pin) -> "Le code doit faire de 4 à 8 chiffres."

                    pin != again -> "Les deux codes sont différents."

                    else -> {
                        repo.updateNow { it.copy(pinHash = Pin.hash(pin)) }
                        controller.settingsUnlocked = true
                        editing = false
                        null
                    }
                }
            },
            onDismiss = { editing = false },
        )
    }
    if (removing) {
        ConfirmDialog("Retirer le code ?", "Les réglages et tous les jeux redeviennent accessibles.", "Retirer", onConfirm = {
            removing = false
            repo.update { it.copy(pinHash = "", lockedGames = emptySet()) }
        }, onDismiss = { removing = false }, destructive = true)
    }
}
