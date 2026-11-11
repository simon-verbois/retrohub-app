@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ovh.verbois.retrohub.game

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ForwardingFileSystem
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.MemorySaveStore
import ovh.verbois.retrohub.MemorySessionStore
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.sync.SaveSync
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class GameSessionTest {
    private val plan = LaunchPlan("game", "play", "Jeu", "nes", "fceumm", "/rom.nes", "local-rom", "/system", "/core", "/sram", false)

    @Test
    fun resetWaitsForAnOngoingSnapshotBeforeResuming() = runTest {
        val saves = MemorySaveStore()
        val engine = FakeEngine()
        val api = TestApi()
        try {
            val session =
                GameSession(plan, engine, saves.fs, saves, SaveSync(api.api, saves), MemorySessionStore(), {
                }, backgroundScope, io = StandardTestDispatcher(testScheduler))
            session.onFirstFrame()
            session.pause()
            val gate = CompletableDeferred<Unit>()
            engine.beforeSnapshot = { gate.await() }
            val snapshot = async { session.autoSave() }
            runCurrent()
            val reset = async { session.reset() }
            runCurrent()
            assertEquals(0, engine.resets)
            assertTrue(session.ui.value.paused)
            gate.complete(Unit)
            snapshot.await()
            reset.await()
            assertEquals(1, engine.resets)
            assertFalse(session.ui.value.paused)
        } finally {
            api.close()
        }
    }

    @Test
    fun closingDuringRestoreCannotReplaceTheSavedGameWithTheBootState() = runTest {
        val saves = MemorySaveStore()
        val original = byteArrayOf(9, 8, 7)
        saves.write("play", "state", 0, original)
        val gate = CompletableDeferred<Unit>()
        val engine = FakeEngine().apply { beforeLoad = { gate.await() } }
        val api = TestApi()
        try {
            val session = GameSession(
                plan.copy(statePath = saves.file("play", "state", 0).toString()), engine, saves.fs, saves,
                SaveSync(api.api, saves), MemorySessionStore(), {}, backgroundScope, io = StandardTestDispatcher(testScheduler),
            )
            val restore = async { session.onFirstFrame() }
            runCurrent()
            val finish = async { session.finish() }
            runCurrent()
            assertFalse(finish.isCompleted)
            gate.complete(Unit)
            restore.await()
            finish.await()
            assertContentEquals(original, saves.read("play", "state", 0))
        } finally {
            gate.complete(Unit)
            api.close()
        }
    }

    @Test
    fun unchangedSnapshotsAreNeitherReadBackNorRecordedAgain() = runTest {
        val reads = mutableListOf<Path>()
        val fs = object : ForwardingFileSystem(FakeFileSystem()) {
            override fun source(file: Path): okio.Source {
                reads += file
                return super.source(file)
            }
        }
        val saves = MemorySaveStore(fs)
        val engine = FakeEngine().apply { snapshot = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() } }
        val api = TestApi()
        try {
            val session = GameSession(
                plan, engine, fs, saves, SaveSync(api.api, saves), MemorySessionStore(), {}, backgroundScope,
                io = StandardTestDispatcher(testScheduler),
            )
            session.onFirstFrame()
            session.autoSave()
            val before = saves.find("play", "state", 0)
            reads.clear()
            session.autoSave()
            assertEquals(before, saves.find("play", "state", 0))
            assertEquals(sha256(engine.snapshot), before?.localSha)
            assertEquals("local-rom", before?.romSha)
            assertTrue(reads.isEmpty(), "Saving must not read the previous snapshot or hash the file again")
            assertContentEquals(engine.snapshot, saves.read("play", "state", 0))
        } finally {
            api.close()
        }
    }

    @Test
    fun manualLoadRejectsAnotherRomOrCoreWithoutTouchingTheEngine() = runTest {
        val saves = MemorySaveStore()
        val engine = FakeEngine()
        val api = TestApi()
        try {
            val session = GameSession(
                plan, engine, saves.fs, saves, SaveSync(api.api, saves), MemorySessionStore(), {}, backgroundScope,
                io = StandardTestDispatcher(testScheduler),
            )
            saves.write("play", "state", 1, byteArrayOf(1))
            val wrongRom = LocalSave("play", "state", 1, "nes", core = "fceumm", romSha = "other-rom")
            saves.put(wrongRom)
            assertFalse(session.loadState(1))
            saves.put(wrongRom.copy(core = "other-core", romSha = "local-rom"))
            assertFalse(session.loadState(1))
            assertEquals(0, engine.loaded)
            saves.put(wrongRom.copy(romSha = "local-rom"))
            assertTrue(session.loadState(1))
            assertEquals(1, engine.loaded)
        } finally {
            api.close()
        }
    }

    @Test
    fun concurrentFinishRecordsOneSessionAndExcludesPausedTime() = runTest {
        val saves = MemorySaveStore()
        val sessions = MemorySessionStore()
        val api = TestApi()
        var now = Instant.parse("2026-11-11T10:00:00Z")
        var syncs = 0
        try {
            val session = GameSession(
                plan, FakeEngine(), saves.fs, saves, SaveSync(api.api, saves), sessions, { syncs++ }, backgroundScope,
                AppClock { now }, StandardTestDispatcher(testScheduler),
            )
            session.onFirstFrame()
            now = Instant.parse("2026-11-11T10:00:12Z")
            session.pause()
            now = Instant.parse("2026-11-11T10:01:12Z")
            listOf(async { session.finish() }, async { session.finish() }).awaitAll()
            assertEquals(1, sessions.sessions.size)
            assertEquals(12L, sessions.sessions.single().playedSeconds)
            assertEquals(1, syncs)
        } finally {
            api.close()
        }
    }
}
