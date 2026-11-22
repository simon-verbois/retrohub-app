package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.verbois.retrohub.ui.theme.Palette

/** Quiet, compact controls inside transient windows; full-page actions keep their own style. */
internal val LocalOverlayControls = staticCompositionLocalOf { false }
internal val OverlayShape = RoundedCornerShape(20.dp)
internal val OverlayControlShape = RoundedCornerShape(12.dp)

/** Keep the title and actions visible while only long content scrolls, including above the IME. */
@Composable
fun ModalPanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 480.dp,
    initialFocus: FocusRequester? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalOverlayControls provides true) {
            BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
                Column(
                    modifier.align(Alignment.Center).widthIn(max = maxWidth).fillMaxWidth().clip(OverlayShape).background(Palette.Deep)
                        .border(1.dp, Palette.Line, OverlayShape).focusGroup().controllerDialog(onDismiss),
                ) {
                    Text(
                        title,
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    HorizontalDivider(color = Palette.Line)
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                    if (footer != null) {
                        HorizontalDivider(color = Palette.Line)
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) { footer() }
                    }
                }
            }
            if (initialFocus != null) AutoFocus(initialFocus)
        }
    }
}
