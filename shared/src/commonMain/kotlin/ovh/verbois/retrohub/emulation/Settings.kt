package ovh.verbois.retrohub.emulation

import kotlinx.serialization.Serializable

/** Rendu de l’image : net (pixels francs), lissé, écran cathodique ou écran LCD de portable. */
@Serializable
enum class Shader(val label: String, val description: String) {
    SHARP("Net", "Pixels francs, sans flou. Idéal pour la 2D."),
    SMOOTH("Lissé", "Adoucit les contours. Agréable pour la 3D."),
    CRT("Écran cathodique", "Lignes de balayage d’un téléviseur d’époque."),
    LCD("Écran LCD", "Grille fine des consoles portables."),
    UPSCALE("Haute définition", "Agrandit les pixels en gardant des bords nets (plus gourmand)."),
}

/** Disposition des deux écrans de la DS. */
@Serializable
enum class DsLayout(val label: String, val description: String) {
    STACKED("L’un sous l’autre", "Écran du haut en haut, écran tactile en bas, comme la console."),
    SIDE_BY_SIDE("Côte à côte", "Écran du haut à gauche, écran tactile à droite."),
    MAIN_LARGE("Grand écran principal", "Un écran agrandi, l’autre en vignette."),
    TOP_ONLY("Écran du haut seul", "L’écran tactile reste accessible avec le bouton d’échange."),
}

@Serializable
enum class GameRotation(val degrees: Int) {
    NORMAL(0),
    RIGHT(90),
    INVERTED(180),
    LEFT(270),
    ;

    val label: String get() = "$degrees°"
    val sideways: Boolean get() = degrees % 180 != 0
    fun aspect(original: Float): Float = if (sideways) 1f / original else original
    fun native(size: Pair<Int, Int>): Pair<Int, Int> = if (sideways) size.second to size.first else size

    /** Map a point in the displayed image back to the core's unrotated touch screen. */
    fun unrotate(x: Float, y: Float): Pair<Float, Float> = when (this) {
        NORMAL -> x to y
        RIGHT -> y to (1f - x)
        INVERTED -> (1f - x) to (1f - y)
        LEFT -> (1f - y) to x
    }
}

/** Profil de performance choisi à l’installation : il fixe les réglages par défaut. */
@Serializable
enum class PerformanceProfile(val label: String, val description: String) {
    BATTERY("Économie", "Rendu d’origine et moins de calculs : la batterie dure plus longtemps."),
    BALANCED("Équilibré", "Le meilleur compromis pour la plupart des téléphones."),
    QUALITY("Qualité", "Rendu en haute définition pour les consoles 3D. Pour les appareils puissants."),
}

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("Comme le téléphone"),
    LIGHT("Clair"),
    DARK("Sombre"),
}

@Serializable
enum class TouchPreset(val label: String) {
    COMFORT("Confort"),
    OVERLAY("Plein écran"),
    COMPACT("Compacte"),
}

@Serializable
enum class HintsMode(val label: String) {
    AUTO("Avec une manette"),
    ALWAYS("Toujours"),
    NEVER("Jamais"),
}

