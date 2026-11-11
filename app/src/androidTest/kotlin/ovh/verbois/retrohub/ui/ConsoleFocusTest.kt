package ovh.verbois.retrohub.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
    fun dpadMovesTheFocusAndButtonAClicks() {
        // Une manette fait sortir Android du mode tactile : les éléments deviennent focalisables.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        var clicked by mutableStateOf("")
        rule.setContent {
            val first = remember { FocusRequester() }
            RetroHubTheme {
                Row {
                    PrimaryButton("Jouer", { clicked = "Jouer" }, Modifier.focusRequester(first))
                    GlassButton("Fiche", { clicked = "Fiche" })
                    GlassButton("Favori", { clicked = "Favori" })
                }
            }
            androidx.compose.runtime.LaunchedEffect(Unit) { first.requestFocus() }
        }
        rule.onNodeWithText("Jouer").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithText("Fiche").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithText("Favori").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals("Favori", clicked)
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        pad(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("Fiche", clicked)
    }
}
