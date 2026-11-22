package ovh.verbois.retrohub.game

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.core.DeviceClass
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.emulation.Consoles
import ovh.verbois.retrohub.emulation.CoreOptions
import ovh.verbois.retrohub.emulation.Orientation
import java.io.File

/** Upstream's public spinning cube exercises real PPSSPP rendering, pause and state capture. */
@RunWith(AndroidJUnit4::class)
class PspLifecycleTest {
    private var expectedPlan: String? = null

    @Test
    fun pspRendersAndResumesWithBundledOfflineAssets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val platform = (context.applicationContext as RetroHubApp).platform
        val device = UiDevice.getInstance(instrumentation)
        val directory = File(context.filesDir, "psp-core-test").apply { mkdirs() }
        val rom = File(directory, "cube.elf")
        instrumentation.context.assets.open("psp-cube.elf").use { input -> rom.outputStream().use { input.copyTo(it) } }
        val plan = LaunchPlan(
            gameId = "psp-cube", playGameId = "psp-cube", title = "PSP Cube", console = "psp", core = "ppsspp",
            coreBuild = platform.coreBuild("ppsspp"),
            romPath = rom.path, romSha = sha256(rom.readBytes()),
            systemDir = File(directory, "system").path, savesDir = File(directory, "saves").path,
            sramPath = File(directory, "sram.bin").path, loadSram = false,
            options = CoreOptions.build(requireNotNull(Consoles["psp"]), "ppsspp", AppSettings(), DeviceClass.MODEST, false),
            orientation = Orientation.LANDSCAPE,
        )
        expectedPlan = ApiJson.encodeToString(LaunchPlan.serializer(), plan)
        val intent = Intent(context, GameActivity::class.java).putExtra(GameActivity.EXTRA_PLAN, expectedPlan)
        try {
            ActivityScenario.launch<GameActivity>(intent).use {
                waitForFrames(90)
                assertTrue(File(plan.systemDir, "PPSSPP/compat.ini").isFile)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val colored = (bitmap.height * 3 / 10 until bitmap.height * 7 / 10).sumOf { y ->
                    (bitmap.width * 3 / 10 until bitmap.width * 7 / 10).count { x ->
                        val color = pixels[y * bitmap.width + x]
                        val r = android.graphics.Color.red(color)
                        val g = android.graphics.Color.green(color)
                        val b = android.graphics.Color.blue(color)
                        maxOf(r, g, b) - minOf(r, g, b) > 70 && maxOf(r, g, b) > 100
                    }
                }
                assertTrue("The cube has visible colored pixels inside the game viewport", colored > 500)
                val pictures = File(context.getExternalFilesDir(null), "test-screens").apply { mkdirs() }
                File(pictures, "psp-cube-landscape.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                device.pressBack()
                assertTrue(device.wait(Until.hasObject(By.text("Reprendre")), 5000))
                val frame = GameActivity.current?.get()?.frames?.value ?: 0L
                device.pressBack()
                waitForFrames(frame + 30)
            }
        } finally {
            device.unfreezeRotation()
        }
    }

    private fun runningActivity(): GameActivity? = GameActivity.current?.get()?.takeIf {
        !it.isDestroyed && it.intent.getStringExtra(GameActivity.EXTRA_PLAN) == expectedPlan
    }

    private fun waitForFrames(count: Long) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val running = runningActivity()
            if ((running?.frames?.value ?: 0L) >= count) return
            SystemClock.sleep(100)
        }
        assertTrue("PSP rendered $count frames", (runningActivity()?.frames?.value ?: 0L) >= count)
    }
}