@Serializable
enum class InterfaceOrientation(val label: String) {
    SYSTEM("Système"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Paysage"),
}

@Serializable
enum class Orientation(val label: String) {
    AUTO("Automatique"),
    LANDSCAPE("Paysage"),
    PORTRAIT("Portrait"),
}

@Serializable
enum class PortraitPosition(val label: String) {
    TOP("En haut"),
    CENTER("Au centre"),
}

@Serializable
enum class HomeOrder(val label: String) {
    RECENT("Récents d’abord"),
    TITLE("De A à Z"),
    FAVORITES("Favoris d’abord"),
}

@Serializable
enum class MenuShortcut(val label: String, val description: String) {
    SELECT_START("Select + Start", "Les deux boutons ensemble, comme RetroArch."),
    GUIDE("Bouton central", "Le bouton Guide, Home ou PS de la manette."),
    L3_R3("L3 + R3", "Les deux sticks enfoncés ensemble."),
}

@Serializable
enum class TextSize(val label: String, val scale: Float) {
    SMALL("Petit", 0.9f),
    NORMAL("Normal", 1f),
    LARGE("Grand", 1.15f),
}

@Serializable
enum class StorageLocation(val label: String) {
    INTERNAL("Mémoire du téléphone"),
    SD_CARD("Carte SD"),
}

@Serializable
enum class GameLanguage(val label: String) {
    FRENCH("Français"),
    ENGLISH("Anglais"),
}

@Serializable
enum class Region(val label: String) {
    AUTO("Automatique"),
    EUROPE("Europe"),
    USA("États-Unis"),
    JAPAN("Japon"),
}

@Serializable
enum class AspectChoice(val label: String, val description: String) {
    TV("Téléviseur 4:3", "Comme sur un écran d’époque."),
    PIXELS("Pixels carrés", "L’image native de la console, sans étirement."),
}

@Serializable
enum class BootMode(val label: String, val description: String) {
    FAST("Rapide", "Le jeu démarre directement, sans l’intro de la console."),
    ORIGINAL("Comme la console", "Avec l’écran de démarrage et le vrai BIOS."),
}

@Serializable
enum class FrameSkip(val label: String) {
    AUTO("Selon l’appareil"),
    OFF("Jamais"),
    ON("Si nécessaire"),
}

/** Position et taille d’un groupe de commandes tactiles, modifiées dans l’éditeur. */
@Serializable
data class ControlPlacement(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f)

/** Réglages d’une console, modifiables par le joueur ; null = valeur conseillée. */
@Serializable
data class ConsoleSettings(
    val core: String? = null,
    val shader: Shader? = null,
    val dsLayoutPortrait: DsLayout? = null,
    val dsLayoutLandscape: DsLayout? = null,
    val dsSwapScreens: Boolean? = null,
    /** Multiplicateur de résolution interne des consoles 3D (1 = d’origine). */
    val resolution: Int? = null,
    val widescreen: Boolean? = null,
    val fastForwardSpeed: Int? = null,
    val integerScale: Boolean? = null,
    val language: GameLanguage? = null,
    val region: Region? = null,
    val aspect: AspectChoice? = null,
    val boot: BootMode? = null,
    val frameSkip: FrameSkip? = null,
    val analog: Boolean? = null,
    val orientation: Orientation? = null,
    /** Boutons RetroPad en tir automatique (noms de PadKey). */
    val turbo: Set<String> = emptySet(),
    /** Manette physique : bouton RetroPad (nom de PadKey) → code de touche Android. */
    val remap: Map<String, Int> = emptyMap(),
    /** Éditeur tactile : groupe (« left », « right », « shoulders », « center ») et orientation → placement. */
    val touchLayout: Map<String, ControlPlacement> = emptyMap(),
    val touchPreset: TouchPreset? = null,
    /** Options libretro avancées, prioritaires sur tout le reste. */
    val options: Map<String, String> = emptyMap(),
    val optionsByCore: Map<String, Map<String, String>> = emptyMap(),
)

/** Manual collections and their order belong to this device's library. */
@Serializable
data class HomeFolder(val id: String, val name: String, val gameIds: List<String> = emptyList(), val favorite: Boolean = false, val onHome: Boolean = true) {
    val key: String get() = "folder:$id"
    val consoleId: String? get() = id.removePrefix("console:").takeIf { id.startsWith("console:") }
}

@Serializable
data class AppSettings(
    val setupDone: Boolean = false,
    val deviceName: String = "",
    val performance: PerformanceProfile = PerformanceProfile.BALANCED,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val animatedBackground: Boolean = true,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val touchOpacity: Float = 0.6f,
    val touchScale: Float = 1f,
    val touchPreset: TouchPreset = TouchPreset.COMFORT,
    val hideTouchWithGamepad: Boolean = true,
    val hints: HintsMode = HintsMode.AUTO,
    /** Partagé avec le compte (préférence auto_save_seconds du web). */
    val autoSaveSeconds: Int = 60,
    val resumeLastState: Boolean = true,
    val audio: Boolean = true,
    val showFps: Boolean = false,
    val cellularDownloads: Boolean = false,
    // Compte (partagé avec le web)
    val preferFrench: Boolean = true,
    // Synchronisation
    val autoSync: Boolean = true,
    val periodicSyncHours: Int = 6,
    val conflictNotifications: Boolean = true,
    // Apparence
    val startOnLastGame: Boolean = false,
    val homeOrder: HomeOrder = HomeOrder.RECENT,
    val homePins: List<String> = emptyList(),
    val homeConsoleFavorites: List<String> = emptyList(),
    val homeFolders: List<HomeFolder> = emptyList(),
    val homeActivity: Map<String, String> = emptyMap(),
    val hiddenConsoles: Set<String> = emptySet(),
    val offlineOnly: Boolean = false,
    val navigationSounds: Boolean = false,
    val textSize: TextSize = TextSize.NORMAL,
    // Affichage
    val interfaceOrientation: InterfaceOrientation = InterfaceOrientation.SYSTEM,
    val orientation: Orientation = Orientation.AUTO,
    val portraitPosition: PortraitPosition = PortraitPosition.TOP,
    val useDisplayCutout: Boolean = false,
    // Commandes
    val rumble: Boolean = true,
    val menuShortcut: MenuShortcut = MenuShortcut.SELECT_START,
    val stickDeadzone: Float = 0.15f,
    val stickSensitivity: Float = 1f,
    val multiplayer: Boolean = true,
    // Audio
    val muteFastForward: Boolean = true,
    val lowLatencyAudio: Boolean = true,
    // Performances
    val batterySaver: Boolean = true,
    // Téléchargements et stockage
    val storage: StorageLocation = StorageLocation.INTERNAL,
    val autoDownloadFavorites: Boolean = false,
    val cleanupDays: Int = 0,
    // Par jeu
    val confirmOverwrite: Boolean = true,
    // Contrôle parental : empreinte du code (vide = désactivé) et jeux verrouillés.
    val pinHash: String = "",
    val lockSettings: Boolean = true,
    val lockedGames: Set<String> = emptySet(),
    val gameRotations: Map<String, GameRotation> = emptyMap(),
    val consoles: Map<String, ConsoleSettings> = emptyMap(),
) {
    fun toggleHomePin(key: String): AppSettings = copy(homePins = if (key in homePins) homePins - key else homePins + key)

    fun moveHomePin(key: String, step: Int): AppSettings {
        val from = homePins.indexOf(key)
        if (from < 0) return this
        val to = (from + step).coerceIn(0, homePins.lastIndex)
        val pins = homePins.toMutableList().apply {
            removeAt(from)
            add(to, key)
        }
        return copy(homePins = pins)
    }

    fun console(id: String): ConsoleSettings = consoles[id] ?: ConsoleSettings()

    fun withConsole(id: String, update: (ConsoleSettings) -> ConsoleSettings): AppSettings = copy(consoles = consoles + (id to update(console(id))))

    /** Langue des jeux : choix de la console, sinon la préférence du compte. */
    fun language(console: String): GameLanguage = console(console).language ?: if (preferFrench) GameLanguage.FRENCH else GameLanguage.ENGLISH

    fun orientation(console: String?): Orientation = console?.let { console(it).orientation } ?: orientation
}
