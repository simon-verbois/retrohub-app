package ovh.verbois.retrohub.e2e

import android.graphics.Bitmap
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
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
import ovh.verbois.retrohub.game.GameActivity
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

        // Catalogue : le jeu importé arrive dans l’interface console.
        waitUntil(30_000, "jeu dans le catalogue") { container.library.games.value.any { it.title == title } }
        waitText("Téléchargés")
        screenshot("home-landscape")

        // Navigation à la manette : RB change de section, B revient à l’accueil.
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        waitText("Toutes les consoles", substring = true)
        pad(KeyEvent.KEYCODE_BUTTON_B)
        waitGone("Toutes les consoles", substring = true)
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        waitText("Toutes les consoles", substring = true)
        screenshot("library-landscape")
        // Recherche (bouton Y) : le jeu du test, quel que soit le contenu de la bibliothèque.
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        val search = rule.onNode(hasSetTextAction())
        search.performTextInput(title)
        waitText(title)
        rule.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes()
        search.performImeAction()

        // Le jeu est choisi sur l’accueil ; toucher sa tuile lance l’action : ici le téléchargement,
        // vérifié par son SHA-256.
        waitText("À télécharger", substring = true)
        clickDescription(title)
        waitUntil(60_000, "jeu téléchargé") { runBlocking { container.database.roms().find(gameId) } != null }
        val local = runBlocking { container.database.roms().find(gameId) }!!
        val expected = backend.game(gameId)["rom"]!!.jsonObject["sha256"]!!.jsonPrimitive.content
        assertEquals(expected, Backend.sha256(File(local.path).readBytes()))
        assertEquals(Backend.sha256(rom), expected)
        screenshot("selected-landscape")

        // Lancement du cœur NES : la vue émulateur rend des images. Au moins 10 s, pour compter.
        clickDescription(title)
        waitFrames(120)
        Thread.sleep(11_000)
        screenshot("game-landscape")
        // Rotation, advanced options and background saves also run against the optimized build.
        device.setOrientationNatural()
        waitFrames(180)
        screenshot("game-portrait")
        device.setOrientationLandscape()
        pad(KeyEvent.KEYCODE_MENU)
        click("Options de l’émulateur")
        waitText("Retour")
        click("Retour")
        click("Reprendre")
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
        waitText("À choisir", timeout = 20_000)
        click("À choisir")
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
        waitText("REPRENDRE")
        clickDescription(title)
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
        click("Commencer")
        val address = rule.onNode(hasSetTextAction() and hasText("Adresse du serveur"))
        address.performTextInput(serverUrl)
        address.performImeAction()
        waitText("Relier ce téléphone")
        rule.onNode(hasSetTextAction() and hasText("Code d’appairage")).performTextInput(code.replace("-", ""))
        val name = rule.onNode(hasSetTextAction() and hasText("Nom de cet appareil"))
        name.performTextClearance()
        name.performTextInput(deviceName)
        click("Relier cet appareil")
    }

    private fun finishSetup() {
        waitText("Bienvenue,", timeout = 20_000, substring = true)
        click("Continuer")
        waitText("Performances")
        click("Continuer")
        waitText("Confort")
        click("Continuer")
        waitText("Découvrir ma bibliothèque")
        click("Découvrir ma bibliothèque")
    }

    private fun saveStateInSlot(slot: Int) {
        clickDescription("Menu du jeu")
        waitText("Sauvegarder la partie")
        click("Sauvegarder la partie")
        waitText("Emplacement $slot")
        click("Emplacement $slot")
        waitGone("Choisissez un emplacement")
        Thread.sleep(500)
    }

    private fun quitGame() {
        clickDescription("Menu du jeu")
        waitText("Quitter")
        click("Quitter")
        // Confirmation : le bouton de la fenêtre est le dernier « Quitter » affiché.
        waitText("Quitter la partie ?")
        rule.onAllNodesWithText("Quitter").fetchSemanticsNodes().size.let { count -> rule.onAllNodesWithText("Quitter")[count - 1].performClick() }
        waitUntil(20_000, "fin de la partie") { GameActivity.current?.get()?.isDestroyed != false }
        rule.waitForIdle()
    }

    private fun waitFrames(count: Long) = waitUntil(30_000, "images du cœur") { (GameActivity.current?.get()?.frames?.value ?: 0) >= count }

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

    private fun waitUntil(timeout: Long, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(300)
        }
        screenshot("echec-attente")
        throw AssertionError("Délai dépassé : $what")
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
