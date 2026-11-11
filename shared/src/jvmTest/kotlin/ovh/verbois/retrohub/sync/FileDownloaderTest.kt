package ovh.verbois.retrohub.sync

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.api.RomInfo
import ovh.verbois.retrohub.bytes
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Téléchargement reprenable (Range / 206), retour à zéro sur un 200 complet, empreinte vérifiée. */
class FileDownloaderTest {
    private val t = TestApi()
    private val fs = FakeFileSystem()
    private val downloader = FileDownloader(t.api, fs)
    private val data = ByteArray(40_000) { (it % 251).toByte() }
    private val rom = RomInfo(sha256(data), data.size.toLong(), "nes", "Jeu.nes", "/api/v1/games/jeu/rom/Jeu.nes?sha=x")
    private val target = "/roms/jeu/Jeu.nes".toPath()
    private val partial = "/roms/jeu/Jeu.nes.part".toPath()

    @AfterTest
    fun close() = t.close()

    @Test
    fun freshDownloadIsVerifiedThenMovedIntoPlace() = runTest {
        t.enqueue(bytes(data))
        val progress = mutableListOf<Long>()
        downloader.downloadRom(rom, target) { received, _ -> progress += received }
        assertContentEquals(data, fs.read(target) { readByteArray() })
        assertFalse(fs.exists(partial))
        assertEquals(data.size.toLong(), progress.last())
        assertNull(t.take().headers["Range"])
    }

    @Test
    fun interruptedDownloadResumesWithRange() = runTest {
        fs.createDirectories(target.parent!!)
        fs.write(partial) { write(data, 0, 15_000) }
        t.enqueue(bytes(data.copyOfRange(15_000, data.size), 206, "Content-Range" to "bytes 15000-39999/40000"))
        downloader.downloadRom(rom, target)
        assertContentEquals(data, fs.read(target) { readByteArray() })
        assertEquals("bytes=15000-", t.take().headers["Range"])
    }

    @Test
    fun fullResponseToARangeRequestStartsOver() = runTest {
        fs.createDirectories(target.parent!!)
        fs.write(partial) { write(ByteArray(15_000) { 9 }) }
        // Stockage S3 : la plage est ignorée, le fichier entier revient en 200.
        t.enqueue(bytes(data))
        downloader.downloadRom(rom, target)
        assertContentEquals(data, fs.read(target) { readByteArray() })
    }

    @Test
    fun corruptedPartialIsDiscardedAndDownloadedAgain() = runTest {
        fs.createDirectories(target.parent!!)
        fs.write(partial) { write(ByteArray(15_000) { 9 }) }
        t.enqueue(bytes(data.copyOfRange(15_000, data.size), 206, "Content-Range" to "bytes 15000-39999/40000"))
        t.enqueue(bytes(data))
        downloader.downloadRom(rom, target)
        assertContentEquals(data, fs.read(target) { readByteArray() })
        assertEquals(2, t.server.requestCount)
    }

    @Test
    fun persistentlyWrongFileIsReported() = runTest {
        t.enqueue(bytes(ByteArray(40_000)))
        t.enqueue(bytes(ByteArray(40_000)))
        assertFailsWith<CorruptedDownloadException> { downloader.downloadRom(rom, target) }
        assertFalse(fs.exists(target))
    }

    @Test
    fun changedRomOnTheServerAsksForTheCatalog() = runTest {
        t.enqueue(json("""{"detail": "La version installée a changé. Rechargez la fiche du jeu."}""", 409))
        assertFailsWith<RomChangedException> { downloader.downloadRom(rom, target) }
    }

    @Test
    fun alreadyVerifiedFileIsNotDownloadedAgain() = runTest {
        fs.createDirectories(target.parent!!)
        fs.write(target) { write(data) }
        downloader.downloadRom(rom, target)
        assertEquals(0, t.server.requestCount)
    }
}
