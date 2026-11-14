package ovh.verbois.retrohub.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.data.GameEntity
import ovh.verbois.retrohub.ui.home.HomeScreen
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.shell.ConsoleShell
import ovh.verbois.retrohub.ui.theme.RetroHubTheme

@RunWith(AndroidJUnit4::class)
class NavigationStateTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as RetroHubApp).container

    @After
    fun clear() = runBlocking { container.database.games().clear() }

    @Test
    fun homeRestoresADistantGameAfterToolsAndCategoryChanges() {
        rule.settleOrientation(portrait = false)
        instrumentation.setInTouchMode(false)
        runBlocking {
            container.database.games().replaceAll(
                (0 until 200).map { index ->
                    val game = LibraryGame("nav-$index", "nav-$index", title = "Jeu ${index.toString().padStart(3, '0')}", console = "nes")
                    GameEntity(
                        game.id, game.playGameId, game.title, game.console, false, null, 0, index,
                        ApiJson.encodeToString(LibraryGame.serializer(), game),
                    )
                },
            )
        }
        lateinit var controller: UiController
        rule.setContent {
            val scope = rememberCoroutineScope()
            val ui = remember { UiController(container, scope).also { controller = it } }
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            RetroHubTheme {
                ConsoleShell(ui, Account("https://example.invalid", userId = "test", username = "Test", deviceId = "test", deviceName = "Test")) { route ->
                    if (route == Route.Home) HomeScreen(ui) else Text("Outil ouvert")
                }
            }
        }
        rule.waitUntil(5_000) { container.library.games.value.size == 200 }
        rule.runOnIdle { controller.showGame("nav-150") }
        waitFocused("home-game-nav-150")
        val before = rule.onNodeWithTag("home-game-nav-150").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { controller.nav.go(Route.Settings()) }
        rule.onNodeWithText("Outil ouvert").assertIsDisplayed()
        rule.onNodeWithTag("home-content").assertIsDisplayed()
        rule.runOnIdle { controller.back() }
        waitFocused("home-game-nav-150")
        rule.runOnIdle { controller.cycleHomeColumn(-1) }
        rule.onNodeWithText("Aucun favori").assertIsDisplayed()
        rule.runOnIdle { controller.cycleHomeColumn(1) }
        waitFocused("home-game-nav-150")
        assertEquals(before, rule.onNodeWithTag("home-game-nav-150").fetchSemanticsNode().boundsInRoot)
        assertEquals(Route.Home, controller.nav.current)
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(0, 0, action, KeyEvent.KEYCODE_DPAD_DOWN, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
        }
        waitFocused("home-game-nav-151")
        rule.onNodeWithTag("home-game-nav-151").assertIsDisplayed().assertIsFocused()
    }

    private fun waitFocused(tag: String) {
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
    }
}
