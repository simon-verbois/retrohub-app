package ovh.verbois.retrohub.game

import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.sync.zipEntries
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeGameSavesTest {
    private val fs = FakeFileSystem()
    private val root = "/runtime".toPath()
    private val zip = "/saves/sram.bin".toPath()
    private val title = "0004000000001234"
    private val zeros = "0".repeat(32)

    private fun write(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) {
        val file = root / name
        fs.createDirectories(file.parent!!)
        fs.write(file) { write(bytes) }
    }

    @Test
    fun failedPublicationRollsBackTheWholeRuntimeWithoutReplacingExistingData() {
        write("Azahar/nand/$zeros/title/00040000/00001234/data/main")
        assertTrue(NativeGameSaves.export(fs, root, "3ds", "game", "rom", title, zip))
        val runtime = "/existing-runtime".toPath()
        fs.createDirectories(runtime)
        fs.write(runtime / "keep.bin") { writeUtf8("local") }
        var failed = false
        val failing = object : ForwardingFileSystem(fs) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == runtime && !failed) {
                    failed = true
                    throw IOException("Simulated publication failure")
                }
                super.atomicMove(source, target)
            }
        }
        assertFailsWith<IOException> { NativeGameSaves.install(failing, zip, runtime, "3ds", "game", "rom", title) }
        assertEquals("local", fs.read(runtime / "keep.bin") { readUtf8() })
        assertFalse(fs.exists(runtime / "Azahar"))
        assertFalse(fs.exists("/existing-runtime.save-import".toPath()))
    }

    @Test
    fun threeDsExportsOnlyTheTitleAndNormalizesSdIdentifiers() {
        val data = "Azahar/sdmc/Nintendo 3DS/${"a".repeat(32)}/${"b".repeat(32)}/title/00040000/00001234/data/main"
        write(data)
        write(data.replace("00001234", "00005678"))
        write("Azahar/sysdata/shared_font.bin")
        write("Azahar/shaders/cache")
        assertTrue(NativeGameSaves.export(fs, root, "3ds", "game", "rom", title, zip))
        val canonical = "Azahar/sdmc/Nintendo 3DS/$zeros/$zeros/title/00040000/00001234/data/main"
        assertEquals(listOf(canonical, "manifest.json"), zipEntries(fs, zip).sorted())
        fs.deleteRecursively(root)
        NativeGameSaves.install(fs, zip, root, "3ds", "game", "rom", title)
        assertTrue(fs.exists(root / canonical))
        assertFalse(fs.exists(root / "Azahar/sysdata/shared_font.bin"))
        // A stopped core can have flushed newer files after the last archive checkpoint.
        fs.write(root / canonical) { writeUtf8("newer local data") }
        NativeGameSaves.install(fs, zip, root, "3ds", "game", "rom", title)
        assertEquals("newer local data", fs.read(root / canonical) { readUtf8() })
        fs.delete(root / ".restored-archive")
        val before = fs.read(root / canonical) { readByteString() }
        assertFailsWith<IllegalArgumentException> { NativeGameSaves.install(fs, zip, root, "3ds", "other-game", "rom", title) }
        assertEquals(before, fs.read(root / canonical) { readByteString() })
    }

    @Test
    fun ps2CardsRoundTripIntoOnlyTheDedicatedGameDirectory() {
        val card = ByteArray(8 * 1024 * 1024)
        "Sony PS2 Memory Card Format".encodeToByteArray().copyInto(card)
        write("pcsx2/memcards/Mcd001.ps2", card)
        write("pcsx2/memcards/Mcd002.ps2", ByteArray(8 * 1024 * 1024)) // Not yet formatted.
        write("pcsx2/bios/bios.bin")
        assertTrue(NativeGameSaves.export(fs, root, "ps2", "game", "rom", null, zip))
        assertEquals(listOf("cards/slot1.ps2", "manifest.json"), zipEntries(fs, zip).sorted())
        val target = "/other-runtime".toPath()
        NativeGameSaves.install(fs, zip, target, "ps2", "game", "rom", null)
        assertEquals(card.size.toLong(), fs.metadata(target / "pcsx2/memcards/Mcd001.ps2").size)
        assertFalse(fs.exists(target / "pcsx2/bios/bios.bin"))
        assertFailsWith<IllegalArgumentException> { NativeGameSaves.install(fs, zip, target, "ps2", "game", "other-rom", null) }
    }

    @Test
    fun homebrewWithoutAValidatedTitleKeepsItsDataLocal() {
        write("Azahar/sdmc/homebrew/save.bin")
        assertFalse(NativeGameSaves.export(fs, root, "3ds", "game", "rom", null, zip))
        assertTrue(fs.exists(root / "Azahar/sdmc/homebrew/save.bin"))
    }
}
