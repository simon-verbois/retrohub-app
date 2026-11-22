package ovh.verbois.retrohub.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.RetroHubApp
import ovh.verbois.retrohub.ui.components.ReportDialogs
import ovh.verbois.retrohub.ui.theme.RetroHubTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReportDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as RetroHubApp).container
    private var reportId: String? = null

    @After
    fun cleanup() {
        runBlocking(Dispatchers.IO) {
            container.reportRequest.value = null
            reportId?.let { container.platform.reportCapture?.remove(it) }
        }
    }

    @Test fun aLocalReportCanBePreviewedAndDeletedWithoutSending() {
        rule.settleOrientation(portrait = false)
        val capture = container.platform.reportCapture!!
        runBlocking {
            container.start()
            reportId = capture.createReport(null).id
        }
        container.reportRequest.value = reportId
        rule.setContent { RetroHubTheme { ReportDialogs(container) } }
        rule.onNodeWithText("Partager le rapport complet").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Envoyer le rapport").assertDoesNotExist()
        rule.onNodeWithText("Envoyer le diagnostic").assertDoesNotExist()
        rule.onNodeWithText("Voir le rapport").performScrollTo().performClick()
        rule.onNodeWithText("Masquer le rapport").assertIsDisplayed()
        rule.onNodeWithText("Fermer").assertIsDisplayed()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "test-screens").apply { mkdirs() }
        File(directory, "report-local-landscape.png").outputStream().use { stream ->
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        check(capture.reports.value.first { it.id == reportId }.approved == null)
        rule.onNodeWithText("Supprimer de l’appareil").performScrollTo().performClick()
        rule.waitUntil(5000) { capture.reports.value.none { it.id == reportId } }
    }
}
