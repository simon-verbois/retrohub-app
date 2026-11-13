package ovh.verbois.retrohub.ui

import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.data.LocalRomEntity
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.emulation.HomeFolder
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.components.gridVerticalTarget
import ovh.verbois.retrohub.ui.home.HomeEntry
import ovh.verbois.retrohub.ui.home.homeEntries
import ovh.verbois.retrohub.ui.home.homeOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeItemsTest {
    private fun game(id: String, played: String? = null, downloaded: String? = null) = Game(
        LibraryGame(id, id, title = id, console = "nes", lastPlayedAt = played),
        downloaded?.let { LocalRomEntity(id, id, "nes", id, "/$id.nes", "sha", 1, it) },
    )

    @Test
    fun downloadAndOfflinePlayStayInTheRecentStripAlongsideServerActivity() {
        val games = (0..30).map { game("game-$it") } + listOf(
            game("download", downloaded = "2026-11-13T12:00:00Z"),
            game("web", played = "2026-11-12T12:00:00Z"),
            game("offline"),
        )
        val settings = AppSettings(homeActivity = mapOf("offline" to "2026-11-13T13:00:00Z"))
        assertEquals(listOf("offline", "download", "web"), homeOrder(games, activity = settings.homeActivity).take(3).map { it.id })
        val folder = homeEntries(games, settings, null).filterIsInstance<HomeEntry.FolderItem>().single()
        assertEquals("nes", folder.folder.consoleId)
        assertTrue("download" in folder.folder.gameIds)
        assertEquals(games.size, folder.folder.gameIds.size)
    }

    @Test
    fun catalogueIsAlwaysFirstAndPinnedGamesAndFoldersKeepTheirExplicitOrder() {
        val folder = HomeFolder("collection", "Mes jeux", listOf("game-1"), favorite = true)
        val games = (0..25).map { game("game-$it") }
        val settings = AppSettings(homePins = listOf("game-25", folder.key, "game-2"), homeFolders = listOf(folder))
        val entries = homeEntries(games, settings, null)
        assertEquals(listOf("catalogue", "game-25", folder.key, "game-2"), entries.take(4).map { it.key })
        assertEquals(entries.size, entries.distinctBy { it.key }.size)
        assertEquals(listOf("game-25", "game-2", folder.key), settings.moveHomePin(folder.key, 1).homePins)
        assertEquals(listOf("game-25", "game-2"), settings.toggleHomePin(folder.key).homePins)
    }

    @Test
    fun foldersAndPinsSurviveSettingsSerializationAndOlderSettingsStillLoad() {
        val folder = HomeFolder("a", "Aventure", listOf("zelda"), favorite = true)
        val settings = AppSettings(homeFolders = listOf(folder), homePins = listOf(folder.key))
        assertEquals(settings, ApiJson.decodeFromString(AppSettings.serializer(), ApiJson.encodeToString(AppSettings.serializer(), settings)))
        assertTrue(ApiJson.decodeFromString(AppSettings.serializer(), "{}").homeFolders.isEmpty())
        assertTrue(homeEntries(emptyList(), settings, null).any { it is HomeEntry.FolderItem })
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
