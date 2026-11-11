package ovh.verbois.retrohub.game

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.core.sha256
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real melonDS lifecycle regression, using an original, generated homebrew cartridge. */
@RunWith(AndroidJUnit4::class)
class DsLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as RetroHubApp).container
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun dsSurvivesBackgroundSaveReturnAndRepeatedReset() {
        val dir = File(context.filesDir, "ds-lifecycle-test").apply { mkdirs() }
        val rom = File(dir, "screen.nds").apply { writeBytes(cartridge()) }
        val plan = LaunchPlan(
            "ds-lifecycle-test", "ds-lifecycle-test", "Test Nintendo DS", "nds", "melonds", rom.path,
            sha256(rom.readBytes()), File(dir, "system").apply { mkdirs() }.path,
            File(dir, "saves").apply { mkdirs() }.path, File(dir, "battery.sav").path, false,
            options = mapOf(
                "melonds_boot_directly" to "enabled",
                "melonds_jit_enable" to "enabled",
                "melonds_threaded_renderer" to "enabled",
                "melonds_screen_layout" to "Top/Bottom",
            ),
            useDisplayCutout = true,
        )
        val intent = Intent(context, GameActivity::class.java).putExtra(GameActivity.EXTRA_PLAN, ApiJson.encodeToString(LaunchPlan.serializer(), plan))
        ActivityScenario.launch<GameActivity>(intent).use { scenario ->
            waitForFrames(30)
            repeat(3) { round ->
                val before = GameActivity.current?.get()?.frames?.value ?: 0
                scenario.moveToState(Lifecycle.State.CREATED)
                waitFor("background snapshot") { runBlocking { container.saveStore.find(plan.playGameId, "state", 0)?.localSha != null } }
                scenario.moveToState(Lifecycle.State.RESUMED)
                click("Reprendre")
                waitForFrames(before + 10)
                device.pressKeyCode(KeyEvent.KEYCODE_MENU)
                click("Redémarrer le jeu")
                val resetFrame = GameActivity.current?.get()?.frames?.value ?: 0
                click("Redémarrer")
                device.wait(Until.gone(By.text("Reprendre")), 10_000)
                waitForFrames(resetFrame + 10)
                if (round == 2) {
                    val pictures = File(context.getExternalFilesDir(null), "test-screens").apply { mkdirs() }
                    File(pictures, "ds-after-background-reset.png").outputStream().use { stream ->
                        check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
                    }
                }
            }
            device.pressKeyCode(KeyEvent.KEYCODE_MENU)
            click("Quitter")
            click("Quitter")
            waitFor("game closed") { GameActivity.current?.get()?.isDestroyed != false }
        }
    }

    private fun click(text: String) {
        // The pause menu scrolls on short landscape screens.
        repeat(6) {
            val item = device.wait(Until.findObject(By.text(text)), 500)
            if (item != null) {
                try {
                    item.click()
                    device.waitForIdle()
                    return
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    return@repeat
                }
            }
            device.swipe(device.displayWidth / 4, device.displayHeight * 4 / 5, device.displayWidth / 4, device.displayHeight / 4, 20)
        }
        throw AssertionError("Missing action: $text")
    }

    private fun waitForFrames(count: Long) = waitFor("DS frames: $count") { (GameActivity.current?.get()?.frames?.value ?: 0L) >= count }

    private fun waitFor(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(label, condition())
    }

    private fun cartridge(): ByteArray {
        val bytes = ByteArray(128 * 1024)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        "RETROHUBTEST".toByteArray().copyInto(bytes)
        "####".toByteArray().copyInto(bytes, 12)
        buffer.putInt(0x20, 0x200).putInt(0x24, 0x02000000).putInt(0x28, 0x02000000).putInt(0x2c, 0x100)
        buffer.putInt(0x30, 0x400).putInt(0x34, 0x03800000).putInt(0x38, 0x03800000).putInt(0x3c, 4)
        buffer.putInt(0x80, bytes.size).putInt(0x84, 0x200)
        // ARM9: enable VRAM A, select framebuffer display, fill the top screen green, then idle.
        val arm9 = longArrayOf(
            0xE59F002C, 0xE3A01080, 0xE5C01000, 0xE59F0024, 0xE3A01802, 0xE5801000,
            0xE59F001C, 0xE59F101C, 0xE59F201C, 0xE4801004, 0xE2522001, 0x1AFFFFFC, 0xEAFFFFFE,
            0x04000240, 0x04000000, 0x06800000, 0x03E003E0, 24576,
        )
        arm9.forEachIndexed { index, word -> buffer.putInt(0x200 + index * 4, word.toInt()) }
        buffer.putInt(0x400, 0xEAFFFFFE.toInt())
        var crc = 0xFFFF
        for (index in 0 until 0x15e) {
            crc = crc xor (bytes[index].toInt() and 255)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        buffer.putShort(0x15e, crc.toShort())
        return bytes
    }
}
