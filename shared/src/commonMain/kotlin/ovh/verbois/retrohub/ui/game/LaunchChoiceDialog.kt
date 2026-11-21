package ovh.verbois.retrohub.ui.game

import androidx.compose.runtime.Composable
import ovh.verbois.retrohub.ui.UiController
import ovh.verbois.retrohub.ui.components.ConfirmDialog

/** One explicit choice prompt for launch checks and notification entry, in both orientations. */
@Composable
internal fun LaunchChoiceDialog(controller: UiController) {
    val issue = controller.launchIssue ?: return
    ConfirmDialog(
        "Quelle partie reprendre ?",
        "Plusieurs progressions sont disponibles. Choisissez celle à garder pour continuer.",
        "Choisir",
        onConfirm = {
            if (controller.launchIssue == issue) {
                controller.launchIssue = null
                controller.nav.go(issue)
            }
        },
        onDismiss = { controller.launchIssue = null },
        focusDismiss = true,
    )
}
