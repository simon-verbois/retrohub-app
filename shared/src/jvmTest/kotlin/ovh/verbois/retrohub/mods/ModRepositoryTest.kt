package ovh.verbois.retrohub.mods

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import ovh.verbois.retrohub.TestApi
import ovh.verbois.retrohub.api.*
import ovh.verbois.retrohub.bytes
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.game.Cheat
import ovh.verbois.retrohub.json
import kotlin.test.*

class ModRepositoryTest {
    @Test
    fun catalogueRefreshCannotAdvanceTheBaseOfAnOfflineChoice() = runTest {
        val (release, bytes) = zipRelease(fs)
        install(release, bytes)
        repository.apply(modGame, emptyList(), false)
        assertEquals(1, repository.state(modGame).pendingBaseRevision)
        t.enqueue(catalogue(listOf(release), ModProfile(2, listOf(release.selection))))
        repository.refresh(modGame, true)
        repository.synchronize(listOf(modGame))
        assertTrue(repository.state(modGame).pending)
        assertTrue(repository.state(modGame).profileConflict)
        assertEquals(1, repository.state(modGame).pendingBaseRevision)
        assertEquals(4, t.server.requestCount)
    }

    @Test
    fun aNewDeviceCannotSilentlyLaunchVanillaWhenTheAccountSelectedMods() = runTest {
        val release = modRelease()
        val remote = ModProfile(1, listOf(release.selection))
        t.enqueue(catalogue(listOf(release), remote))
        repository.refresh(modGame, true)
        assertFailsWith<ModProblem> { repository.runtime(modGame, modEnvironment, false) }
        owner = "fresh-account"
        val game = modGame.copy(remote = modGame.remote.copy(modProfile = remote))
        assertFailsWith<ModProblem> { repository.runtime(game, modEnvironment, false) }
    }

    @Test
    fun nativeCheatPacksAreWrittenBeforeLaunchWithTheirOwnProgression() = runTest {
        val release = modRelease("camera").copy(
            romSha256 = listOf(modEnvironment.romSha),
            components = listOf(
                ModComponent(ModPlanner.CHEATS, cheats = listOf(Cheat("Camera", "12345678 00000001"))),
            ),
        )
        val plan = ModPlanner.plan(listOf(release.selection), listOf(release), modEnvironment)
        t.enqueue(catalogue(listOf(release)))
        repository.refresh(modGame, true)
        t.enqueue(profile(ModProfile(1, listOf(release.selection), plan.gameplayFingerprint, "cheat-context")))
        repository.apply(modGame, listOf(release.selection), true)
        val runtime = repository.runtime(modGame, modEnvironment, false)
        repository.prepare(runtime, "/cheat-runtime".toPath())
        assertEquals("cheat-context", runtime.saveGameId)
        assertEquals("[Camera]\n*citra_enabled\n12345678 00000001\n", fs.read("/cheat-runtime/Azahar/cheats/0004000000033600.txt".toPath()) { readUtf8() })
        assertEquals(2, t.server.requestCount)
    }

    @Test
    fun profileConflictNeverActivatesTheNewProfile() = runTest {
        val (release, bytes) = zipRelease(fs)
        t.enqueue(catalogue(listOf(release)))
        repository.refresh(modGame, true)
        t.enqueue(bytes(bytes))
        t.enqueue(json("""{"detail":{"current_revision":1,"message":"Conflict"}}""", 409))
        assertFailsWith<ApiException.SaveConflict> { repository.apply(modGame, listOf(release.selection), true) }
        assertNull(repository.state(modGame).active)
        assertEquals("base", repository.runtime(modGame, modEnvironment, false).saveGameId)
    }

