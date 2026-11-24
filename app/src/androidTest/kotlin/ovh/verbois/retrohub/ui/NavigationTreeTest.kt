package ovh.verbois.retrohub.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.data.GameEntity
import ovh.verbois.retrohub.ui.AppShell
import ovh.verbois.retrohub.ui.HomeScreen
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.settings.SettingsScreen
import ovh.verbois.retrohub.ui.theme.Palette
import ovh.verbois.retrohub.ui.theme.RetroHubTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class NavigationTreeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as RetroHubApp).container
    private lateinit var controller: UiController

    @After
    fun clear() {
        runBlocking {
            container.database.games().clear()
            container.settings.updateNow { it.copy(homeFolders = emptyList(), homePins = emptyList(), homeActivity = emptyMap(), reduceMotion = false) }
        }
        rule.settleOrientation(portrait = false)
    }

    private fun show(
        section: Section = Section.HOME,
        largeText: Boolean = false,
        portrait: Boolean = false,
        twoConsoles: Boolean = false,
        reducedMotion: Boolean = false,
        browseConsole: Boolean = true,
        artwork: Boolean = false,
        longTitle: Boolean = false,
    ) {
        rule.settleOrientation(portrait)
        instrumentation.setInTouchMode(false)
        runBlocking {
            container.settings.updateNow { it.copy(homePins = (0 until 8).map { index -> "tree-$index" }, reduceMotion = reducedMotion) }
            container.database.games().replaceAll(
                (0 until 8).map { index ->
                    val game = LibraryGame(
                        "tree-$index",
                        "tree-$index",
                        title = if (longTitle && index == 0) "Professeur Layton et la Boîte de Pandore" else "Jeu $index",
                        console = if (twoConsoles &&
                            index >= 4
                        ) {
                            "snes"
                        } else {
                            "nes"
                        },
                        screenshots = if (artwork) listOf("/games/tree-$index/screenshots/0") else emptyList(),
                    )
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
                    it.portrait = portrait
                    it.nav.section(section)
                }
            }
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                RetroHubTheme {
                    Box(Modifier.fillMaxSize().background(Palette.Background))
                    AppShell(ui, Account("https://example.invalid", userId = "test", username = "Test", deviceId = "test", deviceName = "Test")) { route ->
                        if (portrait) {
                            // The portrait pages as these tests always drew them (no back header around a game).
                            when (route) {
                                Route.Home -> HomeScreen(ui)
                                is Route.Game -> ovh.verbois.retrohub.ui.mobile.home.GameDetailsScreen(ui, route.id)
                                Route.Downloads -> ovh.verbois.retrohub.ui.mobile.library.DownloadsScreen(ui)
                                is Route.Library -> ovh.verbois.retrohub.ui.mobile.library.LibraryScreen(ui, route.console, false)
                                is Route.Folder -> HomeScreen(ui)
                                Route.Favorites -> ovh.verbois.retrohub.ui.mobile.library.LibraryScreen(ui, null, true)
                                is Route.Settings -> SettingsScreen(ui, route.page, route.console)
                                Route.Catalogue -> ovh.verbois.retrohub.ui.catalogue.CatalogueScreen(ui)
                                else -> Unit
                            }
                        } else {
                            AppScreen(ui, route)
                        }
                    }
                    if (ui.exitRequested) {
                        ConfirmDialog("Quitter RetroHub ?", "Votre progression est conservée.", "Quitter", {
                        }, { ui.exitRequested = false }, focusDismiss = true)
                    }
                }
            }
        }
        if (section != Section.USER) rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        if (section == Section.HOME && !portrait && browseConsole) {
            rule.waitUntil(5_000) { container.library.games.value.any { it.id == "tree-0" } }
            rule.runOnIdle {
                controller.selectHomeColumn("console:nes")
                if (longTitle) controller.selectHomeGame("tree-0")
            }
        }
        try {
            rule.waitUntil(5_000) {
                val node = if (section ==
                    Section.HOME
                ) {
                    rule.onNodeWithTag(
                        when {
                            portrait -> "library-game-tree-0"

                            browseConsole -> "home-game-tree-0"

                            // Nothing played and no favorite: the home opens on the catalogue.
                            else -> "home-catalogue-action"
                        },
                        useUnmergedTree = true,
                    )
                } else {
                    rule.onNodeWithTag(if (portrait) "profile-shortcut-root" else "control-home")
                }
                runCatching { node.fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
            }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            screenshot("startup-focus-failure")
            val focused = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).fetchSemanticsNodes().map { it.config.toString() }
            throw AssertionError(
                "Initial home column=${controller.homeColumn}, game=${controller.homeGame}, route=${controller.nav.current}, " +
                    "games=${container.library.games.value.map { it.id }}, focused=$focused",
                error,
            )
        }
    }

    private fun selectGame(index: Int) {
        rule.runOnIdle { controller.showGame("tree-$index") }
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("home-game-tree-$index").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "test-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { stream ->
            check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun pad(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(0, 0, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
        }
        rule.waitForIdle()
        if (code == KeyEvent.KEYCODE_BUTTON_B) UiDevice.getInstance(instrumentation).waitForIdle()
    }

    private fun assertCoverStaysNearTheConsoleLabel() {
        val label = rule.onNodeWithText("NES / Famicom", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val cover = rule.onNodeWithTag("home-cover-${controller.homeGame}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val gap = cover.top - label.bottom
        assertTrue("Cover is too far from its console: $gap px", gap >= 0f && gap <= 32f * rule.activity.resources.displayMetrics.density)
    }

    @Test
    fun onlyTheActiveCategoryAndGameShowTheirLabels() {
        show(twoConsoles = true)
        rule.onNodeWithText(ovh.verbois.retrohub.emulation.Consoles.name("nes")).assertIsDisplayed()
        rule.onNodeWithText(ovh.verbois.retrohub.emulation.Consoles.name("snes")).assertDoesNotExist()
        rule.onNodeWithText("Jeu 0").assertIsDisplayed()
        rule.onNodeWithText("Jeu 1").assertDoesNotExist()
        rule.onNodeWithContentDescription("Jeu 1").assertIsDisplayed()
        screenshot("xmb-active-labels")
    }

    @Test
    fun interruptedColumnTransitionsExposeOnlyTheCurrentGameToInput() {
        show(twoConsoles = true)
        rule.mainClock.autoAdvance = false
        try {
            pad(KeyEvent.KEYCODE_DPAD_RIGHT)
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("home-game-tree-4").assertIsFocused()
            rule.onNodeWithTag("home-game-tree-0").assertDoesNotExist()
            pad(KeyEvent.KEYCODE_DPAD_LEFT)
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("home-game-tree-0").assertIsFocused()
            rule.onNodeWithTag("home-game-tree-4").assertDoesNotExist()
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun previousGamesRemainVisibleAboveTheCategoryBand() {
        show()
        selectGame(5)
        val previous = rule.onNodeWithTag("home-game-tree-4").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val categories = rule.onNodeWithTag("home-categories").fetchSemanticsNode().boundsInRoot
        val selected = rule.onNodeWithTag("home-game-tree-5").assertIsFocused().fetchSemanticsNode().boundsInRoot
        assertTrue(previous.bottom <= categories.top)
        assertTrue(selected.top >= categories.bottom)
        screenshot("xmb-previous-items-above")
    }

    @Test
    fun sideActionsKeepTheSelectedGameVisibleAndRestoreItsFocus() {
        show()
        selectGame(2)
        pad(KeyEvent.KEYCODE_BUTTON_X)
        val panel = rule.onNodeWithTag("xmb-options-panel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val parent = rule.onNodeWithTag("home-cover-tree-2", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val home = rule.onNodeWithTag("home-content").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.width <= home.right - parent.right)
        screenshot("xmb-side-actions")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("xmb-options-panel").assertDoesNotExist()
        rule.onNodeWithTag("home-game-tree-2").assertIsFocused()
    }

    @Test
    fun restingArtworkIsCancelledWhenTheSelectionChangesAgain() {
        show(artwork = true)
        rule.mainClock.autoAdvance = false
        try {
            pad(KeyEvent.KEYCODE_DPAD_DOWN)
            rule.mainClock.advanceTimeBy(160)
            rule.onNodeWithTag("home-preview-tree-1").assertDoesNotExist()
            pad(KeyEvent.KEYCODE_DPAD_DOWN)
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
            rule.onNodeWithTag("home-preview-tree-1").assertDoesNotExist()
            rule.onNodeWithTag("home-preview-tree-2").assertIsDisplayed()
            rule.onNodeWithTag("home-game-tree-2").assertIsFocused()
            screenshot("xmb-resting-preview")
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun carouselFitsTheViewportAndKeepsItsCrossWhileNavigating() {
        show(artwork = true)
        val before = rule.onNodeWithTag("home-cover-tree-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        selectGame(4)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("home-preview-tree-4").fetchSemanticsNodes().isNotEmpty() }
        val home = rule.onNodeWithTag("home-content").fetchSemanticsNode().boundsInRoot
        val cover = rule.onNodeWithTag("home-cover-tree-4", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val category = rule.onNodeWithTag("home-category-console:nes").fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithTag("home-game-tree-4").assertIsFocused().fetchSemanticsNode().boundsInRoot
        val preview = rule.onNodeWithTag("home-preview-tree-4").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before.center.x, cover.center.x, 1f)
        assertEquals(before.center.y, cover.center.y, 1f)
        assertEquals(category.center.x, cover.center.x, 1f)
        assertTrue(cover.top >= home.top && cover.bottom <= home.bottom)
        assertTrue(row.right <= preview.left)
        assertTrue(preview.top >= home.top && preview.bottom <= home.bottom && preview.right <= home.right)
        assertCoverStaysNearTheConsoleLabel()
        screenshot("xmb-adaptive-viewport")
    }

    @Test
    fun portraitHomeUsesTheLibraryAndOpensAScrollableGamePage() {
        show(portrait = true)
        rule.onNodeWithTag("home-games", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("library-grid").assertIsDisplayed()
        rule.onNodeWithTag("library-game-tree-2").performClick()
        rule.onNodeWithTag("game-details").assertIsDisplayed()
        rule.onNodeWithTag("game-primary-action").assertIsDisplayed()
        assertEquals(Route.Game("tree-2"), controller.nav.current)
        rule.waitUntil(5000) {
            runCatching {
                rule.onNodeWithTag("game-primary-action").assertIsFocused()
                true
            }.getOrDefault(false)
        }
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("library-grid").assertIsDisplayed()
        screenshot("portrait-library")
    }

    @Test
    fun consolesHaveIndependentVerticalSelectionsOnTheSameHome() {
        show(twoConsoles = true)
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-game-tree-2").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("home-game-tree-4").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-game-tree-5").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithTag("home-game-tree-2").assertIsFocused()
        assertEquals(Route.Home, controller.nav.current)
        rule.onNodeWithTag("home-categories").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("console-game-info", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
        screenshot("console-vertical-strip")
    }

    @Test
    fun changingConsolesFromTheCategoryLaneDoesNotConsumeTheFirstDown() {
        show(twoConsoles = true)
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("home-category-console:nes").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("home-game-tree-4").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-game-tree-5").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("home-category-console:snes").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_L1)
        rule.onNodeWithTag("home-game-tree-0").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-game-tree-1").assertIsFocused()
    }

    @Test
    fun repeatedUpStopsOnTheHeaderAndSearchWaitsForConfirmation() {
        show(twoConsoles = true)
        repeat(8) { pad(KeyEvent.KEYCODE_DPAD_UP) }
        rule.onNodeWithTag("header-search-button").assertIsFocused()
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("header-profile-button").assertIsFocused()
        repeat(3) { pad(KeyEvent.KEYCODE_DPAD_UP) }
        rule.onNodeWithTag("header-profile-button").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithTag("header-search-button").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-category-console:nes").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-game-tree-0").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-game-tree-1").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        repeat(2) { pad(KeyEvent.KEYCODE_DPAD_UP) }
        rule.onNodeWithTag("header-search-button").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("header-search-field").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("header-search-field").assertIsFocused().performTextInput("Jeu 2")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("search-result-tree-2").fetchSemanticsNodes().isNotEmpty() }
        // Submitting closes the keyboard and moves to the results; A opens the chosen one.
        rule.onNodeWithTag("header-search-field").performImeAction()
        waitFocused("search-result-tree-2")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        // A result opens its game page; the search closes.
        try {
            rule.waitUntil(5_000) { controller.nav.current == Route.CatalogueGame("tree-2") }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Route ${controller.nav.current}, searching ${controller.searching}", failure)
        }
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
    }

    @Test
    fun touchSwipesUseTheSameCrossNavigationWithReducedMotion() {
        show(twoConsoles = true, reducedMotion = true)
        rule.onNodeWithTag("home-games").performTouchInput { swipeUp() }
        rule.runOnIdle { assertEquals("tree-3", controller.homeGame) }
        rule.onNodeWithTag("home-categories").performTouchInput { swipeLeft() }
        rule.runOnIdle {
            assertEquals("console:snes", controller.homeColumn)
            assertEquals("tree-4", controller.homeGame)
            assertEquals(Route.Home, controller.nav.current)
        }
        screenshot("console-touch-reduced-motion")
    }

    @Test
    fun coverActionsOnlyOpenOnHoldOrTheControllersOptionsButton() {
        fun waitForActions() {
            rule.waitUntil(5_000) {
                runCatching { rule.onNodeWithText("Ajouter aux favoris").assertIsDisplayed() }.isSuccess
            }
            UiDevice.getInstance(instrumentation).waitForIdle()
        }
        show()
        rule.onNodeWithTag("home-favorite").assertDoesNotExist()
        rule.onNodeWithTag("home-game-options").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_START)
        waitForActions()
        assertEquals(Route.Home, controller.nav.current)
        rule.onNodeWithTag("home-overlay").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        pad(KeyEvent.KEYCODE_BUTTON_SELECT)
        waitForActions()
        assertEquals(Route.Home, controller.nav.current)
        pad(KeyEvent.KEYCODE_BUTTON_B)
        pad(KeyEvent.KEYCODE_MENU)
        waitForActions()
        assertEquals(Route.Home, controller.nav.current)
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("home-game-tree-0").performTouchInput { longClick() }
        waitForActions()
        screenshot("cover-actions-menu")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(Route.Home, controller.nav.current)
    }

    @Test
    fun homeFitsTheViewportWithoutVerticalScrollingEvenWithLargerText() {
        show(largeText = true)
        val home = rule.onNodeWithTag("home-content").fetchSemanticsNode()
        assertFalse(home.config.contains(SemanticsProperties.VerticalScrollAxisRange))
        val game = rule.onNodeWithTag("home-game-tree-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(game.top >= home.boundsInRoot.top)
        assertTrue(game.bottom <= home.boundsInRoot.bottom)
        screenshot("console-large-text")
    }

    @Test
    fun activeLabelsRemainReadableWithLargeTextAndNoArtwork() {
        show(largeText = true, longTitle = true)
        for (text in listOf("NES / Famicom", "Professeur Layton et la Boîte de Pandore", "Pas encore sur le serveur")) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Missing text layout: $text", layouts.isNotEmpty())
            for (layout in layouts) {
                assertFalse("Clipped height: $text", layout.didOverflowHeight)
                repeat(layout.lineCount) { line -> assertFalse("Ellipsis: $text", layout.isLineEllipsized(line)) }
            }
        }
        assertCoverStaysNearTheConsoleLabel()
        screenshot("xmb-readable-active-labels")
    }

    @Test
    fun focusingTheSearchButtonWaitsForExplicitActivation() {
        show()
        rule.onNodeWithTag("header-search-button").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithTag("header-search-button").assertIsFocused()
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithTag("header-search-field").assertIsDisplayed()
        // B first ends typing, then closes the search.
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("header-search-field").assertIsDisplayed()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
    }

    @Test
    fun headerSearchKeepsThePageAndRemainsOpenUntilExplicitlyClosed() {
        show()
        val homeBefore = rule.onNodeWithTag("home-content", useUnmergedTree = true).fetchSemanticsNode()
        val selectedBefore = controller.homeGame
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        rule.onNodeWithTag("header-search-field").assertIsFocused()
        assertEquals(Route.Home, controller.nav.contentRoute)
        val homeAfter = rule.onNodeWithTag("home-content", useUnmergedTree = true).fetchSemanticsNode()
        // The search covers the home without recreating or moving it.
        assertEquals(homeBefore.id, homeAfter.id)
        assertEquals(homeBefore.boundsInRoot.top, homeAfter.boundsInRoot.top, 1f)
        assertEquals(selectedBefore, controller.homeGame)
        rule.onNodeWithTag("console-search").assertIsDisplayed()
        rule.onNodeWithTag("header-search-field").performTextInput("Jeu 3")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("search-result-tree-3").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("search-result-tree-3").assertExists()
        rule.onNodeWithTag("search-result-tree-2").assertDoesNotExist()
        screenshot("header-search-landscape")
        rule.onNodeWithTag("header-search-field").performImeAction()
        rule.onNodeWithTag("header-search-field").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("search-result-tree-3").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
        rule.onNodeWithTag("home-content", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(Route.Home, controller.nav.current)
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        rule.onNodeWithTag("header-search-field").assertIsDisplayed()
        assertTrue(controller.searching)
        repeat(2) { pad(KeyEvent.KEYCODE_BUTTON_B) }
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
    }

    @Test
    fun portraitUsesOneSectionDropdownInsteadOfTheAppNameAndTabRow() {
        show(portrait = true)
        rule.onNodeWithText("RetroHub").assertDoesNotExist()
        rule.onNodeWithTag("section-menu").assertIsDisplayed().performClick()
        rule.onNodeWithText("Favoris").assertIsDisplayed()
        rule.onNodeWithText("Téléchargés").assertIsDisplayed()
        rule.onNodeWithTag("section-choice-catalogue").assertDoesNotExist()
        screenshot("section-menu-portrait")
        rule.onNodeWithText("Téléchargés").performClick()
        rule.waitUntil(5_000) { controller.nav.current == Route.Downloads }
        rule.onNodeWithTag("downloads-storage").assertIsDisplayed()
        assertEquals(Route.Downloads, controller.nav.current)
        screenshot("downloads-portrait")
    }

    @Test
    fun portraitSectionMenuSupportsAAndBAndRestoresItsHeaderFocus() {
        show(portrait = true)
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("section-menu").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithTag("section-choice-home").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("section-choice-favorites").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        pad(KeyEvent.KEYCODE_BUTTON_A)
        try {
            rule.waitUntil(5_000) {
                runCatching { rule.onNodeWithTag("downloads-storage").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
            }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val focused = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).fetchSemanticsNodes().map { it.config.toString() }
            throw AssertionError("Route ${controller.nav.current}, focused $focused", failure)
        }
        pad(KeyEvent.KEYCODE_DPAD_UP)
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("section-choice-downloads").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("section-menu").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("downloads-storage").assertIsFocused()
    }

    @Test
    fun emptyHomeKeepsTheCatalogueReachableWithTheController() {
        show()
        runBlocking { container.database.games().clear() }
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("home-catalogue-action").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
        // Without games, played games or favorites, the catalogue is the only column.
        rule.onNodeWithTag("home-category-favorites").assertDoesNotExist()
        rule.onNodeWithTag("home-category-recent").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        rule.onNodeWithTag("home-catalogue-action").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_L1)
        rule.onNodeWithTag("home-catalogue-action").assertIsFocused()
        rule.onNodeWithTag("home-category-downloads").assertDoesNotExist()
        assertEquals(Route.Home, controller.nav.current)
    }

    @Test
    fun homeRestoresTheSelectedCoverAfterSettingsAndBack() {
        show()
        selectGame(2)
        rule.runOnIdle { controller.nav.go(Route.Settings()) }
        waitFocused("settings-section-account")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        try {
            waitFocused("home-game-tree-2")
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val focused = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).fetchSemanticsNodes().map { it.config.toString() }
            throw AssertionError("Home ${controller.homeGame}, route ${controller.nav.current}, focused $focused", failure)
        }
        assertEquals("tree-2", controller.homeGame)
    }

    private fun waitFocused(tag: String) {
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
    }

    @Test
    fun choosingAnOffscreenGameDoesNotRestoreAnotherCoversFocus() {
        show()
        rule.runOnIdle { controller.nav.go(Route.Settings()) }
        waitFocused("settings-section-account")
        rule.runOnIdle { controller.showGame("tree-7") }
        waitFocused("home-game-tree-7")
        assertEquals("tree-7", controller.homeGame)
    }

    @Test
    fun settingsPagesKeepControllerFocusVisibleAndReturnToTheirHeader() {
        show()
        val focused = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)
        // Sections: the list keeps the focus, Right enters the section, B returns to the list.
        for (section in listOf(SettingsPage.APPEARANCE, SettingsPage.DISPLAY, SettingsPage.CONTROLS, SettingsPage.AUDIO, SettingsPage.STORAGE)) {
            rule.runOnIdle {
                controller.nav.section(Section.HOME)
                controller.nav.go(Route.Settings(section))
            }
            try {
                waitFocused("settings-section-${section.name.lowercase()}")
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                val now = rule.onAllNodes(focused).fetchSemanticsNodes().map { it.config.toString() }
                throw AssertionError("Section $section, route ${controller.nav.current}, focused $now", failure)
            }
            pad(KeyEvent.KEYCODE_DPAD_RIGHT)
            repeat(12) {
                pad(KeyEvent.KEYCODE_DPAD_DOWN)
                rule.onNode(focused).assertIsDisplayed()
            }
            pad(KeyEvent.KEYCODE_BUTTON_B)
            rule.onNodeWithTag("settings-section-${section.name.lowercase()}").assertIsFocused()
        }
        // Deeper pages open full screen with the focus on their first control.
        for (page in listOf(SettingsPage.CONSOLE, SettingsPage.REMAP, SettingsPage.CHANGELOG)) {
            rule.runOnIdle {
                controller.nav.section(Section.HOME)
                controller.nav.go(Route.Settings(page, "nds"))
            }
            rule.waitUntil(5_000) { rule.onAllNodes(focused).fetchSemanticsNodes().isNotEmpty() }
            repeat(12) {
                pad(KeyEvent.KEYCODE_DPAD_DOWN)
                rule.onNode(focused).assertIsDisplayed()
            }
        }
    }

    @Test
    fun settingsRemainNavigableInPortraitWithLargeText() {
        show(portrait = true, largeText = true)
        checkSettingsTraversal(
            listOf(SettingsPage.APPEARANCE, SettingsPage.CONTROLS, SettingsPage.AUDIO, SettingsPage.STORAGE, SettingsPage.CONSOLE, SettingsPage.GAME),
        )
    }

    private fun checkSettingsTraversal(pages: List<SettingsPage>) {
        val focused = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)
        for (page in pages) {
            rule.runOnIdle {
                controller.nav.section(Section.HOME)
                controller.nav.go(Route.Settings(page, if (page == SettingsPage.GAME) "tree-0" else "nds"))
            }
            rule.waitUntil(5_000) {
                runCatching { rule.onNodeWithContentDescription("Retour").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
            }
            var previous: Pair<Int, androidx.compose.ui.geometry.Rect>? = null
            var unchanged = 0
            for (step in 0 until 36) {
                pad(KeyEvent.KEYCODE_DPAD_DOWN)
                val node = rule.onNode(focused).assertIsDisplayed().fetchSemanticsNode()
                val position = node.id to node.boundsInRoot
                if (position == previous) unchanged++ else unchanged = 0
                previous = position
                if (unchanged == 2) break
            }
            // Also cross adaptive columns and flow rows before returning to the fixed header.
            pad(KeyEvent.KEYCODE_DPAD_RIGHT)
            rule.onNode(focused).assertIsDisplayed()
            for (step in 0 until 48) {
                if (runCatching {
                        rule.onNodeWithContentDescription("Retour").fetchSemanticsNode().config[SemanticsProperties.Focused]
                    }.getOrDefault(false)
                ) {
                    break
                }
                pad(KeyEvent.KEYCODE_DPAD_UP)
                rule.onNode(focused).assertIsDisplayed()
            }
            rule.onNodeWithContentDescription("Retour").assertIsFocused()
        }
    }

    @Test
    fun legacyDownloadLinksOpenStorageWithoutAddingAHomeCategory() {
        show()
        rule.runOnIdle { controller.nav.section(Section.DOWNLOADS) }
        rule.onNodeWithTag("home-category-downloads").assertDoesNotExist()
        rule.onNodeWithTag("home-overlay").assertIsDisplayed()
        rule.onNodeWithTag("console-settings").assertIsDisplayed()
        rule.onNodeWithTag("settings-section-storage").assertExists()
        assertEquals(Route.Settings(SettingsPage.STORAGE), controller.nav.current)
    }

    @Test
    fun gameOptionsRestoreFocusAndChangingConsoleEntersItsGames() {
        show(twoConsoles = true)
        selectGame(2)
        pad(KeyEvent.KEYCODE_BUTTON_X)
        rule.onNodeWithText("Ajouter aux favoris").assertIsDisplayed()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("home-game-tree-2").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        pad(KeyEvent.KEYCODE_DPAD_UP)
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("home-category-console:nes").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("home-game-tree-4").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("home-game-tree-5").assertIsFocused()
        assertEquals(Route.Home, controller.nav.current)
    }

    @Test
    fun catalogueOpensAboveThePermanentHome() {
        show(browseConsole = false)
        assertEquals("catalogue", controller.homeColumn)
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.waitUntil(5_000) { controller.nav.current == Route.Catalogue }
        rule.onNodeWithTag("home-overlay").assertIsDisplayed()
        rule.onNodeWithTag("catalogue").assertIsDisplayed()
        rule.onNodeWithTag("hint-bar").assertIsDisplayed()
        UiDevice.getInstance(instrumentation).waitForIdle()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.waitUntil(5_000) { controller.nav.current == Route.Home }
        rule.onNodeWithTag("home-overlay").assertDoesNotExist()
        waitFocused("home-catalogue-action")
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("home-game-tree-0").assertIsFocused()
    }

    @Test
    fun theCatalogueStartsAtTheFixedCrossAndConsolesMoveToTheSameAnchor() {
        show(twoConsoles = true, browseConsole = false)
        val anchor = rule.onNodeWithTag("home-category-catalogue").fetchSemanticsNode().boundsInRoot.center.x
        assertEquals("catalogue", controller.homeColumn)
        rule.onNodeWithTag("home-category-favorites").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        rule.onNodeWithTag("home-game-tree-0").assertIsFocused()
        assertEquals(anchor, rule.onNodeWithTag("home-category-console:nes").fetchSemanticsNode().boundsInRoot.center.x, 2f)
        assertEquals(anchor, rule.onNodeWithTag("home-cover-tree-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.x, 2f)
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        rule.onNodeWithTag("home-game-tree-4").assertIsFocused()
        assertEquals(anchor, rule.onNodeWithTag("home-cover-tree-4", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.x, 2f)
        screenshot("home-fixed-cross")
    }

    @Test
    fun selectedCoverKeepsTheSameBoundsWhileTheStripMoves() {
        show()
        val initial = rule.onNodeWithTag("home-game-tree-0").fetchSemanticsNode().boundsInRoot
        repeat(7) { step ->
            pad(KeyEvent.KEYCODE_DPAD_DOWN)
            val cover = rule.onNodeWithTag("home-game-tree-${step + 1}").assertIsFocused().fetchSemanticsNode().boundsInRoot
            assertEquals(initial.left, cover.left, 2f)
            assertEquals(initial.top, cover.top, 2f)
            assertEquals(initial.height, cover.height, 1f)
        }
        screenshot("home-anchored-carousel")
    }

    @Test
    fun submittedSearchScrollsThroughAllResultsWithoutClosing() {
        show()
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        rule.onNodeWithTag("header-search-field").performTextInput("Jeu")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("search-result-tree-0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("header-search-field").performImeAction()
        waitFocused("search-result-tree-0")
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("search-result-tree-7").assertIsDisplayed().assertIsFocused()
        rule.onNodeWithTag("header-search-field").assertIsDisplayed()
        screenshot("search-scrolled-results")
    }

    @Test
    fun legacyBrowsingLinksConvergeOnHomeFolders() {
        show(twoConsoles = true)
        rule.runOnIdle { controller.nav.go(Route.Library("snes")) }
        rule.onNodeWithTag("home-game-tree-4").assertIsDisplayed()
        assertEquals("console:snes", controller.homeColumn)
        assertEquals(Route.Home, controller.nav.current)
        // Without favorites there is no Favoris column: the link lands on the home.
        rule.runOnIdle { controller.nav.section(Section.FAVORITES) }
        rule.waitForIdle()
        rule.onNodeWithTag("home-category-favorites").assertDoesNotExist()
        assertEquals(Route.Home, controller.nav.current)
    }

    @Test
    fun landscapeFoldersKeepTheHeaderStableWithoutPhoneNavigation() {
        show(twoConsoles = true)
        val before = rule.onNodeWithTag("header-profile-button").fetchSemanticsNode().boundsInRoot
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        val after = rule.onNodeWithTag("header-profile-button").fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        rule.onNodeWithTag("section-tab-home").assertDoesNotExist()
        rule.onNodeWithTag("section-menu").assertDoesNotExist()
        assertEquals(Route.Home, controller.nav.current)
    }

    @Test
    fun storedCustomFoldersStayOutOfHomeAndGameMenus() {
        show()
        val folder = ovh.verbois.retrohub.emulation.HomeFolder("old-folder", "Aventure", listOf("tree-2"))
        runBlocking { container.settings.updateNow { it.copy(homeFolders = listOf(folder), homePins = listOf(folder.key)) } }
        rule.onNodeWithTag("home-category-${folder.key}").assertDoesNotExist()
        rule.onNodeWithText("Nouveau dossier").assertDoesNotExist()
        rule.onNodeWithContentDescription("Créer un dossier").assertDoesNotExist()
        selectGame(2)
        pad(KeyEvent.KEYCODE_BUTTON_START)
        rule.onNodeWithText("Ajouter aux favoris").assertIsDisplayed()
        rule.onNodeWithText("Nouveau dossier").assertDoesNotExist()
        rule.onNodeWithText("Ajouter à Aventure").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.runOnIdle { controller.nav.go(Route.Folder(folder.id)) }
        rule.waitUntil(5_000) { controller.nav.current == Route.Home }
        rule.onNodeWithTag("home-game-tree-2").assertIsDisplayed()
        assertEquals(listOf(folder), container.settings.settings.value.homeFolders)
    }

    @Test
    fun profileKeepsTheHomeVisibleAndClosesWithBackStartOrOutsideClick() {
        show()
        selectGame(2)
        val cover = rule.onNodeWithTag("home-game-tree-2", useUnmergedTree = true)
        rule.onNodeWithTag("header-profile-button").performClick()
        rule.onNodeWithTag("profile-menu").assertIsDisplayed()
        rule.onNodeWithTag("home-content", useUnmergedTree = true).assertIsDisplayed()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("profile-menu").assertDoesNotExist()
        cover.assertIsFocused()
        rule.onNodeWithTag("header-profile-button").performClick()
        pad(KeyEvent.KEYCODE_BUTTON_START)
        rule.onNodeWithTag("profile-menu").assertDoesNotExist()
        rule.onNodeWithTag("header-profile-button").performClick()
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        rule.runOnIdle { assertFalse(controller.gameMenu) }
        waitFocused("control-home")
        device.waitForIdle()
        // A tap above the band, on the dimmed home, closes the control center.
        device.click(device.displayWidth / 2, device.displayHeight / 8)
        rule.waitUntil(5_000) { !controller.menuOpen }
        rule.waitForIdle()
        rule.onNodeWithTag("profile-menu").assertDoesNotExist()
        assertEquals("tree-2", controller.homeGame)
        assertEquals(Route.Home, controller.nav.current)
    }

    @Test
    fun controlCenterIconsAreReachableAndOpenTheirCards() {
        show(Section.USER)
        assertControlCenterFits()
        screenshot("control-center-landscape")
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("control-account").assertIsFocused()
        repeat(3) { pad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        rule.onNodeWithTag("control-sync").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithTag("profile-sync").assertIsDisplayed()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitFocused("control-sync")
        repeat(3) { pad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        rule.onNodeWithTag("control-settings").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.waitUntil(5_000) { controller.nav.current == Route.Settings() }
        rule.onNodeWithTag("profile-menu").assertDoesNotExist()
    }

    @Test
    fun profileGridFitsPortraitAndRemainsReachableWithLargeText() {
        show(Section.USER, largeText = true, portrait = true)
        assertProfileFitsWithoutScrolling()
        val settings = rule.onNodeWithTag("profile-shortcut-root").fetchSemanticsNode().boundsInRoot
        val controls = rule.onNodeWithTag("profile-shortcut-controls").fetchSemanticsNode().boundsInRoot
        if (controls.left > settings.right) {
            pad(KeyEvent.KEYCODE_DPAD_RIGHT)
            pad(KeyEvent.KEYCODE_DPAD_DOWN)
        } else {
            repeat(3) { pad(KeyEvent.KEYCODE_DPAD_DOWN) }
        }
        rule.onNodeWithTag("profile-shortcut-storage").assertIsDisplayed().assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("profile-sync").assertIsDisplayed().assertIsFocused()
        screenshot("profile-grid-large-text-portrait")
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("profile-shortcut-storage").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(Route.Settings(ovh.verbois.retrohub.ui.nav.SettingsPage.STORAGE), controller.nav.current)
    }

    @Test
    fun controlCenterFitsLandscapeWithAControllerAndLargeText() {
        show(Section.USER, largeText = true)
        assertControlCenterFits()
        repeat(7) { pad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        rule.onNodeWithTag("control-settings").assertIsDisplayed().assertIsFocused()
        screenshot("control-center-large-text-landscape")
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithTag("control-downloads").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithText("Stockage").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { controller.nav.current == Route.Settings(SettingsPage.STORAGE) }
    }

    private fun assertControlCenterFits() {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val minimum = 48f * instrumentation.targetContext.resources.displayMetrics.density - 1f
        listOf("home", "account", "notices", "downloads", "sync", "controller", "sound", "settings", "quit").forEach { item ->
            val bounds = rule.onNodeWithTag("control-$item").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(
                "control-$item must fit on screen: $bounds in $root",
                bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom,
            )
            assertTrue("control-$item must retain a complete touch target", bounds.height >= minimum)
        }
    }

    private fun assertProfileFitsWithoutScrolling() {
        val panel = rule.onNodeWithTag("profile-menu").fetchSemanticsNode().boundsInRoot
        val tags =
            listOf(
                "profile-account",
                "profile-shortcut-root",
                "profile-shortcut-controls",
                "profile-shortcut-consoles",
                "profile-shortcut-storage",
                "profile-sync",
            )
        tags.forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag must fit inside the panel: $bounds in $panel",
                bounds.left >= panel.left && bounds.top >= panel.top && bounds.right <= panel.right && bounds.bottom <= panel.bottom,
            )
            assertTrue("$tag must retain a complete touch target", bounds.height >= 48f * instrumentation.targetContext.resources.displayMetrics.density - 1f)
        }
        rule.onAllNodes(
            hasAnyAncestor(hasTestTag("profile-menu")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            useUnmergedTree = true,
        ).assertCountEquals(0)
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
