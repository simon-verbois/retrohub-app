package ovh.verbois.retrohub.game

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.emulation.Consoles
import java.io.File

/**
 * Chaque cœur de chaque console est embarqué, pour la bonne architecture, et ses dépendances se
 * résolvent sur l’appareil. LibretroDroid ouvre les cœurs avec dlopen ; System.load n’est utilisé
 * ici que pour les cœurs sans JNI_OnLoad (celui de Dolphin attend la classe Java de l’application
 * Dolphin officielle, qui n’est jamais appelée par dlopen).
 */
@RunWith(AndroidJUnit4::class)
class CoresTest {
    @Test
    fun everyCoreIsPackagedForThisAbiAndLoads() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.nativeLibraryDir)
        val machine = if (Build.SUPPORTED_ABIS.first() == "x86_64") 0x3E else 0xB7
        val failures = mutableListOf<String>()
        for (core in Consoles.coresForAbi(Build.SUPPORTED_ABIS.first()).sorted()) {
            val file = File(dir, "lib${core}_libretro_android.so")
            if (!file.exists()) {
                failures += "$core absent"
                continue
            }
            val bytes = file.readBytes()
            val elf = bytes.size > 20 && bytes[0] == 0x7F.toByte() && String(bytes, 1, 3) == "ELF"
            val arch = (bytes[18].toInt() and 0xFF) or ((bytes[19].toInt() and 0xFF) shl 8)
            if (!elf || arch != machine) {
                failures += "$core : architecture $arch au lieu de $machine"
                continue
            }
            if (String(bytes, Charsets.ISO_8859_1).contains("JNI_OnLoad")) continue
            runCatching { System.load(file.absolutePath) }.onFailure { failures += "$core : ${it.message}" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
