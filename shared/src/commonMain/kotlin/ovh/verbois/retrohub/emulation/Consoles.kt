package ovh.verbois.retrohub.emulation

/** Disposition des commandes tactiles à l’écran, propre à chaque manette d’origine. */
enum class PadLayout {
    NES,
    SNES,
    GAME_BOY,
    GBA,
    MASTER_SYSTEM,
    MEGA_DRIVE,
    PLAYSTATION,
    PLAYSTATION_2,
    NINTENDO_3DS,
    NINTENDO_64,
    NINTENDO_DS,
    ATARI_2600,
    GAMECUBE,
    DREAMCAST,
}

/** Illustration locale d’ambiance quand un jeu n’a pas de jaquette (mêmes choix que le web). */
enum class Artwork { STAR_CANYON, MOSS_GARDEN, DEEP_BLUE, ORBITAL_LOUNGE, NIGHT_DRIVE }

/** Une console jouable dans l’application et ses réglages fixes. */
data class ConsoleSpec(
    val id: String,
    val name: String,
    val shortName: String,
    val maker: String,
    val year: Int,
    /** Couleur d’accent, reprise du web (CONSOLE_COLORS). */
    val color: Long,
    val artwork: Artwork,
    /** Cœurs libretro embarqués, le premier est le choix par défaut. */
    val cores: List<String>,
    val pad: PadLayout,
    /** Rapport d’image d’origine (écran entier pour la DS : deux écrans empilés). */
    val aspect: Float,
    val handheld: Boolean = false,
    val dualScreen: Boolean = false,
    val analog: Boolean = false,
    /** Console 3D exigeante : rendu interne réglable et appareil récent conseillé. */
    val demanding: Boolean = false,
    val needsBios: Boolean = false,
    val supportedAbis: Set<String> = setOf("arm64-v8a", "x86_64"),
) {
    val generation: Int get() = when (id) {
        "atari2600" -> 2
        "nes", "mastersystem" -> 3
        "snes", "megadrive", "gb", "gbc" -> 4
        "psx", "n64" -> 5
        "dreamcast", "gamecube", "gba", "ps2" -> 6
        "nds" -> 7
        else -> 8
    }
}