    @Test
    fun damagedProfileRequiresExplicitServerRecoveryAndNeverSilentlyLaunchesVanilla() = runTest {
        val path = "/app/mods/owner/profiles/${sha256(modGame.id.encodeToByteArray())}.json".toPath()
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("damaged") }
        assertFailsWith<ModProblem> { repository.runtime(modGame, modEnvironment, false) }
        t.enqueue(catalogue(emptyList()))
        assertTrue(repository.refresh(modGame, true).recoveryRequired)
        assertFailsWith<ModProblem> { repository.runtime(modGame, modEnvironment, false) }
        t.enqueue(profile(ModProfile(1)))
        repository.apply(modGame, emptyList(), true)
        assertFalse(repository.state(modGame).recoveryRequired)
        assertEquals("base", repository.runtime(modGame, modEnvironment, false).saveGameId)
        assertTrue(fs.exists(path.parent!! / (path.name + ".damaged")))
    }

    private val t = TestApi()
    private val fs = FakeFileSystem()
    private var owner = "owner"
    private var supported = true
    private var running = false
    private var wifi = true
    private val repository = ModRepository(
        t.api, fs, "/app/mods".toPath(), { owner }, { supported }, { 100L * 1024 * 1024 * 1024 },
        { modEnvironment }, { running }, { wifi },
    )

    @AfterTest
    fun close() = t.close()

    private fun catalogue(releases: List<ModRelease>, profile: ModProfile = ModProfile()) =
        json(ApiJson.encodeToString(ModCatalogue.serializer(), ModCatalogue(gameId = modGame.id, releases = releases, profile = profile)))

    private fun profile(value: ModProfile) = json(ApiJson.encodeToString(ModProfile.serializer(), value))

    private suspend fun install(release: ModRelease, bytes: ByteArray, context: String? = null) {
        t.enqueue(catalogue(listOf(release)))
        repository.refresh(modGame, true)
        t.enqueue(bytes(bytes))
        val plan = ModPlanner.plan(listOf(release.selection), listOf(release), modEnvironment)
        t.enqueue(profile(ModProfile(1, listOf(release.selection), plan.gameplayFingerprint, context)))
        repository.apply(modGame, listOf(release.selection), true)
    }

    @Test
    fun olderServersAndFreshOfflineGamesNeedNoModRequests() = runTest {
        supported = false
        assertNull(repository.refresh(modGame, true).catalogue)
        assertEquals(modGame.playGameId, repository.runtime(modGame, modEnvironment, false).saveGameId)
        assertEquals(0, t.server.requestCount)
    }

    @Test
    fun completeInstallPinsProfilePreservesOriginalAndWorksOfflineAfterRestart() = runTest {
        val (release, bytes) = zipRelease(fs)
        val original = "/app/roms/original.3ds".toPath()
        fs.createDirectories(original.parent!!)
        fs.write(original) { writeUtf8("original") }
        install(release, bytes)
        assertEquals("original", fs.read(original) { readUtf8() })
        assertEquals(listOf(release), repository.state(modGame).active!!.releases)
        assertEquals("/api/v1/native/games/zelda/mods", t.take().url.encodedPath)
        val download = t.take()
        assertEquals("Bearer device-token", download.headers["Authorization"])
        val update = t.take()
        assertEquals("PUT", update.method)
        assertTrue(update.body!!.utf8().contains("\"base_revision\":0"))
        val restarted = ModRepository(t.api, fs, "/app/mods".toPath(), { owner }, { supported }, { Long.MAX_VALUE }, { modEnvironment }, { false })
        val runtime = restarted.runtime(modGame, modEnvironment, false)
        assertEquals("base", runtime.saveGameId)
        val gameRoot = "/runtime".toPath()
        restarted.prepare(runtime, gameRoot)
        assertContentEquals(byteArrayOf(1, 2, 3), fs.read(gameRoot / "Azahar/load/textures/0004000000033600/image.png") { readByteArray() })
        assertEquals(3, t.server.requestCount)
    }

    @Test
    fun gameplayNeedsServerAssignedProgressionAndNeverFallsBackToOriginal() = runTest {
        val (release, bytes) = zipRelease(fs, "camera", ModPlanner.EXEFS, "code.ips")
        install(release, bytes, "mod-context")
        val runtime = repository.runtime(modGame, modEnvironment, false)
        assertEquals("mod-context", runtime.saveGameId)
        assertNotNull(runtime.plan.gameplayFingerprint)
        repository.apply(modGame, emptyList(), false)
        assertTrue(repository.state(modGame).pending)
        assertEquals("base", repository.runtime(modGame, modEnvironment, false).saveGameId)
        repository.apply(modGame, listOf(release.selection), false)
        assertEquals("mod-context", repository.runtime(modGame, modEnvironment, false).saveGameId)
        assertFailsWith<ModProblem> { repository.runtime(modGame, modEnvironment.copy(romSha = "d".repeat(64)), false) }
    }

    @Test
    fun extendedHeaderIsWrittenBesideExefsAndLeavesWithItsMod() = runTest {
        val (release, bytes) = zipReleaseFiles(fs, "single-screen", ModPlanner.EXEFS, listOf("code.ips", "exheader.bin"))
        install(release, bytes, "mod-context")
        val runtime = repository.runtime(modGame, modEnvironment, false)
        // The launcher gives each gameplay profile its own runtime folder, like apply() and uninstall().
        val profileRoot = "/app/core-saves/owner/3ds".toPath() / sha256("mod-context".encodeToByteArray()) / modEnvironment.romSha /
            "mod-profiles" / runtime.plan.fingerprint!!
        repository.prepare(runtime, profileRoot)
        val mods = profileRoot / "Azahar/load/mods/0004000000033600"
        assertTrue(fs.exists(mods / "exefs/code.ips"))
        assertTrue(fs.exists(mods / "exheader.bin"))
        assertFalse(fs.exists(mods / "exefs/exheader.bin"))
        // Disabled: the original game's load tree has no mod file at all.
        repository.apply(modGame, emptyList(), false)
        val vanilla = repository.runtime(modGame, modEnvironment, false)
        val originalRoot = "/app/core-saves/owner/3ds/original".toPath()
        repository.prepare(vanilla, originalRoot)
        assertFalse(fs.exists(originalRoot / "Azahar/load/mods/0004000000033600/exheader.bin"))
        assertFalse(fs.exists(originalRoot / "Azahar/load/mods/0004000000033600/exefs"))
        // Uninstalled: the profile's load tree, header included, is removed.
        repository.uninstall(modGame, release.selection)
        assertFalse(fs.exists(mods / "exheader.bin"))
        assertFalse(fs.exists(profileRoot / "Azahar/load"))
    }

    @Test
    fun maliciousServerCannotShareOriginalProgressWithGameplay() = runTest {
        val (release, bytes) = zipRelease(fs, "camera", ModPlanner.EXEFS, "code.ips")
        assertFailsWith<ModProblem> { install(release, bytes, "base") }
        assertNull(repository.state(modGame).active)
    }

    @Test
    fun corruptedFileCannotReplaceActiveProfileAndRepairUsesVerifiedObject() = runTest {
        val (release, bytes) = zipRelease(fs)
        install(release, bytes)
        val hash = release.components.single().artifact!!.sha256
        fs.write("/app/mods/owner/files/$hash/image.png".toPath()) { writeUtf8("corrupted") }
        val before = repository.state(modGame).active
        val runtime = repository.runtime(modGame, modEnvironment, false)
        repository.prepare(runtime, "/new-runtime".toPath())
        assertEquals(before, repository.state(modGame).active)
        t.enqueue(profile(before!!.profile.copy(revision = 2)))
        repository.apply(modGame, listOf(release.selection), true)
        repository.prepare(repository.runtime(modGame, modEnvironment, false), "/new-runtime".toPath())
        assertContentEquals(byteArrayOf(1, 2, 3), fs.read("/new-runtime/Azahar/load/textures/0004000000033600/image.png".toPath()) { readByteArray() })
    }

    @Test
    fun offlineDisableQueuesAndReconnectPublishesWithOptimisticRevision() = runTest {
        val (release, bytes) = zipRelease(fs)
        install(release, bytes)
        repository.apply(modGame, emptyList(), false)
        val old = ModProfile(1, listOf(release.selection))
        t.enqueue(catalogue(listOf(release), old))
        t.enqueue(profile(ModProfile(2)))
        repository.synchronize(listOf(modGame))
        assertFalse(repository.state(modGame).pending)
        assertEquals(emptyList(), repository.state(modGame).catalogue!!.profile.selections)
    }

    @Test
    fun reconnectDoesNotOverwriteAConcurrentRemoteChoice() = runTest {
        val (release, bytes) = zipRelease(fs)
        install(release, bytes)
        repository.apply(modGame, emptyList(), false)
        t.enqueue(catalogue(listOf(release), ModProfile(2, listOf(release.selection))))
        repository.synchronize(listOf(modGame))
        assertTrue(repository.state(modGame).pending)
        assertTrue(repository.state(modGame).active!!.releases.isEmpty())
        assertTrue(repository.state(modGame).profileConflict)
        repository.synchronize(listOf(modGame))
        assertEquals(4, t.server.requestCount)
    }

    @Test
    fun localTextureQualityDoesNotPublishAnotherGlobalProfile() = runTest {
        val (release, bytes) = zipRelease(fs)
        val other = release.copy(selection = release.selection.copy(variant = "4k"))
        t.enqueue(catalogue(listOf(release, other)))
        repository.refresh(modGame, true)
        t.enqueue(bytes(bytes))
        t.enqueue(profile(ModProfile(1, listOf(release.selection))))
        repository.apply(modGame, listOf(release.selection), true)
        repository.apply(modGame, listOf(other.selection), false, localVariant = true)
        assertEquals(listOf(other), repository.state(modGame).active!!.releases)
        assertEquals(listOf(release.selection), repository.state(modGame).active!!.profile.selections)
        assertFalse(repository.state(modGame).pending)
        assertEquals(3, t.server.requestCount)
    }

    @Test
    fun accountBoundariesRunningGameAndMeteredDownloadsAreEnforced() = runTest {
        val (release, bytes) = zipRelease(fs)
        install(release, bytes)
        running = true
        assertFailsWith<ModProblem> { repository.apply(modGame, emptyList(), false) }
        running = false
        owner = "another-account"
        assertNull(repository.state(modGame).active)
        assertEquals("base", repository.runtime(modGame, modEnvironment, false).saveGameId)
        t.enqueue(catalogue(listOf(release)))
        repository.refresh(modGame, true)
        wifi = false
        assertFailsWith<ModProblem> { repository.apply(modGame, listOf(release.selection), true) }
        assertEquals(4, t.server.requestCount)
    }

    @Test
    fun releaseContentIsImmutableAndUninstallRetainsSharedObjects() = runTest {
        val (release, bytes) = zipRelease(fs)
        install(release, bytes)
        val mutated = release.copy(components = listOf(release.components.single().copy(installer = ModPlanner.ROMFS)))
        t.enqueue(catalogue(listOf(mutated)))
        assertFailsWith<ModProblem> { repository.refresh(modGame, true) }
        assertFailsWith<ModProblem> { repository.uninstall(modGame, release.selection) }
        repository.apply(modGame, emptyList(), false)
        repository.uninstall(modGame, release.selection)
        val hash = release.components.single().artifact!!.sha256
        assertFalse(fs.exists("/app/mods/owner/files/$hash".toPath()))
        assertTrue(repository.state(modGame).history.any { it.releases == listOf(release) })
        assertFailsWith<ModProblem> { repository.apply(modGame, listOf(release.selection), false) }
    }
}
