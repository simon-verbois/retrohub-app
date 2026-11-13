package ovh.verbois.retrohub.game

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameContentTest {
    private val fs = FakeFileSystem()
    private val archive = "/roms/game.zip".toPath()

    private fun archive(vararg files: Pair<String, String>) {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        fs.createDirectories(archive.parent!!)
        fs.write(archive) { write(bytes.toByteArray()) }
    }

    @Test
    fun cartridgeArchivesKeepTheirExistingCoreLoadingPath() {
        archive("game.nes" to "cartridge")
        assertEquals(archive, GameContent.prepare(fs, archive, "first", "nes"))
        assertFalse(fs.exists(archive.parent!! / "content"))
    }

    @Test
    fun playstationZipLoadsTheCueWithAllItsTracksAndKeepsTheOriginalArchive() {
        archive("Game/disc.cue" to "FILE \"track.bin\" BINARY", "Game/track.bin" to "track data", "cover.png" to "picture")
        val content = GameContent.prepare(fs, archive, "first", "psx")
        assertEquals("/roms/content/Game/disc.cue", content.toString())
        assertTrue(fs.exists(content.parent!! / "track.bin"))
        assertTrue(fs.exists(archive))
        assertEquals(content, GameContent.prepare(fs, archive, "first", "psx"))
    }

    @Test
    fun changedArchiveReplacesExtractedFilesWithoutLeavingOldTracks() {
        archive("disc.cue" to "old", "old.bin" to "old")
        GameContent.prepare(fs, archive, "first", "psx")
        archive("disc.cue" to "new", "new.bin" to "new")
        GameContent.prepare(fs, archive, "second", "psx")
        assertFalse(fs.exists("/roms/content/old.bin".toPath()))
        assertTrue(fs.exists("/roms/content/new.bin".toPath()))
    }

    @Test
    fun multipleDiscsUseAStablePlaylistAndAnExistingPlaylistTakesPriority() {
        archive("disc2.cue" to "two", "disc1.cue" to "one")
        val content = GameContent.prepare(fs, archive, "first", "psx")
        assertEquals("discs.m3u", content.name)
        assertEquals("disc1.cue\ndisc2.cue\n", fs.read(content) { readUtf8() })
        archive("collection.m3u" to "disc1.cue", "disc1.cue" to "one")
        assertEquals("collection.m3u", GameContent.prepare(fs, archive, "second", "psx").name)
    }

    @Test
    fun dreamcastUsesItsDiscDescriptorAndUnknownContentsAreRejected() {
        archive("disc.gdi" to "descriptor", "track01.bin" to "data")
        assertEquals("disc.gdi", GameContent.prepare(fs, archive, "first", "dreamcast").name)
        archive("../escape.cue" to "invalid", "readme.txt" to "no disc")
        assertFailsWith<IllegalArgumentException> { GameContent.prepare(fs, archive, "second", "psx") }
        assertFalse(fs.exists("/roms/escape.cue".toPath()))
    }
}
