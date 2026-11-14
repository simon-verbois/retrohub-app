package ovh.verbois.retrohub

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ovh.verbois.retrohub.account.AccountPreferences
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.core.DeviceClass
import ovh.verbois.retrohub.core.Pin
import ovh.verbois.retrohub.core.UpdateLevel
import ovh.verbois.retrohub.core.compareVersions
import ovh.verbois.retrohub.core.updateLevel
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.emulation.ConsoleSettings
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.CoreOptions
import ovh.verbois.retrohub.emulation.CoreVariable
import ovh.verbois.retrohub.emulation.FrameSkip
import ovh.verbois.retrohub.emulation.GameLanguage
import ovh.verbois.retrohub.emulation.GameLayouts
import ovh.verbois.retrohub.emulation.HomeOrder
import ovh.verbois.retrohub.emulation.InterfaceOrientation
import ovh.verbois.retrohub.emulation.Orientation
import ovh.verbois.retrohub.emulation.Region
import ovh.verbois.retrohub.game.InputRouter
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.home.homeOrder
import ovh.verbois.retrohub.ui.settings.parseChangelog
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsLogicTest {
    @Test
    fun interfaceOrientationDefaultsToSystemAndSurvivesStoredSettings() {
        assertEquals(InterfaceOrientation.SYSTEM, ApiJson.decodeFromString(AppSettings.serializer(), "{}").interfaceOrientation)
        InterfaceOrientation.entries.forEach { choice ->
            val settings = AppSettings(interfaceOrientation = choice, orientation = Orientation.LANDSCAPE)
            val restored = ApiJson.decodeFromString(AppSettings.serializer(), ApiJson.encodeToString(AppSettings.serializer(), settings))
            assertEquals(choice, restored.interfaceOrientation)
            assertEquals(Orientation.LANDSCAPE, restored.orientation)
        }
    }

    @Test
    fun accountPreferencesWinAndOnlyKnownCoresAreTaken() {
        val remote =
            JsonObject(
                mapOf(
                    "prefer_french" to JsonPrimitive(false),
                    "auto_save_seconds" to JsonPrimitive(5),
                    "cores" to JsonObject(mapOf("psx" to JsonPrimitive("swanstation"), "nes" to JsonPrimitive("nestopia"))),
                    "volume" to JsonPrimitive(0.4),
                ),
            )
        val merged = AccountPreferences.merge(AppSettings(), remote)
        assertFalse(merged.preferFrench)
        assertEquals(15, merged.autoSaveSeconds)
        assertEquals("swanstation", merged.console("psx").core)
        // Un cœur que l’application n’a pas est ignoré.
        assertEquals(null, merged.console("nes").core)
    }

    @Test
    fun versionsCompareNumberByNumber() {
        assertTrue(compareVersions("2026.46.10", "2026.46.9") > 0)
        assertEquals(0, compareVersions("2026.46", "2026.46.0"))
        assertEquals(UpdateLevel.REQUIRED, updateLevel("2026.40.1", "2026.46.1", "2026.45.0"))
        assertEquals(UpdateLevel.AVAILABLE, updateLevel("2026.45.1", "2026.46.1", "2026.45.0"))
        assertEquals(UpdateLevel.NONE, updateLevel("2026.46.1", "2026.46.1", null))
        assertEquals(UpdateLevel.NONE, updateLevel("2026.46.1", null, null))
    }

    @Test
    fun integerScaleSnapsToWholeMultiples() {
        val native = Consoles.nativeSize("gba")!!
        val layout = GameLayouts.compute(2400f, 1080f, native.first.toFloat() / native.second, false, native = native)
        val width = layout.game.width * 2400f
        val height = layout.game.height * 1080f
        assertEquals(0f, width % native.first, 0.5f)
        assertEquals(width / native.first, height / native.second, 0.01f)
        assertEquals(0.5f, layout.game.left + layout.game.width / 2, 0.001f)
        // Pas de taille native pour la 3D.
        assertEquals(null, Consoles.nativeSize("n64"))
    }

    @Test
    fun consoleChoicesBecomeCoreOptionsAndAdvancedOptionsWin() {
        val psx = Consoles["psx"]!!
        val settings =
            AppSettings().withConsole("psx") {
                ConsoleSettings(region = Region.JAPAN, frameSkip = FrameSkip.OFF, options = mapOf("pcsx_rearmed_frameskip_type" to "auto"))
            }
        val options = CoreOptions.build(psx, "pcsx_rearmed", settings, DeviceClass.CAPABLE, portrait = false)
        assertTrue(options["pcsx_rearmed_region"].orEmpty().isNotEmpty())
        assertEquals("auto", options["pcsx_rearmed_frameskip_type"])
        assertEquals(GameLanguage.ENGLISH, AppSettings(preferFrench = false).language("nds"))
        assertEquals(Orientation.PORTRAIT, AppSettings().withConsole("nds") { it.copy(orientation = Orientation.PORTRAIT) }.orientation("nds"))
        assertEquals(Orientation.AUTO, AppSettings().orientation("nes"))
    }

    @Test
    fun pinIsCheckedAgainstItsHashOnly() {
        assertTrue(Pin.valid("1234"))
        assertFalse(Pin.valid("12a4"))
        assertFalse(Pin.valid("123"))
        val hash = Pin.hash("2468")
        assertTrue(Pin.matches("2468", hash))
        assertFalse(Pin.matches("1357", hash))
        assertFalse(Pin.matches("2468", ""))
    }

    @Test
    fun sticksHaveADeadzoneAndStayInTheUnitDisc() {
        assertEquals(0f to 0f, InputRouter.shape(0.1f, 0.05f, 0.15f, 1f))
        val (x, y) = InputRouter.shape(1f, 1f, 0.15f, 1.5f)
        assertTrue(hypot(x, y) <= 1.0001f)
        val (half, _) = InputRouter.shape(0.575f, 0f, 0.15f, 1f)
        assertEquals(0.5f, half, 0.001f)
    }

    @Test
    fun homeHidesConsolesAndFollowsTheChosenOrder() {
        fun game(title: String, console: String, played: String? = null, favorite: Boolean = false) =
            Game(LibraryGame(id = title, playGameId = title, title = title, console = console, lastPlayedAt = played, favorite = favorite), null)
        val games = listOf(game("Zelda", "snes", "2026-11-01T10:00:00Z"), game("Mario", "nes"), game("Tetris", "gb", favorite = true))
        assertEquals(listOf("Zelda", "Mario", "Tetris"), homeOrder(games).map { it.title })
        assertEquals(listOf("Mario", "Tetris", "Zelda"), homeOrder(games, HomeOrder.TITLE).map { it.title })
        assertEquals("Tetris", homeOrder(games, HomeOrder.FAVORITES).first().title)
        assertEquals(listOf("Zelda", "Tetris"), homeOrder(games, hidden = setOf("nes")).map { it.title })
        assertTrue(homeOrder(games, downloadedOnly = true).isEmpty())
    }

    @Test
    fun coreVariablesAndChangelogAreParsed() {
        val variable = CoreVariable.parse("fceumm_region", "Auto", "Région; Auto|NTSC|PAL")
        assertEquals("Région", variable.label)
        assertEquals(listOf("Auto", "NTSC", "PAL"), variable.values)
        val entries =
            parseChangelog(
                """
                # Changelog
                <!-- ## [Exemple] -->
                ## [Unreleased]
                ### Nouveautés
                - Une chose
                  sur deux lignes.
                ### Internal
                - Interne
                ## [2026.46.1] - 2026-11-10
                ### Corrections
                - Réparé
                """.trimIndent(),
            )
        assertEquals(listOf("Unreleased", "2026.46.1 · 2026-11-10"), entries.map { it.version })
        assertEquals(listOf("Une chose sur deux lignes."), entries.first().sections.single().second)
    }
}