object Consoles {
    val all: List<ConsoleSpec> =
        listOf(
            ConsoleSpec(
                "nes", "NES / Famicom", "NES", "Nintendo", 1983, 0xFFFF9EAA, Artwork.STAR_CANYON, listOf("fceumm"), PadLayout.NES,
                4f / 3f,
            ),
            ConsoleSpec(
                "snes", "Super Nintendo", "Super NES", "Nintendo", 1990, 0xFFB8A1F4, Artwork.MOSS_GARDEN, listOf("snes9x"), PadLayout.SNES,
                4f / 3f,
            ),
            ConsoleSpec(
                "gb", "Game Boy", "Game Boy", "Nintendo", 1989, 0xFFD1D58B, Artwork.MOSS_GARDEN,
                listOf(
                    "gambatte",
                    "mgba",
                ),
                PadLayout.GAME_BOY,
                10f / 9f, handheld = true,
            ),
            ConsoleSpec(
                "gbc", "Game Boy Color", "GB Color", "Nintendo", 1998, 0xFFE7A3D4, Artwork.DEEP_BLUE,
                listOf(
                    "gambatte",
                    "mgba",
                ),
                PadLayout.GAME_BOY,
                10f / 9f, handheld = true,
            ),
            ConsoleSpec(
                "gba", "Game Boy Advance", "GB Advance", "Nintendo", 2001, 0xFFA7ABFF, Artwork.ORBITAL_LOUNGE,
                listOf(
                    "mgba",
                ),
                PadLayout.GBA,
                3f / 2f, handheld = true,
            ),
            ConsoleSpec(
                "mastersystem", "Master System", "Master System", "Sega", 1985, 0xFFEEA071, Artwork.STAR_CANYON,
                listOf(
                    "genesis_plus_gx",
                ),
                PadLayout.MASTER_SYSTEM,
                4f / 3f,
            ),
            ConsoleSpec(
                "megadrive", "Mega Drive / Genesis", "Mega Drive", "Sega", 1988, 0xFF89B8FF, Artwork.NIGHT_DRIVE,
                listOf(
                    "genesis_plus_gx",
                ),
                PadLayout.MEGA_DRIVE,
                4f / 3f,
            ),
            ConsoleSpec(
                "psx", "PlayStation", "PlayStation", "Sony", 1994, 0xFFC5C9D6, Artwork.ORBITAL_LOUNGE,
                listOf(
                    "pcsx_rearmed",
                    "swanstation",
                ),
                PadLayout.PLAYSTATION,
                4f / 3f, analog = true, demanding = true, needsBios = true,
            ),
            ConsoleSpec(
                "nds", "Nintendo DS", "DS", "Nintendo", 2004, 0xFF9FD3E8, Artwork.DEEP_BLUE, listOf("melonds"), PadLayout.NINTENDO_DS,
                256f / 384f, handheld = true, dualScreen = true, demanding = true,
            ),
            ConsoleSpec(
                "3ds", "Nintendo 3DS", "3DS", "Nintendo", 2011, 0xFFB8D5E8, Artwork.DEEP_BLUE, listOf("azahar"), PadLayout.NINTENDO_3DS,
                400f / 480f, handheld = true, dualScreen = true, analog = true, demanding = true, supportedAbis = setOf("arm64-v8a"),
            ),
            ConsoleSpec(
                "ps2", "PlayStation 2", "PS2", "Sony", 2000, 0xFF9BA7E8, Artwork.ORBITAL_LOUNGE, listOf("armsx2"), PadLayout.PLAYSTATION_2,
                4f / 3f, analog = true, demanding = true, needsBios = true, supportedAbis = setOf("arm64-v8a"),
            ),
            ConsoleSpec(
                "atari2600", "Atari 2600", "Atari 2600", "Atari", 1977, 0xFFE8B98A, Artwork.STAR_CANYON,
                listOf(
                    "stella2014",
                ),
                PadLayout.ATARI_2600,
                4f / 3f,
            ),
            ConsoleSpec(
                "n64", "Nintendo 64", "N64", "Nintendo", 1996, 0xFFA7CF89, Artwork.ORBITAL_LOUNGE,
                listOf(
                    "parallel_n64",
                ),
                PadLayout.NINTENDO_64,
                4f / 3f, analog = true, demanding = true,
            ),
            ConsoleSpec(
                "gamecube", "GameCube", "GameCube", "Nintendo", 2001, 0xFF9B8CFF, Artwork.NIGHT_DRIVE,
                listOf(
                    "dolphin",
                ),
                PadLayout.GAMECUBE,
                4f / 3f, analog = true, demanding = true,
            ),
            ConsoleSpec(
                "dreamcast", "Dreamcast", "Dreamcast", "Sega", 1998, 0xFFF29B6B, Artwork.DEEP_BLUE, listOf("flycast"), PadLayout.DREAMCAST,
                4f / 3f, analog = true, demanding = true, needsBios = true,
            ),
        )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): ConsoleSpec? = byId[id]

    fun name(id: String): String = byId[id]?.name ?: id

    /** Résolution native en pixels carrés des consoles 2D, pour la mise à l’échelle entière. */
    private val native =
        mapOf(
            "nes" to (256 to 224),
            "snes" to (256 to 224),
            "gb" to (160 to 144),
            "gbc" to (160 to 144),
            "gba" to (240 to 160),
            "mastersystem" to (256 to 192),
            "megadrive" to (320 to 224),
            "atari2600" to (160 to 210),
        )

    /** Taille native de l’image (DS : selon la disposition des écrans) ; null pour la 3D. */
    fun nativeSize(console: String, ds: DsLayout? = null): Pair<Int, Int>? = if (console == "nds") {
        when (ds ?: DsLayout.STACKED) {
            DsLayout.STACKED -> 256 to 384
            DsLayout.SIDE_BY_SIDE -> 512 to 192
            DsLayout.MAIN_LARGE -> 768 to 384
            DsLayout.TOP_ONLY -> 256 to 192
        }
    } else {
        native[console]
    }

    /** Tous les cœurs à embarquer dans l’application. */
    fun coresForAbi(abi: String): Set<String> = all.filter { abi in it.supportedAbis }.flatMap { it.cores }.toSet()

    val allCores: Set<String> get() = all.flatMap { it.cores }.toSet()
}
