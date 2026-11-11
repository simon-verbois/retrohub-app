package ovh.verbois.retrohub.game

import kotlinx.serialization.Serializable
import ovh.verbois.retrohub.emulation.ControlPlacement
import ovh.verbois.retrohub.emulation.DsLayout
import ovh.verbois.retrohub.emulation.MenuShortcut
import ovh.verbois.retrohub.emulation.Orientation
import ovh.verbois.retrohub.emulation.PortraitPosition
import ovh.verbois.retrohub.emulation.Shader

/** Tout ce dont l’écran de jeu a besoin, préparé avant le lancement (passé en JSON). */
@Serializable
data class LaunchPlan(
    val gameId: String,
    val playGameId: String,
    val title: String,
    val console: String,
    val core: String,
    val romPath: String,
    /** SHA-256 de la ROM exécutée : il accompagne chaque sauvegarde envoyée. */
    val romSha: String,
    val systemDir: String,
    val savesDir: String,
    val sramPath: String,
    val loadSram: Boolean,
    val statePath: String? = null,
    val options: Map<String, String> = emptyMap(),
    val shader: Shader = Shader.SHARP,
    val dsLayoutPortrait: DsLayout = DsLayout.STACKED,
    val dsLayoutLandscape: DsLayout = DsLayout.SIDE_BY_SIDE,
    val fastForwardSpeed: Int = 3,
    val autoSaveSeconds: Int = 60,
    val audio: Boolean = true,
    val touchOpacity: Float = 0.6f,
    val touchScale: Float = 1f,
    val hideTouchWithGamepad: Boolean = true,
    val haptics: Boolean = true,
    val showFps: Boolean = false,
    val notice: String? = null,
    /** GameCube : code jeu et dossier « User » de Dolphin, pour les sauvegardes GCI. */
    val gameCode: String? = null,
    val dolphinUserDir: String? = null,
    val integerScale: Boolean = false,
    val portraitPosition: PortraitPosition = PortraitPosition.TOP,
    val useDisplayCutout: Boolean = false,
    val orientation: Orientation = Orientation.AUTO,
    val turbo: Set<String> = emptySet(),
    val remap: Map<String, Int> = emptyMap(),
    val stickDeadzone: Float = 0.15f,
    val stickSensitivity: Float = 1f,
    val rumble: Boolean = true,
    val menuShortcut: MenuShortcut = MenuShortcut.SELECT_START,
    val multiplayer: Boolean = true,
    val muteFastForward: Boolean = true,
    val lowLatencyAudio: Boolean = true,
    val analog: Boolean = false,
    val confirmOverwrite: Boolean = true,
    val touchLayout: Map<String, ControlPlacement> = emptyMap(),
    val cheats: List<Cheat> = emptyList(),
)

/** Un code de triche d’un jeu (format du cœur : Game Genie, Action Replay…). */
@Serializable
data class Cheat(val name: String, val code: String, val enabled: Boolean = true)
