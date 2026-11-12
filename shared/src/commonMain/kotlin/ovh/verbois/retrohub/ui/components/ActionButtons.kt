package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Reflow actions from their actual labels, including the current locale and font scale. */
@Composable
fun ActionButtons(
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    primaryIcon: ImageVector? = null,
    secondaryIcon: ImageVector? = null,
    loading: Boolean = false,
    secondaryModifier: Modifier = Modifier,
    entryFocus: FocusRequester? = null,
    parentFocus: FocusRequester? = null,
    primaryModifier: Modifier = Modifier,
) {
    val primaryFocus = remember { FocusRequester() }
    val secondaryFocus = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val requiredWidth = with(density) {
            val primaryWidth =
                measurer.measure(primary, style, softWrap = false).size.width.toDp() + 48.dp + if (primaryIcon != null || loading) 32.dp else 0.dp
            val secondaryWidth =
                secondary?.let { measurer.measure(it, style, softWrap = false).size.width.toDp() + 40.dp + if (secondaryIcon != null) 30.dp else 0.dp } ?: 0.dp
            primaryWidth + secondaryWidth + 16.dp
        }
        val horizontal = secondary != null && requiredWidth <= maxWidth
        val primaryNavigation = if (parentFocus != null) {
            Modifier.focusRequester(primaryFocus).focusProperties {
                up = parentFocus
                down = if (!horizontal && secondary != null) secondaryFocus else FocusRequester.Cancel
                left = if (horizontal) secondaryFocus else FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
        } else {
            Modifier
        }
        val secondaryNavigation = if (parentFocus != null) {
            Modifier.focusRequester(secondaryFocus).focusProperties {
                up = if (horizontal) parentFocus else primaryFocus
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = if (horizontal) primaryFocus else FocusRequester.Cancel
            }
        } else {
            Modifier
        }
        val mainModifier = primaryModifier.then(primaryNavigation).then(
            if (entryFocus != null &&
                !horizontal
            ) {
                Modifier.focusRequester(entryFocus)
            } else {
                Modifier
            },
        )
        val otherModifier = secondaryModifier.then(secondaryNavigation)
            .then(if (entryFocus != null && horizontal) Modifier.focusRequester(entryFocus) else Modifier)
        if (horizontal && secondary != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton(secondary, onSecondary, otherModifier, icon = secondaryIcon)
                PrimaryButton(primary, onPrimary, Modifier.weight(1f).then(mainModifier), icon = primaryIcon, loading = loading)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton(primary, onPrimary, Modifier.fillMaxWidth().then(mainModifier), icon = primaryIcon, loading = loading)
                if (secondary != null) GlassButton(secondary, onSecondary, otherModifier, icon = secondaryIcon)
            }
        }
    }
}
