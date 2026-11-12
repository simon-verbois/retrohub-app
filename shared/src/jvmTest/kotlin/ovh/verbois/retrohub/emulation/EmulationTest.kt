package ovh.verbois.retrohub.emulation

import ovh.verbois.retrohub.core.DeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameLayoutsTest {
    @Test
    fun portraitPutsTheGameOnTopAndControlsBelow() {
        val layout = GameLayouts.compute(1080f, 2400f, 4f / 3f, touchControls = true, topInset = 100f)
        assertEquals(ControlsPlacement.BELOW, layout.controls)
        assertEquals(0f, layout.game.left)
        assertEquals(1f, layout.game.width, 0.001f)
        assertEquals(100f / 2400f, layout.game.top, 0.001f)
        assertTrue(layout.game.bottom * 2400 <= 2400 - 135f * 5.5f)
    }

    @Test
    fun landscapeKeepsSideColumnsForTheThumbs() {
        val layout = GameLayouts.compute(2400f, 1080f, 4f / 3f, touchControls = true)
        assertEquals(ControlsPlacement.SIDES, layout.controls)
        assertTrue(layout.game.height > 0.9f)
        assertTrue(layout.game.left >= 0.2f)
        assertEquals(0.5f, layout.game.left + layout.game.width / 2, 0.001f)
    }

    @Test
    fun wideDsScreensLeaveTheBottomCornersToTheControls() {
        val layout = GameLayouts.compute(2400f, 1080f, GameLayouts.dsAspect(DsLayout.SIDE_BY_SIDE), touchControls = true)
        assertEquals(ControlsPlacement.BOTTOM_CORNERS, layout.controls)
        assertEquals(0f, layout.game.top)
        assertTrue(layout.game.bottom <= 0.661f)
        assertTrue(layout.game.width > 0.6f)
    }

    @Test
    fun gamepadGivesTheWholeScreenToTheGame() {
        val layout = GameLayouts.compute(2400f, 1080f, 4f / 3f, touchControls = false)
        assertEquals(ControlsPlacement.NONE, layout.controls)
        assertEquals(1f, layout.game.height, 0.001f)
    }

    @Test
    fun dsDefaultsStackInPortraitAndSitSideBySideInLandscape() {
        assertEquals(DsLayout.STACKED, GameLayouts.defaultDsLayout(portrait = true))
        assertEquals(DsLayout.SIDE_BY_SIDE, GameLayouts.defaultDsLayout(portrait = false))
        assertEquals(256f / 384f, GameLayouts.dsAspect(DsLayout.STACKED))
        assertEquals(2f, GameLayouts.dsAspect(DsLayout.MAIN_LARGE))
    }

    @Test
    fun tallPhonesUseTheFullWidthForStackedDsScreens() {
        val layout = GameLayouts.compute(411f, 891f, 2f / 3f, true, controlsUnit = 48f)
        assertEquals(1f, layout.game.width, 0.001f)
        assertEquals(0f, layout.game.top)
        assertTrue(layout.game.bottom * 891f <= 891f - 48f * 5.5f)
    }

    @Test
    fun everyDsLayoutAndRotationFitsAndPreservesProportions() {
        for ((width, height) in listOf(411f to 891f, 891f to 411f, 800f to 1280f)) {
            for (ds in DsLayout.entries) {
                for (rotation in GameRotation.entries) {
                    for (touch in listOf(true, false)) {
                        val aspect = rotation.aspect(GameLayouts.dsAspect(ds))
                        val layout = GameLayouts.compute(width, height, aspect, touch, controlsUnit = 48f)
                        assertTrue(layout.game.left >= 0 && layout.game.top >= 0)
                        assertTrue(layout.game.right <= 1.001f && layout.game.bottom <= 1.001f)
                        assertEquals(aspect, layout.game.width * width / (layout.game.height * height), 0.001f)
                        if (!touch) assertTrue(layout.game.width > 0.999f || layout.game.height > 0.999f)
                    }
                }
            }
        }
    }

    @Test
    fun rotatedStylusCoordinatesReturnToTheOriginalScreen() {
        assertEquals(0.3f to 0.8f, GameRotation.RIGHT.unrotate(0.2f, 0.3f))
        assertEquals(0.8f to 0.7f, GameRotation.INVERTED.unrotate(0.2f, 0.3f))
        assertEquals(0.7f to 0.2f, GameRotation.LEFT.unrotate(0.2f, 0.3f))
        assertEquals(0.2f to 0.3f, GameRotation.NORMAL.unrotate(0.2f, 0.3f))
    }
}

class CoreOptionsTest {
    private val settings = AppSettings()

