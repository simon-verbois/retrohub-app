package ovh.verbois.retrohub.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.data.GameEntity
import ovh.verbois.retrohub.data.LocalRomEntity
import ovh.verbois.retrohub.emulation.GameRotation
import ovh.verbois.retrohub.emulation.ThemeMode
import ovh.verbois.retrohub.library.DownloadState
import ovh.verbois.retrohub.library.Game
import ovh.verbois.retrohub.ui.components.ActionButtons
import ovh.verbois.retrohub.ui.components.ChoiceRow
import ovh.verbois.retrohub.ui.components.SettingsGroup
import ovh.verbois.retrohub.ui.home.GameTile
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.nav.SettingsPage
import ovh.verbois.retrohub.ui.onboarding.SetupWizard
import ovh.verbois.retrohub.ui.onboarding.WelcomeScreen
import ovh.verbois.retrohub.ui.settings.SettingsScreen
import ovh.verbois.retrohub.ui.theme.Palette
import ovh.verbois.retrohub.ui.theme.RetroHubTheme
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as RetroHubApp).container
    private val device = UiDevice.getInstance(instrumentation)

    @After
    fun restoreRotation() {
        runBlocking {
            container.database.roms().find("options-layout")?.let { container.database.roms().delete(it) }
            val keep = container.database.games().all().filter { it.id != "options-layout" }.map { it.id }
            container.database.games().deleteOthers(keep)
            container.settings.updateNow { it.copy(gameRotations = it.gameRotations - "options-layout") }
        }
        device.setOrientationLandscape()
        instrumentation.waitForIdleSync()
    }

    @Test
    fun welcomeIsCenteredOnSmallPortraitPhone() {
        device.setOrientationNatural()
        show(320f) { WelcomeScreen {} }
        rule.onNodeWithText("Bienvenue sur RetroHub").assertIsDisplayed()
        val title = rule.onNodeWithText("Bienvenue sur RetroHub").fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithText("Commencer").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(title.center.x - button.center.x) < 2f)
        screenshot("welcome-small-portrait")
        assertNoClippedText()
    }

    @Test
    fun setupKeepsActionsAndLongUsernameReadableAtDoubleTextSize() {
        device.setOrientationNatural()
        show(320f, fontScale = 2f) { SetupWizard(container, "Camille avec un nom de compte particulièrement long") }
        assertNoClippedText()
        rule.onNodeWithText("Continuer").assertIsDisplayed().performClick()
        rule.onNodeWithText("Continuer").assertIsDisplayed().performClick()
        rule.onNodeWithText("Données mobiles").performScrollTo().assertIsDisplayed()
        assertNoClippedText()
        screenshot("setup-comfort-large-text")
        rule.onNodeWithText("Continuer").assertIsDisplayed().performClick()
        rule.onNodeWithText("Découvrir ma bibliothèque").assertIsDisplayed()
        assertNoClippedText()
        screenshot("setup-ready-large-text")
    }

    @Test
    fun dsSettingsPreserveLabelsAndValuesOnSmallPhone() {
        device.setOrientationNatural()
        show(320f, fontScale = 1.3f) {
            val scope = rememberCoroutineScope()
            val controller = remember { UiController(container, scope) }
            SettingsScreen(controller, SettingsPage.CONSOLE, "nds")
        }
        rule.onNodeWithText("Orientation").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Comme l’application").assertIsDisplayed()
        screenshot("settings-ds-small-portrait")
        assertNoClippedText()
        rule.onNodeWithText("Langue de la console").performScrollTo().assertIsDisplayed()
        assertNoClippedText()
    }

    @Test
    fun gameOptionsUseColumnsAndExposeRotationWithoutHidingGameActions() {
        device.setOrientationLandscape()
        gameOptions(840f)
        val image = rule.onNodeWithText("Rotation de l’image").fetchSemanticsNode().boundsInRoot
        val saves = rule.onNodeWithText("Sauvegardes").fetchSemanticsNode().boundsInRoot
        assertTrue("Save settings should use the right column", saves.left > image.right)
        rule.onNodeWithText("Supprimer du téléphone").assertIsDisplayed()
        rule.onNodeWithContentDescription("Rotation de l’image : 90°").performClick().assertIsSelected()
        assertEquals(GameRotation.RIGHT, container.settings.settings.value.gameRotations["options-layout"])
        assertNoClippedText()
        screenshot("game-options-landscape")
    }

    @Test
    fun gameOptionsStackOnSmallDarkPhonesAndKeepLongTitlesReadable() {
        device.setOrientationNatural()
        gameOptions(320f, fontScale = 1.5f, dark = true)
        rule.onNodeWithContentDescription("Rotation de l’image : 270°").performScrollTo().performClick().assertIsSelected()
        assertNoClippedText()
        screenshot("game-options-small-portrait-dark")
        rule.onNodeWithText("Supprimer du téléphone").performScrollTo().assertIsDisplayed()
        assertNoClippedText()
    }

    private fun gameOptions(width: Float, fontScale: Float = 1f, dark: Boolean = false) {
        val title = "Programme d’entraînement cérébral du Dr Kawashima : Quel âge a votre cerveau ?"
        runBlocking {
            val game = LibraryGame("options-layout", "options-layout", title = title, console = "nds")
            container.database.games().upsert(
                listOf(GameEntity(game.id, game.playGameId, title, game.console, false, null, 0, 0, ApiJson.encodeToString(LibraryGame.serializer(), game))),
            )
            container.database.roms().upsert(
                LocalRomEntity(game.id, game.id, game.console, title, "unused-layout-fixture", "fixture", 1, "2026-11-12T00:00:00Z"),
            )
        }
        show(width, fontScale = fontScale, dark = dark) {
            val scope = rememberCoroutineScope()
            val controller = remember { UiController(container, scope) }
            SettingsScreen(controller, SettingsPage.GAME, "options-layout")
        }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Supprimer du téléphone").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun recommendedBadgeAndLongTranslatedActionsReflowInRtlDarkTheme() {
        device.setOrientationNatural()
        val option = "Dieselben Anzeigeeinstellungen wie auf diesem Telefon verwenden"
        val action = "Meine heruntergeladene Spielesammlung entdecken"
        show(320f, fontScale = 1.5f, rtl = true, dark = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsGroup {
                    ChoiceRow("Darstellung und Benutzeroberfläche", listOf(option), option, { it }, {}, icon = Lucide.Palette, recommended = option)
                }
                ActionButtons(action, {}, secondary = "Zur vorherigen Auswahl zurückkehren", primaryIcon = Lucide.Play)
            }
        }
        rule.onNodeWithText("Darstellung und Benutzeroberfläche").performClick()
        rule.onNodeWithText("Conseillé").performScrollTo().assertIsDisplayed()
        assertNoClippedText()
        rule.onNodeWithText(action).performScrollTo().assertIsDisplayed()
        assertNoClippedText()
        screenshot("long-labels-rtl-dark")
    }

    @Test
    fun downloadProgressStaysOnTheCoverAndUpdates() {
        device.setOrientationNatural()
        var progress by mutableStateOf(DownloadState.Running(25, 100))
        val game = Game(LibraryGame("cover", "cover", title = "Jeu", console = "nds"), null)
        show(320f, fontScale = 1.6f) {
            GameTile(game, true, 80.dp, 112.dp, {}, {}, Modifier.testTag("cover"), download = progress)
        }
        val overlay = rule.onNodeWithTag("game-download-progress", useUnmergedTree = true)
        val cover = rule.onNodeWithTag("cover", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bounds = overlay.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= cover.top)
        assertTrue(abs(bounds.bottom - cover.bottom) < 1f)
        assertEquals(0.25f, overlay.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
        rule.runOnIdle { progress = DownloadState.Running(75, 100) }
        assertEquals(0.75f, overlay.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
        screenshot("download-overlay")
    }

    @Test
    fun settingsRemainReadableOnWideLandscapeViewport() {
        device.setOrientationLandscape()
        show(840f) {
            val scope = rememberCoroutineScope()
            val controller = remember { UiController(container, scope) }
            SettingsScreen(controller, SettingsPage.APPEARANCE, null)
        }
        rule.onNodeWithText("Thème").performClick()
        rule.onNodeWithText("Conseillé").assertIsDisplayed()
        assertNoClippedText()
        screenshot("settings-wide-landscape")
    }

    private fun show(width: Float, fontScale: Float = 1f, rtl: Boolean = false, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val pixels = with(LocalDensity.current) { maxWidth.toPx() }
                CompositionLocalProvider(
                    LocalDensity provides Density(pixels / width, fontScale),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    RetroHubTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                        Box(Modifier.fillMaxSize().background(Palette.Background)) { content() }
                    }
                }
            }
        }
    }

    private fun assertNoClippedText() {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        rule.runOnIdle {
            nodes.forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.forEach { layout ->
                    // Intrinsic measurement can leave unused paragraph width; check the actual glyph lines.
                    assertFalse("Clipped height: ${layout.layoutInput.text.text}", layout.didOverflowHeight)
                    repeat(layout.lineCount) { line ->
                        val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
                        assertTrue(
                            "Clipped line: ${layout.layoutInput.text.text}; lineWidth=$lineWidth; width=${layout.size.width}",
                            lineWidth <= layout.size.width + 1f,
                        )
                        assertFalse("Ellipsis: ${layout.layoutInput.text.text}", layout.isLineEllipsized(line))
                    }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        Thread.sleep(350)
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "test-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { stream ->
            check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
}
