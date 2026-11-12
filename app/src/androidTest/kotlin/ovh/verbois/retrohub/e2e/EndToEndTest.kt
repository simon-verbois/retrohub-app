package ovh.verbois.retrohub.e2e

import android.graphics.Bitmap
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.MainActivity
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.account.AuthState
import ovh.verbois.retrohub.api.FEATURE_LIBRARY_EVENTS
import ovh.verbois.retrohub.emulation.ThemeMode
import ovh.verbois.retrohub.emulation.TouchPreset
import ovh.verbois.retrohub.game.GameActivity
import ovh.verbois.retrohub.ui.theme.Palette
import java.io.File

/**
 * Parcours complet sur le vrai backend (scripts/e2e-backend.py, adb reverse tcp:8011) :
 * appairage, catalogue, téléchargement vérifié, jeu NES qui rend des images, sauvegardes avec
 * appareil et date, conflit, sessions hors ligne puis renvoi (duplicate), révocation (401).
 * Lancé par scripts/test.sh e2e sur une application aux données effacées.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val serverUrl = InstrumentationRegistry.getArguments().getString("retrohubServer") ?: "http://127.0.0.1:8011"
    private val backend by lazy { Backend(serverUrl) }
    private val app get() = instrumentation.targetContext.applicationContext as RetroHubApp
    private val container get() = app.container
    private val deviceName = "Pixel e2e"

    @After
    fun restoreNetwork() {
        shell("svc wifi enable")
        shell("svc data enable")
    }

    @Test
    fun fullJourney() {
        val suffix = (System.currentTimeMillis() % 100_000).toString()
        val title = "Rétro Test $suffix"
        val rom = Backend.nesRom(suffix.hashCode())
        val gameId = backend.importRom("$title (FR).nes", rom)
        device.setOrientationLandscape()

        pairWithCode(backend.pairingCode())
        finishSetup()
        checkSettingsAndBackGesture()
        checkAccountAvatars()

        // Catalogue : le jeu importé arrive dans l’interface console.
        waitUntil(30_000, "jeu dans le catalogue") { container.library.games.value.any { it.title == title } }
        waitText("Téléchargés")
        screenshot("home-landscape")
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        waitText("Aucun favori pour l’instant")
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        rule.onNodeWithTag("downloads-storage").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        waitFocused("Filtres")
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitFocused("Console")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithText("Console").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitFocused("Filtres")
        waitText("Chercher dans le catalogue")
        rule.onNode(hasSetTextAction()).performTextInput(title)
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("catalogue-game-$gameId").fetchSemanticsNodes().isNotEmpty() }
        screenshot("catalogue-landscape")
        rule.onNodeWithTag("catalogue-game-$gameId").performClick()
        waitText("Télécharger sur le téléphone")
        screenshot("catalogue-game-landscape")
        click("Télécharger sur le téléphone")
        waitText("Télécharger cette version ?")
        click("Annuler")
        assertTrue(container.downloads.states.value[gameId] == null)
        device.pressBack()
        waitText("Chercher dans le catalogue")
        device.pressBack()
        waitText("Téléchargés")

        // Changes imported while the app stays active arrive without a lifecycle restart.
        val liveEvents = container.serverStatus.value?.features?.contains(FEATURE_LIBRARY_EVENTS) == true
        if (liveEvents) {
            waitUntil(10_000, "flux de bibliothèque connecté") {
                rule.mainClock.advanceTimeBy(250)
                container.foregroundLibrary.streaming.value
            }
        }
        val liveTitle = "Ajout en direct $suffix"
        backend.importRom("$liveTitle (FR).nes", Backend.nesRom(suffix.hashCode() + 1))
        waitUntil(if (liveEvents) 8_000 else 30_000, "import reçu sans relancer l’application") {
            // Keep virtual time below the polling interval when the real stream is available.
            rule.mainClock.advanceTimeBy(if (liveEvents) 250 else 1_000)
            container.library.games.value.any { it.title == liveTitle }
        }
        if (liveEvents) assertTrue(container.foregroundLibrary.streaming.value)
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        waitText("Aucun favori pour l’instant")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("home-games").performScrollToNode(hasContentDescription("Toute la bibliothèque"))
        clickDescription("Toute la bibliothèque")
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Filtrer et trier").fetchSemanticsNodes().isNotEmpty() }
        screenshot("library-landscape")
        // Recherche (bouton Y) : le jeu du test, quel que soit le contenu de la bibliothèque.
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        val search = rule.onNode(hasSetTextAction())
        search.performTextInput(title)
        waitText(title)
        rule.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("search-result-$gameId").fetchSemanticsNodes().isNotEmpty() }
        screenshot("search-dropdown-landscape")
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithTag("search-result-$gameId").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("header-search-field").assertDoesNotExist()
        rule.onNodeWithText("Accueil").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        rule.onNode(hasSetTextAction()).performTextInput(title)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("search-result-$gameId").fetchSemanticsNodes().isNotEmpty() }
        search.performImeAction()

        // Le jeu est choisi sur l’accueil ; toucher sa tuile lance l’action : ici le téléchargement,
        // vérifié par son SHA-256.
        waitText("Télécharger ·", substring = true)
        rule.onNodeWithTag("game-primary-action").performClick()
        waitText("Télécharger ce jeu ?")
        click("Annuler")
        assertTrue(container.downloads.states.value[gameId] == null)
        rule.onNodeWithTag("game-primary-action").performClick()
        click("Télécharger")
        waitUntil(60_000, "jeu téléchargé") { runBlocking { container.database.roms().find(gameId) } != null }
        val local = runBlocking { container.database.roms().find(gameId) }!!
        val expected = backend.game(gameId)["rom"]!!.jsonObject["sha256"]!!.jsonPrimitive.content
        assertEquals(expected, Backend.sha256(File(local.path).readBytes()))
        assertEquals(Backend.sha256(rom), expected)
        rule.onNodeWithTag("home-favorite").performClick()
        waitUntil(10_000, "favori enregistré") { backend.game(gameId)["favorite"]!!.jsonPrimitive.content == "true" }
        rule.onNodeWithTag("home-favorite").assertIsSelected()
        click("Favoris")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("library-game-$gameId").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("library-game-$gameId").performClick()
        rule.onNodeWithTag("home-favorite").assertIsSelected().performClick()
        waitUntil(10_000, "favori retiré") { backend.game(gameId)["favorite"]!!.jsonPrimitive.content == "false" }
        rule.onNodeWithTag("home-favorite").assertIsNotSelected()
        click("Téléchargés")
        rule.onNodeWithTag("downloads-storage").assertExists()
        rule.onNodeWithTag("download-game-$gameId").assertExists()
        screenshot("downloads-landscape")
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithTag("download-game-$gameId").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_X)
        waitFocused("Supprimer du téléphone")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitFocused("Annuler")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        rule.onNodeWithTag("download-game-$gameId").assertIsFocused()
        rule.onNodeWithTag("download-game-$gameId").performClick()
        screenshot("selected-landscape")
        pad(KeyEvent.KEYCODE_BUTTON_SELECT)
        waitFocused("Retour")
        waitText("Paramètres")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitText("Jouer")
        instrumentation.setInTouchMode(true)
        pad(KeyEvent.KEYCODE_BUTTON_START)
        waitFocused("Paramètres")
        screenshot("profile-menu-landscape")
        val originalTheme = container.settings.settings.value.theme
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            runBlocking { container.settings.updateNow { it.copy(theme = mode) } }
            rule.waitUntil(5_000) { Palette.scheme.dark == (mode == ThemeMode.DARK) }
            screenshot("profile-menu-${mode.name.lowercase()}-landscape")
        }
        runBlocking { container.settings.updateNow { it.copy(theme = originalTheme) } }
        rule.waitForIdle()
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitGone("Mon compte")
        clickDescription("Options du jeu")
        click("Options du jeu")
        waitText("Rotation de l’image")
        screenshot("game-options-landscape")
        device.setOrientationNatural()
        screenshot("game-options-portrait")
        device.pressBack()
        waitText("Jouer")

        // Selecting the cover never starts a game; the explicit action does.
        device.setOrientationNatural()
        screenshot("selected-portrait")
        clickDescription(title)
        assertTrue(GameActivity.current?.get() == null)
        // Lancement du cœur NES : la vue émulateur rend des images. Au moins 10 s, pour compter.
        rule.onNodeWithTag("game-primary-action").performClick()
        waitFrames(120)
        if (liveEvents) {
            waitUntil(5_000, "flux suspendu pendant le jeu") { !container.foregroundLibrary.streaming.value }
        }
        device.setOrientationLandscape()
        Thread.sleep(11_000)
        screenshot("game-landscape")
        checkTouchPresets()
        // Rotation, advanced options and background saves also run against the optimized build.
        device.setOrientationNatural()
        waitFrames(180)
        screenshot("game-portrait")
        device.setOrientationLandscape()
        pad(KeyEvent.KEYCODE_MENU)
        waitFocused("Reprendre")
        focusDownTo("Image et écrans")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitFocused("Retour")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitFocused("Reprendre")
        focusDownTo("Options de l’émulateur")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitFocused("Retour")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitFocused("Reprendre")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitFrames((GameActivity.current?.get()?.frames?.value ?: 0) + 10)
        val gameIntent = android.content.Intent(GameActivity.current!!.get()!!.intent)
        device.pressHome()
        waitUntil(15_000, "sauvegarde au passage en arrière-plan") {
            runBlocking { container.saveStore.find(gameId, "state", 0)?.localSha != null }
        }
        instrumentation.runOnMainSync { rule.activity.startActivity(gameIntent) }
        waitText("Reprendre")
        click("Reprendre")

        // Une partie arrive du web pendant qu’on joue : conflit au retour.
        backend.putSave(gameId, "state", 1, "partie depuis le web".encodeToByteArray())
        saveStateInSlot(1)
        quitGame()

        // La sauvegarde automatique part avec l’appareil et l’heure de la partie.
        waitUntil(60_000, "sauvegarde automatique sur le serveur") {
            backend.saves(gameId).any { it["kind"]!!.jsonPrimitive.content == "state" && it["slot"]!!.jsonPrimitive.content == "0" }
        }
        val auto = backend.saves(gameId).first {
            it["kind"]!!.jsonPrimitive.content == "state" && it["slot"]!!.jsonPrimitive.content == "0"
        }
        assertEquals(deviceName, auto["device"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(auto["played_at"]!!.jsonPrimitive.content.isNotBlank())

        // Conflit : les deux versions côte à côte, le joueur garde celle du téléphone.
        waitUntil(60_000, "conflit détecté") { runBlocking { container.saveStore.find(gameId, "state", 1)?.inConflict == true } }
        rule.onNodeWithTag("game-primary-action").performClick()
        waitText("Quelle partie reprendre ?", timeout = 20_000)
        click("Choisir")
        waitText("Sur ce téléphone")
        waitText("Sur le serveur")
        screenshot("conflict-landscape")
        rule.onAllNodesWithText("Garder celle-ci")[0].performClick()
        waitUntil(30_000, "choix envoyé") {
            runBlocking {
                container.saveStore.find(gameId, "state", 1)?.let {
                    !it.inConflict &&
                        !it.pendingUpload
                } ==
                    true
            }
        }
        val kept = File(container.saveStore.file(gameId, "state", 1).toString()).readBytes()
        val slot1 = backend.saves(gameId).first {
            it["kind"]!!.jsonPrimitive.content == "state" && it["slot"]!!.jsonPrimitive.content == "1"
        }
        assertEquals(Backend.sha256(kept), slot1["sha256"]!!.jsonPrimitive.content)
        assertEquals(deviceName, slot1["device"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        pad(KeyEvent.KEYCODE_BUTTON_B)

        // Sessions hors ligne : réseau coupé, la session attend ; réseau rétabli, la file se vide.
        val playedBefore = backend.game(gameId)["played_seconds"]!!.jsonPrimitive.content.toLong()
        shell("svc wifi disable")
        shell("svc data disable")
        waitUntil(30_000, "hors ligne") { !container.platform.online.value }
        // Déjà joué : la carte « Reprendre » apparaît, et le jeu se lance sans réseau.
        waitText("Reprendre")
        rule.onNodeWithTag("game-primary-action").performClick()
        waitFrames(60)
        Thread.sleep(12_000)
        quitGame()
        waitUntil(15_000, "session en file") { runBlocking { container.database.sessions().count() } >= 1 }
        val queued = runBlocking { container.database.sessions().oldest(10) }
        Thread.sleep(3_000)
        assertEquals(playedBefore, backend.game(gameId)["played_seconds"]!!.jsonPrimitive.content.toLong())
        shell("svc wifi enable")
        shell("svc data enable")
        waitUntil(90_000, "file des sessions vidée") { runBlocking { container.database.sessions().count() } == 0 }
        assertTrue(backend.game(gameId)["played_seconds"]!!.jsonPrimitive.content.toLong() >= playedBefore + 10)
        val again = queued.first()
        val duplicate =
            backend.postSessions(
                """{"sessions":[{"client_id":"${again.clientId}","game_id":"${again.gameId}","started_at":"${again.startedAt}","ended_at":"${again.endedAt}","played_seconds":${again.playedSeconds}}]}""",
            )
        assertEquals("duplicate", duplicate["results"]!!.jsonArray.single().jsonObject["status"]!!.jsonPrimitive.content)

        // Révocation sur le web : le prochain appel reçoit 401, l’application revient à la connexion.
        val id = backend.devices().first { it["name"]!!.jsonPrimitive.content == deviceName }["id"]!!.jsonPrimitive.content
        backend.revoke(id)
        runBlocking { container.sync.syncNow() }
        waitUntil(30_000, "retour à la connexion") { container.account.auth.value is AuthState.SignedOut }
        waitText("Relier ce téléphone")
        waitText("n’est plus connecté", substring = true)
        screenshot("signed-out-landscape")
    }

    // ---- Étapes -------------------------------------------------------------------------------

    private fun pairWithCode(code: String) {
        waitText("Commencer")
        device.setOrientationNatural()
        screenshot("welcome-portrait")
        click("Commencer")
        device.setOrientationLandscape()
        rule.waitForIdle()
        Thread.sleep(350)
        val address = rule.onNode(hasSetTextAction() and hasText("Adresse du serveur"))
        address.performTextInput(serverUrl)
        address.performImeAction()
        waitText("Relier ce téléphone")
        // Denial leaves manual entry available; then exercise the registered camera-result flow.
        click("Scanner un code QR")
        val deny = device.wait(
            androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("com.android.permissioncontroller:id/permission_deny_button")),
            5_000,
        )
        if (deny != null) {
            deny.click()
            waitText("Autorisez la caméra", substring = true)
        }
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, android.Manifest.permission.CAMERA)
        val result = android.content.Intent().putExtra("SCAN_RESULT", code).putExtra("SCAN_RESULT_FORMAT", "QR_CODE")
        val monitor = instrumentation.addMonitor(
            ovh.verbois.retrohub.PairingScanActivity::class.java.name,
            android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, result),
            true,
        )
        try {
            click("Scanner un code QR")
            rule.waitUntil(10_000) { rule.onAllNodesWithText(code).fetchSemanticsNodes().isNotEmpty() }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        val name = rule.onNode(hasSetTextAction() and hasText("Nom de cet appareil"))
        name.performTextClearance()
        name.performTextInput(deviceName)
        click("Relier cet appareil")
    }

    private fun finishSetup() {
        waitText("Bienvenue,", timeout = 20_000, substring = true)
        device.setOrientationNatural()
        screenshot("setup-play-portrait")
        click("Continuer")
        waitText("Performances")
        click("Continuer")
        waitText("Confort")
        screenshot("setup-comfort-portrait")
        click("Continuer")
        waitText("Découvrir ma bibliothèque")
        screenshot("setup-ready-portrait")
        click("Découvrir ma bibliothèque")
        device.setOrientationLandscape()
    }

    private fun checkSettingsAndBackGesture() {
        waitText("Téléchargés")
        pad(KeyEvent.KEYCODE_BUTTON_START)
        click("Paramètres")
        click("Réglages des consoles")
        click("Nintendo DS")
        device.setOrientationNatural()
        screenshot("settings-ds-portrait")
        shell("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural")
        Thread.sleep(500)
        immersiveBackGesture("Consoles")
        immersiveBackGesture("Paramètres")
        click("Apparence")
        click("Thème")
        waitText("Conseillé")
        screenshot("appearance-portrait")
        device.pressBack()
        click("Affichage")
        click("Utiliser tout l’écran")
        assertTrue(container.settings.settings.value.useDisplayCutout)
        device.pressBack()
        click("Commandes")
        rule.onNodeWithText("Compacte").performScrollTo()
        screenshot("touch-presets-settings-portrait")
        device.pressBack()
        device.pressBack()
        click("Accueil")
        waitText("Téléchargés")
        device.setOrientationLandscape()
    }

    private fun checkAccountAvatars() {
        if (container.serverStatus.value?.features?.contains("account_avatars") != true) return
        val catalogue = backend.avatarCatalogue()
        val defaultId = catalogue["default_avatar"]!!.jsonObject["id"]!!.jsonPrimitive.content
        val target = catalogue["avatars"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content != defaultId }
        val targetId = target["id"]!!.jsonPrimitive.content
        pad(KeyEvent.KEYCODE_BUTTON_START)
        click("Mon compte")
        click("Photo de profil")
        waitText("Par défaut")
        rule.onNodeWithTag("account-avatar-$targetId").performScrollTo().performClick()
        screenshot("account-avatars-landscape")
        click("Enregistrer")
        rule.waitUntil(10_000) { container.account.account?.avatar?.id == targetId }
        waitGone("Enregistrer")
        assertEquals(targetId, backend.accountAvatar()["id"]!!.jsonPrimitive.content)
        click("Photo de profil")
        waitText("Par défaut")
        rule.onNodeWithTag("account-avatar-default").performScrollTo().performClick()
        click("Enregistrer")
        rule.waitUntil(10_000) { container.account.account?.avatar?.id == defaultId }
        waitGone("Enregistrer")
        assertEquals(defaultId, backend.accountAvatar()["id"]!!.jsonPrimitive.content)
        pad(KeyEvent.KEYCODE_BUTTON_B)
        click("Accueil")
    }

    private fun saveStateInSlot(slot: Int) {
        pad(KeyEvent.KEYCODE_MENU)
        waitFocused("Reprendre")
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitText("Emplacement $slot")
        waitFocused("Retour")
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(slot - 1) { pad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        rule.onNodeWithText("Emplacement $slot").assertIsFocused()
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitGone("Choisissez un emplacement")
        Thread.sleep(500)
    }

    private fun checkTouchPresets() {
        for (preset in listOf(TouchPreset.OVERLAY, TouchPreset.COMPACT, TouchPreset.COMFORT)) {
            val current = container.settings.settings.value.console("nes").touchPreset ?: container.settings.settings.value.touchPreset
            pad(KeyEvent.KEYCODE_MENU)
            waitFocused("Reprendre")
            focusDownTo("Image et écrans")
            pad(KeyEvent.KEYCODE_BUTTON_A)
            waitFocused("Retour")
            focusDownTo("Disposition tactile")
            pad(KeyEvent.KEYCODE_BUTTON_A)
            val delta = preset.ordinal - current.ordinal
            repeat(kotlin.math.abs(delta)) { pad(if (delta > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP) }
            pad(KeyEvent.KEYCODE_BUTTON_A)
            rule.waitUntil(5_000) { container.settings.settings.value.console("nes").touchPreset == preset }
            pad(KeyEvent.KEYCODE_BUTTON_B)
            waitFocused("Reprendre")
            pad(KeyEvent.KEYCODE_BUTTON_B)
            waitFrames((GameActivity.current?.get()?.frames?.value ?: 0) + 10)
            val dock = rule.onNodeWithTag("game-utility-dock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(dock.left > device.displayWidth * 0.7f && dock.top < device.displayHeight * 0.15f)
            screenshot("touch-${preset.name.lowercase()}-landscape")
        }
    }

    private fun quitGame() {
        pad(KeyEvent.KEYCODE_MENU)
        waitFocused("Reprendre")
        focusDownTo("Quitter")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        // Confirmation : le bouton de la fenêtre est le dernier « Quitter » affiché.
        waitText("Quitter la partie ?")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitGone("Quitter la partie ?")
        waitFocused("Quitter")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitText("Quitter la partie ?")
        rule.waitUntil(5_000) {
            val nodes = rule.onAllNodesWithText("Quitter").fetchSemanticsNodes()
            nodes.lastOrNull()?.let { runCatching { it.config[SemanticsProperties.Focused] }.getOrDefault(false) } == true
        }
        pad(KeyEvent.KEYCODE_BUTTON_A)
        waitUntil(20_000, "fin de la partie") { GameActivity.current?.get()?.isDestroyed != false }
        rule.waitForIdle()
    }

    private fun waitFrames(count: Long) = waitUntil(30_000, "images du cœur") {
        // Game content is composed after asynchronous ROM preparation. Keep the test frame clock
        // advancing until that new activity has drawn; native frames are still counted separately.
        rule.waitForIdle()
        (GameActivity.current?.get()?.frames?.value ?: 0) >= count
    }

    // ---- Outils -------------------------------------------------------------------------------

    private fun waitText(text: String, timeout: Long = 15_000, substring: Boolean = false) {
        try {
            rule.waitUntil(timeout) {
                try {
                    rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                } catch (_: IllegalStateException) {
                    false // Activity transitions can temporarily have no Compose root.
                }
            }
        } catch (error: Throwable) {
            screenshot("echec-attente")
            throw AssertionError("Texte absent : « $text »", error)
        }
    }

    private fun immersiveBackGesture(expected: String) {
        // Android can consume the first edge swipe to reveal transient system bars.
        repeat(3) {
            device.swipe(1, device.displayHeight / 2, device.displayWidth / 3, device.displayHeight / 2, 25)
            try {
                rule.waitUntil(1500) { rule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
                return
            } catch (_: androidx.compose.ui.test.ComposeTimeoutException) { }
        }
        waitText(expected)
    }

    private fun waitGone(text: String, substring: Boolean = false) = rule.waitUntil(15_000) {
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }

    private fun click(text: String, substring: Boolean = false) {
        waitText(text, substring = substring)
        val node = rule.onAllNodesWithText(text, substring = substring)[0]
        // Le clavier logiciel peut masquer le bouton : on le fait d’abord défiler à l’écran.
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun clickDescription(description: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription(description)[0].performClick()
        rule.waitForIdle()
    }

    private fun waitFocused(text: String) {
        rule.waitUntil(5_000) {
            runCatching {
                rule.onNode(hasText(text) or hasContentDescription(text)).fetchSemanticsNode().config[SemanticsProperties.Focused]
            }.getOrDefault(false)
        }
    }

    private fun focusDownTo(text: String) {
        repeat(14) {
            if (runCatching { rule.onNodeWithText(text).fetchSemanticsNode().config[SemanticsProperties.Focused] }.getOrDefault(false)) return
            pad(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        rule.onNodeWithText(text).assertIsFocused()
    }

    private fun waitUntil(timeout: Long, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(300)
        }
        val game = GameActivity.current?.get()
        android.util.Log.e(
            "RetroHubE2E",
            "Timeout $what: frames=${game?.frames?.value}, lifecycle=${game?.lifecycle?.currentState}, finishing=${game?.isFinishing}, destroyed=${game?.isDestroyed}",
        )
        screenshot("echec-attente")
        throw AssertionError("Délai dépassé : $what (frames=${game?.frames?.value}, lifecycle=${game?.lifecycle?.currentState})")
    }

    /** Un appui de manette (KeyEvent avec la source gamepad). */
    private fun pad(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(0, 0, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD))
        }
        rule.waitForIdle()
    }

    private fun shell(command: String) {
        val output = device.executeShellCommand(command)
        android.util.Log.i("RetroHubE2E", "$command → $output")
    }

    /** Capture d’écran, récupérée par scripts/test.sh dans build/screens/. */
    private fun screenshot(name: String) {
        rule.waitForIdle()
        Thread.sleep(600)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("unused")
    private val headings = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
}
