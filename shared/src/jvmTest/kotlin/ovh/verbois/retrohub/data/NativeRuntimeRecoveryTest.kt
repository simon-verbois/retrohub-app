package ovh.verbois.retrohub.data

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.MemorySaveStore
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.api.LibraryManifest
import ovh.verbois.retrohub.api.RemoteSave
import ovh.verbois.retrohub.bytes
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.game.NativeGameSaves
import ovh.verbois.retrohub.sync.SaveSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeRuntimeRecoveryTest {
    private val zeros = "0".repeat(32)
    private val title = "0004000000001234"
    private val data = "Azahar/nand/$zeros/title/00040000/00001234/data/main"

    @Test
    fun restoredEpochCannotReuseAnActiveGamesOldBaseRevision() = runTest {
        val t = TestApi()
        val fs = FakeFileSystem()
        val store = NativeSaveStore(MemorySaveStore(fs), fs, "/private".toPath(), { "account" })
        try {
            store.prepareGame("game", "3ds")
            val file = store.file("game", "sram", 0)
            fs.createDirectories(file.parent!!)
            fs.write(file) { writeUtf8("checkpoint") }
            val digest = fs.sha256(file)
            store.put(LocalSave("game", "sram", 0, "3ds", baseRevision = 1, baseSha = digest, localSha = digest))
            store.setGameActive("game", true)
            val remote = RemoteSave(gameId = "game", kind = "sram", slot = 0, revision = 1, sha256 = "restored-content")
            SaveSync(t.api, store).onServerRestored(
                LibraryManifest(
                    games = listOf(
                        LibraryGame("game", "game", title = "Game", console = "3ds", saves = listOf(remote)),
                    ),
                ),
            )
            val pending = store.find("game", "sram", 0)!!
            assertEquals(0, pending.baseRevision)
            assertTrue(pending.pendingUpload)
            assertEquals(digest, fs.sha256(file))
        } finally {
            t.close()
        }
    }

    @Test
    fun flushedDataAfterACrashConflictsWithRemoteProgressInsteadOfBeingOverwritten() = runTest {
        for (restoredServer in listOf(false, true)) {
            val t = TestApi()
            val fs = FakeFileSystem()
            val store = NativeSaveStore(MemorySaveStore(fs), fs, "/private".toPath(), { "account" })
            val root = "/runtime".toPath()
            fun write(directory: String, value: String) {
                val file = directory.toPath() / data
                fs.createDirectories(file.parent!!)
                fs.write(file) { writeUtf8(value) }
            }
            try {
                store.registerNativeRuntime("game", "3ds", "azahar", root.toString(), "rom", title)
                write("/runtime", "checkpoint")
                val localFile = store.file("game", "sram", 0)
                NativeGameSaves.export(fs, root, "3ds", "game", "rom", title, localFile)
                val base = fs.sha256(localFile)
                store.put(LocalSave("game", "sram", 0, "3ds", baseRevision = 1, baseSha = base, localSha = base, core = "azahar", romSha = "rom"))
                // The native core flushed this file, then the process ended before the next autosave.
                write("/runtime", "newer local progress")
                write("/remote-runtime", "remote progress")
                val remoteArchive = "/remote.bin".toPath()
                NativeGameSaves.export(fs, "/remote-runtime".toPath(), "3ds", "game", "rom", title, remoteArchive)
                val remoteSha = fs.sha256(remoteArchive)
                val body = fs.read(remoteArchive) { readByteArray() }
                val remote = RemoteSave(gameId = "game", kind = "sram", slot = 0, revision = 2, sha256 = remoteSha, core = "azahar", romSha256 = "rom")
                val game = LibraryGame("game", "game", title = "Game", console = "3ds", saves = listOf(remote))
                t.enqueue(bytes(body, 200, "ETag" to "2", "X-Content-SHA256" to remoteSha))
                val sync = SaveSync(t.api, store)
                if (restoredServer) sync.onServerRestored(LibraryManifest(games = listOf(game))) else sync.pull(game)
                val conflict = store.find("game", "sram", 0)!!
                assertTrue(conflict.inConflict)
                assertEquals(2, conflict.conflictRevision)
                NativeGameSaves.install(fs, localFile, "/inspect-local".toPath(), "3ds", "game", "rom", title)
                assertEquals("newer local progress", fs.read("/inspect-local".toPath() / data) { readUtf8() })
                assertEquals(remoteSha, fs.sha256(store.conflictFile("game", "sram", 0)))
                // A deliberate Keep server choice must not be undone by the recovery hook.
                sync.keepServer(conflict)
                store.checkpointAll()
                assertEquals(remoteSha, store.find("game", "sram", 0)?.localSha)
                assertFalse(store.find("game", "sram", 0)!!.inConflict)
                NativeGameSaves.install(fs, localFile, root, "3ds", "game", "rom", title)
                assertEquals("remote progress", fs.read(root / data) { readUtf8() })
            } finally {
                t.close()
            }
        }
    }

    @Test
    fun firstFlushedSaveIsRecoveredButAnActiveGameIsNeverRead() = runTest {
        val fs = FakeFileSystem()
        val store = NativeSaveStore(MemorySaveStore(fs), fs, "/private".toPath(), { "account" })
        store.registerNativeRuntime("game", "3ds", "azahar", "/runtime", "rom", title)
        val file = "/runtime".toPath() / data
        fs.createDirectories(file.parent!!)
        fs.write(file) { writeUtf8("first save") }
        store.checkpointAll()
        val first = store.find("game", "sram", 0)!!
        assertTrue(first.pendingUpload)
        store.setGameActive("game", true)
        fs.write(file) { writeUtf8("in progress") }
        store.checkpointAll()
        assertEquals(first.localSha, store.find("game", "sram", 0)?.localSha)
        store.setGameActive("game", false)
        store.checkpointAll()
        assertTrue(first.localSha != store.find("game", "sram", 0)?.localSha)
    }
}
