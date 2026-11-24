package ovh.verbois.retrohub.ui

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.AppContainer
import ovh.verbois.retrohub.MainActivity
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.account.Account
import ovh.verbois.retrohub.api.ApiJson
import ovh.verbois.retrohub.api.LibraryGame
import ovh.verbois.retrohub.core.sha256
import ovh.verbois.retrohub.data.GameEntity
import ovh.verbois.retrohub.data.LocalSave
import ovh.verbois.retrohub.emulation.AppSettings
import ovh.verbois.retrohub.emulation.InterfaceOrientation
import ovh.verbois.retrohub.platform.Notifications
import ovh.verbois.retrohub.work.SyncWorker
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotificationNavigationTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as RetroHubApp).container
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val ids = listOf("notice-a", "notice-b")
    private lateinit var previousSettings: AppSettings
    private var previousAccount: String? = null
    private var previousToken: String? = null
    private var previousGames = emptyList<GameEntity>()

    @Before fun prepare() {
        runBlocking {
            container.start()
            previousSettings = container.settings.settings.value
            previousAccount = container.keyValues.string("account")
            previousToken = container.platform.tokenVault.read()
            previousGames = container.database.games().all()
            if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            container.settings.updateNow {
                it.copy(
                    setupDone = true,
                    autoSync = false,
                    startOnLastGame = false,
                    animatedBackground = false,
                    interfaceOrientation = InterfaceOrientation.LANDSCAPE,
                )
            }
            container.keyValues.put(
                "account",
                Account.serializer(),
                Account("http://127.0.0.1:1", userId = "notice", username = "Test", deviceId = "notice", deviceName = "Test"),
            )
            container.platform.tokenVault.write("notification-test-token")
            container.account.load()
            container.database.games().replaceAll(
                ids.mapIndexed { index, id ->
                    val game = LibraryGame(id, id, title = if (index == 0) "Jeu Alpha" else "Jeu Beta", console = "nes", favorite = true)
                    GameEntity(id, id, game.title, game.console, true, null, 0, index, ApiJson.encodeToString(LibraryGame.serializer(), game))
                },
            )
            ids.forEach { id ->
                val local = "local-$id".encodeToByteArray()
                val remote = "server-$id".encodeToByteArray()
                val save = LocalSave(
                    id, "sram", 0, "nes", baseRevision = 1, baseSha = "base", localSha = sha256(local),
                    localPlayedAt = "2026-11-20T10:00:00Z", conflictRevision = 2, conflictSha = sha256(remote),
                    conflictPlayedAt = "2026-11-21T10:00:00Z", conflictDevice = "Autre téléphone",
                )
                container.saveStore.put(save)
                val file = container.saveStore.file(id, "sram", 0)
                container.fs.createDirectories(file.parent!!)
                container.fs.write(file) { write(local) }
                container.fs.write(container.saveStore.conflictFile(id, "sram", 0)) { write(remote) }
            }
        }
    }

    @After fun restore() {
        scenario?.close()
        notifications.cancel(AppContainer.NOTIFY_CONFLICT)
        notifications.cancel(Notifications.DOWNLOAD_BASE + 1)
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.NAME).result.get()
        runBlocking {
            ids.forEach { id ->
                container.database.saves().find(id, "sram", 0)?.let { container.database.saves().delete(it) }
                container.fs.delete(container.saveStore.file(id, "sram", 0), mustExist = false)
                container.fs.delete(container.saveStore.conflictFile(id, "sram", 0), mustExist = false)
            }
            container.keyValues.putString("account", previousAccount)
            previousToken?.let { container.platform.tokenVault.write(it) } ?: container.platform.tokenVault.clear()
            container.account.load()
            container.database.games().replaceAll(previousGames)
            container.settings.updateNow { previousSettings }
            container.sync.refreshCounts()
        }
    }

    @Test fun postedConflictAndDownloadNotificationsHaveIndependentActions() {
        launch()
        Notifications.show(context, Notifications.DOWNLOAD_BASE + 1, "Téléchargement terminé", "Jeu prêt")
        postConflict()
        val conflict = notifications.activeNotifications.single { it.id == AppContainer.NOTIFY_CONFLICT }.notification.contentIntent
        val download = notifications.activeNotifications.single { it.id == Notifications.DOWNLOAD_BASE + 1 }.notification.contentIntent
        assertNotEquals(conflict, download)
        val before = runBlocking { container.saveStore.all().filter { it.gameId in ids } }
        val beforeFiles = ids.map(::read)
        conflict.send()
        waitText("Quelle partie reprendre ?")
        rule.onNodeWithText("Annuler").performClick()
        waitForWindow()
        assertEquals(before, runBlocking { container.saveStore.all().filter { it.gameId in ids } })
        assertEquals(beforeFiles, ids.map(::read))
        download.send()
        rule.waitForIdle()
        rule.onNodeWithText("Quelle partie reprendre ?").assertDoesNotExist()
        conflict.send()
        waitText("Quelle partie reprendre ?")
    }

    @Test fun launchIntentIsConsumedOnCreationAndNotReplayedAfterRecreation() {
        launch(conflict = true)
        waitText("Quelle partie reprendre ?")
        rule.onNodeWithText("Annuler").performClick()
        waitForWindow()
        scenario!!.recreate()
        waitHome()
        rule.onNodeWithText("Quelle partie reprendre ?").assertDoesNotExist()
    }

    @Test fun summaryChoiceReviewsBothGamesAndWritesOnlyAfterExplicitSelection() {
        launch()
        postConflict()
        notifications.activeNotifications.single { it.id == AppContainer.NOTIFY_CONFLICT }.notification.contentIntent.send()
        waitText("Quelle partie reprendre ?")
        rule.onNodeWithText("Choisir").performClick()
        waitText("Sur ce téléphone")
        assertVersionLabelReadable("Sur ce téléphone")
        rule.onAllNodesWithText("Garder celle-ci")[0].performScrollTo()
        screenshot("notification-local-version")
        waitText("Sur le serveur")
        assertVersionLabelReadable("Sur le serveur")
        assertEquals(listOf(true, true), runBlocking { ids.map { container.saveStore.find(it, "sram", 0)!!.inConflict } })
        rule.onAllNodesWithText("Garder celle-ci")[1].performScrollTo()
        screenshot("notification-save-choice")
        rule.onAllNodesWithText("Garder celle-ci")[1].performScrollTo().performClick()
        rule.waitUntil(5000) { runBlocking { container.saveStore.find("notice-a", "sram", 0)?.inConflict == false } }
        assertEquals("server-notice-a", read("notice-a"))
        assertEquals("local-notice-b", read("notice-b"))
        rule.waitUntil(5000) { rule.onAllNodesWithText("Jeu Beta", substring = true).fetchSemanticsNodes().isNotEmpty() }
        notifications.activeNotifications.single { it.id == AppContainer.NOTIFY_CONFLICT }.notification.contentIntent.send()
        rule.waitForIdle()
        rule.onNodeWithText("Quelle partie reprendre ?").assertDoesNotExist()
        rule.onAllNodesWithText("Garder celle-ci")[1].performScrollTo().performClick()
        waitText("Tout est en ordre")
        assertEquals("server-notice-b", read("notice-b"))
        assertFalse(runBlocking { container.saveStore.find("notice-b", "sram", 0)!!.inConflict })
    }

    @Test fun anAlreadyResolvedNotificationShowsTheCurrentStateWithoutChoices() {
        launch()
        runBlocking { ids.forEach { id -> container.saveStore.put(container.saveStore.find(id, "sram", 0)!!.copy(conflictRevision = null)) } }
        postConflict()
        notifications.activeNotifications.single { it.id == AppContainer.NOTIFY_CONFLICT }.notification.contentIntent.send()
        waitText("Quelle partie reprendre ?")
        rule.onNodeWithText("Choisir").performClick()
        waitText("Tout est en ordre")
        rule.onNodeWithText("Garder celle-ci").assertDoesNotExist()
        assertEquals("local-notice-a", read("notice-a"))
    }

    private fun launch(conflict: Boolean = false) {
        scenario = ActivityScenario.launch(Notifications.openIntent(context, if (conflict) AppContainer.NOTIFY_CONFLICT else Notifications.DOWNLOAD_BASE))
        if (!conflict) waitHome()
    }

    /** The landscape home opens on the catalogue when nothing was played yet: wait for the home itself. */
    private fun waitHome() {
        rule.waitUntil(5000) { rule.onAllNodesWithTag("home-content").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForWindow() {
        rule.waitUntil(5000) {
            var focused = false
            scenario!!.onActivity { focused = it.hasWindowFocus() }
            focused
        }
    }

    private fun postConflict() {
        Notifications.show(
            context,
            AppContainer.NOTIFY_CONFLICT,
            "Une sauvegarde attend votre choix",
            "Choisissez la version à garder.",
            Notifications.CHANNEL_SYNC,
        )
        rule.waitUntil(5000) { notifications.activeNotifications.any { it.id == AppContainer.NOTIFY_CONFLICT } }
    }

    private fun waitText(text: String) {
        rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        val node = rule.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.assertIsDisplayed()
    }

    private fun read(id: String): String = container.fs.read(container.saveStore.file(id, "sram", 0)) { readUtf8() }

    private fun assertVersionLabelReadable(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Missing layout: $text", layouts.isEmpty())
        for (layout in layouts) {
            assertFalse("Clipped label: $text", layout.didOverflowWidth || layout.didOverflowHeight)
            org.junit.Assert.assertTrue("Fragmented label: $text", layout.lineCount <= 2)
        }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val target = File(context.getExternalFilesDir(null), "test-screens/$name.png")
        target.parentFile!!.mkdirs()
        target.outputStream().use { check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
