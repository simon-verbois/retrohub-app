package ovh.verbois.retrohub.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.PrimaryButton
import ovh.verbois.retrohub.ui.theme.RetroHubTheme

/** Les éléments de l’interface console se pilotent avec les KeyEvent d’une manette. */
@RunWith(AndroidJUnit4::class)
class ConsoleFocusTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pad(code: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(0, 0, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
        }
        rule.waitForIdle()
    }

    @Test
    fun joystickNavigationMovesOneItemAndNeutralStopsRepeating() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.setInTouchMode(false)
        val first = FocusRequester()
        rule.setContent {
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            RetroHubTheme {
                Row {
                    GlassButton("Un", {}, Modifier.focusRequester(first))
                    GlassButton("Deux", {})
                    GlassButton("Trois", {})
                }
                AutoFocus(first)
            }
        }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("Un").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false) }
        fun axis(value: Float, axis: Int = MotionEvent.AXIS_X) {
            val pointer = MotionEvent.PointerProperties().apply { id = 0 }
            val coordinates = MotionEvent.PointerCoords().apply { setAxisValue(axis, value) }
            val time = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(
                time, time, MotionEvent.ACTION_MOVE, 1,
                arrayOf(
                    pointer,
                ),
                arrayOf(coordinates), 0, 0, 1f, 1f, -1, 0, InputDevice.SOURCE_JOYSTICK, 0,
            )
            check(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        axis(1f)
        axis(0f)
        rule.waitForIdle()
        rule.onNodeWithText("Deux").assertIsFocused()
        Thread.sleep(700)
        rule.onNodeWithText("Deux").assertIsFocused()
        axis(-1f, MotionEvent.AXIS_HAT_X)
        axis(0f, MotionEvent.AXIS_HAT_X)
        rule.waitForIdle()
        rule.onNodeWithText("Un").assertIsFocused()
    }

    @Test
    fun dpadMovesTheFocusAndButtonAClicks() {
        // Une manette fait sortir Android du mode tactile : les éléments deviennent focalisables.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        var clicked by mutableStateOf("")
        var clicks = 0
        val first = FocusRequester()
        lateinit var inputMode: InputModeManager
        rule.setContent {
            inputMode = LocalInputModeManager.current
            RetroHubTheme {
                Row {
                    PrimaryButton("Jouer", {
                        clicked = "Jouer"
                        clicks++
                    }, Modifier.focusRequester(first))
                    GlassButton("Fiche", {
                        clicked = "Fiche"
                        clicks++
                    })
                    GlassButton("Favori", {
                        clicked = "Favori"
                        clicks++
                    })
                }
            }
            AutoFocus(first)
        }
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        rule.runOnIdle {
            inputMode.requestInputMode(InputMode.Keyboard)
            first.requestFocus()
        }
        rule.waitUntil(5_000) { rule.onNodeWithText("Jouer").fetchSemanticsNode().config[SemanticsProperties.Focused] }
        rule.onNodeWithText("Jouer").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithText("Fiche").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithText("Favori").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals("Favori", clicked)
        assertEquals(1, clicks)
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        pad(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("Fiche", clicked)
        assertEquals(2, clicks)
        pad(KeyEvent.KEYCODE_ENTER)
        assertEquals(3, clicks)
        pad(KeyEvent.KEYCODE_SPACE)
        assertEquals(4, clicks)
    }
}
