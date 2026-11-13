package ovh.verbois.retrohub.sync

import kotlinx.coroutines.test.runTest
import ovh.verbois.retrohub.MemorySaveStore
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.api.LibraryManifest
import ovh.verbois.retrohub.api.RemoteSave
import ovh.verbois.retrohub.bytes
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Révision de base, conflits sans écrasement, restauration du serveur (save_epoch). */
class SaveSyncTest {
    private val t = TestApi()
    private val store = MemorySaveStore()
    private val clock = AppClock { Instant.parse("2026-11-10T10:00:00Z") }
    private val sync = SaveSync(t.api, store, clock)
    private val server = "serveur".encodeToByteArray()
    private val local = "téléphone".encodeToByteArray()

    @AfterTest
    fun close() = t.close()

    private fun remote(revision: Int, data: ByteArray = server, device: String? = "Pixel 8") = RemoteSave(
        gameId = "jeu",
        kind = "sram",
        slot = 0,
        revision = revision,
        sha256 = sha256(data),
        core = "fceumm",
        romSha256 = "rom",
        playedAt = "2026-11-09T20:00:00",
        device = device?.let { ovh.verbois.retrohub.api.Device("d", it, "android") },
    )

    private fun game(vararg saves: RemoteSave) = LibraryGame(id = "jeu", playGameId = "jeu", title = "Jeu", console = "nes", saves = saves.toList())

    private fun serveBody(data: ByteArray, revision: Int) =
        t.enqueue(bytes(data, 200, "ETag" to "\"$revision\"", "X-Content-SHA256" to sha256(data), "X-Save-Core" to "fceumm"))

    /** Copie locale synchronisée à la révision [revision], puis éventuellement modifiée. */
    private suspend fun synced(revision: Int, changed: Boolean): LocalSave {
        store.write("jeu", "sram", 0, if (changed) local else server)
        val save =
            LocalSave(
                gameId = "jeu",
                kind = "sram",
                slot = 0,
                console = "nes",
                baseRevision = revision,
                baseSha = sha256(server),
                localSha = sha256(if (changed) local else server),
                localPlayedAt = "2026-11-10T09:00:00Z",
                core = "fceumm",
                romSha = "rom",
            )
        store.put(save)
        return save
    }

    @Test
    fun nativeStateBuildSurvivesDownloadConflictAndUpload() = runTest {
        val state = remote(1).copy(kind = "state", core = "azahar", coreBuild = "build-a")
        serveBody(server, 1)
        sync.pull(game(state).copy(console = "3ds"))
        assertEquals("build-a", store.find("jeu", "state", 0)?.coreBuild)
        store.write("jeu", "state", 0, local)
        sync.recordLocal("jeu", "3ds", "state", 0, "azahar", "rom", coreBuild = "build-local")
        serveBody(server, 2)
        sync.pull(game(state.copy(revision = 2, coreBuild = "build-server", romSha256 = "remote-rom")).copy(console = "3ds"))
        val conflict = store.find("jeu", "state", 0)!!
        assertEquals("build-local", conflict.coreBuild)
        assertEquals("build-server", conflict.conflictCoreBuild)
        sync.keepServer(conflict)
        assertEquals("remote-rom", store.find("jeu", "state", 0)?.romSha)
        assertEquals("build-server", store.find("jeu", "state", 0)?.coreBuild)
        store.write("jeu", "state", 0, local)
        val recorded = sync.recordLocal("jeu", "3ds", "state", 0, "azahar", "rom", coreBuild = "build-server")!!
        t.enqueue(json("""{"kind":"state","slot":0,"revision":3,"sha256":"${sha256(local)}","core_build":"build-server"}"""))
        sync.push(recorded, "epoch")
        t.take()
        t.take()
        assertEquals("build-server", t.take().headers["X-Emulator-Build"])
    }

    @Test
    fun newerServerRevisionReplacesAnUnchangedCopy() = runTest {
        synced(2, changed = false)
        val newer = "nouvelle partie".encodeToByteArray()
        serveBody(newer, 3)
        assertTrue(sync.pull(game(remote(3, newer))).isEmpty())
        val save = store.find("jeu", "sram", 0)!!
        assertEquals(3, save.baseRevision)
        assertEquals(sha256(newer), save.localSha)
        assertEquals("nouvelle partie", store.read("jeu", "sram", 0).decodeToString())
        assertEquals("/api/v1/games/jeu/saves/sram/0?revision=3", t.take().target)
    }

    @Test
    fun firstSaveFromTheServerIsDownloaded() = runTest {
        serveBody(server, 1)
        sync.pull(game(remote(1)))
        assertEquals(1, store.find("jeu", "sram", 0)!!.baseRevision)
    }

    @Test
    fun bothSidesChangedIsAConflictAndNothingIsOverwritten() = runTest {
        synced(2, changed = true)
        val newer = "partie web".encodeToByteArray()
        serveBody(newer, 3)
        val conflicts = sync.pull(game(remote(3, newer, device = null)))
        val save = conflicts.single()
        assertEquals(3, save.conflictRevision)
        assertEquals(SaveSync.WEB_DEVICE, save.conflictDevice)
        assertEquals("téléphone", store.read("jeu", "sram", 0).decodeToString())
        assertEquals(2, save.baseRevision)
        assertTrue(save.inConflict && !save.pendingUpload)
    }

