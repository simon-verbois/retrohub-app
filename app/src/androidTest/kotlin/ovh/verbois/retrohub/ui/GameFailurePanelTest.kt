package ovh.verbois.retrohub.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.verbois.retrohub.ui.play.GameFailurePanel
import ovh.verbois.retrohub.ui.theme.RetroHubTheme

@RunWith(AndroidJUnit4::class)
class GameFailurePanelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun aFailedGameOffersReportingAndReturnWithoutResumeOrSaveActions() {
        rule.settleOrientation(portrait = false)
        var returned = 0
        var reported = 0
        rule.setContent {
            RetroHubTheme {
                GameFailurePanel("Votre dernière sauvegarde valide est conservée.", { returned++ }, { reported++ })
            }
        }
        rule.onNodeWithText("Jeu interrompu").assertIsDisplayed()
        rule.onNodeWithText("Reprendre").assertDoesNotExist()
        rule.onNodeWithText("Sauvegarder la partie").assertDoesNotExist()
        rule.onNodeWithText("Signaler un problème").performClick()
        assertEquals(1, reported)
        rule.onNodeWithText("Retour à l’accueil").performClick()
        assertEquals(1, returned)
    }
}
