package ovh.verbois.retrohub.mods

import ovh.verbois.retrohub.api.*
import ovh.verbois.retrohub.game.Cheat
import kotlin.test.*

class ModPlanTest {
    @Test
    fun largeTextureManifestsAreSharedAcrossCatalogueInstalledCopiesAndHistory() {
        val release = modRelease(files = (1..1000).map { ModFile("texture-$it.png", 3, "b".repeat(64)) })
        val installation = ModInstallation(ModProfile(1, listOf(release.selection)), listOf(release))
        val state = ModLocalState(
            catalogue = ModCatalogue(gameId = "game", releases = listOf(release)),
            active = installation,
            installed = listOf(release),
            history = (1..20).map { installation.copy(profile = installation.profile.copy(revision = it)) },
        )
        val encoded = ApiJson.encodeToString(ModLocalState.serializer(), state)
        assertTrue(encoded.length < 2 * ApiJson.encodeToString(ModRelease.serializer(), release).length)
        val restored = ApiJson.decodeFromString(ModLocalState.serializer(), encoded)
        assertEquals(state, restored)
        assertSame(restored.active!!.releases.single(), restored.history.first().releases.single())
        assertSame(restored.catalogue!!.releases.single(), restored.installed.single())
    }

    @Test
    fun fingerprintHasAStableCrossClientContractVector() {
        val release = modRelease("camera").copy(
            selection = ModSelection("camera", "1"),
            romSha256 = listOf(modEnvironment.romSha),
            components = listOf(ModComponent(ModPlanner.CHEATS, cheats = listOf(Cheat("Camera", "12345678 00000001")))),
        )
        val plan = ModPlanner.plan(listOf(release.selection), listOf(release), modEnvironment)
        assertEquals("0c6174b7f240db2ed1e2d468c0638d868302d4d0ca629830484c3cbfccd8176f", plan.fingerprint)
        assertEquals("9ccaf6429016ae7609f8499b2ff2f5fd272b06cb941d672465c0e3a8433e560b", plan.gameplayFingerprint)
    }

    @Test
    fun textureQualityChangesStateIdentityWhileSharingGameProgress() {
        val first = modRelease()
        val other = first.copy(selection = first.selection.copy(variant = "4k"))
        val a = ModPlanner.plan(listOf(first.selection), listOf(first), modEnvironment)
        val b = ModPlanner.plan(listOf(other.selection), listOf(other), modEnvironment)
        assertNull(a.gameplayFingerprint)
        assertNotEquals(a.fingerprint, b.fingerprint)
        assertEquals("textures/0004000000033600/image.png", a.files.single().destination)
    }

    @Test
    fun gameplayIdentityIgnoresVisualAdditionsAndSelectionOrder() {
        val patch = modRelease("camera", ModPlanner.EXEFS, listOf(ModFile("code.bps", 3, "b".repeat(64))))
        val texture = modRelease()
        val only = ModPlanner.plan(listOf(patch.selection), listOf(patch), modEnvironment)
        val combined = ModPlanner.plan(listOf(texture.selection, patch.selection), listOf(texture, patch), modEnvironment)
        val reordered = ModPlanner.plan(listOf(patch.selection, texture.selection), listOf(texture, patch), modEnvironment)
        assertNotNull(only.gameplayFingerprint)
        assertEquals(only.gameplayFingerprint, combined.gameplayFingerprint)
        assertEquals(reordered.fingerprint, combined.fingerprint)
        assertNotEquals(only.fingerprint, combined.fingerprint)
    }

    @Test
    fun conflictsOverlappingFilesAndDependencyCyclesAreRejected() {
        val first = modRelease()
        val second = first.copy(selection = ModSelection("other", "1", "1080p"))
        fun rejected(a: ModRelease, b: ModRelease) {
            assertFailsWith<ModProblem> { ModPlanner.plan(listOf(a.selection, b.selection), listOf(a, b), modEnvironment) }
        }
        rejected(first, second)
        rejected(first.copy(conflicts = listOf("other")), second)
        rejected(first.copy(requires = listOf(second.selection)), second.copy(requires = listOf(first.selection)))
        assertFailsWith<ModProblem> { ModPlanner.plan(listOf(first.selection), listOf(first.copy(requires = listOf(second.selection))), modEnvironment) }
    }

