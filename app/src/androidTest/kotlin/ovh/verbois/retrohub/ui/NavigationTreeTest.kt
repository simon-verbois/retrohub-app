package ovh.verbois.retrohub.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
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
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.home.HomeScreen
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.settings.SettingsScreen
import ovh.verbois.retrohub.ui.shell.ConsoleShell
import ovh.verbois.retrohub.ui.theme.RetroHubTheme

@RunWith(AndroidJUnit4::class)
class NavigationTreeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as RetroHubApp).container
    private lateinit var controller: UiController

    @After
    fun clear() = runBlocking { container.database.games().clear() }

    private fun show(section: Section = Section.HOME, largeText: Boolean = false) {
        instrumentation.setInTouchMode(false)
        runBlocking {
            container.database.games().replaceAll(
                (0 until 8).map { index ->
                    val game = LibraryGame("tree-$index", "tree-$index", title = "Jeu $index", console = "nes")
                    GameEntity(
                        game.id, game.playGameId, game.title, game.console, false, null, 0, index,
                        ApiJson.encodeToString(LibraryGame.serializer(), game),
                    )
                },
            )
        }
        rule.setContent {
            val scope = rememberCoroutineScope()
            val ui = remember {
                UiController(container, scope).also {
                    controller = it
                    it.nav.section(section)
                }
            }
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                RetroHubTheme {
                    ConsoleShell(ui, Account("https://example.invalid", userId = "test", username = "Test", deviceId = "test", deviceName = "Test")) { route ->
                        when (route) {
                            Route.Home -> HomeScreen(ui)
                            is Route.Settings -> SettingsScreen(ui, route.page, route.console)
                            else -> Unit
                        }
                    }
                    if (ui.exitRequested) {
                        ConfirmDialog("Quitter RetroHub ?", "Votre progression est conservée.", "Quitter", {
                        }, { ui.exitRequested = false }, focusDismiss = true)
                    }
                }
            }
        }
        rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        rule.waitUntil(5_000) {
            val node = if (section ==
                Section.HOME
            ) {
                rule.onNodeWithTag("home-game-tree-0", useUnmergedTree = true)
            } else {
                rule.onNodeWithText("Paramètres de l’application")
            }
            runCatching { node.fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
    }

    private fun pad(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(0, 0, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
        }
        rule.waitForIdle()
    }

    @Test
    fun actionsReturnToTheirOwnCoverAndHorizontalMovementStaysInTheRow() {
        show()
        val cover = rule.onNodeWithTag("home-game-tree-2", useUnmergedTree = true)
        cover.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("game-primary-action").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithContentDescription("Options du jeu").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        cover.assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("home-game-tree-3", useUnmergedTree = true).assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Accueil").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-game-tree-3", useUnmergedTree = true).assertIsFocused()
        assertEquals("tree-3", controller.homeGame)
    }

    @Test
    fun leftEdgeOfCoversDoesNotJumpToHomeTab() {
        show()
        val first = rule.onNodeWithTag("home-game-tree-0", useUnmergedTree = true)
        first.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        first.assertIsFocused()
    }

    @Test
    fun profileScrollsToStorageWithAControllerAndLargeText() {
        show(Section.USER, largeText = true)
        rule.onNodeWithText("Paramètres de l’application").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        repeat(4) { pad(KeyEvent.KEYCODE_DPAD_DOWN) }
        rule.onNodeWithText("Stockage").assertIsDisplayed().assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithText("Téléchargements et stockage").assertIsDisplayed()
        repeat(5) { pad(KeyEvent.KEYCODE_DPAD_DOWN) }
        rule.onNodeWithText("Vider le cache des jaquettes").assertIsDisplayed()
    }

    @Test
    fun backAtHomeRequiresConfirmationAndInitiallyFocusesCancel() {
        show()
        rule.runOnIdle { controller.back() }
        rule.onNodeWithText("Quitter RetroHub ?").assertIsDisplayed()
        rule.onNodeWithText("Annuler").assertIsFocused().performClick()
        rule.runOnIdle { assertEquals(false, controller.exitRequested) }
        assertEquals(Route.Home, controller.nav.current)
    }
}
