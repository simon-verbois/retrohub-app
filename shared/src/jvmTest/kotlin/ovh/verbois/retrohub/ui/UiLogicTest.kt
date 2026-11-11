package ovh.verbois.retrohub.ui

import androidx.compose.ui.geometry.Offset
import kotlinx.datetime.TimeZone
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.core.formatBytes
import ovh.verbois.retrohub.core.formatDate
import ovh.verbois.retrohub.core.formatPlayTime
import ovh.verbois.retrohub.core.formatRelative
import ovh.verbois.retrohub.core.parseInstant
import ovh.verbois.retrohub.emulation.PadKey
import ovh.verbois.retrohub.emulation.PadLayout
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.home.homeOrder
import ovh.verbois.retrohub.ui.nav.Navigator
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.onboarding.formatPairingCode
import ovh.verbois.retrohub.ui.play.dpadKeys
import ovh.verbois.retrohub.ui.play.padSpec
import ovh.verbois.retrohub.ui.shell.matches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class UiLogicTest {
    private fun game(title: String, console: String = "nes", played: String? = null) =
        Game(LibraryGame(id = title, playGameId = title, title = title, console = console, lastPlayedAt = played), null)

    @Test
    fun searchFindsAccentInsensitiveMatchesInALargeCatalogueAndCapsResults() {
        val games = (0 until 5000).map { game("Pokémon $it", "gb") }
        val results = ovh.verbois.retrohub.ui.shell.searchGames(games, "POKEMON game boy")
        assertEquals(40, results.size)
        assertEquals(games.take(40), results)
    }

    @Test
    fun pairingCodeIsFormattedWhileTyping() {
        assertEquals("ABCD-EFGH", formatPairingCode("abcdefgh"))
        assertEquals("ABC", formatPairingCode("a b-c"))
        assertEquals("ABCD-EFGH", formatPairingCode("ABCD-EFGH-IJ"))
    }

    @Test
    fun searchIgnoresAccentsCaseAndMatchesConsoles() {
        assertTrue(matches(game("Pokémon Rouge", "gb"), "pokemon"))
        assertTrue(matches(game("Super Mario Bros.", "nes"), "mario nes"))
        assertFalse(matches(game("Zelda", "snes"), "mario"))
    }

    @Test
    fun homeShowsRecentGamesFirst() {
        val order =
            homeOrder(
                listOf(
                    game("Zelda"),
                    game("Mario", played = "2026-11-01T10:00:00"),
                    game("Alpha"),
                    game("Metroid", played = "2026-11-09T10:00:00"),
                ),
            )
        assertEquals(listOf("Metroid", "Mario", "Alpha", "Zelda"), order.map { it.title })
    }

    @Test
    fun dpadSupportsEightDirectionsWithADeadZone() {
        assertEquals(emptySet(), dpadKeys(Offset(0.1f, 0.1f)))
        assertEquals(setOf(PadKey.UP), dpadKeys(Offset(0f, -0.8f)))
        assertEquals(setOf(PadKey.RIGHT, PadKey.DOWN), dpadKeys(Offset(0.6f, 0.6f)))
        assertEquals(setOf(PadKey.LEFT), dpadKeys(Offset(-0.9f, 0.1f)))
    }

    @Test
    fun padsFollowTheOriginalControllers() {
        assertEquals(listOf("B", "A"), padSpec(PadLayout.NES).face.map { it.label })
        assertEquals(setOf("△", "○", "✕", "□"), padSpec(PadLayout.PLAYSTATION).face.map { it.label }.toSet())
        // Mega Drive : A, B, C sur RetroPad Y, B, A (genesis_plus_gx).
        assertEquals(listOf(PadKey.Y, PadKey.B, PadKey.A), padSpec(PadLayout.MEGA_DRIVE).face.map { it.key })
        // N64 : A = RetroPad B, B = RetroPad Y, Z = L2, boutons C sur le stick droit.
        val n64 = padSpec(PadLayout.NINTENDO_64)
        assertEquals(PadKey.B, n64.face.first { it.label == "A" }.key)
        assertEquals(PadKey.L2, n64.shoulders.first { it.label == "Z" }.key)
        assertEquals(4, n64.stickButtons.size)
        assertTrue(n64.leftStick && !n64.dpad)
        // Dreamcast : A = RetroPad B (flycast).
        assertEquals(PadKey.B, padSpec(PadLayout.DREAMCAST).face.first { it.label == "A" }.key)
    }

    @Test
    fun navigationCyclesSectionsAndGoesBackHome() {
        val nav = Navigator()
        nav.cycleSection(1)
        assertEquals(Route.Library(), nav.current)
        nav.cycleSection(-2)
        assertEquals(Route.Downloads, nav.current)
        nav.go(Route.Settings())
        assertTrue(nav.back())
        assertEquals(Route.Downloads, nav.current)
        assertTrue(nav.back())
        assertEquals(Route.Home, nav.current)
        assertFalse(nav.back())
        assertEquals(Section.HOME, Section.of(nav.current))
    }

    @Test
    fun datesAndSizesReadNaturallyInFrench() {
        val now = Instant.parse("2026-11-10T20:00:00Z")
        val utc = TimeZone.UTC
        assertEquals("aujourd’hui à 18:30", formatDate(Instant.parse("2026-11-10T18:30:00Z"), now, utc))
        assertEquals("hier à 21:14", formatDate(Instant.parse("2026-11-09T21:14:00Z"), now, utc))
        assertEquals("8 nov. à 21:14", formatDate(Instant.parse("2026-11-08T21:14:00Z"), now, utc))
        assertEquals("À l’instant", formatRelative(Instant.parse("2026-11-10T19:59:30Z"), now, utc))
        assertEquals("il y a 3 h", formatRelative(Instant.parse("2026-11-10T17:00:00Z"), now, utc))
        assertEquals("3 h 20 min", formatPlayTime(12_000))
        assertEquals("24,0 Ko", formatBytes(24_592))
        assertEquals(Instant.parse("2026-11-10T13:11:37Z"), parseInstant("2026-11-10T13:11:37"))
    }
}
