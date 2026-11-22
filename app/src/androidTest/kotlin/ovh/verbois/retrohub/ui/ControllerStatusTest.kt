package ovh.verbois.retrohub.ui

import android.hardware.input.InputManager
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.core.BatteryLevel
import ovh.verbois.retrohub.core.ControllerBattery
import ovh.verbois.retrohub.core.Platform
import ovh.verbois.retrohub.core.PowerStatus
import ovh.verbois.retrohub.ui.shell.StatusPill
import ovh.verbois.retrohub.ui.theme.RetroHubTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ControllerStatusTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val platform get() = (instrumentation.targetContext.applicationContext as RetroHubApp).platform

    @Test fun connectingAndReconnectingUsbAndBluetoothControllersUpdatesTheRunningApp() {
        rule.settleOrientation(portrait = false)
        val input = instrumentation.targetContext.getSystemService(InputManager::class.java)
        assertFalse("Use the isolated test emulator without a physical gamepad", platform.gamepadConnected.value)
        val container = (instrumentation.targetContext.applicationContext as RetroHubApp).container
        rule.setContent {
            RetroHubTheme {
                Box(Modifier.fillMaxSize()) {
                    RetroHubRoot(container)
                    StatusPill(platform, transparent = true, compact = true)
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Commencer")).fetchSemanticsNodes().isNotEmpty() }
        val status = rule.onNodeWithTag("header-status").fetchSemanticsNode().id
        rule.onNodeWithTag("header-controller", useUnmergedTree = true).assertDoesNotExist()
        for (bus in listOf("usb", "bluetooth")) {
            Gamepad(bus).use { gamepad ->
                rule.waitUntil(5_000) { input.inputDeviceIds.any { input.getInputDevice(it)?.name == "RetroHub test controller" } }
                rule.waitUntil(2_000) { platform.gamepadConnected.value }
                rule.onNodeWithContentDescription("Manette connectée").assertIsDisplayed()
                assertEquals(status, rule.onNodeWithTag("header-status").fetchSemanticsNode().id)
                rule.waitUntil(2_000) {
                    runCatching { rule.onNodeWithText("Commencer").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
                }
                rule.onNodeWithText("Commencer").assertIsFocused()
                gamepad.press(304)
                // Wait for the destination to accept keys before sending another hardware event.
                rule.waitUntil(5_000) {
                    runCatching { rule.onNodeWithText("Adresse du serveur").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
                }
                gamepad.press(305)
                rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Commencer")).fetchSemanticsNodes().isNotEmpty() }
                if (bus == "bluetooth") {
                    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "test-screens").apply { mkdirs() }
                    androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(File(directory, "controller-connected-landscape.png"))
                }
            }
            rule.waitUntil(5_000) { input.inputDeviceIds.none { input.getInputDevice(it)?.name == "RetroHub test controller" } }
            rule.waitUntil(2_000) { !platform.gamepadConnected.value }
            rule.onNodeWithTag("header-controller", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test fun batteryReportsRefreshImmediatelyAndOnForegroundReturnWithoutInventingALevel() {
        rule.settleOrientation(portrait = false)
        val connected = MutableStateFlow(false)
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val snapshot = AtomicReference(PowerStatus(phone = BatteryLevel(81)))
        val source = object : Platform by platform {
            override val gamepadConnected = connected
            override val powerStatusChanges = changes
            override fun powerStatus() = snapshot.get()
        }
        rule.setContent { RetroHubTheme { StatusPill(source, transparent = true, compact = true) } }
        rule.onNodeWithTag("header-controller", useUnmergedTree = true).assertDoesNotExist()
        connected.value = true
        rule.onNodeWithContentDescription("Manette connectée").assertIsDisplayed()
        fun battery(percent: Int, charging: Boolean = false) {
            snapshot.set(PowerStatus(BatteryLevel(81), listOf(ControllerBattery("Manette test", BatteryLevel(percent, charging)))))
        }
        battery(74)
        assertTrue(changes.tryEmit(Unit))
        rule.waitUntil(2_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Batterie de Manette test : 74 %")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        battery(62, charging = true)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(2_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Batterie de Manette test : 62 %, en charge")).fetchSemanticsNodes().isNotEmpty()
        }
        connected.value = false
        rule.onNodeWithTag("header-controller", useUnmergedTree = true).assertDoesNotExist()
    }

    /** The Android input stack sees a newly attached device, rather than an injected key event. */
    private inner class Gamepad(bus: String) : AutoCloseable {
        private val pipes = instrumentation.uiAutomation.executeShellCommandRw("uinput -")
        private val commands = ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])

        init {
            val registration = """
                {"id":1,"command":"register","name":"RetroHub test controller","vid":1118,"pid":654,"bus":"$bus",
                 "configuration":[{"type":100,"data":[1]},{"type":101,"data":[304,305,307,308,310,311,314,315]}]}
            """.trimIndent()
            commands.write((registration + "\n").toByteArray())
            commands.flush()
        }

        override fun close() {
            commands.close()
            pipes[0].close()
        }

        fun press(button: Int) {
            commands.write(("""{"id":1,"command":"inject","events":[1,$button,1,0,0,0,1,$button,0,0,0,0]}""" + "\n").toByteArray())
            commands.flush()
        }
    }
}
