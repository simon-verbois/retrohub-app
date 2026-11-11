package ovh.verbois.retrohub.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** La base Room sur l’appareil : catalogue, ROM locales, sauvegardes, sessions, réglages. */
@RunWith(AndroidJUnit4::class)
class RoomDaoTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder<AppDatabase>(InstrumentationRegistry.getInstrumentation().targetContext)
            .setDriver(BundledSQLiteDriver())
            .build()

    @After
    fun close() = db.close()

    private fun game(id: String, position: Int) = GameEntity(id, "play-$id", "Jeu $id", "nes", false, null, 0, position, "{}")

    @Test
    fun catalogueIsReplacedAsAWholeAndKeepsItsOrder() = runTest {
        db.games().replaceAll(listOf(game("b", 1), game("a", 0), game("c", 2)))
        db.games().replaceAll(listOf(game("a", 0), game("c", 1)))
        assertEquals(listOf("a", "c"), db.games().observeAll().first().map { it.id })
        assertEquals("a", db.games().find("play-a")?.id)
    }

    @Test
    fun savesTrackTheirBaseRevisionAndConflicts() = runTest {
        val save = LocalSave("jeu", "sram", 0, "nes", baseRevision = 2, baseSha = "x", localSha = "y")
        db.saves().upsert(save)
        assertTrue(db.saves().find("jeu", "sram", 0)!!.pendingUpload)
        db.saves().upsert(save.copy(conflictRevision = 3))
        val stored = db.saves().find("jeu", "sram", 0)!!
        assertTrue(stored.inConflict)
        assertTrue(!stored.pendingUpload)
        assertEquals(1, db.saves().forGame("jeu").size)
    }

    @Test
    fun sessionQueueIsOldestFirstAndCounted() = runTest {
        db.sessions().upsert(PlaySessionEntity("b", "jeu", "2026-11-10T09:00:00Z", "2026-11-10T09:30:00Z", 1800))
        db.sessions().upsert(PlaySessionEntity("a", "jeu", "2026-11-10T08:00:00Z", "2026-11-10T08:30:00Z", 1800))
        assertEquals(listOf("a", "b"), db.sessions().oldest(10).map { it.clientId })
        assertEquals(2, db.sessions().observeCount().first())
        db.sessions().delete(listOf("a"))
        assertEquals(1, db.sessions().count())
    }

    @Test
    fun deletingARomKeepsItsSaves() = runTest {
        val rom = LocalRomEntity("jeu", "jeu", "nes", "Jeu", "/r/jeu.nes", "sha", 10, "2026-11-10T08:00:00Z")
        db.roms().upsert(rom)
        db.saves().upsert(LocalSave("jeu", "state", 1, "nes", localSha = "z"))
        db.roms().delete(rom)
        assertNull(db.roms().find("jeu"))
        assertEquals(1, db.saves().forGame("jeu").size)
    }

    @Test
    fun settingsAreStoredAsJson() = runTest {
        val store = KeyValueStore(db.settings())
        store.put(
            "app_settings",
            ovh.verbois.retrohub.emulation.AppSettings.serializer(),
            ovh.verbois.retrohub.emulation.AppSettings(setupDone = true, haptics = false),
        )
        val read = store.get("app_settings", ovh.verbois.retrohub.emulation.AppSettings.serializer())!!
        assertTrue(read.setupDone)
        assertTrue(!read.haptics)
    }
}
