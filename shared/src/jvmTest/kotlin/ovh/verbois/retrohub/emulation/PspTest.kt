package ovh.verbois.retrohub.emulation

import ovh.verbois.retrohub.core.DeviceClass
import ovh.verbois.retrohub.ui.play.padSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PspTest {
    @Test
    fun originalControlsAndRenderSettingsStayConsistentAcrossProfiles() {
        val spec = requireNotNull(Consoles["psp"])
        assertEquals(480f / 272f, spec.aspect)
        assertEquals(7, spec.generation)
        val pad = padSpec(spec.pad)
        assertTrue(pad.dpad && pad.leftStick)
        assertFalse(pad.rightStick)
        assertEquals(setOf(PadKey.L, PadKey.R), pad.shoulders.map { it.key }.toSet())
        assertEquals(setOf("△", "○", "✕", "□"), pad.face.map { it.label }.toSet())
        for (profile in PerformanceProfile.entries) {
            for (device in DeviceClass.entries) {
                val settings = AppSettings(performance = profile)
                val scale = CoreOptions.resolution(spec, settings, device)
                val options = CoreOptions.build(spec, "ppsspp", settings, device, false)
                assertEquals("${480 * scale}x${272 * scale}", options["ppsspp_internal_resolution"])
                assertEquals("opengl", options["ppsspp_backend"])
                assertEquals("disabled", options["ppsspp_enable_wlan"])
            }
        }
    }
}
