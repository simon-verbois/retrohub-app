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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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
import ovh.verbois.retrohub.ui.components.ConfirmDialog
import ovh.verbois.retrohub.ui.home.HomeScreen
import ovh.verbois.retrohub.ui.library.DownloadsScreen
import ovh.verbois.retrohub.ui.nav.Route
import ovh.verbois.retrohub.ui.nav.Section
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.settings.SettingsScreen
import ovh.verbois.retrohub.ui.shell.ConsoleShell
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
        runBlocking { container.database.games().clear() }
        UiDevice.getInstance(instrumentation).setOrientationLandscape()
    }

    private fun show(section: Section = Section.HOME, largeText: Boolean = false, portrait: Boolean = false) {
        val device = UiDevice.getInstance(instrumentation)
        if (portrait) device.setOrientationNatural() else device.setOrientationLandscape()
        instrumentation.waitForIdleSync()
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
                    it.portrait = portrait
                    it.nav.section(section)
                }
            }
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                RetroHubTheme {
                    Box(Modifier.fillMaxSize().background(Palette.Background))
                    ConsoleShell(ui, Account("https://example.invalid", userId = "test", username = "Test", deviceId = "test", deviceName = "Test")) { route ->
                        when (route) {
                            Route.Home -> HomeScreen(ui)
                            Route.Downloads -> DownloadsScreen(ui)
                            Route.Favorites -> ovh.verbois.retrohub.ui.library.LibraryScreen(ui, null, true)
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
        if (section != Section.USER) rule.waitUntil(5_000) { rule.activity.hasWindowFocus() }
        rule.waitUntil(5_000) {
            val node = if (section ==
                Section.HOME
            ) {
                rule.onNodeWithTag("home-game-tree-0", useUnmergedTree = true)
            } else {
                rule.onNodeWithTag("profile-shortcut-root")
            }
            runCatching { node.fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
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
    }

    @Test
    fun homeFitsTheViewportWithoutVerticalScrollingEvenWithLargerText() {
        show(largeText = true)
        val home = rule.onNodeWithTag("home-content", useUnmergedTree = true).fetchSemanticsNode()
        assertFalse(home.config.contains(SemanticsProperties.VerticalScrollAxisRange))
        val action = rule.onNodeWithTag("game-primary-action").fetchSemanticsNode().boundsInRoot
        assertTrue(action.top >= home.boundsInRoot.top)
        assertTrue(action.bottom <= home.boundsInRoot.bottom)
        rule.onNodeWithTag("game-primary-action").assertIsDisplayed()
    }

    @Test
    fun headerSearchKeepsThePageAndSupportsResultsAndFocusDismissal() {
        show()
        val homeBefore = rule.onNodeWithTag("home-content", useUnmergedTree = true).fetchSemanticsNode()
        val selectedBefore = controller.homeGame
        val profile = rule.onNodeWithTag("header-profile-avatar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val status = rule.onNodeWithTag("header-status").fetchSemanticsNode().boundsInRoot
        val search = rule.onNodeWithTag("header-search-bubble", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(status.height, profile.height, 1f)
        assertEquals(profile.width, profile.height, 1f)
        assertEquals(status.height, search.height, 1f)
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        rule.onNodeWithTag("header-search-field").assertIsFocused()
        assertEquals(Route.Home, controller.nav.contentRoute)
        val homeAfter = rule.onNodeWithTag("home-content", useUnmergedTree = true).fetchSemanticsNode()
        // The IME may reduce the window height; search must not recreate or move the page header.
        assertEquals(homeBefore.id, homeAfter.id)
        assertEquals(homeBefore.boundsInRoot.top, homeAfter.boundsInRoot.top, 1f)
        assertEquals(homeBefore.boundsInRoot.left, homeAfter.boundsInRoot.left, 1f)
        assertEquals(selectedBefore, controller.homeGame)
        rule.onNodeWithTag("search-results").assertDoesNotExist()
        rule.onNodeWithTag("header-search-field").performTextInput("Jeu 3")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("search-result-tree-3").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("search-result-tree-3").assertIsDisplayed()
        val field = rule.onNodeWithTag("header-search-field").fetchSemanticsNode().boundsInRoot
        val dropdown = rule.onNodeWithTag("search-results").fetchSemanticsNode().boundsInRoot
        assertEquals(field.left, dropdown.left, 1f)
        assertEquals(field.width, dropdown.width, 1f)
        assertTrue(dropdown.top >= field.bottom)
        screenshot("header-search-landscape")
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("search-result-tree-3").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
        rule.onNodeWithTag("home-content", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(Route.Home, controller.nav.current)
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        rule.onNodeWithTag("home-game-tree-0", useUnmergedTree = true).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.waitUntil(5_000) { !controller.searching }
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
    }

    @Test
    fun portraitUsesOneSectionDropdownInsteadOfTheAppNameAndTabRow() {
        show(portrait = true)
        rule.onNodeWithText("RetroHub").assertDoesNotExist()
        rule.onNodeWithTag("section-menu").assertIsDisplayed().performClick()
        rule.onNodeWithText("Favoris").assertIsDisplayed()
        rule.onNodeWithText("Téléchargés").assertIsDisplayed()
        rule.onNodeWithText("Catalogue").assertIsDisplayed()
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
    fun emptyHomeAndFavoritesKeepAValidControllerDestination() {
        show()
        runBlocking { container.database.games().clear() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Actualiser").fetchSemanticsNodes().isNotEmpty() }
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithText("Favoris").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
        pad(KeyEvent.KEYCODE_BUTTON_L1)
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithText("Actualiser").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Accueil").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Actualiser").assertIsFocused()
    }

    @Test
    fun homeRestoresTheSelectedCoverAfterSettingsAndBack() {
        show()
        rule.onNodeWithTag("home-game-tree-2", useUnmergedTree = true).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pad(KeyEvent.KEYCODE_BUTTON_SELECT)
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithContentDescription("Retour").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.waitUntil(5_000) {
            runCatching {
                rule.onNodeWithTag("home-game-tree-2", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Focused]
            }.getOrDefault(false)
        }
        assertEquals("tree-2", controller.homeGame)
    }

    @Test
    fun choosingAnOffscreenGameDoesNotRestoreAnotherCoversFocus() {
        show()
        rule.runOnIdle { controller.nav.go(Route.Settings()) }
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithContentDescription("Retour").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
        rule.runOnIdle { controller.showGame("tree-7") }
        rule.waitUntil(5_000) {
            runCatching {
                rule.onNodeWithTag("home-game-tree-7", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Focused]
            }.getOrDefault(false)
        }
        assertEquals("tree-7", controller.homeGame)
    }

    @Test
    fun settingsPagesKeepControllerFocusVisibleAndReturnToTheirHeader() {
        show()
        checkSettingsTraversal(
            listOf(
                SettingsPage.ROOT, SettingsPage.APPEARANCE, SettingsPage.DISPLAY, SettingsPage.CONTROLS,
                SettingsPage.AUDIO, SettingsPage.PERFORMANCE, SettingsPage.STORAGE, SettingsPage.PARENTAL,
                SettingsPage.CONSOLES, SettingsPage.CONSOLE, SettingsPage.REMAP, SettingsPage.GAME, SettingsPage.ABOUT, SettingsPage.CHANGELOG,
            ),
        )
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
    fun downloadsKeepStorageAsTheFirstCoverEvenWithoutGames() {
        show()
        rule.runOnIdle { controller.nav.section(Section.DOWNLOADS) }
        rule.onNodeWithTag("downloads-grid").assertIsDisplayed()
        rule.onNodeWithTag("downloads-storage").assertIsDisplayed()
        rule.onNodeWithText("Stockage").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("downloads-storage").fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
        }
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Téléchargés").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("downloads-storage").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithText("Téléchargements et stockage").assertIsDisplayed()
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
    fun profileKeepsTheHomeVisibleAndClosesWithBackStartOrOutsideClick() {
        show()
        val cover = rule.onNodeWithTag("home-game-tree-2", useUnmergedTree = true)
        cover.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pad(KeyEvent.KEYCODE_BUTTON_START)
        rule.onNodeWithTag("profile-menu").assertIsDisplayed()
        rule.onNodeWithTag("home-content", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Profil").assertDoesNotExist()
        rule.onNodeWithContentDescription("Fermer le profil").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("profile-menu").assertDoesNotExist()
        cover.assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_START)
        pad(KeyEvent.KEYCODE_BUTTON_START)
        rule.onNodeWithTag("profile-menu").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_START)
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        device.click(device.displayWidth / 10, device.displayHeight / 2)
        rule.waitForIdle()
        rule.onNodeWithTag("profile-menu").assertDoesNotExist()
        assertEquals("tree-2", controller.homeGame)
        assertEquals(Route.Home, controller.nav.current)
    }

    @Test
    fun profileGridLinksTheAccountShortcutsAndSyncWithDirectionalFocus() {
        show(Section.USER)
        assertProfileFitsWithoutScrolling()
        val settings = rule.onNodeWithTag("profile-shortcut-root").fetchSemanticsNode().boundsInRoot
        val controls = rule.onNodeWithTag("profile-shortcut-controls").fetchSemanticsNode().boundsInRoot
        val consoles = rule.onNodeWithTag("profile-shortcut-consoles").fetchSemanticsNode().boundsInRoot
        assertTrue(controls.left > settings.right)
        assertEquals(settings.top, controls.top, 1f)
        assertTrue(consoles.top >= settings.bottom)
        screenshot("profile-grid-landscape")
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("profile-shortcut-controls").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("profile-shortcut-storage").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("profile-sync").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("profile-shortcut-storage").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithTag("profile-shortcut-consoles").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("profile-shortcut-root").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithTag("profile-account").assertIsFocused()
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("profile-shortcut-root").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(Route.Settings(), controller.nav.current)
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
    fun profileFitsLandscapeWithoutScrollingWithAControllerAndLargeText() {
        show(Section.USER, largeText = true)
        assertProfileFitsWithoutScrolling()
        rule.onNodeWithTag("profile-shortcut-root").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val settings = rule.onNodeWithTag("profile-shortcut-root").fetchSemanticsNode().boundsInRoot
        val storage = rule.onNodeWithTag("profile-shortcut-storage").fetchSemanticsNode().boundsInRoot
        if (kotlin.math.abs(storage.top - settings.top) < 1f) {
            repeat(3) { pad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        } else {
            pad(KeyEvent.KEYCODE_DPAD_RIGHT)
            pad(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        rule.onNodeWithTag("profile-shortcut-storage").assertIsDisplayed().assertIsFocused()
        screenshot("profile-grid-large-text-landscape")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        rule.onNodeWithText("Téléchargements et stockage").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retour").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val cache = rule.onNodeWithText("Vider le cache des jaquettes")
        repeat(12) {
            val focused = runCatching { cache.fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)
            if (!focused) pad(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        cache.assertIsDisplayed().assertIsFocused()
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
