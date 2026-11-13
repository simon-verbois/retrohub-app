package ovh.verbois.retrohub.data

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import ovh.verbois.retrohub.MemorySaveStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeSaveStoreTest {
    @Test
    fun accountsAndRunningSessionsKeepSeparatePersistentSaveIdentities() = runTest {
        val legacy = MemorySaveStore()
        var owner = "account-a"
        val store = NativeSaveStore(legacy, legacy.fs, "/private".toPath(), { owner })
        store.prepareGame("game", "ps2")
        val running = store.boundTo(owner).also { it.prepareGame("game", "ps2") }
        running.setGameActive("game", true)
        assertTrue(store.isGameActive("game"))
        val a = store.file("game", "state", 0)
        store.put(LocalSave("game", "state", 0, "ps2", localSha = "a", coreBuild = "build-a"))
        owner = "account-b"
        assertFalse(store.isGameActive("game"))
        assertNotEquals(a, store.file("game", "state", 0))
        assertNull(store.find("game", "state", 0))
        store.put(LocalSave("game", "state", 0, "ps2", localSha = "b", coreBuild = "build-b"))
        // Token expiry/account replacement cannot redirect a running game's final write.
        running.put(running.find("game", "state", 0)!!.copy(localSha = "a-new"))
        assertEquals("b", store.find("game", "state", 0)?.localSha)
        val restarted = NativeSaveStore(legacy, legacy.fs, "/private".toPath(), { "account-a" })
        assertEquals("a-new", restarted.find("game", "state", 0)?.localSha)
        assertEquals("build-a", restarted.find("game", "state", 0)?.coreBuild)
        running.setGameActive("game", false)
        assertFalse(running.isGameActive("game"))
        assertEquals(listOf("b"), store.all().map { it.localSha })
    }
}
