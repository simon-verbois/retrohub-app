package ovh.verbois.retrohub.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.MainActivity
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.emulation.InterfaceOrientation

@RunWith(AndroidJUnit4::class)
class InterfaceOrientationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val container get() = (instrumentation.targetContext.applicationContext as RetroHubApp).container

    @After
    fun restoreSystemOrientation() {
        runBlocking { container.settings.updateNow { it.copy(interfaceOrientation = InterfaceOrientation.SYSTEM) } }
        rule.waitUntil(5_000) { rule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        rule.settleOrientation(portrait = false)
    }

    @Test
    fun interfaceLocksApplyImmediatelyAndReturnAfterAnActivityRestart() {
        runBlocking { container.start() }
        device.unfreezeRotation()
        choose(InterfaceOrientation.LANDSCAPE)
        waitForOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        val previous = rule.activity
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { rule.activity !== previous && rule.activity.hasWindowFocus() }
        assertNotSame(previous, rule.activity)
        waitForOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        choose(InterfaceOrientation.PORTRAIT)
        waitForOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        runBlocking { container.settings.load() }
        assertEquals(InterfaceOrientation.PORTRAIT, container.settings.settings.value.interfaceOrientation)
        choose(InterfaceOrientation.SYSTEM)
        rule.waitUntil(5_000) { rule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun choose(value: InterfaceOrientation) {
        runBlocking { container.settings.updateNow { it.copy(interfaceOrientation = value) } }
    }

    private fun waitForOrientation(requested: Int, configuration: Int) {
        rule.waitUntil(10_000) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = rule.activity.requestedOrientation == requested && rule.activity.resources.configuration.orientation == configuration
            }
            ready
        }
    }
}
