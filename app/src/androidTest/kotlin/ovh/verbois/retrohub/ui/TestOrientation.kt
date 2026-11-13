package ovh.verbois.retrohub.ui

import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/** Install test content only after rotation has finished recreating the activity. */
internal fun AndroidComposeTestRule<*, *>.settleOrientation(portrait: Boolean) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    fun requestRotation() {
        if (portrait) device.setOrientationNatural() else device.setOrientationLandscape()
    }
    requestRotation()
    instrumentation.waitForIdleSync()
    var previous: ComponentActivity? = null
    var stableSince = SystemClock.uptimeMillis()
    var lastRequest = stableSince
    waitUntil(10_000) {
        val current = activity
        var ready = false
        instrumentation.runOnMainSync {
            val expected = if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            ready = !current.isDestroyed && !current.isChangingConfigurations &&
                current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                current.resources.configuration.orientation == expected
        }
        ready = ready && (device.displayHeight > device.displayWidth) == portrait
        val now = SystemClock.uptimeMillis()
        // A preceding activity launch can override UiAutomation's first rotation request.
        // Request it again while the actual activity/display still disagree with the target.
        if (!ready && now - lastRequest >= 500) {
            requestRotation()
            lastRequest = now
        }
        if (!ready || previous !== current) {
            previous = current
            stableSince = now
        }
        ready && now - stableSince >= 200
    }
}
