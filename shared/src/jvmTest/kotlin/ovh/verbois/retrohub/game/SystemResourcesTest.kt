package ovh.verbois.retrohub.game

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.api.SystemResource
import ovh.verbois.retrohub.api.SystemResourceFile
import ovh.verbois.retrohub.bytes
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.sync.zipFiles
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SystemResourcesTest {
    @Test
    fun privateResourceDownloadVerifiesEveryFileBeforeReplacementAndCachesOffline() = runTest {
        val t = TestApi()
        val fs = FakeFileSystem()
        val source = "/source/font".toPath()
        fs.createDirectories(source.parent!!)
        val font = byteArrayOf(1, 2, 3)
        fs.write(source) { write(font) }
        val archive = "/object.zip".toPath()
        val name = "Azahar/sysdata/shared_font.bin"
        zipFiles(fs, mapOf(name to source), archive)
        val body = fs.read(archive) { readByteArray() }
        val root = "/account/game".toPath()
        val resource = SystemResource(
            "system",
            "zip",
            sha256(body),
            body.size.toLong(),
            listOf(SystemResourceFile(name, font.size.toLong(), sha256(font))),
            "/api/v1/native/system-resources/3ds/system",
        )
        try {
            val installer = SystemResources(t.api, fs)
            t.enqueue(bytes(body))
            installer.install("3ds", listOf(resource), root, online = true)
            assertEquals("Bearer device-token", t.take().headers["Authorization"])
            assertContentEquals(font, fs.read(root / name) { readByteArray() })
            installer.install("3ds", listOf(resource), root, online = false)
            // A bad descriptor/object cannot replace the previous resource.
            t.enqueue(bytes(body))
            assertFailsWith<IllegalArgumentException> {
                installer.install("3ds", listOf(resource.copy(files = listOf(resource.files.single().copy(sha256 = "bad")))), root, online = true)
            }
            assertContentEquals(font, fs.read(root / name) { readByteArray() })
            assertFalse(fs.exists(root / ".resource-import"))
        } finally {
            t.close()
        }
    }

    @Test
    fun missingPs2ResourcesHaveAnActionableImportError() = runTest {
        val t = TestApi()
        try {
            val error = assertFailsWith<IllegalArgumentException> {
                SystemResources(t.api, FakeFileSystem()).install("ps2", emptyList(), "/game".toPath(), online = false)
            }
            assertEquals("Importez le BIOS et les ressources PS2 dans les réglages de votre serveur, puis synchronisez la bibliothèque.", error.message)
        } finally {
            t.close()
        }
    }
}
