package ovh.verbois.retrohub.ui.settings

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.api.AccountAvatar
import ovh.verbois.retrohub.api.AvatarCatalog
import ovh.verbois.retrohub.ui.theme.RetroHubTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvatarPickerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    // Empty image paths exercise the offline/missing-image fallback without any network requests.
    private val avatars = (0 until 80).map { AccountAvatar("pixel-$it", "Pixel $it", "") }
    private val owner = Account("https://example.invalid", userId = "u", username = "Camille", deviceId = "d", deviceName = "Phone")

    @After
    fun restore() {
        device.setOrientationLandscape()
        instrumentation.waitForIdleSync()
    }

    private fun show(
        current: Int? = null,
        default: Int? = null,
        small: Boolean = false,
        onSave: (String?) -> Unit = {},
        onDismiss: () -> Unit = {},
        error: () -> String? = { null },
    ) {
        instrumentation.setInTouchMode(false)
        if (small) device.setOrientationNatural()
        lateinit var input: InputModeManager
        rule.setContent {
            input = LocalInputModeManager.current
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val pixels = with(density) { maxWidth.toPx() }
                CompositionLocalProvider(LocalDensity provides if (small) Density(pixels / 320f, 1.6f) else density) {
                    RetroHubTheme {
                        AvatarPicker(
                            owner.copy(
                                avatar = current?.let {
                                    avatars[it]
                                },
                            ),
                            AvatarCatalog(avatars, defaultAvatar = default?.let { avatars[it] }),
                            error(),
                            false,
                            {},
                            onSave,
                            onDismiss,
                        )
                    }
                }
            }
        }
        rule.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        val first = if (default != null && (current == null || current == default)) "Par défaut" else current?.let { "Pixel $it" } ?: "Initiale"
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithContentDescription(first).fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
    }

    private fun pad(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(0, 0, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
        }
        rule.waitForIdle()
    }

    @Test
    fun existingAvatarIsVisibleAndFocusedBeyondTheFirstPageAndCanBeChangedWithAPad() {
        var chosen: String? = null
        show(current = 60, onSave = { chosen = it })
        rule.onNodeWithContentDescription("Pixel 60").assertIsDisplayed().assertIsFocused().assertIsSelected()
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithContentDescription("Pixel 59").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithContentDescription("Pixel 59").assertIsSelected()
        rule.onNodeWithText("Enregistrer").performClick()
        assertEquals("pixel-59", chosen)
    }

    @Test
    fun cancellingAPreviewNeverSavesIt() {
        var saves = 0
        var dismissed = false
        show(current = 0, onSave = { saves++ }, onDismiss = { dismissed = true })
        rule.onNodeWithContentDescription("Pixel 1").performScrollTo().performClick().assertIsSelected()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        assertTrue(dismissed)
        assertEquals(0, saves)
    }

    @Test
    fun theDefaultAvatarCanBeRestoredWithoutSendingAnExplicitImageId() {
        var chosen: String? = "unchanged"
        show(current = 0, default = 1, onSave = { chosen = it })
        rule.onNodeWithContentDescription("Par défaut").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("Enregistrer").performClick()
        assertNull(chosen)
    }

    @Test
    fun aSaveErrorKeepsTheChoiceAndActionsReachableOnASmallPhoneWithLargeText() {
        var error by mutableStateOf<String?>(null)
        show(small = true, default = 0, error = { error }, onSave = { error = "Photo non enregistrée. Reconnectez-vous au serveur puis réessayez." })
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Photo de profil").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1.6f, layouts.single().layoutInput.density.fontScale, 0.01f)
        rule.onNodeWithContentDescription("Pixel 1").performScrollTo().performClick()
        rule.onNodeWithText("Enregistrer").performClick()
        rule.onNodeWithText("Photo non enregistrée.", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Pixel 1").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Enregistrer").assertIsDisplayed()
        rule.onNodeWithText("Annuler").assertIsDisplayed()
        rule.onNodeWithText("Enregistrer").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Annuler").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Enregistrer").assertIsFocused()
        screenshot("avatar-picker-small-portrait")
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "test-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { stream ->
            check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
}
