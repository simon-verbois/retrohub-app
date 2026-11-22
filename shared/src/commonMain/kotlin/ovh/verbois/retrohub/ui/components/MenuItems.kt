package ovh.verbois.retrohub.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ovh.verbois.retrohub.ui.icons.Lucide
import ovh.verbois.retrohub.ui.theme.Palette

/** Shared popup sizing and surface for game actions, filters, navigation and core choices. */
@Composable
fun ConsoleDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalOverlayControls provides true) {
        DropdownMenu(
            expanded, onDismissRequest,
            modifier.widthIn(min = 220.dp, max = 360.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = Palette.Deep,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Palette.Line),
            content = content,
        )
    }
}

/** Menus use the same A/Enter activation and focus feedback as the rest of the console. */
@Composable
fun ConsoleMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Palette.Text,
    selected: Boolean? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { if (selected != null) this.selected = selected }
            .consoleFocus(RoundedCornerShape(12.dp), onClick, enabled = enabled, focusScale = 1f).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = tint)
        if (selected == true) Icon(Lucide.Check, null, Modifier.size(18.dp), tint = tint)
    }
}