    @Test
    fun sameContentOnBothSidesIsNotAConflict() = runTest {
        synced(2, changed = true)
        assertTrue(sync.pull(game(remote(3, local))).isEmpty())
        val save = store.find("jeu", "sram", 0)!!
        assertEquals(3, save.baseRevision)
        assertTrue(!save.changedLocally)
        assertEquals(0, t.server.requestCount)
    }

    @Test
    fun localChangeIsUploadedOnTopOfTheBaseRevision() = runTest {
        val save = synced(2, changed = true)
        t.enqueue(json("""{"kind": "sram", "slot": 0, "revision": 3, "sha256": "${sha256(local)}"}"""))
        assertIs<PushResult.Uploaded>(sync.push(save, "initial"))
        val request = t.take()
        assertEquals("2", request.headers["If-Match"])
        assertEquals("initial", request.headers["X-Save-Epoch"])
        assertEquals("2026-11-10T09:00:00Z", request.headers["X-Played-At"])
        val after = store.find("jeu", "sram", 0)!!
        assertEquals(3, after.baseRevision)
        assertTrue(!after.pendingUpload)
    }

    @Test
    fun conflictOnUploadKeepsTheLocalCopyAndFetchesTheServerOne() = runTest {
        val save = synced(2, changed = true)
        t.enqueue(json("""{"detail": {"message": "Une progression plus récente existe.", "current_revision": 4}}""", 409))
        t.enqueue(json("[" + ovh.verbois.retrohub.api.ApiJson.encodeToString(RemoteSave.serializer(), remote(4)) + "]"))
        serveBody(server, 4)
        assertIs<PushResult.Conflict>(sync.push(save, "initial"))
        val after = store.find("jeu", "sram", 0)!!
        assertEquals(4, after.conflictRevision)
        assertEquals("Pixel 8", after.conflictDevice)
        assertEquals("téléphone", store.read("jeu", "sram", 0).decodeToString())
    }

    @Test
    fun conflictWithTheSameContentJustMovesTheBase() = runTest {
        val save = synced(2, changed = true)
        t.enqueue(json("""{"detail": {"message": "Une progression plus récente existe.", "current_revision": 4}}""", 409))
        t.enqueue(json("[" + ovh.verbois.retrohub.api.ApiJson.encodeToString(RemoteSave.serializer(), remote(4, local)) + "]"))
        assertIs<PushResult.Uploaded>(sync.push(save, "initial"))
        assertEquals(4, store.find("jeu", "sram", 0)!!.baseRevision)
    }

    @Test
    fun keepingTheLocalCopySendsItOverTheServerRevision() = runTest {
        synced(2, changed = true)
        serveBody(server, 4)
        val conflict = sync.pull(game(remote(4, "autre".encodeToByteArray()))).single()
        t.enqueue(json("""{"kind": "sram", "slot": 0, "revision": 5, "sha256": "${sha256(local)}"}"""))
        t.take()
        assertIs<PushResult.Uploaded>(sync.keepLocal(conflict, "initial"))
        assertEquals("4", t.take().headers["If-Match"])
        val after = store.find("jeu", "sram", 0)!!
        assertNull(after.conflictRevision)
        assertEquals(5, after.baseRevision)
    }

    @Test
    fun keepingTheServerCopyReplacesTheLocalOne() = runTest {
        synced(2, changed = true)
        val web = "partie web".encodeToByteArray()
        serveBody(web, 4)
        val conflict = sync.pull(game(remote(4, web))).single()
        sync.keepServer(conflict)
        assertEquals("partie web", store.read("jeu", "sram", 0).decodeToString())
        val after = store.find("jeu", "sram", 0)!!
        assertEquals(4, after.baseRevision)
        assertTrue(!after.changedLocally && !after.inConflict)
    }

    @Test
    fun refusedUploadIsKeptOnTheDevice() = runTest {
        val save = synced(2, changed = true)
        t.enqueue(json("""{"detail": "Sauvegarde trop volumineuse"}""", 413))
        assertEquals(PushResult.Kept("too_large"), sync.push(save, "initial"))
        assertEquals("too_large", store.find("jeu", "sram", 0)!!.lastError)
        assertEquals("téléphone", store.read("jeu", "sram", 0).decodeToString())
    }

    @Test
    fun serverRestoreTurnsLocalChangesIntoConflicts() = runTest {
        synced(9, changed = true)
        val restored = "restaurée".encodeToByteArray()
        serveBody(restored, 2)
        sync.onServerRestored(LibraryManifest(saveEpoch = "restore-1", games = listOf(game(remote(2, restored)))))
        val save = store.find("jeu", "sram", 0)!!
        assertEquals(2, save.conflictRevision)
        assertEquals("téléphone", store.read("jeu", "sram", 0).decodeToString())
    }

    @Test
    fun serverRestoreWithoutTheSaveKeepsItForUpload() = runTest {
        synced(9, changed = false)
        sync.onServerRestored(LibraryManifest(saveEpoch = "restore-1", games = listOf(game())))
        val save = store.find("jeu", "sram", 0)!!
        assertEquals(0, save.baseRevision)
        assertTrue(save.pendingUpload)
    }

    @Test
    fun recordingALocalSaveMarksItForUpload() = runTest {
        store.write("jeu", "state", 1, local)
        val save = sync.recordLocal("jeu", "nes", "state", 1, "fceumm", "rom")!!
        assertEquals(sha256(local), save.localSha)
        assertEquals("2026-11-10T10:00:00Z", save.localPlayedAt)
        assertTrue(save.pendingUpload)
    }
}