    @Test
    fun melonDsLayoutFollowsTheOrientation() {
        val nds = Consoles["nds"]!!
        assertEquals(
            "Top/Bottom",
            CoreOptions.build(nds, "melonds", settings, DeviceClass.CAPABLE, portrait = true)["melonds_screen_layout"],
        )
        assertEquals(
            "Left/Right",
            CoreOptions.build(nds, "melonds", settings, DeviceClass.CAPABLE, portrait = false)["melonds_screen_layout"],
        )
        val swapped = settings.withConsole("nds") { it.copy(dsSwapScreens = true, dsLayoutLandscape = DsLayout.MAIN_LARGE) }
        assertEquals(
            "Hybrid Bottom",
            CoreOptions.build(nds, "melonds", swapped, DeviceClass.CAPABLE, portrait = false)["melonds_screen_layout"],
        )
        assertEquals("Touch", CoreOptions.build(nds, "melonds", settings, DeviceClass.CAPABLE, portrait = true)["melonds_touch_mode"])
        assertEquals("French", CoreOptions.build(nds, "melonds", settings, DeviceClass.CAPABLE, portrait = true)["melonds_language"])
    }

    @Test
    fun n64UsesAGlesRendererNotVulkan() {
        val options = CoreOptions.build(Consoles["n64"]!!, "parallel_n64", settings, DeviceClass.CAPABLE, false)
        assertEquals("glide64", options["parallel-n64-gfxplugin"])
        assertEquals("hle", options["parallel-n64-rspplugin"])
    }

    @Test
    fun swanStationRendersInSoftware() {
        assertEquals(
            "Software",
            CoreOptions.build(Consoles["psx"]!!, "swanstation", settings, DeviceClass.POWERFUL, false)["swanstation_GPU_Renderer"],
        )
    }

    @Test
    fun resolutionFollowsTheProfileTheDeviceAndThePlayer() {
        val gc = Consoles["gamecube"]!!
        assertEquals(1, CoreOptions.resolution(gc, settings.copy(performance = PerformanceProfile.BATTERY), DeviceClass.POWERFUL))
        assertEquals(3, CoreOptions.resolution(gc, settings.copy(performance = PerformanceProfile.QUALITY), DeviceClass.POWERFUL))
        assertEquals(1, CoreOptions.resolution(gc, settings.copy(performance = PerformanceProfile.QUALITY), DeviceClass.MODEST))
        assertEquals(2, CoreOptions.resolution(gc, settings.withConsole("gamecube") { it.copy(resolution = 2) }, DeviceClass.MODEST))
        assertEquals(
            "3",
            CoreOptions.build(
                gc,
                "dolphin",
                settings.copy(performance = PerformanceProfile.QUALITY),
                DeviceClass.POWERFUL,
                false,
            )["dolphin_efb_scale"],
        )
        assertEquals("3", CoreOptions.build(gc, "dolphin", settings, DeviceClass.CAPABLE, false)["dolphin_language"])
    }

    @Test
    fun advancedOptionsWinOverEverything() {
        val custom = settings.withConsole("nes") { it.copy(options = mapOf("fceumm_palette" to "nescap")) }
        assertEquals("nescap", CoreOptions.build(Consoles["nes"]!!, "fceumm", custom, DeviceClass.CAPABLE, false)["fceumm_palette"])
    }

    @Test
    fun handheldsDefaultToTheLcdLook() {
        assertEquals(Shader.LCD, CoreOptions.shader(Consoles["gba"]!!, settings))
        assertEquals(Shader.SHARP, CoreOptions.shader(Consoles["snes"]!!, settings))
        assertEquals(Shader.SMOOTH, CoreOptions.shader(Consoles["n64"]!!, settings))
    }

    @Test
    fun everyConsoleUsesACoreTheServerAccepts() {
        // Noms de cœurs acceptés par le serveur (consoles.py : cœurs web + natifs).
        val accepted =
            mapOf(
                "nes" to setOf("fceumm", "nestopia"),
                "snes" to setOf("snes9x"),
                "gb" to setOf("gambatte", "mgba"),
                "gbc" to setOf("gambatte", "mgba"),
                "gba" to setOf("mgba"),
                "mastersystem" to setOf("genesis_plus_gx", "smsplus"),
                "megadrive" to setOf("genesis_plus_gx", "picodrive"),
                "psx" to setOf("pcsx_rearmed", "mednafen_psx_hw", "swanstation"),
                "nds" to setOf("melonds", "desmume"),
                "atari2600" to setOf("stella2014"),
                "n64" to setOf("mupen64plus_next", "parallel_n64"),
                "gamecube" to setOf("dolphin"),
            )
        for ((console, cores) in accepted) {
            assertTrue(Consoles[console]!!.cores.all { it in cores }, "Cœur refusé par le serveur pour $console")
        }
    }
}
