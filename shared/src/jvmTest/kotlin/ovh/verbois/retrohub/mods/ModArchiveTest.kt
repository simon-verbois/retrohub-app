package ovh.verbois.retrohub.mods

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.api.ApiException
import ovh.verbois.retrohub.api.ModArtifact
import ovh.verbois.retrohub.api.ModComponent
import ovh.verbois.retrohub.api.ModFile
import ovh.verbois.retrohub.bytes
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.sync.FileDownloader
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ModArchiveTest {
    private val fs = FakeFileSystem()
    private val path = "/pack.zip".toPath()

    private fun archive(vararg names: String): Pair<ModArtifact, ByteArray> {
        val data = byteArrayOf(1, 2, 3)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for (name in names) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        val bytes = output.toByteArray()
        fs.write(path) { write(bytes) }
        val hash = sha256(bytes)
        return ModArtifact(hash, bytes.size.toLong(), "/api/v1/native/mods/objects/$hash", listOf(ModFile("image.png", 3, sha256(data)))) to bytes
    }

    @Test
    fun rawNamesCannotHideTraversalAbsolutePathsDuplicatesOrUnlistedFiles() {
        for (names in listOf(
            arrayOf("../image.png"),
            arrayOf("/image.png"),
            arrayOf("folder/../image.png"),
            arrayOf("image.png", "IMAGE.PNG"),
            arrayOf("image.png", "unlisted.png"),
            arrayOf("folder\\image.png"),
        )) {
            val (artifact, _) = archive(*names)
            assertFailsWith<IllegalArgumentException> { validateModArchive(fs, path, artifact) }
        }
        val (valid, _) = archive("image.png")
        validateModArchive(fs, path, valid)
    }

    @Test
    fun zip64DirectoryIsValidatedWithoutInflatingEntries() {
        val (artifact, bytes) = archive("image.png")
        val end = bytes.size - 22
        val eocd = Buffer().write(bytes, end, 22)
        eocd.skip(12)
        val centralSize = eocd.readIntLe().toLong() and 0xffffffffL
        val centralOffset = eocd.readIntLe().toLong() and 0xffffffffL
        val result = Buffer().write(bytes, 0, end)
            .writeIntLe(0x06064b50).writeLongLe(44).writeShortLe(45).writeShortLe(45)
            .writeIntLe(0).writeIntLe(0).writeLongLe(1).writeLongLe(1).writeLongLe(centralSize).writeLongLe(centralOffset)
            .writeIntLe(0x07064b50).writeIntLe(0).writeLongLe(end.toLong()).writeIntLe(1)
            .writeIntLe(0x06054b50).writeShortLe(0).writeShortLe(0).writeShortLe(65535).writeShortLe(65535)
            .writeIntLe(-1).writeIntLe(-1).writeShortLe(0).readByteArray()
        fs.write(path) { write(result) }
        validateModArchive(fs, path, artifact)
    }

    @Test
    fun symlinksAndEncryptedArchivesAreRejectedBeforeExtraction() {
        val (artifact, bytes) = archive("image.png")
        val central = bytes.indices.first { index -> index + 4 < bytes.size && bytes.sliceArray(index..index + 3).contentEquals(byteArrayOf(0x50, 0x4b, 1, 2)) }
        val symlink = bytes.copyOf()
        symlink[central + 41] = 0xa0.toByte()
        fs.write(path) { write(symlink) }
        assertFailsWith<IllegalArgumentException> { validateModArchive(fs, path, artifact) }
        val encrypted = bytes.copyOf()
        encrypted[central + 8] = (encrypted[central + 8].toInt() or 1).toByte()
        fs.write(path) { write(encrypted) }
        assertFailsWith<IllegalArgumentException> { validateModArchive(fs, path, artifact) }
    }

    @Test
    fun fileChecksumsAndBoundsAreVerifiedBeforePublishingAnyFiles() = runTest {
        val t = TestApi()
        try {
            val (artifact, bytes) = archive("image.png")
            val release = modRelease().copy(
                components = listOf(ModComponent(ModPlanner.TEXTURES, artifact.copy(files = listOf(artifact.files.single().copy(sha256 = "0".repeat(64)))))),
            )
            val plan = ModPlanner.plan(listOf(release.selection), listOf(release), modEnvironment)
            t.enqueue(bytes(bytes))
            val installer = ModFiles(fs, "/mods".toPath(), FileDownloader(t.api, fs)) { Long.MAX_VALUE }
            assertFailsWith<IllegalArgumentException> { installer.install(plan, true) { _, _ -> } }
            assertFalse(fs.exists("/mods/files/${artifact.sha256}/.complete".toPath()))
            assertFalse(fs.exists("/mods/files/${artifact.sha256}.staging".toPath()))
        } finally {
            t.close()
        }
    }

    @Test
    fun fullResponseCannotGrowPastTheAdvertisedObjectSize() = runTest {
        val t = TestApi()
        try {
            t.enqueue(bytes(ByteArray(10_000)))
            val downloader = FileDownloader(t.api, fs)
            assertEquals(422, assertFailsWith<ApiException.Http> { downloader.downloadVerified("/object", "0".repeat(64), 3, "/bounded".toPath()) }.status)
            assertFalse(fs.exists("/bounded".toPath()))
            assertTrue((fs.metadataOrNull("/bounded.part".toPath())?.size ?: 0) <= 3)
        } finally {
            t.close()
        }
    }
}