    @Test
    fun unknownInstallersWrongGamesBuildsAndUnverifiedGameplayAreRejected() {
        val first = modRelease()
        for (release in listOf(
            first.copy(console = "nds"),
            first.copy(core = "citra"),
            first.copy(titleIds = listOf("0004000000033500")),
            first.copy(coreBuilds = listOf("other")),
            first.copy(romSha256 = listOf("other")),
            first.copy(components = listOf(first.components.single().copy(installer = "external_exe_v1"))),
            first.copy(components = listOf(first.components.single().copy(installer = ModPlanner.ROMFS))),
        )) {
            assertNotNull(ModPlanner.compatibility(release, modEnvironment))
        }
        assertNotNull(ModPlanner.compatibility(first, modEnvironment.copy(titleId = "../../outside")))
    }

    @Test
    fun maliciousPathsAndDownloadOriginsNeverBecomeInstallTargets() {
        val first = modRelease()
        val component = first.components.single()
        val artifact = component.artifact!!
        for (path in listOf("../image.png", "/image.png", "folder/../image.png", "a\\image.png", "a//image.png", "C:image.png", ".complete")) {
            val bad = first.copy(components = listOf(component.copy(artifact = artifact.copy(files = listOf(artifact.files.single().copy(path = path))))))
            assertNotNull(ModPlanner.compatibility(bad, modEnvironment), path)
        }
        assertNotNull(
            ModPlanner.compatibility(
                first.copy(components = listOf(component.copy(artifact = artifact.copy(url = "https://other.invalid/object")))),
                modEnvironment,
            ),
        )
    }

    @Test
    fun executableAliasesCannotAccidentallyStack() {
        val patch = modRelease("patch", ModPlanner.EXEFS, listOf(ModFile("code.ips", 3, "b".repeat(64))))
        val code = modRelease("code", ModPlanner.EXEFS, listOf(ModFile("code.bin", 3, "b".repeat(64))))
        assertFailsWith<ModProblem> { ModPlanner.plan(listOf(code.selection, patch.selection), listOf(code, patch), modEnvironment) }
    }

    @Test
    fun extendedHeaderIsInstalledBesideTheExefsFolder() {
        val patch = modRelease(
            "camera",
            ModPlanner.EXEFS,
            listOf(ModFile("code.ips", 3, "b".repeat(64)), ModFile("exheader.bin", 3, "c".repeat(64))),
        )
        assertNull(ModPlanner.compatibility(patch, modEnvironment))
        val plan = ModPlanner.plan(listOf(patch.selection), listOf(patch), modEnvironment)
        assertEquals(
            setOf("mods/0004000000033600/exefs/code.ips", "mods/0004000000033600/exheader.bin"),
            plan.files.map { it.destination }.toSet(),
        )
        val unsupported = modRelease("other", ModPlanner.EXEFS, listOf(ModFile("main.bin", 3, "b".repeat(64))))
        assertNotNull(ModPlanner.compatibility(unsupported, modEnvironment))
    }

    @Test
    fun anExtendedHeaderNeverMeetsAnotherModsProgram() {
        fun exefs(id: String, vararg files: String) = modRelease(id, ModPlanner.EXEFS, files.map { ModFile(it, 3, "b".repeat(64)) })
        fun rejected(a: ModRelease, b: ModRelease) =
            assertFailsWith<ModProblem> { ModPlanner.plan(listOf(a.selection, b.selection), listOf(a, b), modEnvironment) }
        // A header from one mod and the code of another describe different programs.
        rejected(exefs("header", "exheader.bin"), exefs("patch", "code.ips"))
        rejected(exefs("single-screen", "code.ips", "exheader.bin"), exefs("patch", "code.bps"))
        // Two headers replace the same file.
        rejected(exefs("first", "exheader.bin"), exefs("second", "exheader.bin"))
        // A header with its own code, beside visual files from another mod, is fine.
        val program = exefs("single-screen", "code.ips", "exheader.bin")
        val banner = exefs("banner", "banner.bin")
        val plan = ModPlanner.plan(listOf(program.selection, banner.selection), listOf(program, banner), modEnvironment)
        assertEquals(3, plan.files.size)
    }

    @Test
    fun cheatNamesAndCodeLinesCannotInjectConfiguration() {
        val first = modRelease().copy(
            romSha256 = listOf(modEnvironment.romSha),
            components = listOf(
                ModComponent(
                    ModPlanner.CHEATS,
                    cheats = listOf(Cheat("Caméra", "12345678 00000001")),
                ),
            ),
        )
        assertNull(ModPlanner.compatibility(first, modEnvironment))
        for (cheat in listOf(Cheat("Injected]\n[Other", "12345678 00000001"), Cheat("Code", "*citra_enabled"))) {
            assertNotNull(ModPlanner.compatibility(first.copy(components = listOf(ModComponent(ModPlanner.CHEATS, cheats = listOf(cheat)))), modEnvironment))
        }
    }
}
