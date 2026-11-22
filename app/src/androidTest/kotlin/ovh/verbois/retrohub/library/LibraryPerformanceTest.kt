package ovh.verbois.retrohub.library

import android.os.StrictMode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.ApiSession
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.api.ModArtifact
import ovh.verbois.retrohub.api.ModComponent
import ovh.verbois.retrohub.api.ModFile
import ovh.verbois.retrohub.api.ModProfile
import ovh.verbois.retrohub.api.ModRelease
import ovh.verbois.retrohub.api.ModSelection
import ovh.verbois.retrohub.api.RetroHubApi
import ovh.verbois.retrohub.api.RomInfo
import ovh.verbois.retrohub.api.apiHttpClient
import ovh.verbois.retrohub.core.AppClock
import ovh.verbois.retrohub.core.DeviceClass
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.data.AppDatabase
import ovh.verbois.retrohub.data.AppPaths
import ovh.verbois.retrohub.data.GameEntity
import ovh.verbois.retrohub.data.KeyValueStore
import ovh.verbois.retrohub.data.LocalRomEntity
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.data.NativeSaveStore
import ovh.verbois.retrohub.data.RoomSaveStore
import ovh.verbois.retrohub.data.configured
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.game.GameLauncher
import ovh.verbois.retrohub.game.LaunchCheck
import ovh.verbois.retrohub.game.NativeGameSaves
import ovh.verbois.retrohub.mods.ModEnvironment
import ovh.verbois.retrohub.mods.ModInstallation
import ovh.verbois.retrohub.mods.ModLocalState
import ovh.verbois.retrohub.mods.ModPlanner
import ovh.verbois.retrohub.mods.ModRepository
import ovh.verbois.retrohub.sync.FileDownloader
import ovh.verbois.retrohub.sync.SaveSync
import java.io.File
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class LibraryPerformanceTest {
    @Test
    fun offlineTextureProfileSwitchRecoversFlushedProgressBeforeReplacingRuntimeBinding() = runBlocking {
        val titleId = "0004000000033600"
        val zeros = "0".repeat(32)
        val savePath = "Azahar/nand/$zeros/title/00040000/00033600/data/main"
        val romPath = paths.romDir("play") / "game.3ds"
        fs.createDirectories(romPath.parent!!)
        fs.write(romPath) { writeUtf8("fixture") }
        val digest = fs.sha256(romPath)
        val remote = LibraryGame("game", "play", title = "Jeu", console = "3ds")
        val game = Game(remote, LocalRomEntity("play", "game", "3ds", "Jeu", romPath.toString(), digest, 7, instant.toString(), titleId))
        val native = NativeSaveStore(saves, fs, paths.root / "native-saves", { "account" })
        val previous = paths.coreSaves / "previous-profile"
        val data = previous / savePath
        fs.createDirectories(data.parent!!)
        fs.write(data) { writeUtf8("checkpoint") }
        native.registerNativeRuntime("play", "3ds", "azahar", previous.toString(), digest, titleId)
        val archive = native.file("play", "sram", 0)
        NativeGameSaves.export(fs, previous, "3ds", "play", digest, titleId, archive)
        val checkpoint = fs.sha256(archive)
        native.put(LocalSave("play", "sram", 0, "3ds", baseRevision = 1, baseSha = checkpoint, localSha = checkpoint, core = "azahar", romSha = digest))
        fs.write(data) { writeUtf8("newest flushed progress") }

        val session = object : ApiSession {
            override val server: String? = null
            override val token: String? = null
            override suspend fun onUnauthorized(message: String) = Unit
        }
        val api = RetroHubApi(http, session)
        val objectHash = "c".repeat(64)
        val selection = ModSelection("textures", "1")
        val release = ModRelease(
            selection,
            "Textures",
            titleIds = listOf(titleId),
            components = listOf(
                ModComponent(
                    ModPlanner.TEXTURES,
                    ModArtifact(objectHash, 123, "/api/v1/native/mods/objects/$objectHash", listOf(ModFile("image.png", 3, sha256(byteArrayOf(1, 2, 3))))),
                ),
            ),
        )
        val state = ModLocalState(active = ModInstallation(ModProfile(1, listOf(selection)), listOf(release)))
        val statePath = paths.root / "mods/account/profiles" / (sha256("game".encodeToByteArray()) + ".json")
        fs.createDirectories(statePath.parent!!)
        fs.write(statePath) { writeUtf8(ApiJson.encodeToString(ModLocalState.serializer(), state)) }
        val extracted = paths.root / "mods/account/files" / objectHash / "image.png"
        fs.createDirectories(extracted.parent!!)
        fs.write(extracted) { write(byteArrayOf(1, 2, 3)) }
        val mods = ModRepository(
            api,
            fs,
            paths.root / "mods",
            { "account" },
            { false },
            { Long.MAX_VALUE },
            { ModEnvironment("3ds", "azahar", "build", titleId, digest) },
            native::isGameActive,
        )
        val launcher = GameLauncher(
            library, downloads, SaveSync(api, native), native, fs, paths, { setOf("azahar") }, DeviceClass.CAPABLE,
            coreBuild = { "build" }, saveOwner = { "account" }, mods = mods,
        )
        val ready = launcher.prepare(game, AppSettings(), online = false, portrait = false) as LaunchCheck.Ready
        val runtime = java.io.File(ready.plan.savesDir).toOkioPath()
        assertTrue(runtime != previous)
        assertEquals("newest flushed progress", fs.read(runtime / savePath) { readUtf8() })
        assertEquals("enabled", ready.plan.options["citra_custom_textures"])
        assertEquals(0, server.requestCount)
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val http = apiHttpClient()
    private val server = MockWebServer()
    private val fs = FileSystem.SYSTEM
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: AppDatabase
    private lateinit var paths: AppPaths
    private lateinit var library: LibraryRepository
    private lateinit var downloads: DownloadManager
    private lateinit var saves: RoomSaveStore
    private lateinit var sync: SaveSync
    private val instant = Instant.parse("2026-11-11T10:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).configured()
        paths = AppPaths(File(context.cacheDir, "performance-${System.nanoTime()}").toOkioPath())
        server.start()
        val session = object : ApiSession {
            override val server = this@LibraryPerformanceTest.server.url("/").newBuilder().host("127.0.0.1").build().toString().trimEnd('/')
            override val token = "test"
            override suspend fun onUnauthorized(message: String) = Unit
        }
        val api = RetroHubApi(http, session, maxMaintenanceRetries = 0)
        saves = RoomSaveStore(db.saves(), fs, paths)
        sync = SaveSync(api, saves)
        library = LibraryRepository(api, db, KeyValueStore(db.settings()), sync, scope)
        downloads = DownloadManager(FileDownloader(api, fs), fs, paths, db, library, scope, { Long.MAX_VALUE }, AppClock { instant })
    }

    @After
    fun close() = runBlocking {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        db.close()
        http.close()
        server.close()
        if (fs.exists(paths.root)) fs.deleteRecursively(paths.root)
    }

    private suspend fun catalogue(bytes: ByteArray): Game {
        val remote = LibraryGame(
            "game",
            "play",
            title = "Jeu",
            console = "nes",
            rom = RomInfo(sha256(bytes), bytes.size.toLong(), "nes", "game.nes", "/rom"),
        )
        db.games().upsert(
            listOf(
                GameEntity(
                    remote.id, remote.playGameId, remote.title, remote.console, false, null, 0, 0,
                    ApiJson.encodeToString(LibraryGame.serializer(), remote),
                ),
            ),
        )
        return Game(remote, null)
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun storageInspectionDoesNotReadFilesOnTheMainThread() = runBlocking {
        fs.createDirectories(paths.saves)
        fs.write(paths.saves / "save.bin") { write(ByteArray(128 * 1024)) }
        val violations = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val usage = withContext(Dispatchers.Main) {
            val previous = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                    .penaltyListener({ it.run() }) { violations += it.toString() }.build(),
            )
            try {
                downloads.usage()
            } finally {
                StrictMode.setThreadPolicy(previous)
            }
        }
        assertEquals(128 * 1024L, usage.saves)
        assertTrue(violations.toString(), violations.isEmpty())
    }

    @Test
    fun launchUsesTheDownloadedRomDigestWhenTheServerHasANewerRom() = runBlocking {
        val game = catalogue(byteArrayOf(9, 9, 9))
        val bytes = byteArrayOf(1, 2, 3)
        val path = paths.romDir("play") / "old.nes"
        fs.createDirectories(path.parent!!)
        fs.write(path) { write(bytes) }
        val local = LocalRomEntity("play", "game", "nes", "Jeu", path.toString(), sha256(bytes), bytes.size.toLong(), instant.toString())
        val launcher = GameLauncher(library, downloads, sync, saves, fs, paths, { setOf("fceumm") }, DeviceClass.CAPABLE)
        val check = launcher.prepare(game.copy(local = local), AppSettings(), online = false, portrait = false) as LaunchCheck.Ready
        assertEquals(sha256(bytes), check.plan.romSha)
        assertEquals(path.toString(), check.plan.romPath)
    }

    @Test
    fun fastDownloadsPublishBoundedProgressAndAlwaysComplete() = runBlocking {
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val game = catalogue(bytes)
        server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
        val progress = mutableListOf<Float>()
        downloads.download(game) { progress += it }
        assertTrue(progress.size <= 3)
        assertEquals(1f, progress.last())
        assertEquals(sha256(bytes), db.roms().find("play")?.sha256)
    }

    @Test
    fun maintenanceRemainsRetryableAndDoesNotClaimTheRomIsDownloaded() = runBlocking {
        catalogue(byteArrayOf(1, 2, 3))
        server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "120").build())
        assertFalse(downloads.perform("play"))
        assertTrue((downloads.states.value["play"] as DownloadState.Failed).retryable)
        assertEquals(null, db.roms().find("play"))
    }
}
