package ovh.verbois.retrohub.game

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sauvegardes GameCube au format gci_zip : seuls les fichiers du jeu sont échangés. */
class GameCubeSavesTest {
    private val fs = FakeFileSystem()
    private val user = "/core/User".toPath()

    private fun disc(path: String, header: ByteArray) = path.toPath().also { file ->
        fs.createDirectories(file.parent!!)
        fs.write(file) { write(header + ByteArray(0x9000)) }
    }

    private fun gci(region: String, name: String, code: String) {
        val dir = GameCubeSaves.cardDir(user, region)
        fs.createDirectories(dir)
        fs.write(dir / name) { write(code.encodeToByteArray() + ByteArray(60 + 8192) { 1 }) }
    }

    @Test
    fun gameCodeIsReadFromIsoAndRvzHeaders() {
        assertEquals("GALE", GameCubeSaves.gameCode(fs, disc("/a.iso", "GALE01".encodeToByteArray())))
        val rvz = ByteArray(0x58) + "GZLP01".encodeToByteArray()
        "RVZ".encodeToByteArray().copyInto(rvz)
        rvz[3] = 1
        assertEquals("GZLP", GameCubeSaves.gameCode(fs, disc("/b.rvz", rvz)))
        assertEquals("EUR", GameCubeSaves.region("GZLP"))
        assertEquals("USA", GameCubeSaves.region("GALE"))
        assertEquals("JAP", GameCubeSaves.region("GALJ"))
    }

    @Test
    fun onlyThisGamesFilesAreExportedThenRestored() {
        gci("USA", "01-GALE-SuperSmashBros.gci", "GALE")
        gci("USA", "01-GZLE-Zelda.gci", "GZLE")
        val zip = "/saves/gc/sram-0.bin".toPath()
        fs.createDirectories(zip.parent!!)
        assertTrue(GameCubeSaves.export(fs, user, "GALE", zip))
        assertEquals(listOf("01-GALE-SuperSmashBros.gci"), GameCubeSaves.entries(fs, zip))
        // Une autre console : dossier vide, puis restauration.
        fs.deleteRecursively(user)
        GameCubeSaves.install(fs, zip, user, "GALE")
        val card = GameCubeSaves.cardDir(user, "USA")
        assertEquals(listOf("01-GALE-SuperSmashBros.gci"), fs.list(card).map { it.name })
    }

    @Test
    fun noSaveYetExportsNothing() {
        assertFalse(GameCubeSaves.export(fs, user, "GALE", "/saves/x.bin".toPath()))
    }

    @Test
    fun exportIsStableWhenNothingChanged() {
        gci("EUR", "a.gci", "GZLP")
        val first = "/z1.bin".toPath()
        val second = "/z2.bin".toPath()
        GameCubeSaves.export(fs, user, "GZLP", first)
        GameCubeSaves.export(fs, user, "GZLP", second)
        assertEquals(fs.read(first) { readByteString() }, fs.read(second) { readByteString() })
    }
}
