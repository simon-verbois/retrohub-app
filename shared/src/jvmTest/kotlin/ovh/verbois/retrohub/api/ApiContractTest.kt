package ovh.verbois.retrohub.api

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.json
import ovh.verbois.retrohub.status
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contrat de l’API native contre MockWebServer, avec les réponses de docs/native-api.md. */
class ApiContractTest {
    private val t = TestApi()

    @AfterTest
    fun close() = t.close()

    @Test
    fun statusIsReadWithoutTokenAndApiVersionChecked() = runTest {
        t.enqueue(json(STATUS))
        val status = t.api.status(t.session.server!!)
        assertEquals("2026.46.1", status.serverVersion)
        assertEquals("2026.47.2", status.apps?.android?.latestVersion)
        val request = t.take()
        assertEquals("/api/v1/native/status", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun unknownApiVersionIsRefused() = runTest {
        t.enqueue(json(STATUS.replace("\"api_version\": 1", "\"api_version\": 2")))
        assertFailsWith<ApiException.UnsupportedServer> { t.api.status(t.session.server!!) }
    }

    @Test
    fun aWebsiteThatIsNotRetroHubIsRefused() = runTest {
        t.enqueue(status(200).newBuilder().body("<html>Bonjour</html>").build())
        assertFailsWith<ApiException.UnsupportedServer> { t.api.status(t.session.server!!) }
    }

    @Test
    fun pairingSendsCodeDeviceAndPlatform() = runTest {
        t.enqueue(
            json(
                """{"token": "abc", "device": {"id": "d1", "name": "Pixel 8", "platform": "android"},
                        "user": {"id": "u1", "username": "player", "is_admin": false, "preferences": {}}}""",
                201,
            ),
        )
        val response = t.api.pair(t.session.server!!, PairRequest("ABCD-EFGH", "Pixel 8", "android"))
        assertEquals("abc", response.token)
        val request = t.take()
        assertEquals("/api/v1/auth/pair", request.url.encodedPath)
        assertEquals("""{"code":"ABCD-EFGH","device_name":"Pixel 8","platform":"android"}""", request.body!!.utf8())
    }

    @Test
    fun invalidPairingCodeIsA400WithTheServerMessage() = runTest {
        t.enqueue(json("""{"detail": "Ce code n’est plus valable. Affichez un nouveau code dans Compte → Appareils."}""", 400))
        val error = assertFailsWith<ApiException.Http> { t.api.pair(t.session.server!!, PairRequest("x", "P", "android")) }
        assertEquals(400, error.status)
        assertTrue(error.message!!.contains("nouveau code"))
    }

    @Test
    fun wrongPasswordDoesNotForgetAnyToken() = runTest {
        t.enqueue(json("""{"detail": "Nom de compte ou mot de passe incorrect"}""", 401))
        assertFailsWith<ApiException.Unauthorized> { t.api.login(t.session.server!!, TokenRequest("a", "b", "P", "android")) }
        assertNull(t.session.unauthorized)
    }

    @Test
    fun revokedTokenIsForgotten() = runTest {
        t.enqueue(json("""{"detail": "Cet appareil n’est plus connecté. Reliez-le de nouveau à votre compte."}""", 401))
        assertFailsWith<ApiException.Unauthorized> { t.api.library(null) }
        assertEquals("Cet appareil n’est plus connecté. Reliez-le de nouveau à votre compte.", t.session.unauthorized)
        assertEquals("Bearer device-token", t.take().headers["Authorization"])
    }

    @Test
    fun libraryUsesEtagAndIfNoneMatch() = runTest {
        t.enqueue(json(LIBRARY, 200, "ETag" to "\"abc\""))
        val first = assertIs<LibraryResult.Updated>(t.api.library(null))
        assertEquals("\"abc\"", first.etag)
        val game = first.manifest.games.single()
        assertEquals("jeu", game.playGameId)
        assertEquals("fceumm", game.saves.single().core)
        assertEquals("Pixel 8", game.saves.single().device?.name)
        assertEquals("Pixel 8", game.saveContexts.single().lastSave?.device?.name)
        assertNull(t.take().headers["If-None-Match"])
        t.enqueue(status(304, "ETag" to "\"abc\""))
        assertIs<LibraryResult.NotModified>(t.api.library("\"abc\""))
        assertEquals("\"abc\"", t.take().headers["If-None-Match"])
    }

    @Test
    fun shortMaintenanceIsRetriedAfterRetryAfter() = runTest {
        t.enqueue(json("""{"detail": "Maintenance"}""", 503, "Retry-After" to "5"))
        t.enqueue(json(LIBRARY))
        assertIs<LibraryResult.Updated>(t.api.library(null))
        assertEquals(listOf(5L), t.waits)
        assertEquals(2, t.server.requestCount)
    }

    @Test
    fun longMaintenanceIsHandedToTheBackgroundTask() = runTest {
        t.enqueue(json("""{"detail": "Maintenance"}""", 503, "Retry-After" to "600"))
        val error = assertFailsWith<ApiException.Maintenance> { t.api.library(null) }
        assertEquals(600, error.retryAfterSeconds)
        assertTrue(t.waits.isEmpty())
    }

    @Test
    fun maintenanceRetriesAreBounded() = runTest {
        repeat(4) { t.enqueue(json("{}", 503, "Retry-After" to "1")) }
        assertFailsWith<ApiException.Maintenance> { t.api.library(null) }
        assertEquals(3, t.waits.size)
    }

    @Test
    fun saveUploadSendsEveryHeaderAndConflictCarriesTheRevision() = runTest {
        t.enqueue(
            json(
                """{"detail": {"message": "Une progression plus récente existe. Votre copie locale est conservée.", "current_revision": 7}}""",
                409,
            ),
        )
        val fs = FakeFileSystem()
        val file = "/s.bin".toPath()
        fs.write(file) { writeUtf8("data") }
        val error =
            assertFailsWith<ApiException.SaveConflict> {
                t.api.putSave(RetroHubApi.SaveUpload("jeu", "sram", 0, 3, "initial", "rom", "fceumm", "2026-11-10T10:00:00Z"), fs, file)
            }
        assertEquals(7, error.currentRevision)
        val request = t.take()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/games/jeu/saves/sram/0", request.url.encodedPath)
        assertEquals("3", request.headers["If-Match"])
        assertEquals("initial", request.headers["X-Save-Epoch"])
        assertEquals("rom", request.headers["X-ROM-SHA256"])
        assertEquals("fceumm", request.headers["X-Emulator-Core"])
        assertEquals("2026-11-10T10:00:00Z", request.headers["X-Played-At"])
        assertEquals(sha256("data".encodeToByteArray()), request.headers["X-Content-SHA256"])
        assertEquals("data", request.body!!.utf8())
    }

    @Test
    fun corruptedUploadIsSentAgainOnce() = runTest {
        t.enqueue(json("""{"detail": "Le contenu reçu ne correspond pas."}""", 400))
        t.enqueue(json("""{"kind": "sram", "slot": 0, "revision": 4, "sha256": "x"}"""))
        val fs = FakeFileSystem()
        val file = "/s.bin".toPath()
        fs.write(file) { writeUtf8("data") }
        val saved = t.api.putSave(RetroHubApi.SaveUpload("jeu", "sram", 0, 3, "initial", "rom", "fceumm", null), fs, file)
        assertEquals(4, saved.revision)
        assertEquals(2, t.server.requestCount)
    }

    @Test
    fun downloadedSaveIsCheckedAgainstContentSha() = runTest {
        val good = "progress".encodeToByteArray()
        t.enqueue(ovh.verbois.retrohub.bytes("abîmé".encodeToByteArray(), 200, "X-Content-SHA256" to sha256(good), "ETag" to "\"5\""))
        t.enqueue(
            ovh.verbois.retrohub.bytes(good, 200, "X-Content-SHA256" to sha256(good), "ETag" to "\"5\"", "X-Save-Core" to "fceumm"),
        )
        val fs = FakeFileSystem()
        fs.createDirectories("/d".toPath())
        val saved = t.api.readSave("jeu", "sram", 0, 5, fs, "/d/sram-0.bin".toPath())
        assertEquals(5, saved.revision)
        assertEquals("fceumm", saved.core)
        assertEquals("progress", fs.read("/d/sram-0.bin".toPath()) { readUtf8() })
        assertEquals("/api/v1/games/jeu/saves/sram/0?revision=5", t.take().target)
    }

    @Test
    fun sessionsArePostedAsDocumented() = runTest {
        t.enqueue(json("""{"results": [{"client_id": "uuid", "status": "recorded"}]}"""))
        val results = t.api.postSessions(listOf(OfflineSession("uuid", "jeu", "2026-11-10T08:00:00Z", "2026-11-10T08:30:00Z", 1500)))
        assertEquals("recorded", results.results.single().status)
        assertEquals(
            """{"sessions":[{"client_id":"uuid","game_id":"jeu","started_at":"2026-11-10T08:00:00Z","ended_at":"2026-11-10T08:30:00Z","played_seconds":1500}]}""",
            t.take().body!!.utf8(),
        )
    }

    @Test
    fun networkFailureBecomesOffline() = runTest {
        t.close()
        assertFailsWith<ApiException.Offline> { t.api.library(null) }
    }

    companion object {
        const val STATUS =
            """{"name": "RetroHub", "server_version": "2026.46.1", "api_version": 1,
 "auth": {"password": true, "pairing": true, "oidc": false},
 "limits": {"max_save_mb": 64, "max_upload_mb": 1024},
 "features": ["device_tokens", "pairing", "library_manifest", "save_sync", "offline_sessions", "range_downloads",
              "server_artwork", "progression_choice", "save_checksums", "gamecube_gci_saves"],
 "apps": {"android": {"min_version": "2026.46.1", "latest_version": "2026.47.2", "download_url": "https://x/retrohub.apk"}}}"""

        const val LIBRARY =
            """{"api_version": 1, "server_version": "2026.46.1", "save_epoch": "initial", "generated_at": "2026-11-10T09:00:00",
 "games": [{"id": "racine", "play_game_id": "jeu", "progression_choice_required": false,
   "save_contexts": [{"game_id": "jeu", "title": "Jeu", "version_id": "v1", "label": "France", "installed": true, "saves": 2,
                      "played_seconds": 5400, "last_played_at": "2026-11-09T20:00:00",
                      "last_save": {"kind": "sram", "slot": 0, "played_at": "2026-11-09T20:00:00",
                                    "device": {"id": "d", "name": "Pixel 8", "platform": "android"}}}],
   "title": "Jeu", "console": "nes", "cover_url": "/api/v1/games/racine/cover?v=1",
   "screenshots": ["/api/v1/games/racine/screenshots/0?v=1"], "languages": ["fr"], "genre": "Action", "year": 1990,
   "favorite": true, "played_seconds": 3600, "last_played_at": "2026-11-09T20:00:00", "installed": true,
   "rom": {"sha256": "abc", "size": 123, "extension": "nes", "filename": "Jeu (France).nes",
           "url": "/api/v1/games/jeu/rom/Jeu%20%28France%29.nes?sha=abc"},
   "bios": null,
   "saves": [{"id": "s", "game_id": "jeu", "kind": "sram", "slot": 0, "revision": 3, "sha256": "f00", "size": 8192,
              "core": "fceumm", "rom_sha256": "abc", "created_at": "2026-11-09T20:00:00", "played_at": "2026-11-09T20:00:00",
              "device": {"id": "d", "name": "Pixel 8", "platform": "android"}}]}]}"""
    }
}
