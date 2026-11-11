package ovh.verbois.retrohub.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.data.GameEntity
import ovh.verbois.retrohub.ui.library.LibraryScreen
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.shell.ConsoleShell
import ovh.verbois.retrohub.ui.theme.RetroHubTheme

@RunWith(AndroidJUnit4::class)
class NavigationStateTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RetroHubApp).container

    @After
    fun clear() = runBlocking { container.database.games().clear() }

    @Test
    fun libraryRestoresSortScrollAndGameFocusAfterVisitingAnotherPage() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
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
            val ui = remember {
                UiController(container, scope).also {
                    controller = it
                    it.nav.section(Section.LIBRARY)
                }
            }
            RetroHubTheme {
                ConsoleShell(ui, Account("https://example.invalid", userId = "test", username = "Test", deviceId = "test", deviceName = "Test")) { route ->
                    if (route is Route.Library) LibraryScreen(ui, null, false) else Text("Autre écran")
                }
            }
        }
        rule.onNodeWithText("Toutes les consoles · Récents", useUnmergedTree = true).performClick()
        rule.onNodeWithText("A → Z").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(151)
        rule.onNodeWithTag("library-game-nav-150", useUnmergedTree = true).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithTag("library-game-nav-150", useUnmergedTree = true).assertIsFocused()
        rule.runOnIdle { controller.nav.go(Route.Settings()) }
        rule.onNodeWithText("Autre écran").assertIsDisplayed()
        rule.runOnIdle { controller.nav.back() }
        rule.onNodeWithTag("library-game-nav-150", useUnmergedTree = true).assertIsDisplayed().assertIsFocused()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        rule.onNodeWithText("Toutes les consoles · A → Z", useUnmergedTree = true).assertIsDisplayed()
    }
}
