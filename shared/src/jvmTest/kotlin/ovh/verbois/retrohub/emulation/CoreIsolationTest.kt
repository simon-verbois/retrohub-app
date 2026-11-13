package ovh.verbois.retrohub.emulation

import ovh.verbois.retrohub.core.DeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreIsolationTest {
    @Test
    fun switchingPlaystationCoreNeverAppliesTheOtherCoresAdvancedOverrides() {
        val settings = AppSettings(
            consoles = mapOf(
                "psx" to ConsoleSettings(
                    optionsByCore = mapOf(
                        "pcsx_rearmed" to mapOf("probe" to "rearmed"),
                        "swanstation" to mapOf("probe" to "swanstation"),
                    ),
                ),
            ),
        )
        assertEquals("rearmed", CoreOptions.build(Consoles["psx"]!!, "pcsx_rearmed", settings, DeviceClass.CAPABLE, false)["probe"])
        assertEquals("swanstation", CoreOptions.build(Consoles["psx"]!!, "swanstation", settings, DeviceClass.CAPABLE, false)["probe"])
        assertNull(CoreOptions.build(Consoles["snes"]!!, "snes9x", settings, DeviceClass.CAPABLE, false)["probe"])
    }
}
