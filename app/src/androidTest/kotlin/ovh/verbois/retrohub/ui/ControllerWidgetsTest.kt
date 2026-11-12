package ovh.verbois.retrohub.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.account.AuthState
import ovh.verbois.retrohub.ui.components.AutoFocus
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.components.DialogField
import ovh.verbois.retrohub.ui.components.FormDialog
import ovh.verbois.retrohub.ui.components.GlassButton
import ovh.verbois.retrohub.ui.components.SliderRow
import ovh.verbois.retrohub.ui.components.controllerVerticalScroll
import ovh.verbois.retrohub.ui.onboarding.Onboarding
import ovh.verbois.retrohub.ui.onboarding.SetupWizard
import ovh.verbois.retrohub.ui.theme.RetroHubTheme

@RunWith(AndroidJUnit4::class)
class ControllerWidgetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun show(content: @Composable () -> Unit) {
        instrumentation.setInTouchMode(false)
        rule.setContent {
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            RetroHubTheme(content = content)
        }
        rule.waitForIdle()
    }

    private fun pad(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(0, 0, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
        }
        rule.waitForIdle()
    }

    private fun waitFocused(text: String) {
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithText(text).fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
    }

    @Test
    fun onboardingReturnsFromTheServerFieldWithButtonB() {
        val container = (instrumentation.targetContext.applicationContext as RetroHubApp).container
        show { Onboarding(container, AuthState.SignedOut()) }
        waitFocused("Commencer")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithText("Votre serveur").assertIsDisplayed()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitFocused("Commencer")
    }

    @Test
    fun setupReturnsOneStepWithButtonBAndKeepsItsControlsReachable() {
        val container = (instrumentation.targetContext.applicationContext as RetroHubApp).container
        show { SetupWizard(container, "Test") }
        repeat(10) {
            val next = runCatching { rule.onNodeWithText("Continuer").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
            if (!next) pad(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        rule.onNodeWithText("Continuer").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithText("Performances").assertIsDisplayed()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithText("Performances").assertDoesNotExist()
        rule.onNodeWithText("Continuer").assertIsDisplayed()
    }

    @Test
    fun inlineChoicesFocusTheCurrentValueAndReturnToTheirRowOnSelectOrBack() {
        var selected by mutableStateOf("Deux")
        show {
            val first = remember { FocusRequester() }
            Column(Modifier.fillMaxSize().controllerVerticalScroll(rememberScrollState())) {
                GlassButton("Avant", {}, Modifier.focusRequester(first))
                ChoiceRow("Choix", listOf("Un", "Deux", "Trois"), selected, { it }, { selected = it })
                GlassButton("Après", {})
            }
            AutoFocus(first)
        }
        waitFocused("Avant")
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Choix").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        val current = rule.onNode(hasText("Deux") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        current.assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        pad(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals("Trois", selected)
        rule.onNodeWithText("Choix").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithText("Choix").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Après").assertIsFocused()
    }

    @Test
    fun formsCloseWithButtonBAndRestoreTheirOpenerWithoutSubmitting() {
        var submitted = 0
        show {
            var open by remember { mutableStateOf(false) }
            val first = remember { FocusRequester() }
            Column {
                GlassButton("Avant", {}, Modifier.focusRequester(first))
                GlassButton("Ouvrir", { open = true })
            }
            AutoFocus(first)
            if (open) {
                FormDialog("Formulaire", null, listOf(DialogField("Nom")), "Enregistrer", {
                    submitted++
                    null
                }, { open = false })
            }
        }
        waitFocused("Avant")
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Ouvrir").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitFocused("Nom")
        for (key in listOf(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_START)) pad(key)
        rule.onNodeWithText("Formulaire").assertIsDisplayed()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithText("Formulaire").assertDoesNotExist()
        rule.onNodeWithText("Ouvrir").assertIsFocused()
        assertEquals(0, submitted)
    }

    @Test
    fun destructiveConfirmationInitiallyFocusesCancelAndNeverLeaksShortcuts() {
        var confirmed = 0
        show {
            var open by remember { mutableStateOf(false) }
            val first = remember { FocusRequester() }
            GlassButton("Retirer", { open = true }, Modifier.focusRequester(first))
            AutoFocus(first)
            if (open) {
                ConfirmDialog("Confirmation", "Contenu à retirer", "Supprimer", {
                    confirmed++
                    open = false
                }, { open = false }, destructive = true)
            }
        }
        waitFocused("Retirer")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitFocused("Annuler")
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        rule.onNodeWithText("Annuler").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithText("Retirer").assertIsFocused()
        assertEquals(0, confirmed)
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitFocused("Annuler")
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithText("Supprimer").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(1, confirmed)
    }

    @Test
    fun sliderUsesLeftRightForItsValueAndUpDownForNavigation() {
        var value by mutableStateOf(0.5f)
        show {
            val first = remember { FocusRequester() }
            Column(Modifier.fillMaxSize().controllerVerticalScroll(rememberScrollState())) {
                GlassButton("Avant", {}, Modifier.focusRequester(first))
                SliderRow("Volume", value, 0f..1f, { value = it }, "${(value * 100).toInt()} %")
                GlassButton("Après", {}, Modifier.testTag("after-slider"))
            }
            AutoFocus(first)
        }
        waitFocused("Avant")
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        org.junit.Assert.assertTrue(value > 0.5f)
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("after-slider").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Avant").assertIsFocused()
    }
}
