package ovh.verbois.retrohub.ui

import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.data.LocalRomEntity
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.emulation.HomeFolder
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.components.gridVerticalTarget
import ovh.verbois.retrohub.ui.home.homeColumns
import ovh.verbois.retrohub.ui.home.homeOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeItemsTest {
    private fun game(id: String, played: String? = null, downloaded: String? = null) = Game(
        LibraryGame(id, id, title = id, console = "nes", lastPlayedAt = played),
        downloaded?.let { LocalRomEntity(id, id, "nes", id, "/$id.nes", "sha", 1, it) },
    )

    @Test
    fun catalogueAndFavoritesComeBeforeConsolesWithoutCustomFoldersOrDownloads() {
        val nes = game("nes-game", downloaded = "2026-11-13T12:00:00Z")
        val snes = Game(LibraryGame("snes-game", "snes-game", title = "SNES", console = "snes", favorite = true), null)
        val folder = HomeFolder("custom", "Aventure", listOf(nes.id, snes.id))
        val settings = AppSettings(homeFolders = listOf(folder), homePins = listOf(nes.id))
        val columns = homeColumns(listOf(nes, snes), settings)
        assertEquals(listOf("catalogue", "favorites", "console:nes", "console:snes"), columns.map { it.key })
        assertEquals(listOf(nes.id), columns.first { it.key == "console:nes" }.games.map { it.id })
        assertEquals(listOf(snes.id), columns.first { it.key == "favorites" }.games.map { it.id })
        assertFalse(columns.any { it.key == "downloads" || it.key == folder.key })
        assertTrue(columns.first().games.isEmpty())
    }

    @Test
    fun emptyCollectionsRemainAvailableAndHiddenConsolesStayHidden() {
        assertEquals(listOf("catalogue", "favorites"), homeColumns(emptyList(), AppSettings()).map { it.key })
        val columns = homeColumns(listOf(game("hidden")), AppSettings(hiddenConsoles = setOf("nes")))
        assertEquals(listOf("catalogue", "favorites"), columns.map { it.key })
        assertTrue(columns.all { it.games.isEmpty() })
    }

    @Test
    fun downloadAndOfflinePlayStayInTheRecentStripAlongsideServerActivity() {
        val games = (0..30).map { game("game-$it") } + listOf(
            game("download", downloaded = "2026-11-13T12:00:00Z"),
            game("web", played = "2026-11-12T12:00:00Z"),
            game("offline"),
        )
        val settings = AppSettings(homeActivity = mapOf("offline" to "2026-11-13T13:00:00Z"))
        assertEquals(listOf("offline", "download", "web"), homeOrder(games, activity = settings.homeActivity).take(3).map { it.id })
        val console = homeColumns(games, settings).first { it.console == "nes" }
        assertTrue("download" in console.indices)
        assertEquals(games.size, console.games.size)
    }

    @Test
    fun legacyCustomFolderPinsAreIgnoredWithoutChangingTheirStoredData() {
        val folder = HomeFolder("collection", "Mes jeux", listOf("game-1"), favorite = true)
        val games = (0..25).map { game("game-$it") }
        val settings = AppSettings(homePins = listOf("game-25", folder.key, "game-2"), homeFolders = listOf(folder))
        val entries = homeColumns(games, settings)
        assertEquals(listOf("catalogue", "favorites", "console:nes"), entries.map { it.key })
        assertEquals(entries.size, entries.distinctBy { it.key }.size)
    }

    @Test
    fun foldersAndPinsSurviveSettingsSerializationAndOlderSettingsStillLoad() {
        val folder = HomeFolder("a", "Aventure", listOf("zelda"), favorite = true)
        val settings = AppSettings(homeFolders = listOf(folder), homePins = listOf(folder.key))
        assertEquals(settings, ApiJson.decodeFromString(AppSettings.serializer(), ApiJson.encodeToString(AppSettings.serializer(), settings)))
        assertTrue(ApiJson.decodeFromString(AppSettings.serializer(), "{}").homeFolders.isEmpty())
        assertEquals(listOf("catalogue", "favorites"), homeColumns(emptyList(), settings).map { it.key })
    }

    @Test
    fun unrelatedSettingsDoNotInvalidateTheHomeModel() {
        val settings = AppSettings(homeActivity = mapOf("game" to "2026-11-13T13:00:00Z"))
        assertEquals(
            ovh.verbois.retrohub.ui.home.HomePreferences(settings),
            ovh.verbois.retrohub.ui.home.HomePreferences(settings.copy(audio = false, reduceMotion = true)),
        )
    }

    @Test
    fun flatPortraitAndConsoleLibrariesShareDownloadedFilteringAndOrdering() {
        val remote = game("remote", played = "2026-11-14T12:00:00Z")
        val local = game("local", downloaded = "2026-11-13T12:00:00Z")
        val model = ovh.verbois.retrohub.ui.home.homeLibrary(listOf(remote, local), ovh.verbois.retrohub.ui.home.HomePreferences(downloadedOnly = true))
        assertEquals(listOf(local), model.games)
        assertEquals(model.games, model.byColumn.getValue("console:nes").games)
        assertEquals(0, model.byColumn.getValue("console:nes").indices[local.id])
        assertFalse(remote.id in model.byId)
    }

    @Test
    fun incompleteRowsClampToTheClosestColumnAndOnlyStopAtTheLastRow() {
        assertEquals(7, gridVerticalTarget(3, 8, 5, true))
        assertEquals(7, gridVerticalTarget(4, 8, 5, true))
        assertEquals(6, gridVerticalTarget(1, 8, 5, true))
        assertEquals(2, gridVerticalTarget(7, 8, 5, false))
        assertNull(gridVerticalTarget(7, 8, 5, true))
        assertNull(gridVerticalTarget(0, 8, 5, false))
    }
}
