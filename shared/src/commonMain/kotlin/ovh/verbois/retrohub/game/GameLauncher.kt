package ovh.verbois.retrohub.game

import okio.FileSystem
import okio.Path.Companion.toPath
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.core.DeviceClass
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.data.AppPaths
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.data.RoomSaveStore
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.CoreOptions
import ovh.verbois.retrohub.emulation.GameLayouts
import ovh.verbois.retrohub.library.DownloadManager
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.library.LibraryRepository
import ovh.verbois.retrohub.sync.SaveSync
import ovh.verbois.retrohub.sync.file

sealed interface LaunchCheck {
    data class Ready(val plan: LaunchPlan) : LaunchCheck

    data object NeedsDownload : LaunchCheck

    data object NeedsProgressionChoice : LaunchCheck

    data class Conflicts(val saves: List<LocalSave>) : LaunchCheck

    /** Lancement impossible pour l’instant ; [message] dit au joueur quoi faire. */
    data class Blocked(val message: String) : LaunchCheck
}

/**
 * Prépare le lancement d’un jeu (docs/native-api.md, « Avant de lancer un jeu ») : catalogue à
 * jour, sauvegardes récupérées ou conflit, BIOS, puis choix des sauvegardes compatibles.
 */
class GameLauncher(
    private val library: LibraryRepository,
    private val downloads: DownloadManager,
    private val saveSync: SaveSync,
    private val saves: RoomSaveStore,
    private val fs: FileSystem,
    private val paths: AppPaths,
    private val availableCores: () -> Set<String>,
    private val deviceClass: DeviceClass,
    private val cheats: suspend (String) -> List<Cheat> = { emptyList() },
    private val batteryLow: () -> Boolean = { false },
) {
    suspend fun prepare(start: Game, chosen: AppSettings, online: Boolean, portrait: Boolean, fresh: Boolean = false): LaunchCheck {
        // Batterie faible : les réglages économes s’appliquent à cette partie, sans changer le profil.
        val saver = chosen.batterySaver && chosen.performance != ovh.verbois.retrohub.emulation.PerformanceProfile.BATTERY && batteryLow()
        val settings = if (saver) chosen.copy(performance = ovh.verbois.retrohub.emulation.PerformanceProfile.BATTERY) else chosen
        val spec = start.spec ?: return LaunchCheck.Blocked("Cette console ne se joue pas encore dans l’application.")
        var game = start
        if (online) {
            try {
                library.refresh()
                game = library.find(start.id) ?: start
            } catch (_: ApiException.Offline) {
            } catch (_: ApiException.Maintenance) {
            }
        }
        if (game.remote.progressionChoiceRequired) return LaunchCheck.NeedsProgressionChoice
        val rom = game.local ?: return LaunchCheck.NeedsDownload
        if (!fs.exists(rom.path.toPath())) return LaunchCheck.NeedsDownload

        val chosen = settings.console(spec.id).core?.takeIf { it in spec.cores } ?: spec.cores.first()
        val cores = availableCores()
        val core = if (chosen in cores) chosen else spec.cores.firstOrNull { it in cores }
        if (core == null) {
            return LaunchCheck.Blocked(
                "L’émulateur ${spec.name} manque dans cette version de l’application. Installez la dernière version.",
            )
        }

        if (online) {
            try {
                val conflicts = saveSync.pull(game.remote)
                if (conflicts.isNotEmpty()) return LaunchCheck.Conflicts(conflicts)
            } catch (_: ApiException.Offline) {
            } catch (_: ApiException.Maintenance) {
            }
        }
        val pendingConflicts = saves.forGame(game.playGameId).filter { it.inConflict }
        if (pendingConflicts.isNotEmpty()) return LaunchCheck.Conflicts(pendingConflicts)

        game.remote.bios?.let { bios ->
            if (!downloads.biosInstalled(spec.id, bios)) {
                if (!online) {
                    return LaunchCheck.Blocked(
                        "Connectez-vous une fois à Internet pour installer les fichiers de la ${spec.name}.",
                    )
                }
                try {
                    downloads.ensureBios(spec.id, bios)
                } catch (error: ApiException) {
                    return LaunchCheck.Blocked("Les fichiers de la ${spec.name} ne se sont pas installés. Réessayez dans un instant.")
                }
            }
        }

        val local = saves.forGame(game.playGameId)
        val sram = local.firstOrNull { it.kind == "sram" && it.slot == 0 && fs.exists(saves.file(it)) }
        val sramCompatible = sram == null || sram.romSha.isEmpty() || sram.romSha.equals(rom.sha256, ignoreCase = true)
        val state =
            if (fresh || !settings.resumeLastState) {
                null
            } else {
                local
                    .filter {
                        it.kind == "state" && it.core == core && (it.romSha.isEmpty() || it.romSha.equals(rom.sha256, true)) &&
                            fs.exists(saves.file(it))
                    }
                    .maxByOrNull { parseInstant(it.localPlayedAt)?.toEpochMilliseconds() ?: 0L }
            }
        val consoleSettings = settings.console(spec.id)
        val coreSaves = paths.coreSaves / spec.id
        // GameCube : les fichiers .gci de ce jeu sont remis dans le dossier de cartes de Dolphin.
        val gameCode = if (spec.id == "gamecube") runCatching { GameCubeSaves.gameCode(fs, rom.path.toPath()) }.getOrNull() else null
        val dolphinUser = coreSaves / "User"
        if (gameCode != null && sram != null && sramCompatible) {
            runCatching { GameCubeSaves.install(fs, saves.file(sram), dolphinUser, gameCode) }
        }
        val plan =
            LaunchPlan(
                gameId = game.id,
                playGameId = game.playGameId,
                title = game.title,
                console = spec.id,
                core = core,
                romPath = rom.path,
                romSha = game.remote.rom?.sha256 ?: rom.sha256,
                systemDir = paths.system.toString(),
                savesDir = coreSaves.toString(),
                sramPath = saves.file(game.playGameId, "sram", 0).toString(),
                // La SRAM GameCube n’est pas chargée par le cœur : ce sont les fichiers GCI ci-dessus.
                loadSram = sram != null && sramCompatible && spec.id != "gamecube",
                statePath = state?.let { saves.file(it).toString() },
                options = CoreOptions.build(spec, core, settings, deviceClass, portrait),
                shader = CoreOptions.shader(spec, settings),
                dsLayoutPortrait = consoleSettings.dsLayoutPortrait ?: GameLayouts.defaultDsLayout(portrait = true),
                dsLayoutLandscape = consoleSettings.dsLayoutLandscape ?: GameLayouts.defaultDsLayout(portrait = false),
                fastForwardSpeed = consoleSettings.fastForwardSpeed ?: 3,
                autoSaveSeconds = settings.autoSaveSeconds,
                audio = settings.audio,
                touchOpacity = settings.touchOpacity,
                touchScale = settings.touchScale,
                hideTouchWithGamepad = settings.hideTouchWithGamepad,
                haptics = settings.haptics,
                showFps = settings.showFps,
                coverUrl = game.remote.coverUrl,
                gameCode = gameCode,
                dolphinUserDir = if (gameCode != null) dolphinUser.toString() else null,
                notice =
                when {
                    !sramCompatible ->
                        "Votre sauvegarde vient d’une autre version du jeu : elle est conservée. " +
                            "Mettez le jeu à jour depuis sa fiche pour la retrouver."

                    saver -> "Batterie faible : réglages économes pour cette partie."

                    else -> null
                },
                integerScale = consoleSettings.integerScale == true && Consoles.nativeSize(spec.id) != null,
                portraitPosition = settings.portraitPosition,
                orientation = settings.orientation(spec.id),
                turbo = consoleSettings.turbo,
                remap = consoleSettings.remap,
                stickDeadzone = settings.stickDeadzone,
                stickSensitivity = settings.stickSensitivity,
                rumble = settings.rumble,
                menuShortcut = settings.menuShortcut,
                multiplayer = settings.multiplayer,
                muteFastForward = settings.muteFastForward,
                lowLatencyAudio = settings.lowLatencyAudio,
                analog = CoreOptions.supportsAnalog(spec.id) && consoleSettings.analog == true,
                confirmOverwrite = settings.confirmOverwrite,
                touchLayout = consoleSettings.touchLayout,
                cheats = cheats(game.playGameId),
            )
        return LaunchCheck.Ready(plan)
    }
}
